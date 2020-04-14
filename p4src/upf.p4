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

const bit<16> UDP_PORT_GTPU = 2152;
const bit<8> GTP_GPDU = 0xff;
const bit<3> GTPU_VERSION = 0x1;
const bit<1> GTP_PROTOCOL_TYPE_GTP = 0x1;
// const bit<8> GTP_MESSAGE_TYPE_something 0x00;


typedef bit<1> bool_param_t;
typedef bit<32> teid_t;
typedef bit<8>  iface_type_t;
typedef bit<2>  direction_t;
typedef bit<8>  far_action_t;
typedef bit<32> pdr_id_t;
typedef bit<32> far_id_t;
typedef bit<32> urr_id_t;
typedef bit<32> qer_id_t;
typedef bit<32> qfi_id_t;
typedef bit<32> session_id_t;
typedef bit<32> net_instance_t;

const pdr_id_t DEFAULT_PDR_ID = 0;
const far_id_t DEFAULT_FAR_ID = 0;
const urr_id_t DEFAULT_URR_ID = 0;
const qer_id_t DEFAULT_QER_ID = 0;
const qfi_id_t DEFAULT_QFI_ID = 0;

const direction_t UPF_DIR_UNKNOWN = 2w0;
const direction_t UPF_DIR_UPLINK = 2w1;
const direction_t UPF_DIR_DOWNLINK = 2w2;

