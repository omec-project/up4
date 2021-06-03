# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
#

# ------------------------------------------------------------------------------
# SPGWU BASE TEST
#
# No actual tests are located here. Tests will inherit GtpuBaseTest
# ------------------------------------------------------------------------------

from base_test import pkt_route, pkt_decrement_ttl, P4RuntimeTest, \
                      autocleanup, print_inline
from ptf.testutils import group
from ptf import testutils as testutils
from scapy.contrib import gtp
from scapy.all import IP, IPv6, TCP, UDP, ICMP, Ether
from time import sleep
from enum import Enum
import random

random.seed(123456)  # for reproducible PTF tests

UDP_GTP_PORT = 2152
DHCP_SERVER_PORT = 67
DHCP_CLIENT_PORT = 68


class GtpuBaseTest(P4RuntimeTest):

    supported_l4 = ["udp", "tcp", "icmp"]

    def random_mac_addr(self):
        octets = [random.randint(0, 0xff) for _ in range(6)]
        octets[0] = 0x32  # arbitrary valid starting byte for a MAC addr
        return ':'.join(["%02x" % octet for octet in octets])

    def random_ip_addr(self):
        octets = [random.randint(0, 255) for _ in range(4)]
        return '.'.join([str(octet) for octet in octets])

    _last_used_rule_id = -1

    def unique_rule_id(self):
        """ Stupid helper method for generating unique ruleIDs.
        """
        self._last_used_rule_id += 1
        return self._last_used_rule_id

    def new_counter_id(self):
        """ Stupider helper method for generating unique counter ID
        """
        return self.unique_rule_id()

    def randint(self, limit):
        """ Method exists here so we can de-randomize quickly for debugging,
            and so other files use this file's seed.
        """
        return random.randint(0, limit - 1)

    def gtpu_encap(self, pkt, ip_ver=4, ip_src=None, ip_dst=None, teid=None):
        """ Adds IP, UDP, and GTP-U headers to the packet situated after the ethernet header.
            Tunnel IP header is v4 if ip_ver==4, else it is v6.
            Params ip_src and ip_dst are the tunnel endpoints, and teid is the tunnel ID.
            If a source, dest, or teid is not provided, they will be randomized.
        """
        ether_payload = pkt[Ether].payload

        IPHeader = IP if ip_ver == 4 else IPv6

        if teid is None:
            teid = random.randint(0, 1023)
        if ip_src is None:
            ip_src = self.random_ip_addr()
        if ip_dst is None:
            ip_dst = self.random_ip_addr()

        return Ether(src=pkt[Ether].src, dst=pkt[Ether].dst) / \
               IPHeader(src=ip_src, dst=ip_dst, id=5395) / \
               UDP(sport=UDP_GTP_PORT, dport=UDP_GTP_PORT, chksum=0) / \
               gtp.GTP_U_Header(gtp_type=255, teid=teid) / \
               ether_payload

    def gtpu_decap(self, pkt):
        """ Strips out the outer IP, UDP, and GTP-U headers from the given packet.
        """
        # isolate the ethernet header
        ether_header = pkt.copy()
        ether_header[Ether].remove_payload()

        # discard the tunnel layers
        return ether_header / pkt[gtp.GTP_U_Header].payload

    _last_read_counter_values = {}

    def read_pdr_counters(self, index, wait_time=0.1):
        sleep(wait_time)
        self._last_read_counter_values[index] = [
            self.read_pdr_counter(index, pre_qos=True, pkts=True),
            self.read_pdr_counter(index, pre_qos=True, pkts=False),
            self.read_pdr_counter(index, pre_qos=False, pkts=True),
            self.read_pdr_counter(index, pre_qos=False, pkts=False)
        ]
        return self._last_read_counter_values[index]

    def verify_counters_increased(self, index, prePktsInc, preBytesInc, postPktsInc, postBytesInc,
                                  wait_time=0.1):
        """ Verify that both the Pre- and Post-QoS counters increased:
            - Pre-Qos pkt count increased by prePktsInc
            - Pre-Qos byte count increased by preBytesInc
            - Post-Qos pkt count increased by postPktsInc
            - Post-Qos byte count increased by postBytesInc
        """
        old_vals = self._last_read_counter_values.get(index, [0, 0, 0, 0])
        new_vals = self.read_pdr_counters(index, wait_time=wait_time)
        increases = [new_vals[i] - old_vals[i] for i in range(4)]
        if increases[0] != prePktsInc:
            self.fail("Pre-QoS pkt counter increased by %d, not %d!" % (increases[0], prePktsInc))
        if increases[1] != preBytesInc:
            self.fail("Pre-QoS byte counter increased by %d, not %d!" % (increases[1], preBytesInc))
        if increases[2] != postPktsInc:
            self.fail("Post-QoS pkt counter increased by %d, not %d!" % (increases[2], postPktsInc))
        if increases[3] != postBytesInc:
            self.fail("Post-QoS byte counter increased by %d, not %d!" %
                      (increases[3], postBytesInc))

    def read_pdr_counter(self, index, pre_qos=True, pkts=True):
        """ Reads the per-PDR counter.
            If pre_qos=True, reads the pre-QoS counter. Else  reads the post-QoS counter
            If pkts=True, returns packet count for the selected counter. Else returns byte count.
        """
        counter_name = "PreQosPipe.pre_qos_pdr_counter"
        if not pre_qos:
            counter_name = "PostQosPipe.post_qos_pdr_counter"

        if pkts:
            return self.helper.read_pkt_count(counter_name, index)
        else:
            return self.helper.read_byte_count(counter_name, index)

    def add_device_mac(self, mac_addr):
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.my_station",
                match_fields={"dst_mac": mac_addr},
                action_name="NoAction",
                action_params={},
            ))

    def add_routing_ecmp_group(self, ip_prefix, mac_ports):

        group_id = self.helper.get_next_grp_id()

        self.insert(
            self.helper.build_act_prof_group(
                act_prof_name="hashed_selector", group_id=group_id,
                actions=[("PreQosPipe.Routing.route", {
                    "src_mac": mac_port[0],
                    "dst_mac": mac_port[1],
                    "egress_port": mac_port[2]
                }) for mac_port in mac_ports]))

        self.insert(
            self.helper.build_table_entry(table_name="PreQosPipe.Routing.routes_v4",
                                          match_fields={"dst_prefix": ip_prefix},
                                          group_id=group_id))

    def add_routing_entry(self, ip_prefix, src_mac, dst_mac, egress_port):
        return self.add_routing_ecmp_group(ip_prefix, [(src_mac, dst_mac, egress_port)])

    def add_interface(self, ip_prefix, iface_type, direction):
        """ Binds a destination prefix 3GPP interface.
        """
        _iface_type = self.helper.get_enum_member_val("InterfaceType", iface_type)
        _direction = self.helper.get_enum_member_val("Direction", direction)

        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.source_iface_lookup",
                match_fields={"ipv4_dst_prefix": ip_prefix},
                action_name="PreQosPipe.set_source_iface",
                action_params={
                    "src_iface": _iface_type,
                    "direction": _direction
                },
            ))

    def add_global_session(self, global_session_id=1025, n4_teid=1025, default_pdr_id=0,
                           default_far_id=0, default_ctr_id=0, n4_ip=None, smf_ip="192.168.1.52",
                           smf_mac="0a:0b:0c:0d:0e:0f", smf_port=None, miss_pdr_ctr_id=None,
                           dhcp_req_ctr_id=None):

        if smf_port is None:
            smf_port = self.port4

        ALL_ONES_32 = (1 << 32) - 1
        ALL_ONES_16 = (1 << 16) - 1
        ALL_ONES_8 = (1 << 8) - 1

        # Default PDR drops
        drop_far_id = self.unique_rule_id()
        miss_pdr_id = self.unique_rule_id()
        self.miss_pdr_ctr_id = miss_pdr_ctr_id or self.new_counter_id()
        self.modify(
            self.helper.build_table_entry(
                table_name="PreQosPipe.pdrs", default_action=True,
                action_name="PreQosPipe.set_pdr_attributes", action_params={
                    "id": miss_pdr_id,
                    "ctr_id": self.miss_pdr_ctr_id,
                    "fseid": global_session_id,
                    "far_id": drop_far_id,
                    "needs_gtpu_decap": False,
                    "needs_udp_decap": False,
                }))
        self.add_far(far_id=drop_far_id, session_id=global_session_id, drop=True)

        # Redirect DHCP messages from UEs to the SMF via N4
        dhcp_req_pdr_id = self.unique_rule_id()
        self.dhcp_req_ctr_id = dhcp_req_ctr_id or self.new_counter_id()

        towards_smf_far_id = self.unique_rule_id()

        self.add_pdr(pdr_id=dhcp_req_pdr_id, far_id=towards_smf_far_id,
                     session_id=global_session_id, src_iface="ACCESS", ctr_id=self.dhcp_req_ctr_id,
                     inet_l4_port=DHCP_SERVER_PORT, needs_gtpu_decap=True)

        self.add_far(far_id=towards_smf_far_id, session_id=global_session_id, tunnel=True,
                     teid=n4_teid, src_addr=n4_ip, dst_addr=smf_ip)

        self.add_routing_entry(ip_prefix=smf_ip + '/32', dst_mac=smf_mac, egress_port=smf_port)

    def add_cpu_clone_session(self, cpu_clone_session_id=99):
        self.insert_pre_clone_session(cpu_clone_session_id, [self.cpu_port])

    # yapf: disable
    def add_acl_entry(self,
                      punt=False, clone_to_cpu=False, set_port=False, drop=False,
                      inport = None, inport_mask = None,
                      src_iface = None, src_iface_mask = None,
                      eth_src  = None, eth_src_mask = None,
                      eth_dst  = None, eth_dst_mask = None,
                      eth_type = None, eth_type_mask = None,
                      ipv4_src = None, ipv4_src_mask = None,
                      ipv4_dst = None, ipv4_dst_mask = None,
                      ipv4_proto = None, ipv4_proto_mask = None,
                      l4_sport = None, l4_sport_mask = None,
                      l4_dport = None, l4_dport_mask = None,
                      priority = 10, outport = None
                      ):
        ALL_ONES_48 = (1 << 48) - 1
        ALL_ONES_32 = (1 << 32) - 1
        ALL_ONES_16 = (1 << 16) - 1
        ALL_ONES_9  = (1 << 9) - 1
        ALL_ONES_8  = (1 << 8) - 1

        match_keys = {}
        if inport is not None:
            match_keys["inport"] = (inport, inport_mask or ALL_ONES_9)
        if src_iface is not None:
            match_keys["src_iface"] = (src_iface, src_iface_mask or ALL_ONES_8)
        if eth_src is not None:
            match_keys["eth_src"] = (eth_src, eth_src_mask or ALL_ONES_48)
        if eth_dst is not None:
            match_keys["eth_dst"] = (eth_dst, eth_dst_mask or ALL_ONES_48)
        if eth_type is not None:
            match_keys["eth_type"] = (eth_type, eth_type_mask or ALL_ONES_16)
        if ipv4_src is not None:
            match_keys["ipv4_src"] = (ipv4_src, ipv4_src_mask or ALL_ONES_32)
        if ipv4_dst is not None:
            match_keys["ipv4_dst"] = (ipv4_dst, ipv4_dst_mask or ALL_ONES_32)
        if ipv4_proto is not None:
            match_keys["ipv4_proto"] = (ipv4_proto, ipv4_proto_mask or ALL_ONES_8)
        if l4_sport is not None:
            match_keys["l4_sport"] = (l4_sport, l4_sport_mask or ALL_ONES_16)
        if l4_dport is not None:
            match_keys["l4_dport"] = (l4_dport, l4_dport_mask or ALL_ONES_16)

        if sum([punt, clone_to_cpu, set_port, drop]) != 1:
            raise Exception("add_acl_entry did not receive exactly 1 action.")
        action_name = None
        action_params = {}
        if punt:
            action_name = "PreQosPipe.Acl.punt"
        elif clone_to_cpu:
            action_name = "PreQosPipe.Acl.clone_to_cpu"
        elif set_port:
            action_name = "PreQosPipe.Acl.set_port"
            action_params = {"port": outport}
        elif drop:
            action_name = "PreQosPipe.Acl.drop"

        self.insert(
            self.helper.build_table_entry(
                table_name = "PreQosPipe.Acl.acls",
                match_fields = match_keys,
                action_name = action_name,
                action_params = action_params,
                priority=priority
            ))

        # yapf: enable

    def add_pdr(self, pdr_id, far_id, session_id, src_iface, ctr_id, ue_addr=None, ue_mask=None,
                teid=None, tunnel_dst_ip=None, inet_addr=None, inet_mask=None, ue_l4_port=None,
                ue_l4_port_hi=None, inet_l4_port=None, inet_l4_port_hi=None, ip_proto=None,
                ip_proto_mask=None, needs_gtpu_decap=False, priority=10):

        ALL_ONES_32 = (1 << 32) - 1
        ALL_ONES_8 = (1 << 8) - 1

        _src_iface = self.helper.get_enum_member_val("InterfaceType", src_iface)
        match_fields = {"src_iface": _src_iface}
        if ue_addr is not None:
            match_fields["ue_addr"] = (ue_addr, ue_mask or ALL_ONES_32)
        if inet_addr is not None:
            match_fields["inet_addr"] = (inet_addr, inet_mask or ALL_ONES_32)
        if ue_l4_port is not None:
            match_fields["ue_l4_port"] = (ue_l4_port, ue_l4_port_hi or ue_l4_port)
        if inet_l4_port is not None:
            match_fields["inet_l4_port"] = (inet_l4_port, inet_l4_port_hi or inet_l4_port)
        if ip_proto is not None:
            match_fields["ip_proto"] = (ip_proto, ip_proto_mask or ALL_ONES_8)
        if teid is not None:
            match_fields["teid"] = (teid, ALL_ONES_32)
        if tunnel_dst_ip is not None:
            match_fields["tunnel_ipv4_dst"] = (tunnel_dst_ip, ALL_ONES_32)

        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.pdrs",
                match_fields=match_fields,
                action_name="PreQosPipe.set_pdr_attributes",
                action_params={
                    "id": pdr_id,
                    "fseid": session_id,
                    "far_id": far_id,
                    "needs_gtpu_decap": needs_gtpu_decap,
                    "ctr_id": ctr_id,
                },
                priority=priority,
            ))

    def remove_far(self, far_id, session_id):
        self.delete(
            self.helper.build_table_entry(
                table_name="PreQosPipe.fars", match_fields={
                    "far_id": far_id,
                    "session_id": session_id,
                }))

    def add_far(self, far_id, session_id, drop=False, notify_cp=False, tunnel=False,
                tunnel_type="GTPU", teid=None, src_addr=None, dst_addr=None, sport=2152,
                buffer=False):

        if tunnel:
            if (None in [src_addr, dst_addr, sport]):
                raise Exception("src_addr, dst_addr, and sport cannot be None for a tunnel FAR")
            if tunnel_type == "GTPU" and teid is None:
                raise Exception("TEID cannot be None for GTPU tunnel")
            _tunnel_type = self.helper.get_enum_member_val("TunnelType", tunnel_type)

            action_params = {
                "needs_dropping": drop,
                "needs_buffering": buffer,
                "notify_cp": notify_cp,
                "tunnel_type": _tunnel_type,
                "src_addr": src_addr,
                "dst_addr": dst_addr,
                "teid": teid,
                "sport": sport
            }
            action_name = "PreQosPipe.load_tunnel_far_attributes"
        else:
            action_params = {"needs_dropping": drop, "notify_cp": notify_cp}
            action_name = "PreQosPipe.load_normal_far_attributes"

        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.load_far_attributes", match_fields={
                    "far_id": far_id,
                    "session_id": session_id,
                }, action_name=action_name, action_params=action_params))

    def add_entries_for_uplink_pkt(self, pkt, exp_pkt, inport, outport, ctr_id, drop=False,
                                   session_id=None, pdr_id=None, far_id=None):
        """ Add all table entries required for the given uplink packet to flow through the UPF
            and emit as the given expected packet.
        """
        if session_id is None:
            session_id = random.randint(0, 1023)

        inner_pkt = pkt[gtp.GTP_U_Header].payload

        if pdr_id is None:
            pdr_id = self.unique_rule_id()
        if far_id is None:
            far_id = self.unique_rule_id()

        ue_l4_port = None
        inet_l4_port = None
        if (UDP in inner_pkt) or (TCP in inner_pkt):
            ue_l4_port = inner_pkt.sport
            net_l4_port = inner_pkt.dport

        self.add_device_mac(pkt[Ether].dst)

        self.add_interface(ip_prefix=pkt[IP].dst + '/32', iface_type="ACCESS", direction="UPLINK")

        #self.add_session(session_id=session_id, ue_addr=inner_pkt[IP].src)

        self.add_pdr(
            pdr_id=pdr_id,
            far_id=far_id,
            session_id=session_id,
            src_iface="ACCESS",
            ctr_id=ctr_id,
            ue_addr=inner_pkt[IP].src,
            inet_addr=inner_pkt[IP].dst,
            ue_l4_port=ue_l4_port,
            inet_l4_port=inet_l4_port,
            ip_proto=inner_pkt[IP].proto,
            needs_gtpu_decap=True,
        )

        self.add_far(far_id=far_id, session_id=session_id, drop=drop)
        if not drop:
            self.add_routing_entry(ip_prefix=exp_pkt[IP].dst + '/32', src_mac=exp_pkt[Ether].src,
                                   dst_mac=exp_pkt[Ether].dst, egress_port=outport)

    def add_entries_for_downlink_pkt(self, pkt, exp_pkt, inport, outport, ctr_id, drop=False,
                                     buffer=False, session_id=None, pdr_id=None, far_id=None):
        """ Add all table entries required for the given downlink packet to flow through the UPF
            and emit as the given expected packet.
        """
        if session_id is None:
            session_id = random.randint(0, 1023)

        # pkt should not be encapsulated, but exp_pkt should be
        if gtp.GTP_U_Header in pkt:
            raise AssertionError("Attempting to inject encapsulated packet in uplink test!")
        if gtp.GTP_U_Header not in exp_pkt:
            raise AssertionError(
                "Expected output packet provided for downlink test is not encapsulated!")

        if pdr_id is None:
            pdr_id = self.unique_rule_id()
        if far_id is None:
            far_id = self.unique_rule_id()

        ue_l4_port = None
        inet_l4_port = None
        if (UDP in pkt) or (TCP in pkt):
            ue_l4_port = pkt.dport
            inet_l4_port = pkt.sport

        self.add_device_mac(pkt[Ether].dst)

        self.add_interface(ip_prefix=pkt[IP].dst + '/32', iface_type="CORE", direction="DOWNLINK")

        #self.add_session(session_id=session_id, ue_addr=pkt[IP].dst)

        self.add_pdr(
            pdr_id=pdr_id,
            far_id=far_id,
            session_id=session_id,
            src_iface="CORE",
            ctr_id=ctr_id,
            ue_addr=pkt[IP].dst,
            inet_addr=pkt[IP].src,
            ue_l4_port=ue_l4_port,
            inet_l4_port=inet_l4_port,
            ip_proto=pkt[IP].proto,
            needs_gtpu_decap=False,
        )

        self.add_far(far_id=far_id, session_id=session_id, drop=drop, buffer=buffer, tunnel=True,
                     teid=exp_pkt[gtp.GTP_U_Header].teid, src_addr=exp_pkt[IP].src,
                     dst_addr=exp_pkt[IP].dst)
        if not drop:
            self.add_routing_entry(ip_prefix=exp_pkt[IP].dst + '/32', src_mac=exp_pkt[Ether].src,
                                   dst_mac=exp_pkt[Ether].dst, egress_port=outport)

    def set_up_ddn_digest(self, ack_timeout_ns):
        # No timeout, not batching. Not recommended for production.
        self.insert(
            self.helper.build_digest_entry(digest_name="ddn_digest_t", max_timeout_ns=0,
                                           max_list_size=1, ack_timeout_ns=ack_timeout_ns))
