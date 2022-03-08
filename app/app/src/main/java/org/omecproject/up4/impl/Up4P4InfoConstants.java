/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2021-present Open Networking Foundation <info@opennetworking.org>
 */

// Do not modify this file manually, use `make constants` to generate this file.

package org.omecproject.up4.impl;

import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiActionProfileId;
import org.onosproject.net.pi.model.PiMeterId;
import org.onosproject.net.pi.model.PiPacketMetadataId;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;
/**
 * P4Info constants.
 */
public final class Up4P4InfoConstants {

    // hide default constructor
    private Up4P4InfoConstants() {
    }

    // Header field IDs
    public static final PiMatchFieldId HDR_APP_ID = PiMatchFieldId.of("app_id");
    public static final int HDR_APP_ID_BITWIDTH = 8;
    public static final PiMatchFieldId HDR_APP_IP_ADDR =
            PiMatchFieldId.of("app_ip_addr");
    public static final int HDR_APP_IP_ADDR_BITWIDTH = 32;
    public static final PiMatchFieldId HDR_APP_IP_PROTO =
            PiMatchFieldId.of("app_ip_proto");
    public static final int HDR_APP_IP_PROTO_BITWIDTH = 8;
    public static final PiMatchFieldId HDR_APP_L4_PORT =
            PiMatchFieldId.of("app_l4_port");
    public static final int HDR_APP_L4_PORT_BITWIDTH = 16;
    public static final PiMatchFieldId HDR_DST_MAC =
            PiMatchFieldId.of("dst_mac");
    public static final int HDR_DST_MAC_BITWIDTH = 48;
    public static final PiMatchFieldId HDR_DST_PREFIX =
            PiMatchFieldId.of("dst_prefix");
    public static final int HDR_DST_PREFIX_BITWIDTH = 32;
    public static final PiMatchFieldId HDR_ETH_DST =
            PiMatchFieldId.of("eth_dst");
    public static final int HDR_ETH_DST_BITWIDTH = 48;
    public static final PiMatchFieldId HDR_ETH_SRC =
            PiMatchFieldId.of("eth_src");
    public static final int HDR_ETH_SRC_BITWIDTH = 48;
    public static final PiMatchFieldId HDR_ETH_TYPE =
            PiMatchFieldId.of("eth_type");
    public static final int HDR_ETH_TYPE_BITWIDTH = 16;
    public static final PiMatchFieldId HDR_INPORT = PiMatchFieldId.of("inport");
    public static final int HDR_INPORT_BITWIDTH = 9;
    public static final PiMatchFieldId HDR_IPV4_DST =
            PiMatchFieldId.of("ipv4_dst");
    public static final int HDR_IPV4_DST_BITWIDTH = 32;
    public static final PiMatchFieldId HDR_IPV4_DST_PREFIX =
            PiMatchFieldId.of("ipv4_dst_prefix");
    public static final int HDR_IPV4_DST_PREFIX_BITWIDTH = 32;
    public static final PiMatchFieldId HDR_IPV4_PROTO =
            PiMatchFieldId.of("ipv4_proto");
    public static final int HDR_IPV4_PROTO_BITWIDTH = 8;
    public static final PiMatchFieldId HDR_IPV4_SRC =
            PiMatchFieldId.of("ipv4_src");
    public static final int HDR_IPV4_SRC_BITWIDTH = 32;
    public static final PiMatchFieldId HDR_L4_DPORT =
            PiMatchFieldId.of("l4_dport");
    public static final int HDR_L4_DPORT_BITWIDTH = 16;
    public static final PiMatchFieldId HDR_L4_SPORT =
            PiMatchFieldId.of("l4_sport");
    public static final int HDR_L4_SPORT_BITWIDTH = 16;
    public static final PiMatchFieldId HDR_N3_ADDRESS =
            PiMatchFieldId.of("n3_address");
    public static final int HDR_N3_ADDRESS_BITWIDTH = 32;
    public static final PiMatchFieldId HDR_SLICE_ID =
            PiMatchFieldId.of("slice_id");
    public static final int HDR_SLICE_ID_BITWIDTH = 4;
    public static final PiMatchFieldId HDR_SRC_IFACE =
            PiMatchFieldId.of("src_iface");
    public static final int HDR_SRC_IFACE_BITWIDTH = 8;
    public static final PiMatchFieldId HDR_TC_UNKNOWN =
            PiMatchFieldId.of("tc_unknown");
    public static final int HDR_TC_UNKNOWN_BITWIDTH = 1;
    public static final PiMatchFieldId HDR_TEID = PiMatchFieldId.of("teid");
    public static final int HDR_TEID_BITWIDTH = 32;
    public static final PiMatchFieldId HDR_TUNNEL_PEER_ID =
            PiMatchFieldId.of("tunnel_peer_id");
    public static final int HDR_TUNNEL_PEER_ID_BITWIDTH = 8;
    public static final PiMatchFieldId HDR_UE_ADDRESS =
            PiMatchFieldId.of("ue_address");
    public static final int HDR_UE_ADDRESS_BITWIDTH = 32;
    // Table IDs
    public static final PiTableId PRE_QOS_PIPE__ACL_ACLS =
            PiTableId.of("PreQosPipe.Acl.acls");
    public static final PiTableId PRE_QOS_PIPE__ROUTING_ROUTES_V4 =
            PiTableId.of("PreQosPipe.Routing.routes_v4");
    public static final PiTableId PRE_QOS_PIPE_APPLICATIONS =
            PiTableId.of("PreQosPipe.applications");
    public static final PiTableId PRE_QOS_PIPE_DEFAULT_TC =
            PiTableId.of("PreQosPipe.default_tc");
    public static final PiTableId PRE_QOS_PIPE_INTERFACES =
            PiTableId.of("PreQosPipe.interfaces");
    public static final PiTableId PRE_QOS_PIPE_MY_STATION =
            PiTableId.of("PreQosPipe.my_station");
    public static final PiTableId PRE_QOS_PIPE_SESSIONS_DOWNLINK =
            PiTableId.of("PreQosPipe.sessions_downlink");
    public static final PiTableId PRE_QOS_PIPE_SESSIONS_UPLINK =
            PiTableId.of("PreQosPipe.sessions_uplink");
    public static final PiTableId PRE_QOS_PIPE_TERMINATIONS_DOWNLINK =
            PiTableId.of("PreQosPipe.terminations_downlink");
    public static final PiTableId PRE_QOS_PIPE_TERMINATIONS_UPLINK =
            PiTableId.of("PreQosPipe.terminations_uplink");
    public static final PiTableId PRE_QOS_PIPE_TUNNEL_PEERS =
            PiTableId.of("PreQosPipe.tunnel_peers");
    // Indirect Counter IDs
    public static final PiCounterId POST_QOS_PIPE_POST_QOS_COUNTER =
            PiCounterId.of("PostQosPipe.post_qos_counter");
    public static final PiCounterId PRE_QOS_PIPE_PRE_QOS_COUNTER =
            PiCounterId.of("PreQosPipe.pre_qos_counter");
    // Direct Counter IDs
    public static final PiCounterId ACLS = PiCounterId.of("acls");
    // Action IDs
    public static final PiActionId NO_ACTION = PiActionId.of("NoAction");
    public static final PiActionId PRE_QOS_PIPE__ACL_CLONE_TO_CPU =
            PiActionId.of("PreQosPipe.Acl.clone_to_cpu");
    public static final PiActionId PRE_QOS_PIPE__ACL_DROP =
            PiActionId.of("PreQosPipe.Acl.drop");
    public static final PiActionId PRE_QOS_PIPE__ACL_PUNT =
            PiActionId.of("PreQosPipe.Acl.punt");
    public static final PiActionId PRE_QOS_PIPE__ACL_SET_PORT =
            PiActionId.of("PreQosPipe.Acl.set_port");
    public static final PiActionId PRE_QOS_PIPE__ROUTING_DROP =
            PiActionId.of("PreQosPipe.Routing.drop");
    public static final PiActionId PRE_QOS_PIPE__ROUTING_ROUTE =
            PiActionId.of("PreQosPipe.Routing.route");
    public static final PiActionId PRE_QOS_PIPE__INITIALIZE_METADATA =
            PiActionId.of("PreQosPipe._initialize_metadata");
    public static final PiActionId PRE_QOS_PIPE_DO_DROP =
            PiActionId.of("PreQosPipe.do_drop");
    public static final PiActionId PRE_QOS_PIPE_DO_GTPU_TUNNEL =
            PiActionId.of("PreQosPipe.do_gtpu_tunnel");
    public static final PiActionId PRE_QOS_PIPE_DO_GTPU_TUNNEL_WITH_PSC =
            PiActionId.of("PreQosPipe.do_gtpu_tunnel_with_psc");
    public static final PiActionId PRE_QOS_PIPE_DOWNLINK_TERM_DROP =
            PiActionId.of("PreQosPipe.downlink_term_drop");
    public static final PiActionId PRE_QOS_PIPE_DOWNLINK_TERM_FWD =
            PiActionId.of("PreQosPipe.downlink_term_fwd");
    public static final PiActionId PRE_QOS_PIPE_DOWNLINK_TERM_FWD_NO_TC =
            PiActionId.of("PreQosPipe.downlink_term_fwd_no_tc");
    public static final PiActionId PRE_QOS_PIPE_LOAD_TUNNEL_PARAM =
            PiActionId.of("PreQosPipe.load_tunnel_param");
    public static final PiActionId PRE_QOS_PIPE_SET_APP_ID =
            PiActionId.of("PreQosPipe.set_app_id");
    public static final PiActionId PRE_QOS_PIPE_SET_DEFAULT_TC =
            PiActionId.of("PreQosPipe.set_default_tc");
    public static final PiActionId PRE_QOS_PIPE_SET_SESSION_DOWNLINK =
            PiActionId.of("PreQosPipe.set_session_downlink");
    public static final PiActionId PRE_QOS_PIPE_SET_SESSION_DOWNLINK_BUFF =
            PiActionId.of("PreQosPipe.set_session_downlink_buff");
    public static final PiActionId PRE_QOS_PIPE_SET_SESSION_DOWNLINK_DROP =
            PiActionId.of("PreQosPipe.set_session_downlink_drop");
    public static final PiActionId PRE_QOS_PIPE_SET_SESSION_UPLINK =
            PiActionId.of("PreQosPipe.set_session_uplink");
    public static final PiActionId PRE_QOS_PIPE_SET_SESSION_UPLINK_DROP =
            PiActionId.of("PreQosPipe.set_session_uplink_drop");
    public static final PiActionId PRE_QOS_PIPE_SET_SOURCE_IFACE =
            PiActionId.of("PreQosPipe.set_source_iface");
    public static final PiActionId PRE_QOS_PIPE_UPLINK_TERM_DROP =
            PiActionId.of("PreQosPipe.uplink_term_drop");
    public static final PiActionId PRE_QOS_PIPE_UPLINK_TERM_FWD =
            PiActionId.of("PreQosPipe.uplink_term_fwd");
    public static final PiActionId PRE_QOS_PIPE_UPLINK_TERM_FWD_NO_TC =
            PiActionId.of("PreQosPipe.uplink_term_fwd_no_tc");
    // Action Param IDs
    public static final PiActionParamId APP_ID = PiActionParamId.of("app_id");
    public static final PiActionParamId APP_METER_IDX =
            PiActionParamId.of("app_meter_idx");
    public static final PiActionParamId CTR_IDX = PiActionParamId.of("ctr_idx");
    public static final PiActionParamId DIRECTION =
            PiActionParamId.of("direction");
    public static final PiActionParamId DST_ADDR =
            PiActionParamId.of("dst_addr");
    public static final PiActionParamId DST_MAC = PiActionParamId.of("dst_mac");
    public static final PiActionParamId EGRESS_PORT =
            PiActionParamId.of("egress_port");
    public static final PiActionParamId PORT = PiActionParamId.of("port");
    public static final PiActionParamId QFI = PiActionParamId.of("qfi");
    public static final PiActionParamId SESSION_METER_IDX =
            PiActionParamId.of("session_meter_idx");
    public static final PiActionParamId SLICE_ID =
            PiActionParamId.of("slice_id");
    public static final PiActionParamId SPORT = PiActionParamId.of("sport");
    public static final PiActionParamId SRC_ADDR =
            PiActionParamId.of("src_addr");
    public static final PiActionParamId SRC_IFACE =
            PiActionParamId.of("src_iface");
    public static final PiActionParamId SRC_MAC = PiActionParamId.of("src_mac");
    public static final PiActionParamId TC = PiActionParamId.of("tc");
    public static final PiActionParamId TEID = PiActionParamId.of("teid");
    public static final PiActionParamId TUNNEL_PEER_ID =
            PiActionParamId.of("tunnel_peer_id");
    // Action Profile IDs
    public static final PiActionProfileId HASHED_SELECTOR =
            PiActionProfileId.of("hashed_selector");
    // Packet Metadata IDs
    public static final PiPacketMetadataId INGRESS_PORT =
            PiPacketMetadataId.of("ingress_port");
    public static final int INGRESS_PORT_BITWIDTH = 9;
    public static final PiPacketMetadataId RESERVED =
            PiPacketMetadataId.of("reserved");
    public static final int RESERVED_BITWIDTH = 8;
    // Meter IDs
    public static final PiMeterId PRE_QOS_PIPE_APP_METER =
            PiMeterId.of("PreQosPipe.app_meter");
    public static final PiMeterId PRE_QOS_PIPE_SESSION_METER =
            PiMeterId.of("PreQosPipe.session_meter");
    public static final PiMeterId PRE_QOS_PIPE_SLICE_TC_METER =
            PiMeterId.of("PreQosPipe.slice_tc_meter");
}
