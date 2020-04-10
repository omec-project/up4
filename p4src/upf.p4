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


#include <core.p4>
#include <v1model.p4>

// CPU_PORT specifies the P4 port number associated to controller packet-in and
// packet-out.
#define CPU_PORT 255

// CPU_CLONE_SESSION_ID specifies the mirroring session for packets to be cloned
// to the CPU port. For cloning to work, the P4Runtime client needs first to
// insert a CloneSessionEntry that maps this session ID to the CPU_PORT.
#define CPU_CLONE_SESSION_ID 99


#define ETH_HDR_SIZE 14
#define IPV4_HDR_SIZE 20
#define UDP_HDR_SIZE 8
#define GTP_HDR_SIZE 8
#define IP_VERSION_4 4
const bit<4> IPV4_MIN_IHL = 5;
const bit<8> DEFAULT_IPV4_TTL = 64;

typedef bit<9>   port_num_t;
typedef bit<48>  mac_addr_t;
typedef bit<32>  ipv4_addr_t;
typedef bit<16>  l4_port_t;

const bit<16> ETHERTYPE_IPV4 = 0x0800;
const bit<16> ETHERTYPE_IPV6 = 0x86dd;

const bit<8> IP_PROTO_ICMP   = 1;
const bit<8> IP_PROTO_TCP    = 6;
const bit<8> IP_PROTO_UDP    = 17;

const bit<16> UDP_PORT_GTPU 2152;
const bit<8> GTP_GPDU 0xff;
const bit<3> GTPU_VERSION 0x1;
const bit<1> GTP_PROTOCOL_TYPE_GTP 0x1;
// const bit<8> GTP_MESSAGE_TYPE_something 0x00;


typedef bit<2> direction_t;
typedef bit<8> spgw_iface_type_t;
typedef bit<32> pdr_rule_id_t;
typedef bit<32> teid_t;

const direction_t SPGW_DIR_UNKNOWN = 2w0;
const direction_t SPGW_DIR_UPLINK = 2w1;
const direction_t SPGW_DIR_DOWNLINK = 2w2;

const bit<8> SPGW_IFACE_TYPE_UNKNOWN = 0x0;
const bit<8> SPGW_IFACE_TYPE_ACCESS  = 0x1;
const bit<8> SPGW_IFACE_TYPE_CORE    = 0x2;

//------------------------------------------------------------------------------
// HEADER DEFINITIONS
//------------------------------------------------------------------------------

header ethernet_t {
    mac_addr_t  dst_addr;
    mac_addr_t  src_addr;
    bit<16>     ether_type;
}

header ipv4_t {
    bit<4>   version;
    bit<4>   ihl;
    bit<6>   dscp;
    bit<2>   ecn;
    bit<16>  len;
    bit<16>  id;
    bit<3>   flags;
    bit<13>  frag_offset;
    bit<8>   ttl;
    bit<8>   proto;
    bit<16>  checksum;
    bit<32>  src_addr;
    bit<32>  dst_addr;
}

header udp_t {
    bit<16> sport;
    bit<16> dport;
    bit<16> len;
    bit<16> checksum;
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
    bit<32> teid;       /* tunnel endpoint id */
}


@controller_header("packet_in")
header cpu_in_header_t {
    port_num_t  ingress_port;
    bit<7>      _pad;
}

@controller_header("packet_out")
header cpu_out_header_t {
    port_num_t  egress_port;
    bit<7>      _pad;
}

struct parsed_headers_t {
    cpu_out_header_t cpu_out;
    cpu_in_header_t cpu_in;
    ethernet_t ethernet;
    ipv4_t outer_ipv4;
    udp_t outer_udp;
    gtpu_t gtpu;
    ipv4_t ipv4;
    udp_t udp;
    ipv4_t inner_ipv4;
    udp_t inner_udp;
}

struct local_metadata_t {
    ipv4_addr_t ue_addr;
    ipv4_addr_t inet_addr;

    l4_port_t ue_port;
    l4_port_t inet_port;

