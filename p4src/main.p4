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


// TODO: PDR-ID and FAR-ID are not globally unique. need to combine them with F-SEID

#include <core.p4>
#include <v1model.p4>

// CPU_PORT specifies the P4 port number associated to controller packet-in and
// packet-out.
#define CPU_PORT 255

// CPU_CLONE_SESSION_ID specifies the mirroring session for packets to be cloned
// to the CPU port. For cloning to work, the P4Runtime client needs first to
// insert a CloneSessionEntry that maps this session ID to the CPU_PORT.
#define CPU_CLONE_SESSION_ID 99

#define MAX_PDRS 1024


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


const bit<16> UDP_PORT_GTPU = 2152;
const bit<3> GTPU_VERSION = 0x1;
const bit<1> GTP_PROTOCOL_TYPE_GTP = 0x1;
const bit<8> GTP_MESSAGE_TYPE_UPDU = 0xff;


typedef bit<32> pdr_id_t;
typedef bit<32> far_id_t;
typedef bit<32> urr_id_t;
typedef bit<32> qer_id_t;
typedef bit<32> qfi_t;
typedef bit<32> net_instance_t;
typedef bit<32> counter_index_t;


typedef bit<32> teid_t;
typedef bit<64> seid_t;
// F-TEID = (4-byte)TEID + GTP endpoint (gnodeb OR UPF) address
typedef bit<64> fteid_t;
// F-SEID = 8-byte SEID + UPF IP(v4/v6) address
typedef bit<96> fseid_t;
// TODO: Do we really need fteid and fseid widths to be the sum of the 
//       two field widths? They're huge


const pdr_id_t DEFAULT_PDR_ID = 0;
const far_id_t DEFAULT_FAR_ID = 0;
const urr_id_t DEFAULT_URR_ID = 0;
const qer_id_t DEFAULT_QER_ID = 0;
const qfi_t    DEFAULT_QFI    = 0;

//------------------------------------------------------------------------------
// ENUMS
// These should be exposed to the control plane by P4Runtime.
//------------------------------------------------------------------------------
enum bit<8> GTPUMessageType {
    GPDU = 255
}

enum bit<16> EtherType {
    IPV4 = 0x0800,
    IPV6 = 0x86dd
}

enum bit<8> IpProtocol {
    ICMP    = 1,
    TCP     = 6,
    UDP     = 17
}

enum bit<8> Direction {
    UNKNOWN             = 0x0,
    UPLINK              = 0x1,
    DOWNLINK            = 0x2,
    OTHER               = 0x3
};

enum bit<8> InterfaceType {
    UNKNOWN     = 0x0,
    ACCESS      = 0x1,
    CORE        = 0x2,
    N6_LAN      = 0x3,
    VN_INTERNAL = 0x4
}

enum bit<8> ActionType {
    NONE        = 0x0,
    FORWARD     = 0x1,
    BUFFER      = 0x2,
    TUNNEL      = 0x3,
    DROP        = 0x4
}


enum bit<8> TunnelType {
    UNKNOWN = 0x0,
    IP      = 0x1,
    UDP     = 0x2,
    GTPU    = 0x3
}


//------------------------------------------------------------------------------
// HEADER DEFINITIONS
//------------------------------------------------------------------------------

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

header udp_t {
    bit<16> sport;
    bit<16> dport;
    bit<16> len;
    bit<16> checksum;
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
    udp_t udp;
    ipv4_t inner_ipv4;
    udp_t inner_udp;
}


// Data associated with a PDR entry
struct pdr_metadata_t {
    pdr_id_t id;
    counter_index_t ctr_idx;
}


// Data associated with a FAR entry. Loaded by a FAR (except ID which is loaded by a PDR)
struct far_metadata_t {
    far_id_t id;
    ActionType action_type;

    TunnelType tunnel_out_type;
    ipv4_addr_t tunnel_out_src_ipv4_addr;
    ipv4_addr_t tunnel_out_dst_ipv4_addr;
    teid_t tunnel_out_teid;

    mac_addr_t dst_mac_addr;
    port_num_t egress_spec;
    InterfaceType dst_iface_type;
}

// QoS related metadata
struct qos_metadata_t {
    qer_id_t qer_id;
    qfi_t    qfi;
}

// The primary metadata structure.
struct local_metadata_t {
    Direction direction;
    
    teid_t teid; // local Tunnel ID.  F-TEID = TEID + GTP endpoint address
    seid_t seid; // local Session ID. F-SEID = SEID + GTP endpoint address

