# Copyright 2019-present Open Networking Foundation
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
# IPV4 ROUTING TESTS
#
# To run all tests:
#     make check TEST=routing
#
# To run a specific test case:
#     make check TEST=routing.<TEST CLASS NAME>
#
# For example:
#     make check TEST=routing.IPv4RoutingTest
# ------------------------------------------------------------------------------

from base_test import *
from ptf.testutils import group
from scapy.contrib.gtp import *

CPU_PORT = 255
CPU_CLONE_SESSION_ID = 99
ETH_HDR_SIZE = 14
IPV4_HDR_SIZE = 20
UDP_HDR_SIZE = 8
GTP_HDR_SIZE = 8
IP_VERSION_4 = 4
out_ipv4_src = '192.168.0.202'
out_ipv4_dst = '10.92.1.164'
src_teid = 101
dst_teid = 1201

IP_MASK = '255.255.255.255'
PORT_MASK = 65535
SPGW_IFACE_TYPE_UNKNOWN = 0;
SPGW_IFACE_TYPE_ACCESS = 1;
SPGW_IFACE_TYPE_CORE = 2;

IP_PROTO_ICMP = 1;
IP_PROTO_TCP = 6;
IP_PROTO_UDP = 17;

UL_PDR_ID = 100
DL_PDR_ID = 200
UL_FAR_ID = 300
DL_FAR_ID = 400
DL_CTR_ID = 50
UL_CTR_ID = 60

TRUE = 1
FALSE = 0

UDP_GTP_SRC_PORT = 2100
UDP_GTP_DST_PORT = 2152


@group("gtpu")
class GtpuDecapUplinkTest(P4RuntimeTest):

    inner_pkt = None
    ulfseid = 1

    def make_gtp(self, msg_len, teid, flags=0x30, msg_type=0xff):
        """Convenience function since GTP header has no scapy support"""
        return struct.pack(">BBHL", flags, msg_type, msg_len, teid)

    def pkt_add_gtp(self, pkt, out_ipv4_src, out_ipv4_dst, teid):
        payload_ether_frame = pkt[Ether].payload
        return Ether(src=pkt[Ether].src, dst=pkt[Ether].dst) / \
               IP(src=out_ipv4_src, dst=out_ipv4_dst, tos=0,
                  id=0x1513, flags=0, frag=0) / \
               UDP(sport=UDP_GTP_SRC_PORT, dport=UDP_GTP_DST_PORT, chksum=0) / \
               GTP_U_Header(gtp_type=255, teid=teid) / \
               payload_ether_frame

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in ["udp"]:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils, "simple_%s_packet" % pkt_type)()
            self.inner_pkt = pkt
            self.testPacket(self.pkt_add_gtp(pkt, out_ipv4_src, out_ipv4_dst,
                                             src_teid))

    @autocleanup
    def testPacket(self, pkt):
        next_hop_mac = SWITCH2_MAC

        # Add entry to "source_iface_lookup" table. 

        ue_src_port = self.inner_pkt[UDP].sport
        inet_dst_port = self.inner_pkt[UDP].dport
        pkt_ue_ip = self.inner_pkt[IP].src
        pkt_inet_ip = self.inner_pkt[IP].dst

        ul_far_id_forward = 1
        ul_qer_id = 1
        ul_pdr_id = 1
        ul_qfi = 5
        ul_upf_instance = 10
        ul_ctr_id = 50

        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.source_iface_lookup",
            match_fields={
                # Exact match.
                "std_meta.ingress_port": self.port1
            },
            action_name="IngressPipeImpl.set_source_iface_type",
            action_params={"src_iface_type": SPGW_IFACE_TYPE_ACCESS}
        ))

        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.fseid_lookup",
            match_fields={
                # Exact match.
                "local_meta.ue_addr": pkt_ue_ip
            },
            action_name="IngressPipeImpl.set_fseid",
            action_params={"fseid": self.ulfseid}
        ))

        # Add entry to pdr_rule_lookup
        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.pdrs",
            match_fields={
                # Exact match.
                "fseid": self.ulfseid,
                "src_iface_type": SPGW_IFACE_TYPE_ACCESS,
                "ue_addr": (pkt_ue_ip, IP_MASK),
                "inet_addr": (pkt_inet_ip, IP_MASK),
                "ue_l4_port": (ue_src_port, ue_src_port),
                "inet_l4_port": (inet_dst_port, inet_dst_port),
                "ip_proto": (IP_PROTO_UDP, 0xFF)
            },
            action_name="IngressPipeImpl.set_pdr_attributes",
            action_params={"id": ul_pdr_id, "far_id": ul_far_id_forward,
                           "qer_id": ul_qer_id, "qfi": ul_qfi,
                           "needs_gtpu_decap": TRUE,
                           "needs_udp_decap": FALSE,
                           "needs_vlan_removal": FALSE,
                           "net_instance": ul_upf_instance,
                           "ctr_id": ul_ctr_id},
            priority=1
        ))

        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.fars",
            match_fields={
                # Exact match.
                "local_meta.far.id": ul_far_id_forward,
            },
            action_name="IngressPipeImpl.set_far_attributes_forward",
            action_params={"egress_spec": self.port2, "dst_addr": next_hop_mac}
        ))

        old_uplink = self.helper.read_pkt_count_pre_qos_pdr(ul_ctr_id)
        old_byte_uplink = self.helper.read_byte_count_pre_qos_pdr(ul_ctr_id)

        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        exp_pkt = self.inner_pkt.copy()
        pkt_route(exp_pkt, next_hop_mac)
        pkt_decrement_ttl(exp_pkt)

        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, exp_pkt, self.port2)
        new_uplink = self.helper.read_pkt_count_pre_qos_pdr(ul_ctr_id)
        new_byte_uplink = self.helper.read_byte_count_pre_qos_pdr(ul_ctr_id)
        self.assertEqual(new_uplink, old_uplink + 1)
        self.assertEqual(new_byte_uplink, old_byte_uplink + 136)