    l4_port_t   l4_src_port;
    l4_port_t   l4_dst_port;
}


//------------------------------------------------------------------------------
// INGRESS PIPELINE
//------------------------------------------------------------------------------

parser ParserImpl (packet_in packet,
                   out parsed_headers_t hdr,
                   inout local_metadata_t local_meta,
                   inout standard_metadata_t std_meta)
{
    state start {
        transition select(std_meta.ingress_port) {
            CPU_PORT: parse_packet_out;
            default: parse_ethernet;
        }
    }

    state parse_packet_out {
        packet.extract(hdr.cpu_out);
        transition parse_ethernet;
    }

    state parse_ethernet {
        packet.extract(hdr.ethernet);
        transition select(hdr.ethernet.ether_type){
            ETHERTYPE_IPV4: parse_ipv4;
            default: accept;
        }
    }

    state parse_ipv4 {
        packet.extract(hdr.ipv4);
        transition select(hdr.ipv4.proto) {
            IP_PROTO_UDP: parse_udp;
            default: accept;
        }
    }
    
    state parse_udp {
        packet.extract(hdr.udp);
        transition select(hdr.udp.dport) {
            UDP_PORT_GTPU: parse_gtpu;
            default: accept;
        }
    }

    state parse_gtpu {
        packet.extract(hdr.gtpu)
        transition parse_inner_ipv4;
    }

    state parse_inner_ipv4 {
        packet.extract(hdr.inner_ipv4);
        transition select(hdr.ipv4.proto) {
            IP_PROTO_UDP: parse_inner_udp;
            default: accept;
        }
    }

    state parse_inner_udp {
        packet.extract(hdr.inner_udp);
        transition accept;
    }
}


control VerifyChecksumImpl(inout parsed_headers_t hdr,
                           inout local_metadata_t meta)
{
    // TODO: verify checksum. For now we assume all packets have valid checksum,
    //  if not, we let the end hosts detect errors.
    apply { /* EMPTY */ }
}


