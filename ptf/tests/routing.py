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


@group("routing")
class IPv4RoutingTest(P4RuntimeTest):
    """Tests basic IPv4 routing"""

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in ["tcp", "udp", "icmp"]:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils, "simple_%s_packet" % pkt_type)()
            self.testPacket(pkt)

    @autocleanup
    def testPacket(self, pkt):
        next_hop_mac = SWITCH2_MAC

        # Add entry to "My Station" table. Consider the given pkt's eth dst addr
        # as the router mac.
        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.my_station",
            match_fields={
                # Exact match.
                "hdr.ethernet.dst_addr": pkt[Ether].dst
            },
            action_name="NoAction"
        ))

        # Insert routing entry
        self.insert(self.helper.build_table_entry(
            table_name="IngressPipeImpl.routing_v4",
            match_fields={
                # LPM match (value, prefix)
                "hdr.ipv4.dst_addr": (pkt[IP].dst, 32)
            },
            action_name="IngressPipeImpl.set_next_hop",
            action_params={"dmac": next_hop_mac, "port": self.port2}
        ))

        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        exp_pkt = pkt.copy()
        pkt_route(exp_pkt, next_hop_mac)
        pkt_decrement_ttl(exp_pkt)

        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, exp_pkt, self.port2)