    fteid_t fteid; //why teid & fteid both fields ?
    fseid_t fseid;

    bool needs_gtpu_decap;
    bool needs_udp_decap;
    bool needs_vlan_removal;

    InterfaceType src_iface_type;
    InterfaceType dst_iface_type;

    ipv4_addr_t ue_addr;
    ipv4_addr_t inet_addr;
    l4_port_t ue_l4_port;
    l4_port_t inet_l4_port;

    l4_port_t   l4_sport;
    l4_port_t   l4_dport;

    net_instance_t net_instance;

    far_metadata_t far;
    qos_metadata_t qos;
    pdr_metadata_t pdr;
}


//------------------------------------------------------------------------------
// PARSER
//------------------------------------------------------------------------------
parser ParserImpl (packet_in packet,
                   out parsed_headers_t hdr,
                   inout local_metadata_t local_meta,
                   inout standard_metadata_t std_meta)
{
    state start {
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
            IpProtocol.UDP: parse_udp;
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
            IpProtocol.UDP: parse_inner_udp; //inner packet can be anything.tcp/udp/icmp/....
            default: accept;
        }
    }

    state parse_inner_udp {
        packet.extract(hdr.inner_udp);
        local_meta.l4_sport = hdr.inner_udp.sport;
        local_meta.l4_dport = hdr.inner_udp.dport;
        transition accept;
    }
}


//------------------------------------------------------------------------------
// PRE-INGRESS CHECKSUM VERIFICATION
//------------------------------------------------------------------------------
control VerifyChecksumImpl(inout parsed_headers_t hdr,
                           inout local_metadata_t meta)
{
    // TODO: verify checksum. For now we assume all packets have valid checksum,
    //  if not, we let the end hosts detect errors.
    apply { /* EMPTY */ }
}


//------------------------------------------------------------------------------
// FAR EXECUTION CONTROL BLOCK
//------------------------------------------------------------------------------
control execute_far (inout parsed_headers_t    hdr,
                     inout local_metadata_t    local_meta,
                     inout standard_metadata_t std_meta) {

    @hidden
    action gtpu_encap(ipv4_addr_t src_addr, ipv4_addr_t dst_addr, teid_t teid) {
        hdr.outer_ipv4.setValid();
        hdr.outer_ipv4.version = IP_VERSION_4;
        hdr.outer_ipv4.ihl = IPV4_MIN_IHL;
        hdr.outer_ipv4.dscp = 0;
        hdr.outer_ipv4.ecn = 0;
        hdr.outer_ipv4.total_len = hdr.ipv4.total_len
                + (IPV4_HDR_SIZE + UDP_HDR_SIZE + GTP_HDR_SIZE);
        hdr.outer_ipv4.identification = 0x1513; /* From NGIC */ //need correction
        hdr.outer_ipv4.flags = 0;
        hdr.outer_ipv4.frag_offset = 0;
        hdr.outer_ipv4.ttl = DEFAULT_IPV4_TTL;
        hdr.outer_ipv4.proto = IpProtocol.UDP;
        hdr.outer_ipv4.src_addr = src_addr;
        hdr.outer_ipv4.dst_addr = dst_addr;
        hdr.outer_ipv4.checksum = 0; // Updated later

        hdr.outer_udp.setValid();
        hdr.outer_udp.sport = UDP_PORT_GTPU;
        hdr.outer_udp.dport = UDP_PORT_GTPU; //should come from FAR
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
        hdr.gtpu.msgtype = GTPUMessageType.GPDU;
        hdr.gtpu.msglen = hdr.ipv4.total_len; //this will include udp header as well... 
        hdr.gtpu.teid = teid; 
    }

    @hidden
    action forward(mac_addr_t dst_addr, port_num_t egress_spec) {
        std_meta.egress_spec = egress_spec;
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
        hdr.ethernet.dst_addr = dst_addr;
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
    }
    

    action do_forward() {
        forward(local_meta.far.dst_mac_addr, local_meta.far.egress_spec);
    }

    action do_gtpu_tunnel() {
        gtpu_encap(local_meta.far.tunnel_out_src_ipv4_addr, 
                   local_meta.far.tunnel_out_dst_ipv4_addr,
                   local_meta.far.tunnel_out_teid);
        forward(local_meta.far.dst_mac_addr, local_meta.far.egress_spec);
    }

    action do_buffer() {
        // add_buffer_info_header();
        // forward(hdr.ethernet.src_addr, BUFFER_DEVICE_PORT);
    }

    action do_drop() {
        mark_to_drop(std_meta);
    }

    apply {
        if      (local_meta.far.action_type == ActionType.FORWARD) {
            do_forward();
        }
        else if (local_meta.far.action_type == ActionType.BUFFER) {
            do_buffer();
        }
        else if (local_meta.far.action_type == ActionType.TUNNEL &&
                 local_meta.far.tunnel_out_type == TunnelType.GTPU) {
            do_gtpu_tunnel();
        }
        else if (local_meta.far.action_type == ActionType.DROP) {
            do_drop();
        }
    }
}


