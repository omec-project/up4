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


//#define DISAGG_UPF


#include <core.p4>
#include <v1model.p4>

#include "include/define.p4"
#include "include/header.p4"
#include "include/parser.p4"
#include "include/checksum.p4"



//------------------------------------------------------------------------------
// ACL BLOCK
//------------------------------------------------------------------------------
control Acl(
    inout parsed_headers_t hdr,
    inout local_metadata_t local_meta,
    inout standard_metadata_t std_meta) {

    action set_port(port_num_t port) {
        std_meta.egress_spec = port;
    }

    action punt() {
        set_port(CPU_PORT);
    }

    // FIXME: what's the right way of cloning in v1model?
    // action clone_to_cpu() {
    //     clone3(CloneType.I2E, CPU_CLONE_SESSION_ID, { smeta.ingress_port });
    // }

    action drop() {
        mark_to_drop(std_meta);
        exit;
    }

    table acls {
        key = {
            std_meta.ingress_port       : ternary @name("inport");
            local_meta.src_iface        : ternary @name("src_iface");
            hdr.ethernet.src_addr       : ternary @name("eth_src");
            hdr.ethernet.dst_addr       : ternary @name("eth_dst");
            hdr.ethernet.ether_type     : ternary @name("eth_type");
            hdr.ipv4.src_addr           : ternary @name("ipv4_src");
            hdr.ipv4.dst_addr           : ternary @name("ipv4_dst");
            hdr.ipv4.proto              : ternary @name("ipv4_proto");
            local_meta.l4_sport         : ternary @name("l4_sport");
            local_meta.l4_dport         : ternary @name("l4_dport");
        }
        actions = {
            set_port;
            punt;
            // clone_to_cpu;
            drop;
            NoAction;
        }
        const default_action = NoAction;
        @name("acls")
        counters = direct_counter(CounterType.packets_and_bytes);
    }

    apply {
        acls.apply();
    }
}

//------------------------------------------------------------------------------
// ROUTING BLOCK
//------------------------------------------------------------------------------
control Routing(inout parsed_headers_t    hdr,
                inout local_metadata_t    local_meta,
                inout standard_metadata_t std_meta) {
    action drop() {
        mark_to_drop(std_meta);
    }

    action route(mac_addr_t dst_mac,
                 port_num_t egress_port) {
        std_meta.egress_spec = egress_port;
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
        hdr.ethernet.dst_addr = dst_mac;
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
    }


    table routes_v4 {
        key = {
            local_meta.next_hop_ip   : lpm @name("dst_prefix");
            hdr.ipv4.src_addr      : selector;
            hdr.ipv4.proto         : selector;
            local_meta.l4_sport    : selector;
            local_meta.l4_dport    : selector;
        }
        actions = {
            route;
            @defaultonly NoAction;
        }
        @name("hashed_selector")
        implementation = action_selector(HashAlgorithm.crc16, 32w1024, 32w16);
        const default_action = NoAction();
        size = MAX_ROUTES;
    }

    apply {
        // Normalize IP address for routing table
        // TODO: find a better alternative to this hack
        if (hdr.outer_ipv4.isValid()) {
            local_meta.next_hop_ip = hdr.outer_ipv4.dst_addr;
        } else if (hdr.ipv4.isValid()){
            local_meta.next_hop_ip = hdr.ipv4.dst_addr;
        }

        if (hdr.ipv4.ttl == 1) {
            drop();
        }
        else {
            routes_v4.apply();
        }
    }

}

        

