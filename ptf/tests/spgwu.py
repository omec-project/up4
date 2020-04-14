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
SPGW_DIR_UNKNOWN = 0;
SPGW_DIR_UPLINK = 1;
SPGW_DIR_DOWNLINK = 2;

IP_MASK = '255.255.255.255'
PORT_MASK = '65535'
SPGW_IFACE_TYPE_UNKNOWN = 0;
SPGW_IFACE_TYPE_ACCESS  = 1;
SPGW_IFACE_TYPE_CORE    = 2;

IP_PROTO_ICMP   = 1;
IP_PROTO_TCP    = 6;
IP_PROTO_UDP    = 17;

UL_PDR_ID = 100
DL_PDR_ID = 200
UL_FAR_ID = 300
DL_FAR_ID = 400

@group("gtpu")
class GTPU_far_Test(P4RuntimeTest):
    """Tests GTPU routing"""

    def make_gtp(msg_len, teid, flags=0x30, msg_type=0xff):
        """Convenience function since GTP header has no scapy support"""
        return struct.pack(">BBHL", flags, msg_type, msg_len, teid)

    def pkt_add_gtp(pkt, out_ipv4_src, out_ipv4_dst, teid):
        payload_ether_frame = pkt[Ether].payload
        return Ether(src=pkt[Ether].src, dst=pkt[Ether].dst) / \
               IP(src=out_ipv4_src, dst=out_ipv4_dst, tos=0,
                  id=0x1513, flags=0, frag=0) / \
               UDP(sport=UDP_GTP_PORT, dport=UDP_GTP_PORT, chksum=0) / \
               make_gtp(len(payload), teid) / \
               payload_ether_frame

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in ["tcp", "udp", "icmp"]:
              print_inline("%s ... " % pkt_type)
              pkt = getattr(testutils, "simple_%s_packet" % pkt_type)()
              pkt_add_gtp(pkt, out_ipv4_src, out_ipv4_dst, src_teid)
              self.testPacket(pkt)

#pkt[IP].payload[IP]
    @autocleanup
    def testPacket(self, pkt):
        next_hop_mac = SWITCH2_MAC

        # Add entry to "source_iface_lookup" table. 
       
        for port, iface in \
           zip([self.port1, self.port2], \
           [SPGW_IFACE_TYPE_ACCESS, \
             SPGW_IFACE_TYPE_CORE]):
                self.insert(self.helper.build_table_entry(
                   table_name="IngressPipeImpl.source_iface_lookup",
                   match_fields={
                   # Exact match.
                     "std_meta.ingress_port": port
                },
                action_name="IngressPipeImpl.set_source_iface_type",
                action_params={"src_iface_type":iface}
                ))

        # Add entry to pdr_rule_lookup
        for teid, src_addr, dst_addr, src_port, dst_port, pdr_id in \
           zip([(src_teid, 0xffffffff),(0,0)], \
           [(out_ipv4_src,IP_MASK), (out_ipv4_dst,IP_MASK)], \
           [(out_ipv4_dst,IP_MASK), (out_ipv4_src,IP_MASK)], \
           [(self.port1, PORT_MASK), (self.port2,PORT_MASK)], \
           [(self.port2, PORT_MASK), (self.port1, PORT_MASK)], \
#range still not sure how to write
           [(IP_PROTO_UDP, 0xFF), (IP_PROTO_UDP, 0xFF)], \
           [UL_PDR_ID, DL_PDR_ID]):
                self.insert(self.helper.build_table_entry(
                   table_name="IngressPipeImpl.pdrs",
                   match_fields={
                   # Exact match.
                     "teid": teid,
                     "ue_addr": src_addr,
                     "inet_addr": dst_addr,
                     "ue_l4_port":src_port,
                     "inet_l4_port":dst_port,
                     "ip_proto":IP_PROTO_UDP
                },
                action_name="IngressPipeImpl.set_pdr_id",
                action_params={"id":pdr_id}
                ))

        for pdr_id, far_id in \
           zip([UL_PDR_ID, DL_PDR_ID], \
           [UL_FAR_ID, DL_FAR_ID]): 
                self.insert(self.helper.build_table_entry(
                   table_name="IngressPipeImpl.fars",
                   match_fields={
                   # Exact match.
                     "local_meta.pdr_id": pdr_id,
                },
                action_name="IngressPipeImpl.set_far_id",
                action_params={"id":far_id}
                ))

        self.insert(self.helper.build_table_entry(
               table_name="IngressPipeImpl.execute_far",
               match_fields={
                   # Exact match.
                     "local_meta.far_id": UL_FAR_ID,
                },
                action_name="IngressPipeImpl.set_next_hop",
                action_params={"dmac":next_hop_mac,"port":self.port2}
                ))

        self.insert(self.helper.build_table_entry(
               table_name="IngressPipeImpl.execute_far",
               match_fields={
                   # Exact match.
                     "local_meta.far_id": DL_FAR_ID,
                },
                action_name="IngressPipeImpl.gtpu_encap_and_forward",
                action_params={"encap_src_addr":out_ipv4_src,"encap_dst_addr":out_ipv4_dst,"nexthop_mac":next_hop_mac,"outport":self.port1}
                ))

        

        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        exp_pkt = pkt.copy()
        pkt_route(exp_pkt, next_hop_mac)
        pkt_decrement_ttl(exp_pkt)

        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, exp_pkt, self.port2)
