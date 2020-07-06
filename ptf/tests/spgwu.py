# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0

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
from scapy.all import IP, IPv6, TCP, UDP, ICMP, Ether
from time import sleep

from spgwu_base import GtpuBaseTest
from unittest import skip

from extra_headers import CpuHeader

CPU_CLONE_SESSION_ID = 99
UE_IPV4 = "17.0.0.1"
ENODEB_IPV4 = "140.0.100.1"
S1U_IPV4 = "140.0.100.2"
SGW_IPV4 = "140.0.200.2"
PDN_IPV4 = "140.0.200.1"
SWITCH_MAC = "00:AA:00:00:00:01"

ENODEB_MAC = "00:00:00:00:00:10"
PDN_MAC = "00:00:00:00:00:20"


@group("gtpu")
class GtpuDecapUplinkTest(GtpuBaseTest):
    """ Tests that a packet received from a UE gets decapsulated and routed.
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
        dst_mac = PDN_MAC
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        # PDR counter ID
        ctr_id = self.new_counter_id()

        # program all the tables
        self.add_entries_for_uplink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, drop=False)

        # read pre and post-QoS packet and byte counters
        self.read_pdr_counters(ctr_id)

        # send packet and verify it is decapsulated and routed
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, exp_pkt, self.port2)

        # Check if pre and post-QoS packet and byte counters incremented
        self.verify_counters_increased(ctr_id, 1, len(pkt), 1, len(pkt))


@group("gtpu")
class GtpuEncapDownlinkTest(GtpuBaseTest):
    """ Tests that a packet received from the internet/core gets encapsulated and forwarded.
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
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        # Should be encapped too obv.
        exp_pkt = self.gtpu_encap(exp_pkt, ip_src=S1U_IPV4, ip_dst=ENODEB_IPV4)

        # PDR counter ID
        ctr_id = self.new_counter_id()

        # program all the tables
        self.add_entries_for_downlink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, drop=False)

        # read pre and post-QoS packet and byte counters
        self.read_pdr_counters(ctr_id)

        # send packet and verify it is decapsulated and routed
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, exp_pkt, self.port2)

        # Check if pre and post-QoS packet and byte counters incremented
        self.verify_counters_increased(ctr_id, 1, len(pkt), 1, len(pkt))


@group("gtpu")
class GtpuDropUplinkTest(GtpuBaseTest):
    """ Tests that a packet received from a UE gets decapsulated and dropped because of FAR rule.
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

        # PDR counter ID
        ctr_id = self.new_counter_id()

        # program all the tables
        self.add_entries_for_uplink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, drop=True)

        # read pre and post-QoS packet and byte counters
        self.read_pdr_counters(ctr_id)

        # send packet and verify it is dropped
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_no_other_packets(self)

        # Check if pre-QoS packet and byte counters incremented,
        # and verify the post-QoS counters did not increment
        self.verify_counters_increased(ctr_id, 1, len(pkt), 0, 0)


@group("gtpu")
class GtpuDropDownlinkTest(GtpuBaseTest):
    """ Tests that a packet received from the internet/core gets dropped because of FAR rule.
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
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)
        # force recomputation of checksum after routing/ttl decrement
        del pkt[IP].chksum

        # Should be encapped too obv.
        exp_pkt = self.gtpu_encap(exp_pkt)

        # PDR counter ID
        ctr_id = self.new_counter_id()

        # program all the tables
        self.add_entries_for_downlink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, drop=True)

        # read pre and post-QoS packet and byte counters
        self.read_pdr_counters(ctr_id)

        # send packet and verify it is dropped
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_no_other_packets(self)

        # Check if pre-QoS packet and byte counters incremented,
        # and verify the post-QoS counters did not increment
        self.verify_counters_increased(ctr_id, 1, len(pkt), 0, 0)


@group("gtpu")
@skip("ACL punting not yet robust")
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
            str(exp_pkt), metadata={
                "ingress_port": self.port1,
                "_pad": 0
            })

        self.add_device_mac(pkt[Ether].dst)

        self.add_cpu_clone_session()
        self.add_acl_entry(clone_to_cpu=True, eth_type=pkt[Ether].type, ipv4_src=pkt[IP].src,
                           ipv4_dst=pkt[IP].dst, ipv4_proto=pkt[IP].proto)

        testutils.send_packet(self, self.port1, str(pkt))

        self.verify_packet_in(exp_pkt_in_msg)


@group("gtpu")
@skip("I misunderstood DHCP")
class GtpuUplinkDhcpTest(GtpuBaseTest):
    """ Tests UE DHCP message is forwarded out the N4 interface
    """

    def runTest(self):
        # Test with different type of packets.
        pkt = testutils.dhcp_request_packet()
        pkt = self.gtpu_encap(pkt)
        self.testPacket(pkt)

    @autocleanup
    def testPacket(self, pkt):

        if gtp.GTP_U_Header not in pkt:
            raise AssertionError("Packet given to uplink DHCP test is not encapsulated!")
        # build the expected N4 packet
        exp_pkt = self.gtpu_decap(pkt)
        dst_mac = self.random_mac_addr()
        # Expected pkt should have routed MAC addresses and decremented hop
        # limit (TTL).
        pkt_route(exp_pkt, dst_mac)
        pkt_decrement_ttl(exp_pkt)

        exp_pkt = self.gtpu_encap(pkt)

        ctr_id = self.new_counter_id()

        self.add_device_mac(pkt[Ether].dst)
        self.add_interface(ip_prefix=pkt[IP].dst + '/32', iface_type="ACCESS", direction="UPLINK")

        self.add_global_session(
            n4_ip=exp_pkt[IP].src,
            smf_ip=exp_pkt[IP].dst,
            smf_mac=exp_pkt[Ether].dst,
            smf_port=self.port2,
            n4_teid=exp_pkt[gtp.GTP_U_Header].teid,
            dhcp_req_ctr_id=ctr_id,
        )

        # read pre and post-QoS packet and byte counters
        self.read_pdr_counters(ctr_id)

        # send packet and verify it is decapsulated and routed
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_packet(self, exp_pkt, self.port2)

        # Check if pre and post-QoS packet and byte counters incremented
        self.verify_counters_increased(ctr_id, 1, len(pkt), 1, len(pkt))
