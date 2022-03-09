/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
#ifndef __HEADERS__
#define __HEADERS__

#include "define.p4"


header ethernet_t {
    mac_addr_t  dst_addr;
    mac_addr_t  src_addr;
    eth_type_t  ether_type;
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
    ip_proto_t      proto;
    bit<16>         checksum;
    ipv4_addr_t     src_addr;
    ipv4_addr_t     dst_addr;
}

header tcp_t {
    l4_port_t   sport;
    l4_port_t   dport;
    bit<32>     seq_no;
    bit<32>     ack_no;
    bit<4>      data_offset;
    bit<3>      res;
    bit<3>      ecn;
    bit<6>      ctrl;
    bit<16>     window;
    bit<16>     checksum;
    bit<16>     urgent_ptr;
}

header udp_t {
    l4_port_t   sport;
    l4_port_t   dport;
    bit<16>     len;
    bit<16>     checksum;
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
    bit<3>  version;    /* version */
    bit<1>  pt;         /* protocol type */
    bit<1>  spare;      /* reserved */
    bit<1>  ex_flag;    /* next extension hdr present? */
    bit<1>  seq_flag;   /* sequence no. */
    bit<1>  npdu_flag;  /* n-pdn number present ? */
    bit<8>  msgtype;    /* message type */
    bit<16> msglen;     /* message length */
    teid_t  teid;       /* tunnel endpoint id */
}

// Follows gtpu_t if any of ex_flag, seq_flag, or npdu_flag is 1.
header gtpu_options_t {
    bit<16> seq_num;   /* Sequence number */
    bit<8>  n_pdu_num; /* N-PDU number */
    bit<8>  next_ext;  /* Next extension header */
}

// GTPU extension: PDU Session Container (PSC) -- 3GPP TS 38.415 version 15.2.0
// https://www.etsi.org/deliver/etsi_ts/138400_138499/138415/15.02.00_60/ts_138415v150200p.pdf
header gtpu_ext_psc_t {
    bit<8> len;      /* Length in 4-octet units (common to all extensions) */
    bit<4> type;     /* Uplink or downlink */
    bit<4> spare0;   /* Reserved */
    bit<1> ppp;      /* Paging Policy Presence (UL only, not supported) */
    bit<1> rqi;      /* Reflective QoS Indicator (UL only) */
    bit<6> qfi;      /* QoS Flow Identifier */
    bit<8> next_ext;
}

@controller_header("packet_out")
header packet_out_t {
    bit<8> reserved; // Not used
}

@controller_header("packet_in")
header packet_in_t {
    PortId_t  ingress_port;
    bit<7>      _pad;
}


struct parsed_headers_t {
    packet_out_t  packet_out;
    packet_in_t   packet_in;
    ethernet_t ethernet;
    ipv4_t ipv4;
    udp_t udp;
    tcp_t tcp;
    icmp_t icmp;
    gtpu_t gtpu;
    gtpu_options_t gtpu_options;
    gtpu_ext_psc_t gtpu_ext_psc;
    ipv4_t inner_ipv4;
    udp_t inner_udp;
    tcp_t inner_tcp;
    icmp_t inner_icmp;
}

//------------------------------------------------------------------------------
// METADATA DEFINITIONS
//------------------------------------------------------------------------------

struct ddn_digest_t {
    ipv4_addr_t  ue_address;
}

// The primary metadata structure.
struct local_metadata_t {
    Direction direction;

    teid_t teid;

    slice_id_t slice_id;
    tc_t tc;

    ipv4_addr_t next_hop_ip;

    ipv4_addr_t ue_addr;
    ipv4_addr_t inet_addr;
    l4_port_t   ue_l4_port;
    l4_port_t   inet_l4_port;

    l4_port_t   l4_sport;
    l4_port_t   l4_dport;

    ip_proto_t  ip_proto;

    bit<8>  application_id;

    bit<8> src_iface;
    bool needs_gtpu_decap;
    bool needs_tunneling;
    bool needs_buffering;
    bool needs_dropping;
    bool terminations_hit;

    counter_index_t ctr_idx;

    tunnel_peer_id_t tunnel_peer_id;

    // GTP tunnel out parameters
    ipv4_addr_t tunnel_out_src_ipv4_addr;
    ipv4_addr_t tunnel_out_dst_ipv4_addr;
    l4_port_t   tunnel_out_udp_sport;
    teid_t      tunnel_out_teid;
    qfi_t       tunnel_out_qfi;

    session_meter_idx_t session_meter_idx_internal;
    app_meter_idx_t app_meter_idx_internal;
    MeterColor session_color;
    MeterColor app_color;
    MeterColor slice_tc_color;

    @field_list(0)
    PortId_t preserved_ingress_port;
}

#endif
