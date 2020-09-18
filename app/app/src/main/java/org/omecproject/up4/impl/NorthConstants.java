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

public final class NorthConstants {
    // Counters
    public static final PiCounterId INGRESS_COUNTER_ID = PiCounterId.of("PreQosPipe.pre_qos_pdr_counter");
    public static final PiCounterId EGRESS_COUNTER_ID = PiCounterId.of("PostQosPipe.post_qos_pdr_counter");
    // P4 enums and constants
    public static final int SESSION_ID_BITWIDTH = 96;
    public static final byte DIRECTION_UPLINK = 1;
    public static final byte DIRECTION_DOWNLINK = 2;
    public static final byte IFACE_ACCESS = 1;
    public static final byte IFACE_CORE = 2;
    public static final byte TUNNEL_TYPE_GTPU = 3;
    // Table names
    public static final PiTableId IFACE_TBL = PiTableId.of("PreQosPipe.source_iface_lookup");
    public static final PiTableId PDR_TBL = PiTableId.of("PreQosPipe.pdrs");
    public static final PiTableId FAR_TBL = PiTableId.of("PreQosPipe.fars");
    // Action names
    public static final PiActionId LOAD_IFACE = PiActionId.of("PreQosPipe.set_source_iface");
    public static final PiActionId LOAD_PDR = PiActionId.of("PreQosPipe.set_pdr_attributes");
    public static final PiActionId LOAD_FAR_TUNNEL = PiActionId.of("PreQosPipe.load_tunnel_far_attributes");
    public static final PiActionId LOAD_FAR_NORMAL = PiActionId.of("PreQosPipe.load_normal_far_attributes");
    // Match key names
    //   interface lookup table
    public static final PiMatchFieldId IFACE_DST_PREFIX_KEY = PiMatchFieldId.of("ipv4_dst_prefix");
    //   pdr table
    public static final PiMatchFieldId SRC_IFACE_KEY = PiMatchFieldId.of("src_iface");
    public static final PiMatchFieldId UE_ADDR_KEY = PiMatchFieldId.of("ue_addr");
    public static final PiMatchFieldId TEID_KEY = PiMatchFieldId.of("teid");
    public static final PiMatchFieldId TUNNEL_DST_KEY = PiMatchFieldId.of("tunnel_ipv4_dst");
    //   far table
    public static final PiMatchFieldId FAR_ID_KEY = PiMatchFieldId.of("far_id");
    public static final PiMatchFieldId SESSION_ID_KEY = PiMatchFieldId.of("session_id");
    // Action parameter names
    //   interface lookup table
    public static final PiActionParamId SRC_IFACE_PARAM = PiActionParamId.of("src_iface");
    public static final PiActionParamId DIRECTION = PiActionParamId.of("direction");
    //   pdr table
    public static final PiActionParamId PDR_ID_PARAM = PiActionParamId.of("id");
    public static final PiActionParamId DECAP_FLAG_PARAM = PiActionParamId.of("needs_gtpu_decap");
    public static final PiActionParamId CTR_ID = PiActionParamId.of("ctr_id");
    public static final PiActionParamId FAR_ID_PARAM = PiActionParamId.of("far_id");
    public static final PiActionParamId SESSION_ID_PARAM = PiActionParamId.of("fseid");
    //   far table
    public static final PiActionParamId TUNNEL_TYPE_PARAM = PiActionParamId.of("tunnel_type");
    public static final PiActionParamId TUNNEL_DPORT_PARAM = PiActionParamId.of("dport");
    public static final PiActionParamId TEID_PARAM = PiActionParamId.of("teid");
    public static final PiActionParamId TUNNEL_SRC_PARAM = PiActionParamId.of("src_addr");
    public static final PiActionParamId TUNNEL_DST_PARAM = PiActionParamId.of("dst_addr");
    public static final PiActionParamId DROP_FLAG = PiActionParamId.of("needs_dropping");
    public static final PiActionParamId NOTIFY_FLAG = PiActionParamId.of("notify_cp");

    // hide default constructor
    private NorthConstants() {
    }
}
