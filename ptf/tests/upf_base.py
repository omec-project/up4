# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: Apache-2.0
#

# ------------------------------------------------------------------------------
# UPF BASE TEST
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

GTPU_EXT_PSC_TYPE_DL = 0
GTPU_EXT_PSC_TYPE_UL = 1

DEFAULT_SLICE = 0
MOBILE_SLICE = 15

APP_ID_UNKNOWN = 0
APP_ID = 10

DEFAULT_SESSION_METER_IDX = 0
DEFAULT_APP_METER_IDX = 0

BURST_DURATION_MS = 100

IP_PROTO_UDP = 0x11
IP_PROTO_TCP = 0x06
IP_PROTO_ICMP = 0x01

DIRECTION_UPLINK = "UL"
DIRECTION_DOWNLINK = "DL"


class GtpuBaseTest(P4RuntimeTest):

    supported_l4 = ["udp", "tcp", "icmp"]

    def random_mac_addr(self):
        octets = [random.randint(0, 0xff) for _ in range(6)]
        octets[0] = 0x32  # arbitrary valid starting byte for a MAC addr
        return ':'.join(["%02x" % octet for octet in octets])

    def random_ip_addr(self):
        octets = [random.randint(0, 255) for _ in range(4)]
        return '.'.join([str(octet) for octet in octets])

    _last_used_rule_id = 0

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

    def gtpu_encap(
        self,
        pkt,
        ip_ver=4,
        ip_src=None,
        ip_dst=None,
        teid=None,
        ext_psc_type=None,
        ext_psc_qfi=None,
    ):
        """ Adds IP, UDP, and GTP-U headers to the packet situated after the ethernet header.
            Tunnel IP header is v4 if ip_ver==4, else it is v6.
            Params ip_src and ip_dst are the tunnel endpoints, and teid is the tunnel ID.
            If a source, dest, or teid is not provided, they will be randomized.
            If ext_psc_type is add the PSC ext header with given QFI.
        """
        ether_payload = pkt[Ether].payload

        IPHeader = IP if ip_ver == 4 else IPv6

        if teid is None:
            teid = random.randint(0, 1023)
        if ip_src is None:
            ip_src = self.random_ip_addr()
        if ip_dst is None:
            ip_dst = self.random_ip_addr()

        gtp_pkt = Ether(src=pkt[Ether].src, dst=pkt[Ether].dst) / \
                    IPHeader(src=ip_src, dst=ip_dst, id=5395) / \
                    UDP(sport=UDP_GTP_PORT, dport=UDP_GTP_PORT, chksum=0) / \
                    gtp.GTP_U_Header(gtp_type=255, teid=teid)
        if ext_psc_type is not None:
            gtp_pkt = gtp_pkt / gtp.GTPPDUSessionContainer(type=ext_psc_type, QFI=ext_psc_qfi)
        return gtp_pkt / ether_payload

    def gtpu_decap(self, pkt):
        """ Strips out the outer IP, UDP, GTP-U and GTP-U ext headers from the
            given packet.
        """
        # isolate the ethernet header
        ether_header = pkt.copy()
        ether_header[Ether].remove_payload()
        if gtp.GTPPDUSessionContainer in pkt:
            payload = pkt[gtp.GTPPDUSessionContainer].payload
        elif gtp.GTP_U_Header in pkt:
            payload = pkt[gtp.GTP_U_Header].payload
        # discard the tunnel layers
        return ether_header / payload

    _last_read_counter_values = {}

    def read_upf_counters(self, index, wait_time=0.1):
        sleep(wait_time)
        self._last_read_counter_values[index] = [
            self.read_upf_counter(index, pre_qos=True, pkts=True),
            self.read_upf_counter(index, pre_qos=True, pkts=False),
            self.read_upf_counter(index, pre_qos=False, pkts=True),
            self.read_upf_counter(index, pre_qos=False, pkts=False)
        ]
        return self._last_read_counter_values[index]

    def verify_counters_increased(self, index, prePktsInc, preBytesInc, postPktsInc, postBytesInc,
                                  wait_time=1):
        """ Verify that both the Pre- and Post-QoS counters increased:
            - Pre-Qos pkt count increased by prePktsInc
            - Pre-Qos byte count increased by preBytesInc
            - Post-Qos pkt count increased by postPktsInc
            - Post-Qos byte count increased by postBytesInc
        """
        old_vals = self._last_read_counter_values.get(index, [0, 0, 0, 0])
        new_vals = self.read_upf_counters(index, wait_time=wait_time)
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

    def read_upf_counter(self, index, pre_qos=True, pkts=True):
        """ Reads the UPF counter.
            If pre_qos=True, reads the pre-QoS counter. Else  reads the post-QoS counter
            If pkts=True, returns packet count for the selected counter. Else returns byte count.
        """
        counter_name = "PreQosPipe.pre_qos_counter"
        if not pre_qos:
            counter_name = "PostQosPipe.post_qos_counter"

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

    def add_interface(self, ip_prefix, iface_type, direction, slice_id=DEFAULT_SLICE):
        """ Binds a destination prefix 3GPP interface.
        """
        _iface_type = self.helper.get_enum_member_val("InterfaceType", iface_type)
        _direction = self.helper.get_enum_member_val("Direction", direction)

        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.interfaces",
                match_fields={"ipv4_dst_prefix": ip_prefix},
                action_name="PreQosPipe.set_source_iface",
                action_params={
                    "src_iface": _iface_type,
                    "direction": _direction,
                    "slice_id": slice_id,
                },
            ))

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

    def add_session_uplink(self, n3_addr, teid, session_meter_idx=DEFAULT_SESSION_METER_IDX,
                           drop=False):
        match_fields = {
            "n3_address": n3_addr,
            "teid": teid,
        }
        if drop:
            action_name = "PreQosPipe.set_session_uplink_drop"
            action_params = {}
        else:
            action_name = "PreQosPipe.set_session_uplink"
            action_params = {"session_meter_idx": session_meter_idx}
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.sessions_uplink",
                match_fields=match_fields,
                action_name=action_name,
                action_params=action_params,
            ))

    def add_session_downlink(self, ue_addr, session_meter_idx=DEFAULT_SESSION_METER_IDX,
                             tunnel_peer_id=None, buffer=False, drop=False):
        match_fields = {
            "ue_address": ue_addr,
        }
        action_params = {"session_meter_idx": session_meter_idx}
        if buffer:
            action_name = "PreQosPipe.set_session_downlink_buff"
        elif drop:
            action_name = "PreQosPipe.set_session_downlink_drop"
            action_params = {}
        else:
            action_name = "PreQosPipe.set_session_downlink"
            action_params["tunnel_peer_id"] = tunnel_peer_id
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.sessions_downlink",
                match_fields=match_fields,
                action_name=action_name,
                action_params=action_params,
            ))

    def add_terminations_uplink(self, ue_address, ctr_idx, app_meter_idx=DEFAULT_APP_METER_IDX,
                                tc=None, drop=False, app_id=APP_ID_UNKNOWN):
        match_fields = {
            "ue_address": ue_address,
            "app_id": app_id,
        }
        action_params = {"ctr_idx": ctr_idx}
        if drop:
            action_name = "PreQosPipe.uplink_term_drop"
        else:
            action_params["app_meter_idx"] = app_meter_idx
            if tc is None:
                action_name = "PreQosPipe.uplink_term_fwd_no_tc"
            else:
                action_params["tc"] = tc
                action_name = "PreQosPipe.uplink_term_fwd"
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.terminations_uplink",
                match_fields=match_fields,
                action_name=action_name,
                action_params=action_params,
            ))

    def add_terminations_downlink(self, ue_address, ctr_idx, app_meter_idx=DEFAULT_APP_METER_IDX,
                                  tc=None, teid=None, qfi=None, drop=False, app_id=APP_ID_UNKNOWN):
        match_fields = {
            "ue_address": ue_address,
            "app_id": app_id,
        }
        action_params = {"ctr_idx": ctr_idx}
        if drop:
            action_name = "PreQosPipe.downlink_term_drop"
        else:
            action_params["teid"] = teid
            action_params["qfi"] = qfi
            action_params["app_meter_idx"] = app_meter_idx
            if tc:
                action_params["tc"] = tc
                action_name = "PreQosPipe.downlink_term_fwd"
            else:
                action_name = "PreQosPipe.downlink_term_fwd_no_tc"
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.terminations_downlink",
                match_fields=match_fields,
                action_name=action_name,
                action_params=action_params,
            ))

    def add_application(self, pkt, app_id, direction, priority=10):
        if direction == DIRECTION_UPLINK:
            match_fields = {"app_ip_addr": pkt[IP].dst + "/32"}
        else:
            match_fields = {"app_ip_addr": pkt[IP].src + "/32"}
        l4_port = None
        ip_proto = None
        if UDP in pkt:
            ip_proto = IP_PROTO_UDP
            if direction == DIRECTION_UPLINK:
                l4_port = pkt[UDP].dport
            else:
                l4_port = pkt[UDP].sport
        elif TCP in pkt:
            ip_proto = IP_PROTO_TCP
            if direction == DIRECTION_UPLINK:
                l4_port = pkt[TCP].dport
            else:
                l4_port = pkt[TCP].sport
        elif ICMP in pkt:
            ip_proto = IP_PROTO_ICMP

        if l4_port:
            match_fields["app_l4_port"] = [l4_port, l4_port]
        if ip_proto:
            match_fields["app_ip_proto"] = (ip_proto, (1 << 8) - 1)
        self.insert(
            self.helper.build_table_entry(table_name="PreQosPipe.applications",
                                          match_fields=match_fields,
                                          action_name="PreQosPipe.set_app_id", action_params={
                                              "app_id": app_id,
                                          }, priority=priority))

    def add_tunnel_peer(self, tunnel_peer_id, src_addr, dst_addr, sport=2152):
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.tunnel_peers", match_fields={
                    "tunnel_peer_id": tunnel_peer_id,
                }, action_name="PreQosPipe.load_tunnel_param", action_params={
                    "src_addr": src_addr,
                    "dst_addr": dst_addr,
                    "sport": sport,
                }))

    def __add_meter_helper(self, meter_name, meter_idx, max_bitrate):
        if max_bitrate is None:
            self.modify(self.helper.build_meter_entry(
                meter_name,
                meter_idx,
            ))
        else:
            pir = int(max_bitrate / 8)
            pburst = int((max_bitrate / 8) * BURST_DURATION_MS * 0.001)
            self.modify(
                self.helper.build_meter_entry(
                    meter_name,
                    meter_idx,
                    cir=1,
                    cburst=1,
                    pir=pir if pir > 0 else 1,
                    pburst=pburst if pburst > 0 else 1,
                ))

    def add_entries_for_uplink_pkt(self, pkt, exp_pkt, inport, outport, ctr_idx,
                                   session_meter_idx=DEFAULT_SESSION_METER_IDX,
                                   session_meter_max_bitrate=None,
                                   app_meter_idx=DEFAULT_APP_METER_IDX, app_meter_max_bitrate=None,
                                   tc=0, app_filtering=False, drop=False):
        """ Add all table entries required for the given uplink packet to flow through the UPF
            and emit as the given expected packet.
        """

        inner_pkt = pkt[gtp.GTP_U_Header].payload

        # TODO: restore when we'll support matching on QFI
        # qfi = 0
        # if gtp.GTPPDUSessionContainer in pkt:
        #     qfi = pkt[gtp.GTPPDUSessionContainer].QFI

        self.add_device_mac(pkt[Ether].dst)

        self.add_interface(
            ip_prefix=pkt[IP].dst + '/32',
            iface_type="ACCESS",
            direction="UPLINK",
            slice_id=MOBILE_SLICE,
        )

        self.add_session_uplink(
            n3_addr=pkt[IP].dst,
            teid=pkt[gtp.GTP_U_Header].teid,
            session_meter_idx=session_meter_idx,
        )

        app_id = APP_ID_UNKNOWN
        if app_filtering:
            app_id = APP_ID
            self.add_application(
                pkt=inner_pkt,
                app_id=app_id,
                direction=DIRECTION_UPLINK,
            )

        self.add_terminations_uplink(ue_address=inner_pkt[IP].src, ctr_idx=ctr_idx, tc=tc,
                                     drop=drop, app_id=app_id, app_meter_idx=app_meter_idx)
        if app_meter_max_bitrate is not None:
            self.add_app_meter(app_meter_idx, app_meter_max_bitrate)
        if session_meter_max_bitrate is not None:
            self.add_session_meter(session_meter_idx, session_meter_max_bitrate)
        # Add routing entry even if drop, UPF drop should be perfomed before routing
        self.add_routing_entry(
            ip_prefix=exp_pkt[IP].dst + '/32',
            src_mac=exp_pkt[Ether].src,
            dst_mac=exp_pkt[Ether].dst,
            egress_port=outport,
        )

    def add_entries_for_downlink_pkt(self, pkt, exp_pkt, inport, outport, ctr_idx,
                                     session_meter_idx=DEFAULT_SESSION_METER_IDX,
                                     session_meter_max_bitrate=None,
                                     app_meter_idx=DEFAULT_APP_METER_IDX,
                                     app_meter_max_bitrate=None, drop=False, buffer=False,
                                     tun_id=None, qfi=0, tc=0, push_qfi=False, app_filtering=False):
        """ Add all table entries required for the given downlink packet to flow through the UPF
            and emit as the given expected packet.
        """

        # pkt should not be encapsulated, but exp_pkt should be
        if gtp.GTP_U_Header in pkt:
            raise AssertionError("Attempting to inject encapsulated packet in uplink test!")
        if gtp.GTP_U_Header not in exp_pkt:
            raise AssertionError(
                "Expected output packet provided for downlink test is not encapsulated!")

        if tun_id is None:
            tun_id = self.unique_rule_id()

        self.add_device_mac(pkt[Ether].dst)

        self.add_interface(
            ip_prefix=pkt[IP].dst + '/32',
            iface_type="CORE",
            direction="DOWNLINK",
            slice_id=MOBILE_SLICE,
        )

        self.add_session_downlink(
            ue_addr=pkt[IP].dst,
            tunnel_peer_id=tun_id,
            buffer=buffer,
            drop=buffer and drop,
            session_meter_idx=session_meter_idx,
        )

        self.add_tunnel_peer(
            tunnel_peer_id=tun_id,
            src_addr=exp_pkt[IP].src,
            dst_addr=exp_pkt[IP].dst,
        )

        app_id = APP_ID_UNKNOWN
        if app_filtering:
            app_id = APP_ID
            self.add_application(
                pkt=pkt,
                app_id=app_id,
                direction=DIRECTION_DOWNLINK,
            )

        self.add_terminations_downlink(
            ue_address=pkt[IP].dst,
            ctr_idx=ctr_idx,
            tc=tc,
            teid=exp_pkt[gtp.GTP_U_Header].teid,
            qfi=qfi if push_qfi else 0,
            drop=drop,
            app_id=app_id,
            app_meter_idx=app_meter_idx,
        )
        if app_meter_max_bitrate is not None:
            self.add_app_meter(app_meter_idx, app_meter_max_bitrate)
        if session_meter_max_bitrate is not None:
            self.add_session_meter(session_meter_idx, session_meter_max_bitrate)
        # Add routing entry even if drop, UPF drop should be perfomed before routing
        self.add_routing_entry(
            ip_prefix=exp_pkt[IP].dst + '/32',
            src_mac=exp_pkt[Ether].src,
            dst_mac=exp_pkt[Ether].dst,
            egress_port=outport,
        )

    def add_app_meter(self, app_meter_idx, app_meter_max_bitrate):
        self.__add_meter_helper("PreQosPipe.app_meter", app_meter_idx, app_meter_max_bitrate)

    def add_session_meter(self, session_meter_idx, session_meter_max_bitrate):
        self.__add_meter_helper("PreQosPipe.session_meter", session_meter_idx,
                                session_meter_max_bitrate)

    def set_up_ddn_digest(self, ack_timeout_ns):
        # No timeout, not batching. Not recommended for production.
        self.insert(
            self.helper.build_digest_entry(digest_name="ddn_digest_t", max_timeout_ns=0,
                                           max_list_size=1, ack_timeout_ns=ack_timeout_ns))