control IngressPipeImpl (inout parsed_headers_t    hdr,
                         inout local_metadata_t    local_meta,
                         inout standard_metadata_t std_meta) {


    /* used by many tables */
    action drop() {
        mark_to_drop(std_meta);
    }


    action set_source_spgw_interface(spgw_iface_t src_iface, direction_t direction) {
        local_meta.src_iface = src_iface;
        local_meta.direction = direction;
    }
    table source_iface_lookup {
        key = {
            standard_metadata.ingress_port : exact;
        }
        actions = {
            set_source_spgw_interface;
        }
        const default_action = set_source_spgw_interface(SPGW_IFACE_TYPE_UNKNOWN);
    }


    action gtpu_decap() {
        gtp.setInvalid();
        outer_ipv4.setInvalid();
        outer_udp.setInvalid();
    }


    action set_pdr_rule_id(pdr_rule_id_t id) {
        local_meta.pdr_rule_id = id;
    }

    // teid+destip+srcip+srcport+dst+port to pdr id
    table pdr_rule_lookup {
        key = {
            local_meta.teid         : ternary;
            local_meta.ue_addr      : exact;
            local_meta.inet_addr    : exact;
            local_meta.ue_port      : exact;
            local_meta.inet_port    : exact;
            hdr.ipv4.protocol       : exact;
        }
        actions = {
            set_pdr_rule_id();
        }
        const default_action = set_pdr_rule_id(DEFAULT_PDR_RULE_ID);
    }


    action set_far_rule_id(far_rule_id_t id) {
        local_meta.far_rule_id = id;
        // load other info here? if so, make this an action profile
    }

    table far_rule_lookup {
        key = {
            local_meta.pdr_rule_id : exact; 
        }
        actions = {
            set_far_rule_id();
        }
        const default_action = set_far_rule_id(DEFAULT_FAR_RULE_ID);
    }

    
    @hidden
    action mark_to_gtpu_encap(ip_addr_t src_addr, ip_addr_t dst_addr) {
        local_meta.needs_gtpu_encap = true;
        local_meta.gtpu_encap_src_addr = src_addr;
        local_meta.gtpu_encap_dst_addr = dst_addr;
    }

    @hidden
    action forward(mac_addr_t dst_addr, port_num_t outport) {
        std_meta.egress_spec = outport;
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;;
        hdr.ethernet.dst_addr = dst_addr;
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
    }

    action decap_and_forward(mac_addr_t nexthop_mac, port_num_t outport) {
        gtpu_decap();
        forward(nexthop_mac, outport);
    }
    action encap_and_forward(ip_addr_t encap_src_addr, ip_addr_t encap_dst_addr,
                        mac_addr_t nexthop_mac, port_num_t outport) {
        mark_to_gtpu_encap(encap_src_addr, encap_dst_addr);
        forward(nexthop_mac, outport); 
    }
    action buffer() {
        // do_buffer();
    }

    table get_far_rule_info {
        key = {
            local_meta.far_rule_id : exact;
        }
        actions = {
            decap_and_forward;
            encap_and_forward;
            drop;
            buffer;
        }
        @name("far_rule_counter")
        counters = direct_counter(CounterType.packets_and_bytes);
        const default_action = set_far_rule_info(FORWARDING_ACTION_DROP);
    }


    table my_station {
        key = {
            hdr.ethernet.dst_addr: exact;
        }
        actions = { NoAction; }
    }

    action set_next_hop(mac_addr_t dmac, port_num_t port) {
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
        hdr.ethernet.dst_addr = dmac;
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
        std_meta.egress_spec = port;
    }

    table routing_v4 {
      key = {
          hdr.ipv4.dst_addr: lpm;
      }
      actions = {
          set_next_hop;
      }
      @name("routing_v4_counter")
      counters = direct_counter(CounterType.packets_and_bytes);
    }

    action send_to_cpu() {
        std_meta.egress_spec = CPU_PORT;
    }

    action clone_to_cpu() {
        // Cloning is achieved by using a v1model-specific primitive. Here we
        // set the type of clone operation (ingress-to-egress pipeline), the
        // clone session ID (the CPU one), and the metadata fields we want to
        // preserve for the cloned packet replica.
        clone3(CloneType.I2E, CPU_CLONE_SESSION_ID, { std_meta.ingress_port });
    }

    table punt {
        key = {
            std_meta.ingress_port:   ternary;
            hdr.ethernet.dst_addr:   ternary;
            hdr.ethernet.src_addr:   ternary;
            hdr.ethernet.ether_type: ternary;
        }
        actions = {
            send_to_cpu;
            clone_to_cpu;
        }
        @name("punt_counter")
        counters = direct_counter(CounterType.packets_and_bytes);
    }

    apply {

        if (hdr.cpu_out.isValid()) {
            std_meta.egress_spec = hdr.cpu_out.egress_port;
            hdr.cpu_out.setInvalid();
            exit;
        }

        // gtpu_normalize
        if (gtpu.isValid()) {
            outer_ipv4 = ipv4;
            ipv4 = inner_ipv4;
            outer_udp = udp;
            if (inner_udp.isValid()) {
                udp = inner_udp;
            } else {
                udp.setInvalid();
            }
        }

        // 5tuple_normalize
        if (local_meta.direction == SPGW_DIR_UPLINK) {
            local_meta.ue_addr = hdr.ipv4.src_addr;
            local_meta.inet_addr = hdr.ipv4.dst_addr;
            local_meta.ue_l4_port = local_meta.l4_sport;
            local_meta.net_l4_port = local_meta.l4_dport;
        }
        else if (local_meta.direction == SPGW_DIR_DOWNLINK) {
            local_meta.ue_addr = hdr.ipv4.dst_addr;
            local_meta.inet_addr = hdr.iv4.src_addr;
            local_meta.ue_l4_port = local_meta.l4_dport;
            local_meta.net_l4_port = local_meta.l4_sport;
        }

        pdr_rule_lookup.apply();
        far_rule_lookup.apply();
        

        // TODO: exit if action is punt
        punt.apply();

        if (my_station.apply().hit) {
            routing_v4.apply();
        }

        if(hdr.ipv4.ttl == 0) { drop(); }
    }
}


