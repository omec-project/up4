from scapy.data import IP_PROTOS
from scapy.fields import ConditionalField, IPField, BitField, BitEnumField, \
    FieldLenField, StrLenField, ByteField, ShortField, ByteEnumField, \
    DestField, FieldListField, FlagsField, IntField, MultiEnumField, \
    PacketListField, ShortEnumField, SourceIPField, StrField, \
    StrFixedLenField, XByteField, XShortField
from scapy.packet import bind_layers, bind_bottom_up, bind_top_down, \
    Packet, Raw
from scapy.layers.l2 import Ether
from scapy.layers.inet import IP, UDP, TCP, ICMP
from scapy.layers.inet6 import IPv6, IP6Field

BUFFER_TUNNEL_PROTO = 0xFD
QOS_TUNNEL_PROTO = 0xFE


class BufferTunnel(Packet):
    name = "BufferTunnel"
    fields_desc = [
        ByteEnumField("next_header", 0, IP_PROTOS),
        IntField("device_id", 0),
        IntField("bar_id", 0),
        BitField("padding", 0, 7),
        BitField("original_ingress_port", 0, 9)
    ]


class QosTunnel(Packet):
    name = "QosTunnel"
    fields_desc = [
        ByteEnumField("next_header", 0, IP_PROTOS),
        IntField("device_id", 0),
        IntField("qer_id", 0),
        ShortField("ctr_idx", 0),
        BitField("padding", 0, 7),
        BitField("original_ingress_port", 0, 9)
    ]


bind_layers(IP, BufferTunnel, proto=BUFFER_TUNNEL_PROTO)
bind_layers(IP, QosTunnel, proto=QOS_TUNNEL_PROTO)

for header in [BufferTunnel, QosTunnel]:
    bind_layers(header, UDP, next_header=17)
    bind_layers(header, TCP, next_header=6)
    bind_layers(header, ICMP, next_header=1)
