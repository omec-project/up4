/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;

public final class SouthConstants {

    public static final int INTERFACE_ACCESS = 1;
    public static final int INTERFACE_CORE = 2;
    public static final int INTERFACE_DBUF = 3;

    public static final int DIRECTION_UPLINK = 1;
    public static final int DIRECTION_DOWNLINK = 2;

    // Counters
    public static final PiCounterId INGRESS_COUNTER_ID = PiCounterId.of("FabricIngress.spgw_ingress.pdr_counter");
    public static final PiCounterId EGRESS_COUNTER_ID = PiCounterId.of("FabricEgress.spgw_egress.pdr_counter");
    // Table names
    //   interface lookup
    public static final PiTableId INTERFACE_LOOKUP = PiTableId.of("FabricIngress.spgw_ingress.interface_lookup");
    //   pdr tables
    public static final PiTableId PDR_UPLINK_TBL = PiTableId.of("FabricIngress.spgw_ingress.uplink_pdr_lookup");
    public static final PiTableId PDR_DOWNLINK_TBL = PiTableId.of("FabricIngress.spgw_ingress.downlink_pdr_lookup");
    //   far tables
    public static final PiTableId FAR_TBL = PiTableId.of("FabricIngress.spgw_ingress.far_lookup");
    // Action names
    public static final PiActionId SET_SOURCE_IFACE = PiActionId.of("FabricIngress.spgw_ingress.set_source_iface");
    //public static final PiActionId NO_ACTION = PiActionId.of("nop");
    public static final PiActionId LOAD_PDR = PiActionId.of("FabricIngress.spgw_ingress.set_pdr_attributes");
    public static final PiActionId LOAD_FAR_NORMAL =
            PiActionId.of("FabricIngress.spgw_ingress.load_normal_far_attributes");
    public static final PiActionId LOAD_FAR_TUNNEL =
            PiActionId.of("FabricIngress.spgw_ingress.load_tunnel_far_attributes");
    public static final PiActionId LOAD_FAR_BUFFER =
            PiActionId.of("FabricIngress.spgw_ingress.load_dbuf_far_attributes");
    // Match key names
    //   interface lookup
    public static final PiMatchFieldId GTPU_IS_VALID = PiMatchFieldId.of("gtpu_is_valid");
    public static final PiMatchFieldId IPV4_DST_ADDR = PiMatchFieldId.of("ipv4_dst_addr");
    //   pdrs
    public static final PiMatchFieldId UE_ADDR_KEY = PiMatchFieldId.of("ue_addr");
    public static final PiMatchFieldId TEID_KEY = PiMatchFieldId.of("teid");
    public static final PiMatchFieldId TUNNEL_DST_KEY = PiMatchFieldId.of("tunnel_ipv4_dst");
    //   fars
    public static final PiMatchFieldId FAR_ID_KEY = PiMatchFieldId.of("far_id");
    // Action parameter names
    //   interface lookup
    public static final PiActionParamId SRC_IFACE_PARAM = PiActionParamId.of("src_iface");
    public static final PiActionParamId DIRECTION_PARAM = PiActionParamId.of("direction");
    public static final PiActionParamId SKIP_SPGW_PARAM = PiActionParamId.of("skip_spgw");
    //   pdrs
    public static final PiActionParamId CTR_ID = PiActionParamId.of("ctr_id");
    public static final PiActionParamId FAR_ID_PARAM = PiActionParamId.of("far_id");
    public static final PiActionParamId NEEDS_GTPU_DECAP_PARAM = PiActionParamId.of("needs_gtpu_decap");
    //   fars
    public static final PiActionParamId DROP_FLAG = PiActionParamId.of("drop");
    public static final PiActionParamId NOTIFY_FLAG = PiActionParamId.of("notify_cp");
    public static final PiActionParamId TEID_PARAM = PiActionParamId.of("teid");
    public static final PiActionParamId TUNNEL_SRC_PARAM = PiActionParamId.of("tunnel_src_addr");
    public static final PiActionParamId TUNNEL_DST_PARAM = PiActionParamId.of("tunnel_dst_addr");
    public static final PiActionParamId TUNNEL_DST_PORT_PARAM = PiActionParamId.of("tunnel_src_port");

    // hide default constructor
    private SouthConstants() {
    }
}