class GtpuEncapDownlinkTest(P4RuntimeTest):
    """Tests GTPU routing"""

    outer_pkt = None
    dlfseid = 2

    def make_gtp(self, msg_len, teid, flags=0x30, msg_type=0xff):
        """Convenience function since GTP header has no scapy support"""
        return struct.pack(">BBHL", flags, msg_type, msg_len, teid)

    def pkt_add_gtp(self, pkt, out_ipv4_src, out_ipv4_dst, teid):
        payload_ether_frame = pkt[Ether].payload
        return Ether(src=pkt[Ether].dst, dst=SWITCH1_MAC) / \
               IP(src=out_ipv4_dst, dst=out_ipv4_src, tos=0,
                  id=0x1513, flags=0, frag=0, chksum=0) / \
               UDP(sport=UDP_GTP_DST_PORT, dport=UDP_GTP_DST_PORT, chksum=0) / \
               GTP_U_Header(gtp_type=255, teid=teid, length=86) / \
               payload_ether_frame

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in ["udp"]:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils, "simple_%s_packet" % pkt_type)()
            self.inner_pkt = pkt
            self.testPacket(pkt)

    @autocleanup
    def testPacket(self, pkt):
        next_hop_mac = SWITCH1_MAC

        # Add entry to "source_iface_lookup" table. 

        inet_src_port = pkt[UDP].sport
        ue_dst_port = pkt[UDP].dport
        pkt_inet_ip = pkt[IP].src
        pkt_ue_ip = pkt[IP].dst

        dl_far_id_forward = 1
        dl_qer_id = 1
        dl_pdr_id = 1
        dl_qfi = 5
        dl_upf_instance = 10
        dl_ctr_id = 60
        tunnel_type = 3

        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.source_iface_lookup",
            match_fields={
                # Exact match.
                "std_meta.ingress_port": self.port2
            },
            action_name="IngressPipeImpl.set_source_iface_type",
            action_params={"src_iface_type": SPGW_IFACE_TYPE_CORE}
        ))

        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.fseid_lookup",
            match_fields={
                # Exact match.
                "local_meta.ue_addr": pkt_ue_ip
            },
            action_name="IngressPipeImpl.set_fseid",
            action_params={"fseid": self.dlfseid}
        ))

        # Add entry to pdr_rule_lookup
        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.pdrs",
            match_fields={
                # Exact match.
                "fseid": self.dlfseid,
                "src_iface_type": SPGW_IFACE_TYPE_CORE,
                "ue_addr": (pkt_ue_ip, IP_MASK),
                "inet_addr": (pkt_inet_ip, IP_MASK),
                "ue_l4_port": (ue_dst_port, ue_dst_port),
                "inet_l4_port": (inet_src_port, inet_src_port),
                "ip_proto": (IP_PROTO_UDP, 0xFF)
            },
            action_name="IngressPipeImpl.set_pdr_attributes",
            action_params={
                "id": dl_pdr_id,
                "far_id": dl_far_id_forward,
                "qer_id": dl_qer_id,
                "qfi": dl_qfi,
                "needs_gtpu_decap": TRUE,
                "needs_udp_decap": FALSE,
                "needs_vlan_removal": FALSE,
                "net_instance": dl_upf_instance,
                "ctr_id": dl_ctr_id},
            priority=1
        ))

        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.fars",
            match_fields={
                # Exact match.
                "local_meta.far.id": dl_far_id_forward,
            },
            action_name="IngressPipeImpl.set_far_attributes_tunnel",
            action_params={
                "tunnel_type": tunnel_type,
                "src_addr": out_ipv4_dst,
                "dst_addr": out_ipv4_src,
                "teid": dst_teid,
                "egress_spec": self.port1,
                "dst_mac": next_hop_mac}
        ))

        old_downlink = self.helper.read_pkt_count_pre_qos_pdr(dl_ctr_id)
        old_byte_downlink = self.helper.read_byte_count_pre_qos_pdr(dl_ctr_id)
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        exp_pkt = pkt.copy()
        pkt_decrement_ttl(exp_pkt)
        outer_pkt = self.pkt_add_gtp(
            exp_pkt, out_ipv4_src, out_ipv4_dst, dst_teid)
        outer_pkt = Ether(raw(outer_pkt))
        testutils.send_packet(self, self.port2, str(pkt))

        testutils.verify_packet(self, outer_pkt, self.port1)
        new_downlink = self.helper.read_pkt_count_pre_qos_pdr(dl_ctr_id)
        new_byte_downlink = self.helper.read_byte_count_pre_qos_pdr(dl_ctr_id)
        self.assertEqual(new_downlink, old_downlink + 1)
        self.assertEqual(new_byte_downlink, old_byte_downlink + 100)
