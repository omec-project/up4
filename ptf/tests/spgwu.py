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

from base_test import pkt_route, pkt_decrement_ttl, P4RuntimeTest, \
                      autocleanup, print_inline
from ptf.testutils import group
from ptf import testutils as testutils
from scapy.contrib import gtp
from time import sleep
from enum import Enum
import random
random.seed(123456)  # for reproducible PTF tests

UDP_GTP_PORT = 2152

class Action(Enum):
    DROP    = 1
    FORWARD = 2
    TUNNEL  = 3

class GtpuBaseTest(P4RuntimeTest):

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

        # TODO: compute checksums correctly in switch and remove the need zeroing checksums
        return Ether(src=pkt[Ether].src, dst=pkt[Ether].dst) / \
               IPHeader(src=ip_src, dst=ip_dst, chksum=0, id=5395) / \
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

    def read_pdr_counter(self, index, pre_qos=True, pkts=True):
        """ Reads the per-PDR counter.
            If pre_qos=True, reads the pre-QoS counter. Else  reads the post-QoS counter
            If pkts=True, returns packet count for the selected counter. Else returns byte count.
        """
        counter_name = "IngressPipeImpl.pre_qos_pdr_counter"
        if not pre_qos:
            counter_name = "EgressPipeImpl.post_qos_pdr_counter"

        if pkts:
            return self.helper.read_pkt_count(counter_name, index)
        else:
            return self.helper.read_byte_count(counter_name, index)

    def add_interface(self, iface_type, port_nums):
        """ Binds a 3GPP interface to a set of ports.
        """
        _iface_type = self.helper.get_enum_member_val("InterfaceType", iface_type)

        for port_num in port_nums:
            self.insert(
                self.helper.build_table_entry(
                    table_name="IngressPipeImpl.source_iface_lookup",
                    match_fields={
                        # Exact match.
                        "std_meta.ingress_port": port_num
                    },
                    action_name="IngressPipeImpl.set_source_iface_type",
                    action_params={"src_iface_type": _iface_type},
                ))

    def add_session(self, session_id, ue_addr):
        """ Associates the given session_id with the given UE address.
        """
        self.insert(
            self.helper.build_table_entry(
                table_name="IngressPipeImpl.fseid_lookup",
                match_fields={
                    # Exact match.
                    "local_meta.ue_addr": ue_addr
                },
                action_name="IngressPipeImpl.set_fseid",
                action_params={"fseid": session_id},
            ))

    def add_pdr(self, pdr_id, far_id, session_id, src_iface, ctr_id, ue_addr=None, ue_mask=None,
                inet_addr=None, inet_mask=None, ue_l4_port=None, ue_l4_port_hi=None,
                inet_l4_port=None, inet_l4_port_hi=None, ip_proto=None, ip_proto_mask=None,
                qer_id=0, qfi=0, needs_gtpu_decap=False, needs_udp_decap=False,
                needs_vlan_removal=False, net_instance=0, priority=10):

        ALL_ONES_32 = (1 << 32) - 1
        ALL_ONES_8 = (1 << 8) - 1

        _src_iface = self.helper.get_enum_member_val("InterfaceType", src_iface)
        match_fields = {"fseid": session_id, "src_iface_type": _src_iface}
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
                table_name="IngressPipeImpl.pdrs",
                match_fields=match_fields,
                action_name="IngressPipeImpl.set_pdr_attributes",
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

    def add_far_forward(self, far_id, session_id, egress_port, dst_mac):
        self.insert(
            self.helper.build_table_entry(
                table_name="IngressPipeImpl.fars",
                match_fields={
                    "far_id": far_id,
                    "session_id": session_id,
                },
                action_name="IngressPipeImpl.set_far_attributes_forward",
                action_params={
                    "egress_spec": egress_port,
                    "dst_mac": dst_mac,
                },
            ))

    def add_far_drop(self, far_id, session_id):
        self.insert(
            self.helper.build_table_entry(
                table_name="IngressPipeImpl.fars",
                match_fields={
                    "far_id": far_id,
                    "session_id": session_id,
                },
                action_name="IngressPipeImpl.set_far_attributes_drop",
            ))

    def add_far_tunnel(self, far_id, session_id, teid, src_addr, dst_addr, egress_port, dst_mac,
                       tunnel_type="GTPU"):
        _tunnel_type = self.helper.get_enum_member_val("TunnelType", tunnel_type)
        self.insert(
            self.helper.build_table_entry(
                table_name="IngressPipeImpl.fars",
                match_fields={
                    "far_id": far_id,
                    "session_id": session_id,
                },
                action_name="IngressPipeImpl.set_far_attributes_tunnel",
                action_params={
                    "tunnel_type": _tunnel_type,
                    "src_addr": src_addr,
                    "dst_addr": dst_addr,
                    "teid": teid,
                    "egress_spec": egress_port,
                    "dst_mac": dst_mac,
                },
            ))

    def add_far(self, far_type="FORWARD", **kwargs):
        # TODO: complete this method if it is deemed useful
        funcs = {
            "FORWARD": self.add_far_forward,
            "TUNNEL": self.add_far_tunnel,
        }

        return funcs[far_type](**kwargs)

    _last_used_rule_id = -1

    def _alloc_rule_id(self):
        """ Stupid helper method for generating unique ruleIDs.
        """
        self._last_used_rule_id += 1
        return self._last_used_rule_id

    def add_entries_for_uplink_pkt(self, pkt, exp_pkt, inport, outport, ctr_id, action, session_id=None):
        """ Add all table entries required for the given uplink packet to flow through the UPF
            and emit as the given expected packet.
        """
        if session_id is None:
            session_id = random.randint(0, 1023)

        inner_pkt = pkt[gtp.GTP_U_Header].payload

        pdr_id = self._alloc_rule_id()
        far_id = self._alloc_rule_id()

        ue_l4_port = None
        inet_l4_port = None
        if (UDP in inner_pkt) or (TCP in inner_pkt):
            ue_l4_port = inner_pkt.sport
            net_l4_port = inner_pkt.dport

        self.add_interface(iface_type="ACCESS", port_nums=[inport])
        self.add_interface(iface_type="CORE", port_nums=[outport])

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
            needs_gtpu_decap=True,
        )

        if (action == Action.FORWARD):

            self.add_far_forward(
                far_id=far_id,
                session_id=session_id,
                egress_port=outport,
                dst_mac=exp_pkt[Ether].dst,
            )
        elif (action == Action.DROP):
            self.add_far_drop(far_id=far_id, session_id=session_id)
        else:
            raise AssertionError("Action Not handled")

    def add_entries_for_downlink_pkt(self, pkt, exp_pkt, inport, outport, ctr_id, action, session_id=None):
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

        pdr_id = self._alloc_rule_id()
        far_id = self._alloc_rule_id()

        ue_l4_port = None
        inet_l4_port = None
        if (UDP in pkt) or (TCP in pkt):
            ue_l4_port = pkt.dport
            inet_l4_port = pkt.sport

        self.add_interface(iface_type="CORE", port_nums=[inport])
        self.add_interface(iface_type="ACCESS", port_nums=[outport])

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
                dst_addr=exp_pkt[IP].dst,
                egress_port=outport,
                dst_mac=exp_pkt[Ether].dst,
            )
        elif (action == Action.DROP):
            self.add_far_drop(far_id=far_id, session_id=session_id)
        else:
            raise AssertionError("FAR Action Not handled %d",action);

    def random_mac_addr(self):
        octets = [random.randint(0, 0xff) for _ in range(6)]
        octets[0] = 0x32  # arbitrary valid starting byte for a MAC addr
        return ':'.join(["%02x" % octet for octet in octets])

    def random_ip_addr(self):
        octets = [random.randint(0, 255) for _ in range(4)]
        return '.'.join([str(octet) for octet in octets])