control EgressPipeImpl (inout parsed_headers_t hdr,
                        inout local_metadata_t local_meta,
                        inout standard_metadata_t std_meta) {
    @hidden
    action gtpu_encap() {
        outer_ipv4.setValid();
        outer_ipv4.version = IP_VERSION_4;
        outer_ipv4.ihl = IPV4_MIN_IHL;
        outer_ipv4.dscp = 0;
        outer_ipv4.ecn = 0;
        outer_ipv4.total_len = ipv4.total_len
                + (IPV4_HDR_SIZE + UDP_HDR_SIZE + GTP_HDR_SIZE);
        outer_ipv4.identification = 0x1513; /* From NGIC */
        outer_ipv4.flags = 0;
        outer_ipv4.frag_offset = 0;
        outer_ipv4.ttl = DEFAULT_IPV4_TTL;
        outer_ipv4.protocol = IP_PROTO_UDP;
        outer_ipv4.dst_addr = local_meta.gtpu_encap_dst_addr;
        outer_ipv4.src_addr = local_meta.gtpu_encap_src_addr;
        outer_ipv4.hdr_checksum = 0; // Updated later

        outer_udp.setValid();
        outer_udp.sport = UDP_PORT_GTPU;
        outer_udp.dport = UDP_PORT_GTPU;
        outer_udp.len = fabric_meta.spgw.ipv4_len
                + (UDP_HDR_SIZE + GTP_HDR_SIZE);
        outer_udp.checksum = 0; // Updated later

        gtpu.setValid();
        gtpu.version = GTPU_VERSION;
        gtpu.pt = GTP_PROTOCOL_TYPE_GTP;
        gtpu.spare = 0;
        gtpu.ex_flag = 0;
        gtpu.seq_flag = 0;
        gtpu.npdu_flag = 0;
        gtpu.msgtype = GTP_GPDU;
        gtpu.msglen = fabric_meta.spgw.ipv4_len;
        gtpu.teid = fabric_meta.spgw.teid;
    }

    apply {

        if (local_meta.needs_gtpu_encap) {
            gtpu_encap();
        }

        if (std_meta.egress_port == CPU_PORT) {
            hdr.cpu_in.setValid();
            hdr.cpu_in.ingress_port = std_meta.ingress_port;
            exit;
        }
    }
}


control ComputeChecksumImpl(inout parsed_headers_t hdr,
                            inout local_metadata_t local_meta)
{
    apply {
        update_checksum(hdr.ipv4.isValid(),{
                hdr.ipv4.version,
                hdr.ipv4.ihl,
                hdr.ipv4.dscp,
                hdr.ipv4.ecn,
                hdr.ipv4.len,
                hdr.ipv4.id,
                hdr.ipv4.flags,
                hdr.ipv4.frag_offset,
                hdr.ipv4.ttl,
                hdr.ipv4.proto,
                hdr.ipv4.src_addr,
                hdr.ipv4.dst_addr
            },
            hdr.ipv4.checksum,
            HashAlgorithm.csum16
        );
    }
}


control DeparserImpl(packet_out packet, in parsed_headers_t hdr) {
    apply {
        packet.emit(hdr.cpu_in);
        packet.emit(hdr.ethernet);
        packet.emit(hdr.outer_ipv4);
        packet.emit(hdr.outer_udp);
        packet.emit(hdr.gtpu);
        packet.emit(hdr.ipv4);
        packet.emit(hdr.udp);
    }
}


V1Switch(
    ParserImpl(),
    VerifyChecksumImpl(),
    IngressPipeImpl(),
    EgressPipeImpl(),
    ComputeChecksumImpl(),
    DeparserImpl()
) main;
