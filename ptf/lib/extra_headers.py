# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

from scapy.fields import BitField
from scapy.packet import bind_layers, Packet
from scapy.layers.l2 import Ether


class CpuHeader(Packet):
    name = "CpuHeader"
    fields_desc = [
        BitField("port_num", 0, 9),
        BitField("padding", 0, 7),
    ]


bind_layers(CpuHeader, Ether)