//------------------------------------------------------------------------------
// INGRESS PIPELINE
//------------------------------------------------------------------------------
control IngressPipeImpl (inout parsed_headers_t    hdr,
                         inout local_metadata_t    local_meta,
                         inout standard_metadata_t std_meta) {


    counter(MAX_PDRS, CounterType.packets_and_bytes) pre_qos_pdr_counter;

    /* used by many tables */
    action drop() {
        mark_to_drop(std_meta);
    }


    // TODO: change type to ID / Number ??
    action set_source_iface_type(InterfaceType src_iface_type) {
        local_meta.src_iface_type = src_iface_type;
    }
    table source_iface_lookup {
        key = {
            std_meta.ingress_port : exact;
            // in practice, will also check vlan ID and destination IP
            // may be this physical port needs to be mapped to logic group - access/core/smf/SGi-LAN/N6-LAN ?
        }
        actions = {
            set_source_iface_type; //what if no match ? looks to be config mismatch. We need a port->{access,core,smf) mapping 
        }
        const default_action = set_source_iface_type(InterfaceType.UNKNOWN);
    }


    @hidden
    action gtpu_decap() {
        hdr.gtpu.setInvalid();
        hdr.outer_ipv4.setInvalid();
        hdr.outer_udp.setInvalid();
    }



    action set_pdr_attributes(pdr_id_t id, 
                             far_id_t far_id, 
                             qer_id_t qer_id,
                             qfi_t qfi,
                             bit<1> needs_gtpu_decap,
                             bit<1> needs_udp_decap,
                             bit<1> needs_vlan_removal,
                             net_instance_t net_instance,
                             counter_index_t ctr_id
                             // TODO: add more attributes to load. 
                             )
    {
        local_meta.pdr.id       = id;
        local_meta.pdr.ctr_idx  = ctr_id;
        local_meta.far.id       = far_id;
        local_meta.qos.qer_id   = qer_id;
        local_meta.qos.qfi      = qfi;
        local_meta.net_instance = net_instance;
        local_meta.needs_gtpu_decap     = (bool)needs_gtpu_decap;
        local_meta.needs_udp_decap      = (bool)needs_udp_decap;
        local_meta.needs_vlan_removal   = (bool)needs_vlan_removal;
    }

    // Contains PDRs for both the Uplink and Downlink Direction
    table pdrs {
        key = {
            local_meta.fseid            : exact     @name("fseid");
            local_meta.src_iface_type   : exact     @name("src_iface_type"); // To differentiate uplink and downlink
            // 5-Tuple
            local_meta.ue_addr          : ternary   @name("ue_addr"); 
            local_meta.inet_addr        : ternary   @name("inet_addr");
            local_meta.ue_l4_port       : range     @name("ue_l4_port");
            local_meta.inet_l4_port     : range     @name("inet_l4_port");
            hdr.ipv4.proto              : ternary   @name("ip_proto");
            // why no teid is used for the match..required for 4G case?
            // add ToS, SPI
            // The 5-tuple fields *should* be optional, but optional is not currently supported by targets
        }
        actions = {
            set_pdr_attributes; 
            @defaultonly NoAction;
        }
    }

    action set_far_attributes_forward(port_num_t egress_spec,
                                      mac_addr_t dst_mac) {
        local_meta.far.action_type = ActionType.FORWARD;
        local_meta.far.egress_spec = egress_spec;
        local_meta.far.dst_mac_addr = dst_mac;
    }
    action set_far_attributes_buffer() {
        local_meta.far.action_type = ActionType.BUFFER;
    }
    action set_far_attributes_tunnel(TunnelType tunnel_type,
                                ipv4_addr_t src_addr, ipv4_addr_t dst_addr,
                                teid_t teid, port_num_t egress_spec,
                                mac_addr_t dst_mac) {
        local_meta.far.action_type              = ActionType.TUNNEL; //only if outer header creation is present else action is forward..Take the action from FAR
        local_meta.far.tunnel_out_type          = tunnel_type;
        local_meta.far.egress_spec              = egress_spec;
        local_meta.far.tunnel_out_teid          = teid;
        local_meta.far.tunnel_out_src_ipv4_addr = src_addr;
        local_meta.far.tunnel_out_dst_ipv4_addr = dst_addr;
        local_meta.far.dst_mac_addr = dst_mac; // is this implementation specific..Spec does not talk about this. 
        // gtpu port needs to be added. Its part of FAR 
    } 
    action set_far_attributes_drop() {
        local_meta.far.action_type = ActionType.DROP;
    }
    table fars {
        key = {
            local_meta.far.id : exact      @name("far_id");
            local_meta.fseid  : exact      @name("session_id");
        }
        actions = {
            set_far_attributes_forward;
            set_far_attributes_buffer;
            set_far_attributes_tunnel;
            set_far_attributes_drop;
        }
    }



    action set_fseid(fseid_t fseid) {
        local_meta.fseid = fseid;
    }
    table fseid_lookup {
        key = {
            local_meta.ue_addr : exact;
            // TODO: what is the other part of the lookup?
            // f-seid is : 8 byte number (SEID), + IPV4 or IPV6 or Both address present. 
        }
        actions = {
            set_fseid;
            //if no fseid found then drop/reject message.
            //if uplink or downlink N9 interface : reject 
            //if dowlink N6 interface - slow path transition
        }
    }


    //----------------------------------------
    // INGRESS APPLY BLOCK
    //----------------------------------------
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
        /*
        if (std_meta.ingress_port != FROM_BUFFERING_DEVICE) {
            bypass_ingress();
        }
        */

        // downlink lookup = UE address lookup

        source_iface_lookup.apply();

        // we directly get destination interface in the FAR...Refer 7.5.2.3-2 table
        // i see you are using Rx interface type to get direction ...this all just to normalize ??
        if (local_meta.src_iface_type == InterfaceType.ACCESS) {
            local_meta.direction = Direction.UPLINK; 
        }
        else if (local_meta.src_iface_type == InterfaceType.CORE) {
            local_meta.direction = Direction.DOWNLINK;
        }
        else {
            local_meta.direction = Direction.UNKNOWN;
        }

        // 5tuple_normalize
        if (local_meta.direction == Direction.UPLINK) {
            local_meta.ue_addr = hdr.ipv4.src_addr;
            local_meta.inet_addr = hdr.ipv4.dst_addr;
            local_meta.ue_l4_port = local_meta.l4_sport;
            local_meta.inet_l4_port = local_meta.l4_dport;
        }
        else if (local_meta.direction == Direction.DOWNLINK) {
            local_meta.ue_addr = hdr.ipv4.dst_addr;
            local_meta.inet_addr = hdr.ipv4.src_addr;
            local_meta.ue_l4_port = local_meta.l4_dport;
            local_meta.inet_l4_port = local_meta.l4_sport;
        }

        fseid_lookup.apply(); // looks like normalize is done to make sure we have ue_addr set from outer header or internal header  
        pdrs.apply(); // i guess we look for pdrs only if fseid is set in local_meta
        pre_qos_pdr_counter.count(local_meta.pdr.ctr_idx);

        if (local_meta.needs_gtpu_decap) {
            gtpu_decap();
        }

        fars.apply();
        execute_far.apply(hdr, local_meta, std_meta);

    }
}


//------------------------------------------------------------------------------
// EGRESS PIPELINE
//------------------------------------------------------------------------------
control EgressPipeImpl (inout parsed_headers_t hdr,
                        inout local_metadata_t local_meta,
                        inout standard_metadata_t std_meta) {


    counter(MAX_PDRS, CounterType.packets_and_bytes) post_qos_pdr_counter;

    apply {

        post_qos_pdr_counter.count(local_meta.pdr.ctr_idx);

    }
}

//------------------------------------------------------------------------------
// CHECKSUM COMPUTATION
//------------------------------------------------------------------------------
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


//------------------------------------------------------------------------------
// DEPARSER
//------------------------------------------------------------------------------
control DeparserImpl(packet_out packet, in parsed_headers_t hdr) {
    apply {
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
