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
# SPGWU TESTS
#
# To run all tests:
#     make check TEST=spgw
#
# To run a specific test case:
#     make check TEST=spgw.<TEST CLASS NAME>
#
# For example:
#     make check TEST=spgw.GtpuEncapDownlinkTest
# ------------------------------------------------------------------------------

from base_test import pkt_route, pkt_decrement_ttl, P4RuntimeTest, \
                      autocleanup, print_inline
from ptf.testutils import group
from ptf import testutils as testutils
from scapy.contrib import gtp
from scapy.all import IP, IPv6, TCP, UDP, ICMP
from time import sleep

from spgwu_base import GtpuBaseTest, Action

@group("gtpu")
class GtpuDecapUplinkTest(GtpuBaseTest):
    """ Tests that a packet received from a UE gets decapsulated and routed.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils, "simple_%s_packet" % pkt_type)()
            pkt = self.gtpu_encap(pkt)

            self.testPacket(pkt)

    @autocleanup
    def testPacket(self, pkt):

        if gtp.GTP_U_Header not in pkt:
            raise AssertionError("Packet given to decap test is not encapsulated!")
        # build the expected decapsulated packet
        exp_pkt = self.gtpu_decap(pkt)
        dst_mac = self.random_mac_addr()
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        # PDR counter ID
        ctr_id = self.unique_rule_id()

        # program all the tables
        self.add_entries_for_uplink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, Action.FORWARD)

        # read pre-QoS packet and byte counters
        uplink_pkt_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        uplink_byte_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)

        # read post-QoS packet and byte counters
        uplink_pkt_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        uplink_byte_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)

        # send packet and verify it is decapsulated and routed
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, exp_pkt, self.port2)

        # wait for counters to update
        sleep(0.1)

        # Check if pre-QoS packet and byte counters incremented
        uplink_pkt_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        uplink_byte_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)
        self.assertEqual(uplink_pkt_count_new, uplink_pkt_count + 1)
        self.assertEqual(uplink_byte_count_new, uplink_byte_count + len(pkt))

        # Check if post-QoS packet and byte counters incremented
        uplink_pkt_count2_new = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        uplink_byte_count2_new = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)
        self.assertEqual(uplink_pkt_count2_new, uplink_pkt_count + 1)
        self.assertEqual(uplink_byte_count2_new, uplink_byte_count + len(pkt))


@group("gtpu")
class GtpuEncapDownlinkTest(GtpuBaseTest):
    """ Tests that a packet received from the internet/core gets encapsulated and forwarded.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils, "simple_%s_packet" % pkt_type)()
            self.testPacket(pkt)

    @autocleanup
    def testPacket(self, pkt):

        # build the expected encapsulated packet
        exp_pkt = pkt.copy()
        dst_mac = self.random_mac_addr()
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        # Should be encapped too obv.
        exp_pkt = self.gtpu_encap(exp_pkt)

        # PDR counter ID
        ctr_id = self.unique_rule_id()

        # program all the tables
        self.add_entries_for_downlink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, Action.TUNNEL)

        # read pre-QoS packet and byte counters
        downlink_pkt_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        downlink_byte_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)

        # read post-QoS packet and byte counters
        downlink_pkt_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        downlink_byte_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)

        # send packet and verify it is decapsulated and routed
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, exp_pkt, self.port2)

        # wait for counters to update
        sleep(0.1)

        # Check if pre-QoS packet and byte counters incremented
        downlink_pkt_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        downlink_byte_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)
        self.assertEqual(downlink_pkt_count_new, downlink_pkt_count + 1)
        self.assertEqual(downlink_byte_count_new, downlink_byte_count + len(pkt))

        # Check if post-QoS packet and byte counters incremented
        downlink_pkt_count2_new = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        downlink_byte_count2_new = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)
        self.assertEqual(downlink_pkt_count2_new, downlink_pkt_count + 1)
        self.assertEqual(downlink_byte_count2_new, downlink_byte_count + len(pkt))

class GtpuDropUplinkTest(GtpuBaseTest):
    """ Tests that a packet received from a UE gets decapsulated and dropped because of FAR rule.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils, "simple_%s_packet" % pkt_type)()
            pkt = self.gtpu_encap(pkt)

            self.testPacket(pkt)

    @autocleanup
    def testPacket(self, pkt):

        if gtp.GTP_U_Header not in pkt:
            raise AssertionError("Packet given to decap test is not encapsulated!")
        # build the expected decapsulated packet
        exp_pkt = self.gtpu_decap(pkt)
        dst_mac = self.random_mac_addr()
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        # PDR counter ID
        ctr_id = self.unique_rule_id()

        # program all the tables
        self.add_entries_for_uplink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, Action.DROP)

        # read pre-QoS packet and byte counters
        uplink_pkt_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        uplink_byte_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)

        # read post-QoS packet and byte counters
        uplink_pkt_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        uplink_byte_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)

        # send packet and verify it is dropped
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_no_other_packets(self)

        # wait for counters to update
        sleep(0.1)

        # Check if pre-QoS packet and byte counters incremented
        uplink_pkt_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        uplink_byte_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)
        self.assertEqual(uplink_pkt_count_new, uplink_pkt_count + 1)
        self.assertEqual(uplink_byte_count_new, uplink_byte_count + len(pkt))

        # Make sure post-QoS packet and byte counters weren't incremented.
        uplink_pkt_count2_new = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        uplink_byte_count2_new = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)
        self.assertEqual(uplink_pkt_count2_new, uplink_pkt_count)
        self.assertEqual(uplink_byte_count2_new, uplink_byte_count)

@group("gtpu")
class GtpuDropDownlinkTest(GtpuBaseTest):
    """ Tests that a packet received from the internet/core gets dropped because of FAR rule.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils, "simple_%s_packet" % pkt_type)()
            self.testPacket(pkt)

    @autocleanup
    def testPacket(self, pkt):

        # build the expected encapsulated packet
        exp_pkt = pkt.copy()
        dst_mac = self.random_mac_addr()
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)
        # force recomputation of checksum after routing/ttl decrement
        del pkt[IP].chksum

        # Should be encapped too obv.
        exp_pkt = self.gtpu_encap(exp_pkt)

        # PDR counter ID
        ctr_id = self.unique_rule_id()

        # program all the tables
        self.add_entries_for_downlink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, Action.DROP)

        # read pre-QoS packet and byte counters
        downlink_pkt_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        downlink_byte_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)

        # read post-QoS packet and byte counters
        downlink_pkt_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        downlink_byte_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)

        # send packet and verify it is dropped
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_no_other_packets(self)

        # wait for counters to update
        sleep(0.1)

        # Check if pre-QoS packet and byte counters incremented
        downlink_pkt_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        downlink_byte_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)
        self.assertEqual(downlink_pkt_count_new, downlink_pkt_count + 1)
        self.assertEqual(downlink_byte_count_new, downlink_byte_count + len(pkt))

        # Make sure post-QoS packet and byte counters weren't incremented.
        downlink_pkt_count2_new = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        downlink_byte_count2_new = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)
        self.assertEqual(downlink_pkt_count2_new, downlink_pkt_count)
        self.assertEqual(downlink_byte_count2_new, downlink_byte_count)


