# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: Apache-2.0

# ------------------------------------------------------------------------------
# SPGWU TESTS
#
# To run all tests:
#     make check TEST=spgwu
#
# To run a specific test case:
#     make check TEST=spgwu.<TEST CLASS NAME>
#
# For example:
#     make check TEST=spgwu.GtpuEncapDownlinkTest
# ------------------------------------------------------------------------------
from time import sleep

from base_test import pkt_route, pkt_decrement_ttl, P4RuntimeTest, \
                      autocleanup, print_inline
from ptf.testutils import group
from ptf import testutils as testutils
from scapy.contrib import gtp
from scapy.all import IP, IPv6, TCP, UDP, ICMP, Ether

from convert import encode
from spgwu_base import GtpuBaseTest, UDP_GTP_PORT, GTPU_EXT_PSC_TYPE_DL, \
    GTPU_EXT_PSC_TYPE_UL

CPU_CLONE_SESSION_ID = 99
UE_ADDR_BITWIDTH = 32
UE_IPV4 = "17.0.0.1"
ENODEB_IPV4 = "140.0.100.1"
S1U_IPV4 = "140.0.100.2"
SGW_IPV4 = "140.0.200.2"
PDN_IPV4 = "140.0.200.1"
SWITCH_MAC = "AA:AA:AA:00:00:01"
ENODEB_MAC = "BB:BB:BB:00:00:01"
PDN_MAC = "CC:CC:CC:00:00:01"


@group("gtpu")
class GtpuDecapUplinkTest(GtpuBaseTest):
    """ Tests that a packet received from a UE gets decapsulated and routed.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            for app_filtering in [False, True]:
                # Verify that default TC behaves in the same way as when we specify TC
                for tc in [0, None]:
                    for app_bitrate in [0, 10000]:
                        for session_bitrate in [0, 10000]:
                            if app_bitrate == 0 and session_bitrate == 0:
                                # Skip when both bitrates are 0
                                continue
                            print(" %s, tc=%s, app_filtering=%s, app_bitrate=%s, session_bitrate=%s... " % (pkt_type, tc, app_filtering, app_bitrate, session_bitrate))
                            pkt = getattr(testutils,
                                          "simple_%s_packet" % pkt_type)(eth_src=ENODEB_MAC,
                                                                         eth_dst=SWITCH_MAC, ip_src=UE_IPV4,
                                                                         ip_dst=PDN_IPV4)
                            pkt = self.gtpu_encap(pkt, ip_src=ENODEB_IPV4, ip_dst=S1U_IPV4)

                            self.testPacket(pkt, app_filtering, tc, app_bitrate, session_bitrate)

    @autocleanup
    def testPacket(self, pkt, app_filtering, tc, app_bitrate, session_bitrate):

        if gtp.GTP_U_Header not in pkt:
            raise AssertionError("Packet given to decap test is not encapsulated!")
        # build the expected decapsulated packet
        exp_pkt = self.gtpu_decap(pkt)
        dst_mac = PDN_MAC
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        # UPF counter ID
        ctr_id = self.new_counter_id()
        # Meter IDs
        app_meter_id = self.unique_rule_id()
        session_meter_id = self.unique_rule_id()

        # program all the tables
        self.add_entries_for_uplink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, tc=tc,
                                        app_meter_id=app_meter_id,
                                        app_meter_max_bitrate=app_bitrate,
                                        session_meter_id=session_meter_id,
                                        session_meter_max_bitrate=session_bitrate,
                                        drop=False, app_filtering=app_filtering)

        # read pre and post-QoS packet and byte counters
        self.read_upf_counters(ctr_id)

        # send packet and verify it is decapsulated and routed
        testutils.send_packet(self, self.port1, pkt)
        if app_bitrate == 0 or session_bitrate == 0:
            testutils.verify_no_other_packets(self)
        else:
            testutils.verify_packet(self, exp_pkt, self.port2)

        # Check if pre and post-QoS packet and byte counters incremented
        if app_bitrate == 0 or session_bitrate == 0:
            post_qos_pkts = 0
            post_qos_bytes = 0
        else:
            post_qos_pkts = 1
            post_qos_bytes = len(pkt)
        self.verify_counters_increased(ctr_id, 1, len(pkt), post_qos_pkts, post_qos_bytes)


@group("gtpu")
class GtpuEncapDownlinkTest(GtpuBaseTest):
    """ Tests that a packet received from the internet/core gets encapsulated and forwarded.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            for app_filtering in [False, True]:
                # Verify that default TC behaves in the same way as when we specify TC
                for tc in [0, None]:
                    for app_bitrate in [0, 10000]:
                        for session_bitrate in [0, 10000]:
                            if app_bitrate == 0 and session_bitrate == 0:
                                # Skip when both bitrates are 0
                                continue
                            print(" %s, tc=%s, app_filtering=%s, app_bitrate=%s, session_bitrate=%s... " % (pkt_type, tc, app_filtering, app_bitrate, session_bitrate))
                            pkt = getattr(testutils,
                                          "simple_%s_packet" % pkt_type)(eth_src=PDN_MAC,
                                                                         eth_dst=SWITCH_MAC,
                                                                         ip_src=PDN_IPV4, ip_dst=UE_IPV4)
                            self.testPacket(pkt, app_filtering, tc, app_bitrate, session_bitrate)

    @autocleanup
    def testPacket(self, pkt, app_filtering, tc, app_bitrate, session_bitrate):
        # build the expected encapsulated packet
        exp_pkt = pkt.copy()
        dst_mac = ENODEB_MAC

        # Should be encapped too obv.
        exp_pkt = self.gtpu_encap(exp_pkt, ip_src=S1U_IPV4, ip_dst=ENODEB_IPV4)

        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        # UPF counter ID
        ctr_id = self.new_counter_id()
        # Meter IDs
        app_meter_id = self.unique_rule_id()
        session_meter_id = self.unique_rule_id()

        # program all the tables
        self.add_entries_for_downlink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, tc=tc,
                                          app_meter_id=app_meter_id,
                                          app_meter_max_bitrate=app_bitrate,
                                          session_meter_id=session_meter_id,
                                          session_meter_max_bitrate=session_bitrate,
                                          app_filtering=app_filtering, drop=False)

        # read pre and post-QoS packet and byte counters
        self.read_upf_counters(ctr_id)

        # send packet and verify it is decapsulated and routed
        testutils.send_packet(self, self.port1, pkt)
        if app_bitrate == 0 or session_bitrate == 0:
            testutils.verify_no_other_packets(self)
        else:
            testutils.verify_packet(self, exp_pkt, self.port2)

        # Check if pre and post-QoS packet and byte counters incremented
        if app_bitrate == 0 or session_bitrate == 0:
            post_qos_pkts = 0
            post_qos_bytes = 0
        else:
            post_qos_pkts = 1
            post_qos_bytes = len(pkt)
        self.verify_counters_increased(ctr_id, 1, len(pkt), post_qos_pkts, post_qos_bytes)