@group("gtpu")
class GtpuDecapUplinkTest(GtpuBaseTest):
    """ Tests that a packet received from a UE gets decapsulated and routed.
    """

    def runTest(self):
        # Test with different type of packets.
        for pkt_type in ["udp"]:
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
        ctr_id = random.randint(0, 1023)

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
        for pkt_type in ["udp"]:
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
        ctr_id = random.randint(0, 1023)

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
        for pkt_type in ["udp"]:
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
        ctr_id = random.randint(0, 1023)

        # program all the tables
        self.add_entries_for_uplink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, Action.DROP)

        # read pre-QoS packet and byte counters
        uplink_pkt_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        uplink_byte_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)

        # read post-QoS packet and byte counters
        uplink_pkt_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        uplink_byte_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)

        # send packet and verify it is decapsulated and routed
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_no_other_packets(self)

        # wait for counters to update
        sleep(0.1)

        # Check if pre-QoS packet and byte counters incremented
        uplink_pkt_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        uplink_byte_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)
        self.assertEqual(uplink_pkt_count_new, uplink_pkt_count + 1)
        self.assertEqual(uplink_byte_count_new, uplink_byte_count + len(pkt))

        # Make sure post-QoS packet and byte counters shouldnt be incremented in Post Qos. 
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
        for pkt_type in ["udp"]:
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
        ctr_id = random.randint(0, 1023)

        # program all the tables
        self.add_entries_for_downlink_pkt(pkt, exp_pkt, self.port1, self.port2, ctr_id, Action.DROP)

        # read pre-QoS packet and byte counters
        downlink_pkt_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        downlink_byte_count = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)

        # read post-QoS packet and byte counters
        downlink_pkt_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        downlink_byte_count2 = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)

        # send packet and verify it is decapsulated and routed
        testutils.send_packet(self, self.port1, str(pkt))
        testutils.verify_no_other_packets(self)

        # wait for counters to update
        sleep(0.1)

        # Check if pre-QoS packet and byte counters incremented
        downlink_pkt_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=True)
        downlink_byte_count_new = self.read_pdr_counter(ctr_id, pre_qos=True, pkts=False)
        self.assertEqual(downlink_pkt_count_new, downlink_pkt_count + 1)
        self.assertEqual(downlink_byte_count_new, downlink_byte_count + len(pkt))

        # Make sure post-QoS packet and byte counters shouldnt be incremented in Post Qos. 
        downlink_pkt_count2_new = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=True)
        downlink_byte_count2_new = self.read_pdr_counter(ctr_id, pre_qos=False, pkts=False)
        self.assertEqual(downlink_pkt_count2_new, downlink_pkt_count)
        self.assertEqual(downlink_byte_count2_new, downlink_byte_count)


