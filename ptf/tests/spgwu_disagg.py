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

from spgwu_base import UpfDisaggBaseTest, Action
from unittest import skipIf, skip

COUNTER_WAIT_TIME = 0.1

switchIsntDisaggregated = not testutils.test_param_get("disagg", False)

#@skipIf(switchIsntDisaggregated, "Test doesn't apply in single-device setting.")
@group("gtpu")
@skip("what")
class DisaggGtpuDecapUplinkTest(UpfDisaggBaseTest):
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
        final_pkt = self.gtpu_decap(pkt)


        dst_mac = self.random_mac_addr()
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(final_pkt, dst_mac)
        pkt_decrement_ttl(final_pkt)

        # intermediate packets to/from the QoS device
        pkt_towards_qos = self.qos_encap(final_pkt)
        pkt_from_qos = pkt_towards_qos.copy()
        self.swap_macs(pkt_from_qos)

        # PDR counter ID, session ID, FAR ID
        ctr_id = self.new_counter_id()
        session_id = self.randint(1024)
        far_id = self.unique_rule_id()

        # program all the tables
        self.add_entries_for_uplink_pkt(pkt, final_pkt, self.port1, self.port2, ctr_id, Action.FORWARD)

        # read pre and post-QoS packet and byte counters
        self.read_pdr_counters(ctr_id)

        # send packet and verify it is decapsulated and is sent towards the QoS device
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, pkt_towards_qos, self.port4)

        # send packet from QoS device and verify it continues out the downlink
        testutils.send_packet(self, self.port4, str(pkt_from_qos))
        testutils.verify_packet(self, final_pkt, self.port2)

        # wait for counters to update
        sleep(COUNTER_WAIT_TIME)

        # Check if pre and post-QoS packet and byte counters incremented
        self.verify_counters_increased(ctr_id, 1, len(pkt), 1, len(pkt))


@group("gtpu")
@skipIf(switchIsntDisaggregated, "Test doesn't apply in single-device setting.")
class QosDownlinkTest(UpfDisaggBaseTest):
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
        final_pkt = pkt.copy()
        dst_mac = self.random_mac_addr()
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(final_pkt, dst_mac)
        pkt_decrement_ttl(final_pkt)

        # Should be encapped too obv.
        final_pkt = self.gtpu_encap(final_pkt)

        # PDR counter ID, QER ID
        ctr_id = self.new_counter_id()
        qer_id = self.unique_rule_id()

        # intermediate packets to/from the QoS device
        pkt_towards_qos = self.qos_encap(final_pkt, qer_id, ctr_id, self.port1)
        pkt_from_qos = pkt_towards_qos.copy()
        self.swap_macs(pkt_from_qos)


        # program all the tables
        self.add_entries_for_downlink_pkt(pkt, final_pkt, self.port1, self.port2, ctr_id, Action.TUNNEL, qer_id=qer_id)

        # read pre and post-QoS packet and byte counters
        self.read_pdr_counters(ctr_id)

        # send packet and verify it is encapsulated and is sent towards the QoS device
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, pkt_towards_qos, self.port4)

        # wait for counters to update
        sleep(COUNTER_WAIT_TIME)
        # Check if pre-QoS packet and byte counters incremented,
        # but post-QoS packet and byte counters have yet to increment
        self.verify_counters_increased(ctr_id, 1, len(pkt), 0, 0)

        # send packet from QoS device and verify it continues out the downlink
        testutils.send_packet(self, self.port4, str(pkt_from_qos))
        testutils.verify_packet(self, final_pkt, self.port2)

        # wait for counters to update
        sleep(COUNTER_WAIT_TIME)
        # Check pre-QoS packet and byte counters didnt increment again,
        # but post-QoS packet and byte counters now incremented
        self.verify_counters_increased(ctr_id, 0, 0, 1, len(pkt_from_qos))
