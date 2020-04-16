import struct
from scapy.fields import BitEnumField, BitField, ByteEnumField, ByteField, \
    ConditionalField, FieldLenField, FieldListField, FlagsField, IntField, \
    IPField, PacketListField, ShortField, StrFixedLenField, StrLenField, \
    XBitField, XByteField, XIntField
from scapy.layers.inet import IP, UDP
from scapy.layers.inet6 import IPv6, IP6Field
from scapy.packet import bind_layers, bind_bottom_up, bind_top_down, \
    Packet, Raw


GTPmessageType = {1: "echo_request",
                  2: "echo_response",
                  16: "create_pdp_context_req",
                  17: "create_pdp_context_res",
                  18: "update_pdp_context_req",
                  19: "update_pdp_context_resp",
                  20: "delete_pdp_context_req",
                  21: "delete_pdp_context_res",
                  26: "error_indication",
                  27: "pdu_notification_req",
                  31: "supported_extension_headers_notification",
                  254: "end_marker",
                  255: "g_pdu"}

ExtensionHeadersTypes = {
    0: "No more extension headers",
    1: "Reserved",
    2: "Reserved",
    64: "UDP Port",
    133: "PDU Session Container",
    192: "PDCP PDU Number",
    193: "Reserved",
    194: "Reserved"
}

class GTPU(Packet):
    # Adapted from https://github.com/secdev/scapy/blob/master/scapy/contrib/gtp.py
    name = "GTPU"
    fields_desc = [BitField("version", 1, 3),
                   BitField("PT", 1, 1),
                   BitField("reserved", 0, 1),
                   BitField("E", 0, 1),
                   BitField("S", 0, 1),
                   BitField("PN", 0, 1),
                   ByteEnumField("gtp_type", 255, GTPmessageType),
                   ShortField("length", None),
                   IntField("teid", 0),
                   ConditionalField(XBitField("seq", 0, 16), lambda pkt:pkt.E == 1 or pkt.S == 1 or pkt.PN == 1),  # noqa: E501
                   ConditionalField(ByteField("npdu", 0), lambda pkt:pkt.E == 1 or pkt.S == 1 or pkt.PN == 1),  # noqa: E501
                   ConditionalField(ByteEnumField("next_ex", 0, ExtensionHeadersTypes), lambda pkt:pkt.E == 1 or pkt.S == 1 or pkt.PN == 1), ]  # noqa: E501

    def guess_payload_class(self, payload):
        return IP

    def post_build(self, p, pay):
        p += pay
        if self.length is None:
            tmp_len = len(p) - 8
            p = p[:2] + struct.pack("!H", tmp_len) + p[4:]
        return p

    def hashret(self):
        return struct.pack("B", self.version) + self.payload.hashret()

    def answers(self, other):
        return (isinstance(other, GTPHeader) and
                self.version == other.version and
                self.payload.answers(other.payload))

bind_layers(UDP, GTPU, dport=2152, sport=2152)