const iface_type_t IFACE_TYPE_UNKNOWN = 0x0;
const iface_type_t IFACE_TYPE_ACCESS  = 0x1;
const iface_type_t IFACE_TYPE_CORE    = 0x2;

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
    bit<16>  total_len;
    bit<16>  identification;
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
    direction_t direction;
    ipv4_addr_t ue_addr;
    ipv4_addr_t inet_addr;
    // each subscriber can have multiple TEIDs
    // we need a counter per TEID+direction
    teid_t teid;
    iface_type_t src_iface_type;
    iface_type_t dst_iface_type;

    pdr_id_t pdr_id; // needs counter for pdr_id+direction
    urr_id_t urr_id;
    qer_id_t qer_id;
    qfi_id_t qfi_id;
    far_id_t far_id;
    far_action_t far_action;

    l4_port_t ue_l4_port;
    l4_port_t inet_l4_port;

    l4_port_t   l4_sport;
    l4_port_t   l4_dport;

    bool needs_gtpu_encap;
    bool needs_gtpu_decap;
    ipv4_addr_t src_encap_addr;
    ipv4_addr_t dst_encap_addr;

    net_instance_t net_instance;
    session_id_t n4_sess_id;
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
        // note: this eventually wont work
        local_meta.l4_sport = hdr.udp.sport;
        local_meta.l4_dport = hdr.udp.dport;
        transition select(hdr.udp.dport) {
            UDP_PORT_GTPU: parse_gtpu;
            default: accept;
        }
    }

    state parse_gtpu {
        packet.extract(hdr.gtpu);
        local_meta.teid = hdr.gtpu.teid;
        // eventually need to add conditional parsing, in the case of non-ip payloads
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
        local_meta.l4_sport = hdr.udp.sport;
        local_meta.l4_dport = hdr.udp.dport;
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


    action set_source_iface_type(iface_type_t src_iface_type) {
        local_meta.src_iface_type = src_iface_type;
    }
    table source_iface_lookup {
        key = {
            std_meta.ingress_port : exact;
            // in practice, will also check vlan ID and destination IP
        }
        actions = {
            set_source_iface_type;
        }
        const default_action = set_source_iface_type(IFACE_TYPE_UNKNOWN); 
    }


    @hidden
    action gtpu_decap() {
        hdr.gtpu.setInvalid();
        hdr.outer_ipv4.setInvalid();
        hdr.outer_udp.setInvalid();
    }

    @hidden
    action gtpu_encap(ipv4_addr_t src_addr, ipv4_addr_t dst_addr) {
        hdr.outer_ipv4.setValid();
        hdr.outer_ipv4.version = IP_VERSION_4;
        hdr.outer_ipv4.ihl = IPV4_MIN_IHL;
        hdr.outer_ipv4.dscp = 0;
        hdr.outer_ipv4.ecn = 0;
        hdr.outer_ipv4.total_len = hdr.ipv4.total_len
                + (IPV4_HDR_SIZE + UDP_HDR_SIZE + GTP_HDR_SIZE);
        hdr.outer_ipv4.identification = 0x1513; /* From NGIC */
        hdr.outer_ipv4.flags = 0;
        hdr.outer_ipv4.frag_offset = 0;
        hdr.outer_ipv4.ttl = DEFAULT_IPV4_TTL;
        hdr.outer_ipv4.proto = IP_PROTO_UDP;
        hdr.outer_ipv4.src_addr = src_addr;
        hdr.outer_ipv4.dst_addr = dst_addr;
        hdr.outer_ipv4.checksum = 0; // Updated later

        hdr.outer_udp.setValid();
        hdr.outer_udp.sport = UDP_PORT_GTPU;
        hdr.outer_udp.dport = UDP_PORT_GTPU;
        hdr.outer_udp.len = hdr.ipv4.total_len 
                + (UDP_HDR_SIZE + GTP_HDR_SIZE);
        hdr.outer_udp.checksum = 0; // Updated later

        hdr.gtpu.setValid();
        hdr.gtpu.version = GTPU_VERSION;
        hdr.gtpu.pt = GTP_PROTOCOL_TYPE_GTP;
        hdr.gtpu.spare = 0;
        hdr.gtpu.ex_flag = 0;
        hdr.gtpu.seq_flag = 0;
        hdr.gtpu.npdu_flag = 0;
        hdr.gtpu.msgtype = GTP_GPDU;
        hdr.gtpu.msglen = hdr.ipv4.total_len; 
        hdr.gtpu.teid = local_meta.teid;
    }


    action set_pdr_attributes(pdr_id_t id, 
                             bool_param_t needs_gtpu_encap, ipv4_addr_t src_encap_addr, ipv4_addr_t dst_encap_addr,
                             bool_param_t needs_gtpu_decap,
                             far_id_t far_id, 
                             urr_id_t urr_id, // urr_id_2, urr_id_3, ...
                             session_id_t n4_sess_id,
                             qer_id_t qer_id,
                             qfi_id_t qfi_id
                             // TODO: add more attributes to load. 
                             )
    {
        local_meta.pdr_id = id;
        local_meta.needs_gtpu_encap = (bool)needs_gtpu_encap;
        local_meta.src_encap_addr = src_encap_addr;
        local_meta.dst_encap_addr = dst_encap_addr;
        local_meta.needs_gtpu_decap = (bool)needs_gtpu_decap;
        local_meta.far_id = far_id;
        local_meta.urr_id = urr_id;
        local_meta.n4_sess_id = n4_sess_id;
        local_meta.qer_id = qer_id;
        local_meta.qfi_id = qfi_id;
    }

    // Contains PDRs for both the Uplink and Downlink Direction
    table pdrs {
        key = {
            // teid, sport, dport, proto should be optional instead of ternary
            // bmv2 supports multiple LPMs, tofino does not
            local_meta.src_iface_type: exact   @name("src_iface_type"); // To differentiate uplink and downlink
            local_meta.teid          : ternary @name("teid");
            local_meta.ue_addr       : ternary @name("ue_addr"); 
            local_meta.inet_addr     : ternary @name("inet_addr");
            local_meta.ue_l4_port    : range   @name("ue_l4_port");
            local_meta.inet_l4_port  : range   @name("inet_l4_port");
            hdr.ipv4.proto           : ternary @name("ip_proto");
            // add ToS, SPI
            // all these fields *should* be optional, but it is not currently supported by targets
        }
        actions = {
            // TODO: should encap and decap actions be moved to a second table that matches on the PDR ID?
            set_pdr_attributes; 
            @defaultonly NoAction;
        }
        @name("pdr_counter")
        counters = direct_counter(CounterType.packets_and_bytes);
    }


    action set_far_id(far_id_t id) {
        local_meta.far_id = id;
        // load other info here? if so, make this an action profile
    }


    action set_far_attributes(  far_action_t far_action, // forward, duplicate, drop, or buffer
                                iface_type_t dst_iface_type,   
                                bit<32> net_instance,
                                bool_param_t needs_gtpu_encap,
                                port_num_t outport
                                // TODO: outer header attributes
                                )
    {
        local_meta.far_action = far_action;
        local_meta.dst_iface_type = dst_iface_type;
        local_meta.net_instance = net_instance;
        local_meta.needs_gtpu_encap = (bool)needs_gtpu_encap;
        std_meta.egress_spec = outport;
    }
    table fars {
        key = {
            local_meta.far_id : exact;
        }
        actions = {
            set_far_attributes;
        }
    }


    

    action forward(mac_addr_t dst_addr, port_num_t outport) {
        std_meta.egress_spec = outport;
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;;
        hdr.ethernet.dst_addr = dst_addr;
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
    }

    action gtpu_encap_and_forward(ipv4_addr_t encap_src_addr, ipv4_addr_t encap_dst_addr,
                        mac_addr_t nexthop_mac, port_num_t outport) {
        gtpu_encap(encap_src_addr, encap_dst_addr);
        forward(nexthop_mac, outport); 
    }

    action buffer() {
        // do_buffer();
    }

    table execute_far {
        key = {
            local_meta.far_id : exact;
        }
        actions = {
            // other types of encap can happen (ex udp+ip)
            gtpu_encap_and_forward;
            forward;
            drop;
            buffer;
            NoAction;
        }
        @name("far_counter")
        counters = direct_counter(CounterType.packets_and_bytes);
        const default_action = NoAction();
        //const entries = {
        //    (DEFAULT_FAR_ID) : NoAction();
        //}
    }


    action set_next_hop(mac_addr_t dmac, port_num_t port) {
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
        hdr.ethernet.dst_addr = dmac;
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
        std_meta.egress_spec = port;
    }

    action send_to_cpu() {
        std_meta.egress_spec = CPU_PORT;
    }


    table rx_count {
        key = {
            local_meta.teid      : exact;
            local_meta.ue_addr   : exact;
            local_meta.src_iface_type : exact;
        }
        actions = {
            NoAction;
        }
        @name("rx_counter")
        counters = direct_counter(CounterType.packets_and_bytes);
    }



    apply {

        // gtpu_normalize
        if (hdr.gtpu.isValid()) {
            hdr.outer_ipv4 = hdr.ipv4;
            hdr.ipv4 = hdr.inner_ipv4;
            hdr.outer_udp = hdr.udp;
            if (hdr.inner_udp.isValid()) {
                hdr.udp = hdr.inner_udp;
            } else {
                hdr.udp.setInvalid();
            }
        }

        source_iface_lookup.apply();


        // map interface type to direction
        if (local_meta.src_iface_type == IFACE_TYPE_ACCESS) {
            local_meta.direction = UPF_DIR_UPLINK;
        }
        else if (local_meta.src_iface_type == IFACE_TYPE_CORE) {
            local_meta.direction = UPF_DIR_DOWNLINK;
        }
        else {
            local_meta.direction = UPF_DIR_UNKNOWN;
        }

        // 5tuple_normalize
        if (local_meta.direction == UPF_DIR_UPLINK) {
            local_meta.ue_addr = hdr.ipv4.src_addr;
            local_meta.inet_addr = hdr.ipv4.dst_addr;
            local_meta.ue_l4_port = local_meta.l4_sport;
            local_meta.inet_l4_port = local_meta.l4_dport;
        }
        else if (local_meta.direction == UPF_DIR_DOWNLINK) {
            local_meta.ue_addr = hdr.ipv4.dst_addr;
            local_meta.inet_addr = hdr.ipv4.src_addr;
            local_meta.ue_l4_port = local_meta.l4_dport;
            local_meta.inet_l4_port = local_meta.l4_sport;
        }

        // count received packets per UE
        // TODO: have fine-grained counters aggregated into URRs in ONOS
        rx_count.apply();

        pdrs.apply();

        if (local_meta.needs_gtpu_encap) {
            gtpu_encap(local_meta.src_encap_addr, local_meta.dst_encap_addr);
        }
        if (local_meta.needs_gtpu_decap) {
            gtpu_decap();
        }

        fars.apply();
        execute_far.apply();

    }
}


control EgressPipeImpl (inout parsed_headers_t hdr,
                        inout local_metadata_t local_meta,
                        inout standard_metadata_t std_meta) {
    
    
    table tx_count {
        key = {
            local_meta.teid : exact;
            local_meta.ue_addr : exact;
            local_meta.src_iface_type : exact;
        }
        actions = {
            NoAction;
        }
        @name("tx_counter")
        counters = direct_counter(CounterType.packets_and_bytes);
    }

    apply {

        tx_count.apply();

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
                hdr.ipv4.total_len,
                hdr.ipv4.identification,
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
