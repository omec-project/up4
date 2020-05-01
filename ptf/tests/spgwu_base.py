# Copyright 2020-present Open Networking Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
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
from scapy.all import IP, IPv6, TCP, UDP, ICMP
from time import sleep
from enum import Enum
import random
random.seed(123456)  # for reproducible PTF tests

from lib import disagg_headers as disaggh

UDP_GTP_PORT = 2152


BUFFER_DEVICE_PORT = 3
QOS_DEVICE_PORT = 4
BUFFER_DEVICE_MAC = "32:00:00:00:00:01"
QOS_DEVICE_MAC = "32:00:00:00:00:01"


class Action(Enum):
    DROP    = 1
    FORWARD = 2
    TUNNEL  = 3
    BUFFER  = 4



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
        return random.randint(0, limit-1)

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
    def read_pdr_counters(self, index):
        self._last_read_counter_values[index] = [
                    self.read_pdr_counter(index, pre_qos=True, pkts=True),
                    self.read_pdr_counter(index, pre_qos=True, pkts=False),
                    self.read_pdr_counter(index, pre_qos=False, pkts=True),
                    self.read_pdr_counter(index, pre_qos=False, pkts=False)
                ]
        return self._last_read_counter_values[index]

    def verify_counters_increased(self, index, prePktsInc, preBytesInc,
                                postPktsInc, postBytesInc):
        """ Verify that both the Pre- and Post-QoS counters increased:
            - Pre-Qos pkt count increased by prePktsInc
            - Pre-Qos byte count increased by preBytesInc
            - Post-Qos pkt count increased by postPktsInc
            - Post-Qos byte count increased by postBytesInc
        """
        old_vals = self._last_read_counter_values.get(index, [0,0,0,0])
        new_vals = self.read_pdr_counters(index)
        if new_vals[0] - old_vals[0] != prePktsInc:
            self.fail("Pre-QoS pkt counter did not increase by %d!" % prePktsInc)
        if new_vals[1] - old_vals[1] != preBytesInc:
            self.fail("Pre-QoS byte counter did not increase by %d!" % preBytesInc)
        if new_vals[2] - old_vals[2] != postPktsInc:
            self.fail("Post-QoS pkt counter did not increase by %d!" % postPktsInc)
        if new_vals[3] - old_vals[3] != postBytesInc:
            self.fail("Post-QoS byte counter did not increase by %d!" % postBytesInc)

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
                match_fields={
                    "dst_mac" : mac_addr
                },
                action_name="NoAction",
                action_params={}
            ))


    def add_routing_ecmp_group(self, ip_prefix, mac_port_pairs):

        group_id = self.helper.get_next_grp_id()

        self.insert(self.helper.build_act_prof_group(
            act_prof_name="hashed_selector",
            group_id = group_id,
            actions = [
                (
                    "PreQosPipe.Routing.route",
                    {
                        "dst_mac"     : pair[0],
                        "egress_port" : pair[1]
                    }
                ) for pair in mac_port_pairs
            ]
            ))

        self.insert(self.helper.build_table_entry(
            table_name="PreQosPipe.Routing.routes_v4",
            match_fields= {
                "dst_prefix" : ip_prefix
            },
            group_id=group_id
            ))


    def add_routing_entry(self, ip_prefix, dst_mac, egress_port):
        return self.add_routing_ecmp_group(ip_prefix, [(dst_mac,egress_port)])


    def add_interface(self, ip_prefix, iface_type, direction):
        """ Binds a destination prefix 3GPP interface.
        """
        _iface_type = self.helper.get_enum_member_val("InterfaceType", iface_type)
        _direction = self.helper.get_enum_member_val("Direction", direction)

        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.source_iface_lookup",
                match_fields={
                    "ipv4_dst_prefix" : ip_prefix
                },
                action_name="PreQosPipe.set_source_iface",
                action_params={"src_iface": _iface_type,
                               "direction":_direction},
            ))

    def add_session(self, session_id, ue_addr):
        """ Associates the given session_id with the given UE address.
        """
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.fseid_lookup",
                match_fields={
                    # Exact match.
                    "local_meta.ue_addr": ue_addr
                },
                action_name="PreQosPipe.set_fseid",
                action_params={"fseid": session_id},
            ))

    def add_default_entries(self,
                            default_pdr_id = 0,
                            default_far_id = 0,
                            default_qer_id = 0,
                            default_qfi = 0,
                            default_net_instance = 0,
                            default_ctr_id = 0):
        return

        # Default PDR entry
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.pdrs",
                default_action=True,
                action_name="PreQosPipe.set_pdr_attributes",
                action_params={
                    "id": default_pdr_id,
                    "far_id": default_far_id,
                    "qer_id": default_qer_id,
                    "qfi": qfi,
                    "needs_gtpu_decap": False,
                    "needs_udp_decap": False,
                    "needs_vlan_removal": False,
                    "net_instance": default_net_instance,
                    "ctr_id": default_ctr_id,
                }
            ))
        # Default FAR entry


    def add_pdr(self, pdr_id, far_id, session_id, src_iface, ctr_id, ue_addr=None, ue_mask=None,
                inet_addr=None, inet_mask=None, ue_l4_port=None, ue_l4_port_hi=None,
                inet_l4_port=None, inet_l4_port_hi=None, ip_proto=None, ip_proto_mask=None,
                qer_id=0, qfi=0, needs_gtpu_decap=False, needs_udp_decap=False,
                needs_vlan_removal=False, net_instance=0, priority=10):

        ALL_ONES_32 = (1 << 32) - 1
        ALL_ONES_8 = (1 << 8) - 1

        _src_iface = self.helper.get_enum_member_val("InterfaceType", src_iface)
        match_fields = {"fseid": session_id, "src_iface": _src_iface}
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

        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.pdrs",
                match_fields=match_fields,
                action_name="PreQosPipe.set_pdr_attributes",
                action_params={
                    "id": pdr_id,
                    "far_id": far_id,
                    "qer_id": qer_id,
                    "qfi": qfi,
                    "needs_gtpu_decap": needs_gtpu_decap,
                    "needs_udp_decap": needs_udp_decap,
                    "needs_vlan_removal": needs_vlan_removal,
                    "net_instance": net_instance,
                    "ctr_id": ctr_id,
                },
                priority=priority,
            ))

    def remove_far(self, far_id, session_id):
        self.delete(
            self.helper.build_table_entry(
                table_name="PreQosPipe.fars",
                match_fields={
                    "far_id": far_id,
                    "session_id": session_id,
                }
            ))


    def add_far_forward(self, far_id, session_id):
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.fars",
                match_fields={
                    "far_id": far_id,
                    "session_id": session_id,
                },
                action_name="PreQosPipe.set_far_attributes_forward"
            ))

    def add_far_drop(self, far_id, session_id):
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.fars",
                match_fields={
                    "far_id": far_id,
                    "session_id": session_id,
                },
                action_name="PreQosPipe.set_far_attributes_drop",
            ))

    def add_far_tunnel(self, far_id, session_id, teid, src_addr, dst_addr,
                       dport=2152, tunnel_type="GTPU"):
        _tunnel_type = self.helper.get_enum_member_val("TunnelType", tunnel_type)
        self.insert(
            self.helper.build_table_entry(
                table_name="PreQosPipe.fars",
                match_fields={
                    "far_id": far_id,
                    "session_id": session_id,
                },
                action_name="PreQosPipe.set_far_attributes_tunnel",
                action_params={
                    "tunnel_type": _tunnel_type,
                    "src_addr": src_addr,
                    "dst_addr": dst_addr,
                    "teid": teid,
                    "dport": dport
                },
            ))

    def add_far(self, far_type="FORWARD", **kwargs):
        # TODO: complete this method if it is deemed useful
        funcs = {
            "FORWARD": self.add_far_forward,
            "TUNNEL": self.add_far_tunnel,
        }

        return funcs[far_type](**kwargs)


    def add_entries_for_uplink_pkt(self, pkt, exp_pkt, inport, outport, ctr_id, action, session_id=None, pdr_id = None, far_id=None, qer_id=None):
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
        if qer_id is None:
            qer_id = self.unique_rule_id()

        ue_l4_port = None
        inet_l4_port = None
        if (UDP in inner_pkt) or (TCP in inner_pkt):
            ue_l4_port = inner_pkt.sport
            net_l4_port = inner_pkt.dport

        self.add_device_mac(pkt[Ether].dst)

        self.add_interface(ip_prefix=pkt[IP].dst + '/32',
                            iface_type="ACCESS",  direction="UPLINK")

        self.add_session(session_id=session_id, ue_addr=inner_pkt[IP].src)

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
            qer_id=qer_id,
            needs_gtpu_decap=True,
        )

        if (action == Action.FORWARD):
            self.add_far_forward(
                far_id=far_id,
                session_id=session_id
            )
            self.add_routing_entry(ip_prefix = exp_pkt[IP].dst + '/32',
                                   dst_mac = exp_pkt[Ether].dst,
                                   egress_port = outport)
        elif (action == Action.DROP):
            self.add_far_drop(far_id=far_id, session_id=session_id)
        else:
            raise AssertionError("Action Not handled")


    def add_entries_for_downlink_pkt(self, pkt, exp_pkt, inport, outport, ctr_id, action, session_id=None, pdr_id=None, far_id=None, qer_id=None):
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
        if qer_id is None:
            qer_id = self.unique_rule_id()

        ue_l4_port = None
        inet_l4_port = None
        if (UDP in pkt) or (TCP in pkt):
            ue_l4_port = pkt.dport
            inet_l4_port = pkt.sport

        self.add_device_mac(pkt[Ether].dst)

        self.add_interface(ip_prefix=pkt[IP].dst + '/32',
                            iface_type="CORE", direction="DOWNLINK")

        self.add_session(session_id=session_id, ue_addr=pkt[IP].dst)

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

        if (action == Action.TUNNEL):
            self.add_far_tunnel(
                far_id=far_id,
                session_id=session_id,
                teid=exp_pkt[gtp.GTP_U_Header].teid,
                src_addr=exp_pkt[IP].src,
                dst_addr=exp_pkt[IP].dst
            )
            self.add_routing_entry(ip_prefix = exp_pkt[IP].dst + '/32',
                                   dst_mac = exp_pkt[Ether].dst,
                                   egress_port = outport)
        elif (action == Action.DROP):
            self.add_far_drop(far_id=far_id, session_id=session_id)
        else:
            raise AssertionError("FAR Action Not handled %d",action);