@group("gtpu")
class GtpuDropUplinkTest(GtpuBaseTest):
    """ Tests that a packet received from a UE gets decapsulated and dropped because of terminations rule.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils,
                          "simple_%s_packet" % pkt_type)(eth_src=ENODEB_MAC, eth_dst=SWITCH_MAC,
                                                         ip_src=UE_IPV4, ip_dst=PDN_IPV4)
            pkt = self.gtpu_encap(pkt, ip_src=ENODEB_IPV4, ip_dst=S1U_IPV4)

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

        # UPF counter ID
        ctr_id = self.new_counter_id()

        # program all the tables
        self.add_entries_for_uplink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, drop=True)

        # read pre and post-QoS packet and byte counters
        self.read_upf_counters(ctr_id)

        # send packet and verify it is dropped
        testutils.send_packet(self, self.port1, pkt)
        testutils.verify_no_other_packets(self)

        # Check if pre-QoS packet and byte counters incremented,
        # and verify the post-QoS counters did not increment
        self.verify_counters_increased(ctr_id, 1, len(pkt), 0, 0)


@group("gtpu")
class GtpuDropDownlinkTest(GtpuBaseTest):
    """ Tests that a packet received from the internet/core gets dropped because of terminations rule.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils,
                          "simple_%s_packet" % pkt_type)(eth_src=PDN_MAC, eth_dst=SWITCH_MAC,
                                                         ip_src=PDN_IPV4, ip_dst=UE_IPV4)
            self.testPacket(pkt)

    @autocleanup
    def testPacket(self, pkt):

        # build the expected encapsulated packet
        exp_pkt = pkt.copy()
        dst_mac = ENODEB_MAC
        # force recomputation of checksum after routing/ttl decrement
        del pkt[IP].chksum

        # Should be encapped too obv.
        exp_pkt = self.gtpu_encap(exp_pkt)

        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        # UPF counter ID
        ctr_id = self.new_counter_id()

        # program all the tables
        self.add_entries_for_downlink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, drop=True)

        # read pre and post-QoS packet and byte counters
        self.read_upf_counters(ctr_id)

        # send packet and verify it is dropped
        testutils.send_packet(self, self.port1, pkt)
        testutils.verify_no_other_packets(self)

        # Check if pre-QoS packet and byte counters incremented,
        # and verify the post-QoS counters did not increment
        self.verify_counters_increased(ctr_id, 1, len(pkt), 0, 0)


