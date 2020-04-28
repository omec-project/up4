/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

#ifdef DISAGG_UPF 
header buffer_tunnel_t {
    IpProtocol      next_header;
    bit<32>         device_id; // id of the source P4 switch
    bar_id_t        bar_id; // Buffering Action Rule ID
    // This header should contain all metadata that is needed
    // to be accurately re-processed by the P4 UPF
    port_num_t      original_ingress_port; // may not be needed
    bit<7> _pad;
}

header qos_tunnel_t {
    IpProtocol      next_header;
    bit<32>         device_id; // id of the source P4 switch
    qer_id_t        qer_id; // Quality Enforcement Rule ID
    // This header should contain all metadata that is needed
    // by the egress i.e. all bridged metadata
    counter_index_t ctr_idx;
    mac_addr_t original_dst_mac;
    port_num_t original_egress_spec;
    bit<7> _pad;
}
#endif // DISAGG_UPF

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



struct parsed_headers_t {
    ethernet_t ethernet;
    ipv4_t outer_ipv4;
    udp_t outer_udp;
    gtpu_t gtpu;
    ipv4_t ipv4;
#ifdef DISAGG_UPF
    buffer_tunnel_t buffer_tunnel;
    qos_tunnel_t qos_tunnel;
#endif // DISAGG_UPF
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

// Data assocaited with a BAR entry
struct bar_metadata_t {
    bar_id_t id;
    bool needs_buffering;
}


// Data associated with a FAR entry. Loaded by a FAR (except ID which is loaded by a PDR)
struct far_metadata_t {
    far_id_t    id;
    ActionType  action_type;

    TunnelType  tunnel_out_type;
    ipv4_addr_t tunnel_out_src_ipv4_addr;
    ipv4_addr_t tunnel_out_dst_ipv4_addr;
    L4Port      tunnel_out_udp_dport;
    teid_t      tunnel_out_teid;

    ipv4_addr_t next_hop_ip;
}

// QoS related metadata
struct qos_metadata_t {
    qer_id_t qer_id;
    qfi_t    qfi;
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
    bool needs_udp_decap;
    bool needs_vlan_removal;

    InterfaceType src_iface;
    InterfaceType dst_iface;

    ipv4_addr_t ue_addr;
    ipv4_addr_t inet_addr;
    L4Port      ue_l4_port;
    L4Port      inet_l4_port;

    L4Port      l4_sport;
    L4Port      l4_dport;

    net_instance_t net_instance;

    far_metadata_t far;
    qos_metadata_t qos;
    pdr_metadata_t pdr;
    bar_metadata_t bar;
}


#endif
