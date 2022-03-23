/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
#include <core.p4>
// Use non retro-compatible (P4_14) v1model.p4 architecture
// this reduces the need for weird casting.
#define V1MODEL_VERSION 20200408
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

    action set_port(PortId_t port) {
        std_meta.egress_spec = port;
    }

    action punt() {
        set_port(CPU_PORT);
    }

    action clone_to_cpu() {
        // Cloning is achieved by using a v1model-specific primitive. Here we
        // set the type of clone operation (ingress-to-egress pipeline), the
        // clone session ID (the CPU one) and the field list ID of user defined
        // metadata we want to preserve (currently none).
        clone_preserving_field_list(CloneType.I2E, CPU_CLONE_SESSION_ID, 0);
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
                 PortId_t egress_port) {
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


    counter<counter_index_t>(MAX_PDRS, CounterType.packets_and_bytes) pre_qos_counter;
    meter<app_meter_idx_t>(MAX_APP_METERS, MeterType.bytes) app_meter;
    meter<session_meter_idx_t>(MAX_SESSION_METERS, MeterType.bytes) session_meter;
    meter<slice_tc_meter_idx_t>(1 << SLICE_TC_WIDTH, MeterType.bytes) slice_tc_meter;

    action _initialize_metadata() {
        local_meta.session_meter_idx_internal = DEFAULT_SESSION_METER_IDX;
        local_meta.app_meter_idx_internal = DEFAULT_APP_METER_IDX;
        local_meta.application_id = APP_ID_UNKNOWN;
        local_meta.preserved_ingress_port = std_meta.ingress_port;
    }

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
    action do_buffer() {
        // Send digest. This is equivalent to a PFCP Downlink Data Notification (DDN), used to
        // notify control plane to initiate the paging procedure to locate and wake-up the UE.
        // The first argument is unused on BMv2.
        digest<ddn_digest_t>(1, { local_meta.ue_addr });
        // The actual buffering cannot be expressed in the logical pipeline.
        mark_to_drop(std_meta);
        exit;
    }

    action do_drop() {
        mark_to_drop(std_meta);
        exit;
    }

    action set_session_uplink(session_meter_idx_t session_meter_idx) {
        local_meta.needs_gtpu_decap = true;
        local_meta.session_meter_idx_internal = session_meter_idx;
    }

    action set_session_uplink_drop() {
        local_meta.needs_dropping = true;
    }

    action set_session_downlink(tunnel_peer_id_t tunnel_peer_id, session_meter_idx_t session_meter_idx) {
        local_meta.tunnel_peer_id = tunnel_peer_id;
        local_meta.session_meter_idx_internal = session_meter_idx;
    }
    action set_session_downlink_drop() {
        local_meta.needs_dropping = true;
    }

    action set_session_downlink_buff(session_meter_idx_t session_meter_idx) {
        local_meta.needs_buffering = true;
        local_meta.session_meter_idx_internal = session_meter_idx;
    }

    table sessions_uplink {
        key = {
            hdr.ipv4.dst_addr   : exact @name("n3_address");
            local_meta.teid     : exact @name("teid");
        }
        actions = {
            set_session_uplink;
            set_session_uplink_drop;
            @defaultonly do_drop;
        }
        const default_action = do_drop;
    }

    table sessions_downlink {
        key = {
            hdr.ipv4.dst_addr   : exact @name("ue_address");
        }
        actions = {
            set_session_downlink;
            set_session_downlink_drop;
            set_session_downlink_buff;
            @defaultonly do_drop;
        }
        const default_action = do_drop;
    }

    @hidden
    action common_term(counter_index_t ctr_idx) {
        local_meta.ctr_idx = ctr_idx;
        local_meta.terminations_hit = true;
    }

    action uplink_term_fwd(counter_index_t ctr_idx, TrafficClass tc, app_meter_idx_t app_meter_idx) {
        common_term(ctr_idx);
        local_meta.app_meter_idx_internal = app_meter_idx;
        local_meta.tc = tc;
    }

    action uplink_term_drop(counter_index_t ctr_idx) {
        common_term(ctr_idx);
        local_meta.needs_dropping = true;
    }

    // QFI = 0 for 4G traffic
    action downlink_term_fwd(counter_index_t ctr_idx, teid_t teid, qfi_t qfi, TrafficClass tc, app_meter_idx_t app_meter_idx) {
        common_term(ctr_idx);
        local_meta.tunnel_out_teid = teid;
        local_meta.tunnel_out_qfi = qfi;
        local_meta.app_meter_idx_internal = app_meter_idx;
        local_meta.tc = tc;
    }

    action downlink_term_drop(counter_index_t ctr_idx) {
        common_term(ctr_idx);
        local_meta.needs_dropping = true;
    }

    table terminations_uplink {
        key = {
            local_meta.ue_addr          : exact @name("ue_address"); // Session ID
            local_meta.application_id   : exact @name("app_id");
        }
        actions = {
            uplink_term_fwd;
            uplink_term_drop;
            @defaultonly do_drop;
        }
        const default_action = do_drop;
    }

    table terminations_downlink {
        key = {
            local_meta.ue_addr          : exact @name("ue_address"); // Session ID
            local_meta.application_id   : exact @name("app_id");
        }
        actions = {
            downlink_term_fwd;
            downlink_term_drop;
            @defaultonly do_drop;
        }
        const default_action = do_drop;
    }

    action set_app_id(bit<8> app_id) {
        local_meta.application_id = app_id;
    }

    table applications {
        key = {
            local_meta.slice_id     : exact     @name("slice_id");
            local_meta.inet_addr    : lpm       @name("app_ip_addr");
            local_meta.inet_l4_port : range     @name("app_l4_port");
            local_meta.ip_proto     : ternary   @name("app_ip_proto");
        }
        actions = {
            set_app_id;
        }
        const default_action = set_app_id(APP_ID_UNKNOWN);
    }

    action load_tunnel_param(ipv4_addr_t    src_addr,
                             ipv4_addr_t    dst_addr,
                             l4_port_t      sport
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
                      l4_port_t udp_sport, l4_port_t udp_dport,
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
        _initialize_metadata();
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
            // Interfaces we care about:
            // N3 (from base station) - GTPU - match on outer IP dst
            // N6 (from internet) - no GTPU - match on IP header dst
            if (interfaces.apply().hit) {
                // Normalize so the UE address/port appear as the same field regardless of direction
                if (local_meta.direction == Direction.UPLINK) {
                    local_meta.ue_addr = hdr.inner_ipv4.src_addr;
                    local_meta.inet_addr = hdr.inner_ipv4.dst_addr;
                    local_meta.ue_l4_port = local_meta.l4_sport;
                    local_meta.inet_l4_port = local_meta.l4_dport;
                    local_meta.ip_proto = hdr.inner_ipv4.proto;

                    sessions_uplink.apply();
                } else if (local_meta.direction == Direction.DOWNLINK) {
                    local_meta.ue_addr = hdr.ipv4.dst_addr;
                    local_meta.inet_addr = hdr.ipv4.src_addr;
                    local_meta.ue_l4_port = local_meta.l4_dport;
                    local_meta.inet_l4_port = local_meta.l4_sport;
                    local_meta.ip_proto = hdr.ipv4.proto;

                    sessions_downlink.apply();
                    tunnel_peers.apply();
                }

                applications.apply();

                if (local_meta.direction == Direction.UPLINK) {
                    terminations_uplink.apply();
                }  else if (local_meta.direction == Direction.DOWNLINK) {
                    terminations_downlink.apply();
                }

                if (local_meta.terminations_hit) {
                    // Count packets at a counter index unique to whichever termination matched.
                    pre_qos_counter.count(local_meta.ctr_idx);
                }

                app_meter.execute_meter<MeterColor>(local_meta.app_meter_idx_internal, local_meta.app_color);
                if (local_meta.app_color == MeterColor.RED) {
                    local_meta.needs_dropping = true;
                } else {
                    session_meter.execute_meter<MeterColor>(local_meta.session_meter_idx_internal, local_meta.session_color);
                    if (local_meta.session_color == MeterColor.RED) {
                        local_meta.needs_dropping = true;
                    } else {
                        slice_tc_meter.execute_meter<MeterColor>(local_meta.slice_id++local_meta.tc, local_meta.slice_tc_color);
                        if (local_meta.slice_tc_color == MeterColor.RED) {
                            local_meta.needs_dropping = true;
                        }
                    }
                }

                // Perform whatever header removal the matching in
                // sessions_* and terminations_* required.
                if (local_meta.needs_gtpu_decap) {
                    gtpu_decap();
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


    counter<counter_index_t>(MAX_PDRS, CounterType.packets_and_bytes) post_qos_counter;

    apply {
        // Count packets that made it through QoS and were not dropped,
        // using the counter index assigned by the terminations table in ingress.
        post_qos_counter.count(local_meta.ctr_idx);

        // If this is a packet-in to the controller, e.g., if in ingress we
        // matched on the ACL table with action send/clone_to_cpu...
        if (std_meta.egress_port == CPU_PORT) {
            // Add packet_in header and set relevant fields, such as the
            // switch ingress port where the packet was received.
            hdr.packet_in.setValid();
            hdr.packet_in.ingress_port = local_meta.preserved_ingress_port;
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