class UpfDisaggBaseTest(GtpuBaseTest):

    def _insert_tunnel_header(self, pkt, tunnel_header):
        # Part of packet before the tunnel header
        pre_tunnel = pkt.copy()
        pre_tunnel[IP].remove_payload()
        pre_tunnel[IP].proto = None # force recomputation
        # Part after the tunnel header
        post_tunnel = pkt[IP].payload

        return  pre_tunnel / tunnel_header / post_tunnel

    def strip_tunnel_header(self, pkt):

        TH = None
        for header in [disaggh.QosTunnel, disaggh.BufferTunnel]:
            if header in pkt:
                TH = header
                break
        else:
            raise Exception("Attempting to strip tunnel header from a packet that has none!")
        pre_tunnel = pkt.copy()
        pre_tunnel[IP].remove_payload()
        pre_tunnel[IP].proto = None # force recomputation
        # Part after the tunnel header
        post_tunnel = pkt[TH].payload

        return pre_tunnel / post_tunnel




    def qos_encap(self, pkt, qer_id, ctr_idx, ingress_port, device_id=0):
        tunnel_header = disaggh.QosTunnel(next_header = pkt[IP].proto,
                          device_id = device_id,
                          qer_id = qer_id,
                          ctr_idx = ctr_idx,
                          original_ingress_port = ingress_port)

        return self._insert_tunnel_header(pkt, tunnel_header)


    def buffer_encap(self, pkt, bar_id, ingress_port, device_id=0):
        tunnel_header = disaggh.QosTunnel(next_header = pkt[IP].proto,
                          device_id = device_id,
                          bar_id = bar_id,
                          original_ingress_port = ingress_port)

        return self._insert_tunnel_header(pkt, tunnel_header)

    def swap_macs(self, pkt):
        pkt[Ether].src, pkt[Ether].dst = pkt[Ether].dst, pkt[Ether].src



