/*
 * SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 * SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
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

    action clone_to_cpu() {
        // Cloning is achieved by using a v1model-specific primitive. Here we
        // set the type of clone operation (ingress-to-egress pipeline), the
        // clone session ID (the CPU one), and the metadata fields we want to
        // preserve for the cloned packet replica.
        clone3(CloneType.I2E, CPU_CLONE_SESSION_ID, { std_meta.ingress_port });
    }

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
            clone_to_cpu;
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

    action route(mac_addr_t src_mac,
                 mac_addr_t dst_mac,
                 port_num_t egress_port) {
        std_meta.egress_spec = egress_port;
        hdr.ethernet.src_addr = src_mac;
        hdr.ethernet.dst_addr = dst_mac;
    }

    table routes_v4 {
        key = {
            hdr.ipv4.dst_addr      : lpm @name("dst_prefix");
            hdr.ipv4.src_addr      : selector;
            hdr.ipv4.proto         : selector;
            local_meta.l4_sport    : selector;
            local_meta.l4_dport    : selector;
        }
        actions = {
            route;
        }
        @name("hashed_selector")
        implementation = action_selector(HashAlgorithm.crc16, 32w1024, 32w16);
        size = MAX_ROUTES;
    }

    apply {
        // Normalize IP address for routing table, and decrement TTL
        // TODO: find a better alternative to this hack
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
        if (hdr.ipv4.ttl == 0) {
            drop();
        }
        else {
            routes_v4.apply();
        }
    }
}

//------------------------------------------------------------------------------
// INGRESS PIPELINE
//------------------------------------------------------------------------------
control PreQosPipe (inout parsed_headers_t    hdr,
                    inout local_metadata_t    local_meta,
                    inout standard_metadata_t std_meta) {


    counter(MAX_PDRS, CounterType.packets_and_bytes) pre_qos_upf_counter;

    table my_station {
        key = {
            hdr.ethernet.dst_addr : exact @name("dst_mac");
        }
        actions = {
            NoAction;
        }
    }

    action set_source_iface(InterfaceType src_iface, Direction direction, slice_id_t slice_id) {
        // Interface type can be access, core (see InterfaceType enum)
        // If interface is from the control plane, direction can be either up or down
        local_meta.src_iface = src_iface;
        local_meta.direction = direction;
        local_meta.slice_id = slice_id;
    }
    table interfaces {
        key = {
            hdr.ipv4.dst_addr : lpm @name("ipv4_dst_prefix");
            // Eventually should also check VLAN ID here
        }
        actions = {
            set_source_iface;
        }
        const default_action = set_source_iface(InterfaceType.UNKNOWN, Direction.UNKNOWN, Slice.DEFAULT);
    }

    @hidden
    action gtpu_decap() {
        hdr.ipv4 = hdr.inner_ipv4;
        hdr.inner_ipv4.setInvalid();
        hdr.udp = hdr.inner_udp;
        hdr.inner_udp.setInvalid();
        hdr.tcp = hdr.inner_tcp;
        hdr.inner_tcp.setInvalid();
        hdr.icmp = hdr.inner_icmp;
        hdr.inner_icmp.setInvalid();
        hdr.gtpu.setInvalid();
        hdr.gtpu_options.setInvalid();
        hdr.gtpu_ext_psc.setInvalid();
    }

    @hidden
    action do_notify_cp() {
        clone3(CloneType.I2E, CPU_CLONE_SESSION_ID, { std_meta.ingress_port });
    }

    @hidden
    action do_buffer() {
        // Send digest. This is equivalent to a PFCP Downlink Data Notification (DDN), used to
        // notify control plane to initiate the paging procedure to locate and wake-up the UE.
        // FIXME: what is the first argument 1 used for?
        digest<ddn_digest_t>(1, { local_meta.ue_addr });
        // The actual buffering cannot be expressed in the logical pipeline.
        mark_to_drop(std_meta);
        exit;
    }

    @hidden
    action do_drop() {
        mark_to_drop(std_meta);
        exit;
    }

    action set_params_uplink() {
        local_meta.needs_gtpu_decap = true;
    }

    action set_params_downlink(tunnel_peer_id_t tunnel_peer_id,
                               bit<1> needs_buffering,
                               bit<1> notify_cp) {
        local_meta.needs_buffering = (bool)needs_buffering;
        local_meta.notify_cp = (bool)notify_cp;
        local_meta.tunnel_peer_id = tunnel_peer_id;
    }

    table sessions {
        key = {
            local_meta.src_iface    : exact     @name("src_iface");
            hdr.ipv4.dst_addr       : exact     @name("ipv4_dst");
            local_meta.teid         : ternary   @name("teid"); // don't care for downlink
        }
        actions = {
            set_params_uplink;
            set_params_downlink;
        }
    }

    action fwd_uplink(counter_index_t ctr_idx, tc_t tc) {
        local_meta.ctr_idx = ctr_idx;
        local_meta.tc = tc;
    }

    // QFI = 0 for 4G traffic
    action fwd_downlink(teid_t teid, counter_index_t ctr_idx, qfi_t qfi, tc_t tc) {
        local_meta.tunnel_out_teid = teid;
        local_meta.ctr_idx = ctr_idx;
        local_meta.tc = tc;
        local_meta.tunnel_out_qfi = qfi;
    }
    action term_drop(counter_index_t ctr_idx) {
        local_meta.needs_dropping = true;
        local_meta.ctr_idx = ctr_idx;
    }

    table terminations {
        key = {
            local_meta.slice_id     : exact @name("slice_id");
            local_meta.src_iface    : exact @name("src_iface");
            local_meta.ue_addr      : exact @name("ue_address"); // Session ID
        }
        actions = {
            fwd_uplink;
            fwd_downlink;
            term_drop;
        }
    }

    action load_tunnel_param(ipv4_addr_t    src_addr,
                             ipv4_addr_t    dst_addr,
                             L4Port         sport
                             ) {
        local_meta.tunnel_out_src_ipv4_addr = src_addr;
        local_meta.tunnel_out_dst_ipv4_addr = dst_addr;
        local_meta.tunnel_out_udp_sport     = sport;
        local_meta.needs_tunneling          = true;
    }

    table tunnel_peers {
        key = {
            local_meta.tunnel_peer_id : exact @name("tunnel_peer_id");
        }
        actions = {
            load_tunnel_param;
        }
    }

    @hidden
    action _udp_encap(ipv4_addr_t src_addr, ipv4_addr_t dst_addr,
                      L4Port udp_sport, L4Port udp_dport,
                      bit<16> ipv4_total_len,
                      bit<16> udp_len) {
        hdr.inner_udp = hdr.udp;
        hdr.udp.setInvalid();
        hdr.inner_tcp = hdr.tcp;
        hdr.tcp.setInvalid();
        hdr.inner_icmp = hdr.icmp;
        hdr.icmp.setInvalid();
        hdr.udp.setValid();
        hdr.udp.sport = udp_sport;
        hdr.udp.dport = udp_dport;
        hdr.udp.len = udp_len;
        hdr.udp.checksum = 0; // Never updated due to p4 limitations

        hdr.inner_ipv4 = hdr.ipv4;
        hdr.ipv4.setValid();
        hdr.ipv4.version = IP_VERSION_4;
        hdr.ipv4.ihl = IPV4_MIN_IHL;
        hdr.ipv4.dscp = 0;
        hdr.ipv4.ecn = 0;
        hdr.ipv4.total_len = ipv4_total_len;
        hdr.ipv4.identification = 0x1513; // TODO: change this to timestamp or some incremental num
        hdr.ipv4.flags = 0;
        hdr.ipv4.frag_offset = 0;
        hdr.ipv4.ttl = DEFAULT_IPV4_TTL;
        hdr.ipv4.proto = IpProtocol.UDP;
        hdr.ipv4.src_addr = src_addr;
        hdr.ipv4.dst_addr = dst_addr;
        hdr.ipv4.checksum = 0; // Updated later


    }

    @hidden
    action _gtpu_encap(teid_t teid) {
        hdr.gtpu.setValid();
        hdr.gtpu.version = GTP_V1;
        hdr.gtpu.pt = GTP_PROTOCOL_TYPE_GTP;
        hdr.gtpu.spare = 0;
        hdr.gtpu.ex_flag = 0;
        hdr.gtpu.seq_flag = 0;
        hdr.gtpu.npdu_flag = 0;
        hdr.gtpu.msgtype = GTPUMessageType.GPDU;
        hdr.gtpu.msglen = hdr.inner_ipv4.total_len;
        hdr.gtpu.teid = teid;
    }

    action do_gtpu_tunnel() {
        _udp_encap(local_meta.tunnel_out_src_ipv4_addr,
                   local_meta.tunnel_out_dst_ipv4_addr,
                   local_meta.tunnel_out_udp_sport,
                   L4Port.GTP_GPDU,
                   hdr.ipv4.total_len + IPV4_HDR_SIZE + UDP_HDR_SIZE + GTP_HDR_MIN_SIZE,
                   hdr.ipv4.total_len + UDP_HDR_SIZE + GTP_HDR_MIN_SIZE);
        _gtpu_encap(local_meta.tunnel_out_teid);
    }


    action do_gtpu_tunnel_with_psc() {
        _udp_encap(local_meta.tunnel_out_src_ipv4_addr,
                   local_meta.tunnel_out_dst_ipv4_addr,
                   local_meta.tunnel_out_udp_sport,
                   L4Port.GTP_GPDU,
                   hdr.ipv4.total_len + IPV4_HDR_SIZE + UDP_HDR_SIZE + GTP_HDR_MIN_SIZE
                    + GTPU_OPTIONS_HDR_BYTES + GTPU_EXT_PSC_HDR_BYTES,
                   hdr.ipv4.total_len + UDP_HDR_SIZE + GTP_HDR_MIN_SIZE
                    + GTPU_OPTIONS_HDR_BYTES + GTPU_EXT_PSC_HDR_BYTES);
        _gtpu_encap(local_meta.tunnel_out_teid);
        hdr.gtpu.msglen = hdr.inner_ipv4.total_len + GTPU_OPTIONS_HDR_BYTES
                            + GTPU_EXT_PSC_HDR_BYTES; // Override msglen set by _gtpu_encap
        hdr.gtpu.ex_flag = 1; // Override value set by _gtpu_encap
        hdr.gtpu_options.setValid();
        hdr.gtpu_options.seq_num   = 0;
        hdr.gtpu_options.n_pdu_num = 0;
        hdr.gtpu_options.next_ext  = GTPU_NEXT_EXT_PSC;
        hdr.gtpu_ext_psc.setValid();
        hdr.gtpu_ext_psc.len      = GTPU_EXT_PSC_LEN;
        hdr.gtpu_ext_psc.type     = GTPU_EXT_PSC_TYPE_DL;
        hdr.gtpu_ext_psc.spare0   = 0;
        hdr.gtpu_ext_psc.ppp      = 0;
        hdr.gtpu_ext_psc.rqi      = 0;
        hdr.gtpu_ext_psc.qfi      = local_meta.tunnel_out_qfi;
        hdr.gtpu_ext_psc.next_ext = GTPU_NEXT_EXT_NONE;
    }

    //----------------------------------------
    // INGRESS APPLY BLOCK
    //----------------------------------------
    apply {

        if (hdr.packet_out.isValid()) {
            // All packet-outs should be routed like regular packets, without UPF processing. This
            // is used for sending GTP End Marker to base stations, and for other packets
            // originating from the control plane.
            hdr.packet_out.setInvalid();
        } else {
            // Only process if the packet is destined for our MAC addr. We don't handle switching
            if (!my_station.apply().hit) {
                return;
            }
            // +------------+     +----------+     +--------------+
            // | interfaces |---->| sessions |---->| terminations |
            // +------------+     +----------+     +--------------+
            //                   |
            //                   |  +--------------+
            //                   +->| tunnel_peers |
            //                      +--------------+
            // Interfaces we care about:
            // N3 (from base station) - GTPU - match on outer IP dst
            // N6 (from internet) - no GTPU - match on IP header dst
            interfaces.apply();
            // Interface lookup happens before normalization of headers,
            // because the lookup uses the outermost IP header in all cases

            // Normalize the headers so that the UE's IPv4 header is always hdr.ipv4
            // regardless of if there is encapsulation or not.
            /*if (hdr.inner_ipv4.isValid()) {
                hdr.outer_ipv4 = hdr.ipv4;
                hdr.ipv4 = hdr.inner_ipv4;
                hdr.inner_ipv4.setInvalid();
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
            }*/

            // Normalize so the UE address/port appear as the same field regardless of direction
            if (local_meta.direction == Direction.UPLINK) {
                local_meta.ue_addr = hdr.inner_ipv4.src_addr;
                local_meta.inet_addr = hdr.inner_ipv4.dst_addr;
                local_meta.ue_l4_port = local_meta.l4_sport;
                local_meta.inet_l4_port = local_meta.l4_dport;
            }
            else if (local_meta.direction == Direction.DOWNLINK) {
                local_meta.ue_addr = hdr.ipv4.dst_addr;
                local_meta.inet_addr = hdr.ipv4.src_addr;
                local_meta.ue_l4_port = local_meta.l4_dport;
                local_meta.inet_l4_port = local_meta.l4_sport;
            }

            sessions.apply();
            tunnel_peers.apply();
            terminations.apply();
            // Count packets at a counter index unique to whichever termination matched.
            pre_qos_upf_counter.count(local_meta.ctr_idx);

            // Perform whatever header removal the matching PDR required.
            if (local_meta.needs_gtpu_decap) {
                gtpu_decap();
            }
            if (local_meta.notify_cp) {
                // FIXME: WHY notifying with a packet in, if we already have the digest??
                do_notify_cp();
            }
            if (local_meta.needs_buffering) {
                do_buffer();
            }
            if (local_meta.needs_tunneling) {
                if (local_meta.tunnel_out_qfi == 0) {
                    // 4G
                    do_gtpu_tunnel();
                } else {
                    // 5G
                    do_gtpu_tunnel_with_psc();
                }
            }
            if (local_meta.needs_dropping) {
                do_drop();
            }
        }

        // UPF only set the destination IP.
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


    counter(MAX_PDRS, CounterType.packets_and_bytes) post_qos_upf_counter;

    apply {
        // Count packets that made it through QoS and were not dropped,
        // using the counter index assigned by the terminations table in ingress.
        post_qos_upf_counter.count(local_meta.ctr_idx);

        // If this is a packet-in to the controller, e.g., if in ingress we
        // matched on the ACL table with action send/clone_to_cpu...
        if (std_meta.egress_port == CPU_PORT) {
            // Add packet_in header and set relevant fields, such as the
            // switch ingress port where the packet was received.
            hdr.packet_in.setValid();
            hdr.packet_in.ingress_port = std_meta.ingress_port;
            // Exit the pipeline here.
            exit;
        }
    }
}


V1Switch(
    ParserImpl(),
    VerifyChecksumImpl(),
    PreQosPipe(),
    PostQosPipe(),
    ComputeChecksumImpl(),
    DeparserImpl()
) main;