class GtpuDdnDigestTest(GtpuBaseTest):
    """ Tests that the switch sends digests for buffering sessions.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils,
                          "simple_%s_packet" % pkt_type)(eth_src=PDN_MAC, eth_dst=SWITCH_MAC,
                                                         ip_src=PDN_IPV4, ip_dst=UE_IPV4)
            self.testPacket(pkt)

    @autocleanup
    def testPacket(self, pkt):
        # Wait up to 1 seconds before sending duplicate digests for the same FSEID.
        self.set_up_ddn_digest(ack_timeout_ns=1 * 10**9)

        # Build the expected encapsulated pkt that we would receive as output without buffering.
        # The actual pkt will be dropped, but we still need it to populate tables with tunneling info.
        exp_pkt = pkt.copy()
        exp_pkt = self.gtpu_encap(exp_pkt, ip_src=S1U_IPV4, ip_dst=ENODEB_IPV4)
        pkt_route(exp_pkt, ENODEB_MAC)
        pkt_decrement_ttl(exp_pkt)

        # UPF counter ID.
        ctr_id = self.new_counter_id()

        # Program all the tables.
        self.add_entries_for_downlink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, buffer=True)

        # Read pre and post-QoS packet and byte counters.
        self.read_upf_counters(ctr_id)

        # Send 1st packet.
        testutils.send_packet(self, self.port1, pkt)
        # Only pre-QoS counters should increase
        self.verify_counters_increased(ctr_id, 1, len(pkt), 0, 0)
        # Verify that we have received the DDN digest
        exp_digest_data = self.helper.build_p4data_struct(
            [self.helper.build_p4data_bitstring(encode(pkt[IP].dst, UE_ADDR_BITWIDTH))])
        self.verify_digest_list("ddn_digest_t", exp_digest_data)

        # Send 2nd packet immediately, verify counter increase but NO digest should be generated.
        self.read_upf_counters(ctr_id)
        testutils.send_packet(self, self.port1, pkt)
        self.verify_counters_increased(ctr_id, 1, len(pkt), 0, 0)
        self.verify_no_other_digest_list(timeout=1)

        # Send third packet after waiting at least ack_timeout_ns.
        # We should receive a new digest.
        sleep(1.1)
        self.read_upf_counters(ctr_id)
        testutils.send_packet(self, self.port1, pkt)
        self.verify_counters_increased(ctr_id, 1, len(pkt), 0, 0)
        self.verify_digest_list("ddn_digest_t", exp_digest_data)

        # All packets should have been buffered, not forwarded.
        testutils.verify_no_other_packets(self)


class GtpEndMarkerPacketOutTest(GtpuBaseTest):
    """ Tests that the switch can route end-marker packet-outs like regular packets, i.e., by
    rewriting MAC addresses and forwarding to an egress port.
    """

    @autocleanup
    def runTest(self):
        # gtp_type=254 -> end marker
        pkt = Ether(src=0x0, dst=0x0) / \
               IP(src=S1U_IPV4, dst=ENODEB_IPV4) / \
               UDP(sport=UDP_GTP_PORT, dport=UDP_GTP_PORT, chksum=0) / \
               gtp.GTPHeader(gtp_type=254, teid=1)

        # Expect routed packet
        exp_pkt = pkt.copy()
        exp_pkt[Ether].src = SWITCH_MAC
        exp_pkt[Ether].dst = ENODEB_MAC
        pkt_decrement_ttl(exp_pkt)

        outport = self.port2

        self.add_routing_entry(ip_prefix=exp_pkt[IP].dst + '/32', src_mac=exp_pkt[Ether].src,
                               dst_mac=exp_pkt[Ether].dst, egress_port=outport)

        self.send_packet_out(self.helper.build_packet_out(pkt, {"reserved": 0}))
        testutils.verify_packet(self, exp_pkt, outport)


@group("gtpu")
class AclPuntTest(GtpuBaseTest):
    """ Test that the ACL table punts a packet to the CPU
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4[:1]:
            print_inline("%s ... " % pkt_type)
            pkt = getattr(testutils, "simple_%s_packet" % pkt_type)()
            self.testPacket(pkt)

    @autocleanup
    def testPacket(self, pkt):
        # exp_pkt = CpuHeader(port_num=self.port1) / pkt
        exp_pkt = pkt
        exp_pkt_in_msg = self.helper.build_packet_in(
            exp_pkt, metadata={
                "ingress_port": self.port1,
                "_pad": 0
            })

        self.add_device_mac(pkt[Ether].dst)

        self.add_cpu_clone_session()
        self.add_acl_entry(clone_to_cpu=True, eth_type=pkt[Ether].type, ipv4_src=pkt[IP].src,
                           ipv4_dst=pkt[IP].dst, ipv4_proto=pkt[IP].proto)

        testutils.send_packet(self, self.port1, pkt)

        self.verify_packet_in(exp_pkt_in_msg)


