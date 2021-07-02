/*
 * SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 * SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */

#ifndef __PARSER__
#define __PARSER__

#include "define.p4"
#include "header.p4"


//------------------------------------------------------------------------------
// PARSER
//------------------------------------------------------------------------------
parser ParserImpl (packet_in packet,
                   out parsed_headers_t hdr,
                   inout local_metadata_t local_meta,
                   inout standard_metadata_t std_meta)
{

    // We assume the first header will always be the Ethernet one, unless the
    // the packet is a packet-out coming from the CPU_PORT.
    state start {
        transition select(std_meta.ingress_port) {
            CPU_PORT: parse_packet_out;
            default: parse_ethernet;
        }
    }

    state parse_packet_out {
        packet.extract(hdr.packet_out);
        transition parse_ethernet;
    }

    state parse_ethernet {
        packet.extract(hdr.ethernet);
        transition select(hdr.ethernet.ether_type){
            EtherType.IPV4: parse_ipv4;
            default: accept;
        }
    }

    state parse_ipv4 {
        packet.extract(hdr.ipv4);
        transition select(hdr.ipv4.proto) {
            IpProtocol.UDP:  parse_udp;
            IpProtocol.TCP:  parse_tcp;
            IpProtocol.ICMP: parse_icmp;
            default: accept;
        }
    }

    // Eventualy add VLAN header parsing

    state parse_udp {
        packet.extract(hdr.udp);
        // note: this eventually wont work
        local_meta.l4_sport = hdr.udp.sport;
        local_meta.l4_dport = hdr.udp.dport;
        gtpu_t gtpu = packet.lookahead<gtpu_t>();
        transition select(hdr.udp.dport, gtpu.version, gtpu.msgtype) {
            (L4Port.IPV4_IN_UDP, _, _): parse_inner_ipv4;
            // Treat GTP control traffic as payload.
            (L4Port.GTP_GPDU, GTP_V1, GTPUMessageType.GPDU): parse_gtpu;
            default: accept;
        }
    }

    state parse_tcp {
        packet.extract(hdr.tcp);
        local_meta.l4_sport = hdr.tcp.sport;
        local_meta.l4_dport = hdr.tcp.dport;
        transition accept;
    }

    state parse_icmp {
        packet.extract(hdr.icmp);
        transition accept;
    }

    state parse_gtpu {
        packet.extract(hdr.gtpu);
        local_meta.teid = hdr.gtpu.teid;
        // Eventually need to add conditional parsing, in the case of non-ip payloads.
        // Also need to add conditional GTP-U extension headers. They are variable length, so will be tricky.
        transition parse_inner_ipv4;
    }

    //-----------------
    // Inner packet
    //-----------------

    state parse_inner_ipv4 {
        packet.extract(hdr.inner_ipv4);
        transition select(hdr.inner_ipv4.proto) {
            IpProtocol.UDP:  parse_inner_udp;
            IpProtocol.TCP:  parse_inner_tcp;
            IpProtocol.ICMP: parse_inner_icmp;
            default: accept;
        }
    }

    state parse_inner_udp {
        packet.extract(hdr.inner_udp);
        local_meta.l4_sport = hdr.inner_udp.sport;
        local_meta.l4_dport = hdr.inner_udp.dport;
        transition accept;
    }

    state parse_inner_tcp {
        packet.extract(hdr.inner_tcp);
        local_meta.l4_sport = hdr.inner_tcp.sport;
        local_meta.l4_dport = hdr.inner_tcp.dport;
        transition accept;
    }

    state parse_inner_icmp {
        packet.extract(hdr.inner_icmp);
        transition accept;
    }
}

//------------------------------------------------------------------------------
// DEPARSER
//------------------------------------------------------------------------------
control DeparserImpl(packet_out packet, in parsed_headers_t hdr) {
    apply {
        packet.emit(hdr.packet_in);
        packet.emit(hdr.ethernet);
        packet.emit(hdr.outer_ipv4);
        packet.emit(hdr.outer_udp);
        packet.emit(hdr.gtpu);
        packet.emit(hdr.ipv4);
        packet.emit(hdr.udp);
        packet.emit(hdr.tcp);
        packet.emit(hdr.icmp);
    }
}


#endif
