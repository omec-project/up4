/*
 * SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 * SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
#ifndef __HEADERS__
#define __HEADERS__

#include "define.p4"


header ethernet_t {
    mac_addr_t  dst_addr;
    mac_addr_t  src_addr;
    EtherType   ether_type;
}

header ipv4_t {
    bit<4>          version;
    bit<4>          ihl;
    bit<6>          dscp;
    bit<2>          ecn;
    bit<16>         total_len;
    bit<16>         identification;
    bit<3>          flags;
    bit<13>         frag_offset;
    bit<8>          ttl;
    IpProtocol      proto;
    bit<16>         checksum;
    ipv4_addr_t     src_addr;
    ipv4_addr_t     dst_addr;
}

header tcp_t {
    L4Port  sport;
    L4Port  dport;
    bit<32> seq_no;
    bit<32> ack_no;
    bit<4>  data_offset;
    bit<3>  res;
    bit<3>  ecn;
    bit<6>  ctrl;
    bit<16> window;
    bit<16> checksum;
    bit<16> urgent_ptr;
}

header udp_t {
    L4Port  sport;
    L4Port  dport;
    bit<16> len;
    bit<16> checksum;
}

header icmp_t {
    bit<8> icmp_type;
    bit<8> icmp_code;
    bit<16> checksum;
    bit<16> identifier;
    bit<16> sequence_number;
    bit<64> timestamp;
}

header gtpu_t {
    bit<3>          version;    /* version */
    bit<1>          pt;         /* protocol type */
    bit<1>          spare;      /* reserved */
    bit<1>          ex_flag;    /* next extension hdr present? */
    bit<1>          seq_flag;   /* sequence no. */
    bit<1>          npdu_flag;  /* n-pdn number present ? */
    GTPUMessageType msgtype;    /* message type */
    bit<16>         msglen;     /* message length */
    teid_t          teid;       /* tunnel endpoint id */
}


@controller_header("packet_out")
header packet_out_t {
    port_num_t  egress_port;
    bit<7>      _pad;
}

@controller_header("packet_in")
header packet_in_t {
    port_num_t  ingress_port;
    bit<7>      _pad;
}



struct parsed_headers_t {
    packet_out_t  packet_out;
    packet_in_t   packet_in;
    ethernet_t ethernet;
    ipv4_t outer_ipv4;
    udp_t outer_udp;
    gtpu_t gtpu;
    ipv4_t ipv4;
    udp_t udp;
    tcp_t tcp;
    icmp_t icmp;
    ipv4_t inner_ipv4;
    udp_t inner_udp;
    tcp_t inner_tcp;
    icmp_t inner_icmp;
}

//------------------------------------------------------------------------------
// METADATA DEFINITIONS
//------------------------------------------------------------------------------

// Data associated with a PDR entry
struct pdr_metadata_t {
    pdr_id_t id;
    counter_index_t ctr_idx;
}

// Data associated with Buffering and BARs
struct bar_metadata_t {
    bool needs_buffering;
    bar_id_t bar_id;
    bit<32> ddn_delay_ms; // unused so far
    bit<32> suggest_pkt_count; // unused so far
}

struct ddn_digest_t {
    bit<32> ue_addr;
}


// Data associated with a FAR entry. Loaded by a FAR (except ID which is loaded by a PDR)
struct far_metadata_t {
    far_id_t    id;

    // Buffering, dropping, tunneling etc. are not mutually exclusive.
    // Hence, they should be flags and not different action types.
    bool needs_dropping;
    bool needs_tunneling;
    bool notify_cp;

    TunnelType  tunnel_out_type;
    ipv4_addr_t tunnel_out_src_ipv4_addr;
    ipv4_addr_t tunnel_out_dst_ipv4_addr;
    L4Port      tunnel_out_udp_sport;
    teid_t      tunnel_out_teid;

    ipv4_addr_t next_hop_ip;
}

// The primary metadata structure.
struct local_metadata_t {
    Direction direction;

    // SEID and F-TEID currently have no use in fast path
    teid_t teid;    // local Tunnel ID.  F-TEID = TEID + GTP endpoint address
    // seid_t seid; // local Session ID. F-SEID = SEID + GTP endpoint address

    // fteid_t fteid;
    fseid_t fseid;

    ipv4_addr_t next_hop_ip;

    bool needs_gtpu_decap;
    bool needs_udp_decap; // unused
    bool needs_vlan_removal; // unused

    InterfaceType src_iface;
    InterfaceType dst_iface; // unused

    ipv4_addr_t ue_addr;
    ipv4_addr_t inet_addr;
    L4Port      ue_l4_port;
    L4Port      inet_l4_port;

    L4Port      l4_sport;
    L4Port      l4_dport;

    net_instance_t net_instance;

    pdr_metadata_t pdr;
    far_metadata_t far;
    bar_metadata_t bar;
}


#endif