//------------------------------------------------------------------------------
// FAR EXECUTION CONTROL BLOCK
//------------------------------------------------------------------------------
control ExecuteFar (inout parsed_headers_t    hdr,
                     inout local_metadata_t    local_meta,
                     inout standard_metadata_t std_meta) {

    @hidden
    action gtpu_encap(ipv4_addr_t src_addr, ipv4_addr_t dst_addr, 
                      L4Port     udp_dport, teid_t teid) {
        hdr.outer_ipv4.setValid();
        hdr.outer_ipv4.version = IP_VERSION_4;
        hdr.outer_ipv4.ihl = IPV4_MIN_IHL;
        hdr.outer_ipv4.dscp = 0;
        hdr.outer_ipv4.ecn = 0;
        hdr.outer_ipv4.total_len = hdr.ipv4.total_len
                + (IPV4_HDR_SIZE + UDP_HDR_SIZE + GTP_HDR_MIN_SIZE);
        hdr.outer_ipv4.identification = 0x1513; // TODO: change this to timestamp or some incremental num
        hdr.outer_ipv4.flags = 0;
        hdr.outer_ipv4.frag_offset = 0;
        hdr.outer_ipv4.ttl = DEFAULT_IPV4_TTL;
        hdr.outer_ipv4.proto = IpProtocol.UDP;
        hdr.outer_ipv4.src_addr = src_addr;
        hdr.outer_ipv4.dst_addr = dst_addr;
        hdr.outer_ipv4.checksum = 0; // Updated later

        hdr.outer_udp.setValid();
        hdr.outer_udp.sport = UDP_PORT_GTPU;
        hdr.outer_udp.dport = udp_dport;
        hdr.outer_udp.len = hdr.ipv4.total_len 
                + (UDP_HDR_SIZE + GTP_HDR_MIN_SIZE);
        hdr.outer_udp.checksum = 0; // Updated later

        hdr.gtpu.setValid();
        hdr.gtpu.version = GTPU_VERSION;
        hdr.gtpu.pt = GTP_PROTOCOL_TYPE_GTP;
        hdr.gtpu.spare = 0;
        hdr.gtpu.ex_flag = 0;
        hdr.gtpu.seq_flag = 0;
        hdr.gtpu.npdu_flag = 0;
        hdr.gtpu.msgtype = GTPUMessageType.GPDU;
        hdr.gtpu.msglen = hdr.ipv4.total_len; 
        hdr.gtpu.teid = teid; 
    }

    action do_gtpu_tunnel() {
        gtpu_encap(local_meta.far.tunnel_out_src_ipv4_addr, 
                   local_meta.far.tunnel_out_dst_ipv4_addr,
                   local_meta.far.tunnel_out_udp_dport,
                   local_meta.far.tunnel_out_teid);
    }
    

    action do_forward() {
        // Currently a no-op due to forwarding being logically separated
    }


    action do_buffer() {
        local_meta.bar.needs_buffering = true;
    }

    action do_drop() {
        mark_to_drop(std_meta);
        exit;
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
control PreQosPipe (inout parsed_headers_t    hdr,
                         inout local_metadata_t    local_meta,
                         inout standard_metadata_t std_meta) {


    counter(MAX_PDRS, CounterType.packets_and_bytes) pre_qos_pdr_counter;


    table my_station {
        key = {
            hdr.ethernet.dst_addr : exact @name("dst_mac");
        }
        actions = {
            NoAction;
        }
    }

    action set_source_iface(InterfaceType src_iface, Direction direction) {
        // Interface type can be access, core, n6_lan or vn_internal (see InterfaceType enum)
        local_meta.src_iface = src_iface;
        local_meta.direction = direction;
    }
    table source_iface_lookup {
        key = {
            hdr.ipv4.dst_addr : lpm @name("ipv4_dst_prefix");
            // hdr.ethernet.dst_addr  : exact; @name("dst_mac") // moved to my_station
            // Eventually should also check VLAN ID here
        }
        actions = {
            set_source_iface;
        }
        const default_action = set_source_iface(InterfaceType.UNKNOWN, Direction.UNKNOWN);
    }


    action set_fseid(fseid_t fseid) {
        local_meta.fseid = fseid;
    }
    table fseid_lookup {
        key = {
            local_meta.ue_addr : exact;
            // TODO: what is the other part of the lookup?
        }
        actions = {
            set_fseid;
        }
        const default_action = set_fseid(DEFAULT_FSEID);
    }


    @hidden
    action gtpu_decap() {
        hdr.gtpu.setInvalid();
        hdr.outer_ipv4.setInvalid();
        hdr.outer_udp.setInvalid();
    }

    action set_pdr_attributes(pdr_id_t          id, 
                              far_id_t          far_id, 
                              qer_id_t          qer_id,
                              qfi_t             qfi, // TODO: should this come from a gtpu extension?
                              bit<1>            needs_gtpu_decap,
                              bit<1>            needs_udp_decap,
                              bit<1>            needs_vlan_removal,
                              net_instance_t    net_instance,
                              counter_index_t   ctr_id
                             // TODO: add more attributes to load. 
                             )
    {
        local_meta.pdr.id       = id;
        local_meta.pdr.ctr_idx  = ctr_id;
        local_meta.far.id       = far_id;
        local_meta.qos.qer_id   = qer_id;
        local_meta.qos.qfi      = qfi;
        local_meta.net_instance = net_instance; //TODO: where is this used in the datapath?
        local_meta.needs_gtpu_decap     = (bool)needs_gtpu_decap;
        local_meta.needs_udp_decap      = (bool)needs_udp_decap;
        local_meta.needs_vlan_removal   = (bool)needs_vlan_removal;
    }

    // Contains PDRs for both the Uplink and Downlink Direction
    table pdrs {
        key = {
            local_meta.fseid            : exact     @name("fseid");
            local_meta.src_iface        : exact     @name("src_iface"); // To differentiate uplink and downlink
            local_meta.teid             : ternary   @name("teid");
            // 5-Tuple
            local_meta.ue_addr          : ternary   @name("ue_addr"); 
            local_meta.inet_addr        : ternary   @name("inet_addr");
            local_meta.ue_l4_port       : range     @name("ue_l4_port");
            local_meta.inet_l4_port     : range     @name("inet_l4_port");
            hdr.ipv4.proto              : ternary   @name("ip_proto");
            // If match keys from other protocols are needed, we must add parsing for those protocol headers
            // add ToS, SPI
            // The 5-tuple fields *should* be optional, but optional is not currently supported by targets
        }
        actions = {
            set_pdr_attributes; 
            @defaultonly NoAction;
        }
    }


    // TODO: These actions should set local_meta.dst_iface, but would any tables use it?
    //       Isn't a destination port sufficient information for the fast path?
    action set_far_attributes_forward() {
        local_meta.far.action_type = ActionType.FORWARD;
    }
    action set_far_attributes_buffer(bar_id_t bar_id) {
        local_meta.far.action_type = ActionType.BUFFER;
        local_meta.bar.id          = bar_id;
    }
    action set_far_attributes_tunnel(TunnelType     tunnel_type,
                                     ipv4_addr_t    src_addr,
                                     ipv4_addr_t    dst_addr, 
                                     teid_t         teid, 
                                     L4Port         dport) {
        local_meta.far.action_type              = ActionType.TUNNEL;
        local_meta.far.tunnel_out_type          = tunnel_type;
        local_meta.far.tunnel_out_src_ipv4_addr = src_addr;
        local_meta.far.tunnel_out_dst_ipv4_addr = dst_addr;
        local_meta.far.tunnel_out_teid          = teid;
        local_meta.far.tunnel_out_udp_dport     = dport;       
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




    //----------------------------------------
    // INGRESS APPLY BLOCK
    //----------------------------------------
    apply {

        // Interfaces we care about:
        // N3 (from base station) - GTPU - match on outer IP dst
        // N6 (from internet) - no GTPU - match on IP header dst
        // N9 (from another UPF) - GTPU - match on outer IP dst
        // N4-u (from SMF) - ?
        if (my_station.apply().hit) {
            // Only look up an interface if the packet is destined 
            // for our MAC addr
            if (!source_iface_lookup.apply().hit) { return; }
        } else {
            // If packet wasn't destined for our MAC addr or one of 
            // our interfaces, then this pipeline does not apply.
            return;
        }
        // Interface lookup happens before normalization of headers,
        // because the lookup uses the outermost IP header in all cases

        // Normalize the headers so that the UE's IPv4 header is always hdr.ipv4
        // regardless of if there is encapsulation or not.
        if (hdr.gtpu.isValid()) {
            hdr.outer_ipv4 = hdr.ipv4;
            hdr.ipv4 = hdr.inner_ipv4;
            hdr.outer_udp = hdr.udp;
            if (hdr.inner_udp.isValid()) {
                hdr.udp = hdr.inner_udp;
                hdr.inner_udp.setInvalid();
            } 
            else {
                hdr.udp.setInvalid();
                if (hdr.inner_tcp.isValid()) {
                    hdr.tcp = hdr.inner_tcp;
                    hdr.inner_tcp.setInvalid();
                }
                else if (hdr.inner_icmp.isValid()) {
                    hdr.icmp = hdr.inner_icmp;
                    hdr.inner_icmp.setInvalid();
                }
            }
        }
        /*
        if (std_meta.ingress_port != FROM_BUFFERING_DEVICE) {
            bypass_ingress();
        }
        */

        // TODO: Check the destination mac address. If isn't ours, we will need to act as a L2 switch
        // Only do source iface lookup if the mac is ours.
        // The L2 switching functionality will be beyond the scope of this program (part of fabric.p4 instead)

        // Normalize so the UE address/port appear as the same field regardless of direction
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


        // Look up F-SEID, which is needed as a match key by the PDR table
        fseid_lookup.apply();
        // Find a matching PDR and load the relevant attributes.
        pdrs.apply();
        // Count packets at a counter index unique to whichever PDR matched.
        pre_qos_pdr_counter.count(local_meta.pdr.ctr_idx);

        // Perform whatever header removal the matching PDR required.
        if (local_meta.needs_gtpu_decap) {
            gtpu_decap();
        }
        /*
        else if (local_meta.needs_udp_decap) {
            udp_decap();
        }
        else if (local_meta.neds_vlan_removal) {
            vlan_untag();
        }
        */

        // Look up FAR info using the FAR-ID loaded by the PDR table.
        fars.apply();
        // Execute the loaded FAR
        ExecuteFar.apply(hdr, local_meta, std_meta);

        // FAR only set the destination IP. 
        // Now we need to choose a destination MAC egress port.
        Routing.apply(hdr, local_meta, std_meta);
        
        // Administrative override ACL is standard in network devices
        Acl.apply(hdr, local_meta, std_meta);
    }
}

//------------------------------------------------------------------------------
// EGRESS PIPELINE
//------------------------------------------------------------------------------
control PostQosPipe (inout parsed_headers_t hdr,
                        inout local_metadata_t local_meta,
                        inout standard_metadata_t std_meta) {


    counter(MAX_PDRS, CounterType.packets_and_bytes) post_qos_pdr_counter;

    apply {
        // Count packets that made it through QoS and were not dropped,
        // using the counter index assigned by the PDR that matched in ingress.
        post_qos_pdr_counter.count(local_meta.pdr.ctr_idx);

    }
}
@name("Egress")

//------------------------------------------------------------------------------
// WRAPPER PIPELINES 
// For multi-device / disaggregated UPF setup
//------------------------------------------------------------------------------
#ifdef DISAGG_UPF
control MultiDeviceIngressPipe(inout parsed_headers_t hdr,
                               inout local_metadata_t local_meta,
                               inout standard_metadata_t std_meta) {

    @hidden
    action encap_and_forward_to_buffer() {
        // Slide the header in there
        hdr.buffer_tunnel.setValid();
        hdr.buffer_tunnel.next_header = hdr.ipv4.proto;
        hdr.ipv4.proto = IpProtocol.BUFFER_TUNNEL;
        // Reroute the packet to the buffering device
        std_meta.egress_spec = BUFFER_DEVICE_PORT;
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
        hdr.ethernet.dst_addr = BUFFER_DEVICE_MAC;

        // Metadata consumed by buffering device
        hdr.buffer_tunnel.bar_id = local_meta.bar.id;
    }

    @hidden
    action decap_from_buffer() {
        // Slide the tunnel header back out
        hdr.ipv4.proto = hdr.buffer_tunnel.next_header;
        hdr.buffer_tunnel.setInvalid();

        // No rerouting or bridged metadata because the packet goes
        // through the pre-QoS pipeline again.
    }

    @hidden
    action encap_and_forward_to_qos() {
        // Slide the header in there
        hdr.qos_tunnel.setValid();
        hdr.qos_tunnel.next_header = hdr.ipv4.proto;
        hdr.ipv4.proto = IpProtocol.QOS_TUNNEL;
        // Reroute the packet to the QoS Device
        hdr.qos_tunnel.original_dst_mac = hdr.ethernet.dst_addr;
        hdr.qos_tunnel.original_egress_spec = std_meta.egress_spec;
        hdr.ethernet.dst_addr = QOS_DEVICE_MAC;
        std_meta.egress_spec = QOS_DEVICE_PORT;
        // Metadata consumed by QoS device
        hdr.qos_tunnel.qer_id = local_meta.qos.qer_id;
        // Bridged metadata needed by egress
        hdr.qos_tunnel.ctr_idx = local_meta.pdr.ctr_idx;
    }

    @hidden
    action decap_from_qos() {
        // Reroute the packet back to its original destination
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
        hdr.ethernet.dst_addr = hdr.qos_tunnel.original_dst_mac;
        std_meta.egress_spec  = hdr.qos_tunnel.original_egress_spec;
        
        // Bridged metadata needed by egress
        local_meta.pdr.ctr_idx = hdr.qos_tunnel.ctr_idx;

        // Slide the tunnel header back out
        hdr.ipv4.proto = hdr.qos_tunnel.next_header;
        hdr.qos_tunnel.setInvalid();
    }

    apply {
        if (hdr.qos_tunnel.isValid()) {
            decap_from_qos();
            // If the packet came from the QoS device, then it must've
            // already been ingress processed, so we skip ingress processing
            exit;
        }
        else if (hdr.buffer_tunnel.isValid()) {
            decap_from_buffer();
        }

        // Main ingress pipeline
        PreQosPipe.apply(hdr, local_meta, std_meta);


        if (local_meta.bar.needs_buffering) {
            encap_and_forward_to_buffer();
        }
        else if (local_meta.direction == Direction.DOWNLINK) {
            encap_and_forward_to_qos();
        }
    }
}
control MultiDeviceEgressPipe(inout parsed_headers_t hdr,
                               inout local_metadata_t local_meta,
                               inout standard_metadata_t std_meta) {
    apply {
        if (!(hdr.qos_tunnel.isValid() || hdr.buffer_tunnel.isValid())) {
            PostQosPipe.apply(hdr, local_meta, std_meta);
        }
    }
}
#endif // DISAGG_UPF



V1Switch(
    ParserImpl(),
    VerifyChecksumImpl(),
#ifdef DISAGG_UPF
    MultiDeviceIngressPipe(),
    MultiDeviceEgressPipe(),
#else
    PreQosPipe(),
    PostQosPipe(),
#endif
    ComputeChecksumImpl(),
    DeparserImpl()
) main;