@group("gtpu")
class GtpuEncapPscDownlinkTest(GtpuBaseTest):
    """ Tests that a packet received from the internet/core gets encapsulated
        and forwarded with PDU Session Container extension header.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            for app_filtering in [False, True]:
                print_inline("%s, app_filtering=%s... " % (pkt_type, app_filtering))
                pkt = getattr(testutils,
                              "simple_%s_packet" % pkt_type)(eth_src=PDN_MAC, eth_dst=SWITCH_MAC,
                                                             ip_src=PDN_IPV4, ip_dst=UE_IPV4)
                self.testPacket(pkt, app_filtering)

    @autocleanup
    def testPacket(self, pkt, app_filtering):
        # build the expected encapsulated packet
        exp_pkt = pkt.copy()
        dst_mac = ENODEB_MAC

        # Encap with PSC ext header and given QFI
        exp_pkt = self.gtpu_encap(exp_pkt, ip_src=S1U_IPV4, ip_dst=ENODEB_IPV4,
                                  ext_psc_type=GTPU_EXT_PSC_TYPE_DL, ext_psc_qfi=1)

        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        # UPF counter ID
        ctr_id = self.new_counter_id()

        # program all the tables
        self.add_entries_for_downlink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, drop=False,
                                          qfi=1, push_qfi=True, app_filtering=app_filtering)

        # read pre and post-QoS packet and byte counters
        self.read_upf_counters(ctr_id)

        # send packet and verify it is encapsulated and routed
        testutils.send_packet(self, self.port1, pkt)
        testutils.verify_packet(self, exp_pkt, self.port2)

        # Check if pre and post-QoS packet and byte counters incremented
        self.verify_counters_increased(ctr_id, 1, len(pkt), 1, len(pkt))


@group("gtpu")
class GtpuDecapPscUplinkTest(GtpuBaseTest):
    """ Tests that a packet received from a UE with PSC header gets decapsulated
        and routed.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in self.supported_l4:
            for app_filtering in [False, True]:
                print_inline("%s, app_filtering=%s... " % (pkt_type, app_filtering))
                pkt = getattr(testutils,
                              "simple_%s_packet" % pkt_type)(eth_src=ENODEB_MAC, eth_dst=SWITCH_MAC,
                                                             ip_src=UE_IPV4, ip_dst=PDN_IPV4)
                pkt = self.gtpu_encap(pkt, ip_src=ENODEB_IPV4, ip_dst=S1U_IPV4,
                                      ext_psc_type=GTPU_EXT_PSC_TYPE_UL, ext_psc_qfi=1)

                self.testPacket(pkt, app_filtering)

    @autocleanup
    def testPacket(self, pkt, app_filtering):

        if gtp.GTP_U_Header not in pkt:
            raise AssertionError("Packet given to decap test is not encapsulated!")
        # build the expected decapsulated packet
        exp_pkt = self.gtpu_decap(pkt)
        dst_mac = PDN_MAC
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        # UPF counter ID
        ctr_id = self.new_counter_id()

        # program all the tables
        self.add_entries_for_uplink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, drop=False,
                                        app_filtering=app_filtering)

        # read pre and post-QoS packet and byte counters
        self.read_upf_counters(ctr_id)

        # send packet and verify it is decapsulated and routed
        testutils.send_packet(self, self.port1, pkt)
        testutils.verify_packet(self, exp_pkt, self.port2)

        # Check if pre and post-QoS packet and byte counters incremented
        self.verify_counters_increased(ctr_id, 1, len(pkt), 1, len(pkt))
