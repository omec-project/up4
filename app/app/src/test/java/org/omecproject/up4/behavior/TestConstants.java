/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.behavior;

import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.impl.NorthConstants;
import org.omecproject.up4.impl.SouthConstants;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiMatchKey;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;

import java.util.Arrays;

public final class TestConstants {
    public static final DeviceId DEVICE_ID = DeviceId.deviceId("CoolSwitch91");
    public static final ApplicationId APP_ID = new DefaultApplicationId(5000, "up4");
    public static final int DEFAULT_PRIORITY = 10;
    public static final ImmutableByteSequence ALL_ONES_32 = ImmutableByteSequence.ofOnes(4);
    public static final ImmutableByteSequence SESSION_ID =
            ImmutableByteSequence.ofOnes(NorthConstants.SESSION_ID_BITWIDTH / 8);
    public static final int UPLINK_COUNTER_CELL_ID = 1;
    public static final int DOWNLINK_COUNTER_CELL_ID = 2;
    public static final int PDR_ID = 0;  // TODO: PDR ID currently not stored on writes, so all reads are 0
    public static final int UPLINK_FAR_ID = 1;
    public static final int UPLINK_PHYSICAL_FAR_ID = 4;
    public static final int DOWNLINK_FAR_ID = 2;
    public static final int DOWNLINK_PHYSICAL_FAR_ID = 5;
    public static final ImmutableByteSequence TEID = ImmutableByteSequence.copyFrom(0xff);
    public static final Ip4Address UE_ADDR = Ip4Address.valueOf("17.0.0.1");
    public static final Ip4Address S1U_ADDR = Ip4Address.valueOf("192.168.0.1");
    public static final Ip4Address ENB_ADDR = Ip4Address.valueOf("192.168.0.2");
    public static final Ip4Prefix S1U_IFACE = Ip4Prefix.valueOf(S1U_ADDR, 24);
    public static final Ip4Prefix UE_POOL = Ip4Prefix.valueOf("17.0.0.0/16");
    // TODO: tunnel source port currently not stored on writes, so all reads are 0
    public static final short TUNNEL_SPORT = 2160;
    public static final int PHYSICAL_COUNTER_SIZE = 512;

    public static final long COUNTER_BYTES = 12;
    public static final long COUNTER_PKTS = 15;


    public static final ImmutableByteSequence FALSE_BYTE = ImmutableByteSequence.copyFrom((byte) 0);
    public static final ImmutableByteSequence TRUE_BYTE = ImmutableByteSequence.copyFrom((byte) 1);

    public static final PacketDetectionRule UPLINK_PDR = PacketDetectionRule.builder()
            .withTunnelDst(S1U_ADDR)
            .withTeid(TEID)
            .withLocalFarId(UPLINK_FAR_ID)
            .withSessionId(SESSION_ID)
            .withCounterId(UPLINK_COUNTER_CELL_ID)
            .build();

    public static final PacketDetectionRule DOWNLINK_PDR = PacketDetectionRule.builder()
            .withUeAddr(UE_ADDR)
            .withLocalFarId(DOWNLINK_FAR_ID)
            .withSessionId(SESSION_ID)
            .withCounterId(DOWNLINK_COUNTER_CELL_ID)
            .build();

    public static final ForwardingActionRule UPLINK_FAR = ForwardingActionRule.builder()
            .withFarId(UPLINK_FAR_ID)
            .withFlags(false, false)
            .withSessionId(SESSION_ID).build();

    public static final ForwardingActionRule DOWNLINK_FAR = ForwardingActionRule.builder()
            .withFarId(DOWNLINK_FAR_ID)
            .withFlags(false, false)
            .withBufferFlag(false)
            .withSessionId(SESSION_ID)
            .withTunnel(S1U_ADDR, ENB_ADDR, TEID, TUNNEL_SPORT)
            .build();

    public static final UpfInterface UPLINK_INTERFACE = UpfInterface.createS1uFrom(S1U_IFACE);

    public static final UpfInterface DOWNLINK_INTERFACE = UpfInterface.createUePoolFrom(UE_POOL);

    public static final PiTableEntry UP4_UPLINK_PDR = PiTableEntry.builder()
            .forTable(NorthConstants.PDR_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SRC_IFACE_KEY,
                            ImmutableByteSequence.copyFrom(NorthConstants.IFACE_ACCESS)))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.TEID_KEY, TEID, ALL_ONES_32))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.TUNNEL_DST_KEY,
                            ImmutableByteSequence.copyFrom(S1U_ADDR.toOctets()), ALL_ONES_32))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_PDR)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.PDR_ID_PARAM, PDR_ID),
                            new PiActionParam(NorthConstants.SESSION_ID_PARAM, SESSION_ID),
                            new PiActionParam(NorthConstants.CTR_ID, UPLINK_COUNTER_CELL_ID),
                            new PiActionParam(NorthConstants.FAR_ID_PARAM, UPLINK_FAR_ID),
                            new PiActionParam(NorthConstants.DECAP_FLAG_PARAM, TRUE_BYTE)
                    ))
                    .build())
            .build();

    public static final PiTableEntry UP4_DOWNLINK_PDR = PiTableEntry.builder()
            .forTable(NorthConstants.PDR_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SRC_IFACE_KEY,
                            ImmutableByteSequence.copyFrom((byte) NorthConstants.IFACE_CORE)))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.UE_ADDR_KEY,
                            ImmutableByteSequence.copyFrom(UE_ADDR.toOctets()), ALL_ONES_32))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_PDR)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.PDR_ID_PARAM, PDR_ID),
                            new PiActionParam(NorthConstants.SESSION_ID_PARAM, SESSION_ID),
                            new PiActionParam(NorthConstants.CTR_ID, DOWNLINK_COUNTER_CELL_ID),
                            new PiActionParam(NorthConstants.FAR_ID_PARAM, DOWNLINK_FAR_ID),
                            new PiActionParam(NorthConstants.DECAP_FLAG_PARAM, FALSE_BYTE)
                    ))
                    .build())
            .build();

    public static final PiTableEntry UP4_UPLINK_FAR = PiTableEntry.builder()
            .forTable(NorthConstants.FAR_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.FAR_ID_KEY,
                            ImmutableByteSequence.copyFrom(UPLINK_FAR_ID)))
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SESSION_ID_KEY, SESSION_ID))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_FAR_NORMAL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.DROP_FLAG, FALSE_BYTE),
                            new PiActionParam(NorthConstants.NOTIFY_FLAG, FALSE_BYTE)
                    ))
                    .build())
            .build();

    public static final PiTableEntry UP4_DOWNLINK_FAR = PiTableEntry.builder()
            .forTable(NorthConstants.FAR_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.FAR_ID_KEY,
                            ImmutableByteSequence.copyFrom(DOWNLINK_FAR_ID)))
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SESSION_ID_KEY, SESSION_ID))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_FAR_TUNNEL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.DROP_FLAG, FALSE_BYTE),
                            new PiActionParam(NorthConstants.NOTIFY_FLAG, FALSE_BYTE),
                            new PiActionParam(NorthConstants.BUFFER_FLAG, FALSE_BYTE),
                            new PiActionParam(NorthConstants.TUNNEL_TYPE_PARAM, NorthConstants.TUNNEL_TYPE_GTPU),
                            new PiActionParam(NorthConstants.TUNNEL_SRC_PARAM, S1U_ADDR.toInt()),
                            new PiActionParam(NorthConstants.TUNNEL_DST_PARAM, ENB_ADDR.toInt()),
                            new PiActionParam(NorthConstants.TEID_PARAM, TEID),
                            new PiActionParam(NorthConstants.TUNNEL_SPORT_PARAM, TUNNEL_SPORT)))
                    .build())
            .build();

    public static final PiTableEntry UP4_UPLINK_INTERFACE = PiTableEntry.builder()
            .forTable(NorthConstants.IFACE_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiLpmFieldMatch(
                            NorthConstants.IFACE_DST_PREFIX_KEY,
                            ImmutableByteSequence.copyFrom(S1U_IFACE.address().toOctets()),
                            S1U_IFACE.prefixLength()))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_IFACE)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.SRC_IFACE_PARAM, NorthConstants.IFACE_ACCESS),
                            new PiActionParam(NorthConstants.DIRECTION, NorthConstants.DIRECTION_UPLINK)
                    ))
                    .build()).build();

    public static final PiTableEntry UP4_DOWNLINK_INTERFACE = PiTableEntry.builder()
            .forTable(NorthConstants.IFACE_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiLpmFieldMatch(
                            NorthConstants.IFACE_DST_PREFIX_KEY,
                            ImmutableByteSequence.copyFrom(UE_POOL.address().toOctets()),
                            UE_POOL.prefixLength()))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_IFACE)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.SRC_IFACE_PARAM, NorthConstants.IFACE_CORE),
                            new PiActionParam(NorthConstants.DIRECTION, NorthConstants.DIRECTION_DOWNLINK)
                    ))
                    .build()).build();

    public static final FlowRule FABRIC_UPLINK_PDR = DefaultFlowRule.builder()
            .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
            .forTable(SouthConstants.FABRIC_INGRESS_SPGW_UPLINK_PDRS)
            .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                    .matchExact(SouthConstants.HDR_TEID, TEID.asArray())
                    .matchExact(SouthConstants.HDR_TUNNEL_IPV4_DST, S1U_ADDR.toInt())
                    .build()).build())
            .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                    .withId(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_PDR)
                    .withParameters(Arrays.asList(
                            new PiActionParam(SouthConstants.CTR_ID, UPLINK_COUNTER_CELL_ID),
                            new PiActionParam(SouthConstants.FAR_ID, UPLINK_PHYSICAL_FAR_ID),
                            new PiActionParam(SouthConstants.NEEDS_GTPU_DECAP, 1)
                    ))
                    .build()).build())
            .withPriority(DEFAULT_PRIORITY)
            .build();

    public static final FlowRule FABRIC_DOWNLINK_PDR = DefaultFlowRule.builder()
            .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
            .forTable(SouthConstants.FABRIC_INGRESS_SPGW_DOWNLINK_PDRS)
            .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                    .matchExact(SouthConstants.HDR_UE_ADDR, UE_ADDR.toInt())
                    .build()).build())
            .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                    .withId(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_PDR)
                    .withParameters(Arrays.asList(
                            new PiActionParam(SouthConstants.CTR_ID, DOWNLINK_COUNTER_CELL_ID),
                            new PiActionParam(SouthConstants.FAR_ID, DOWNLINK_PHYSICAL_FAR_ID),
                            new PiActionParam(SouthConstants.NEEDS_GTPU_DECAP, 0)
                    ))
                    .build()).build())
            .withPriority(DEFAULT_PRIORITY)
            .build();

    public static final FlowRule FABRIC_UPLINK_FAR = DefaultFlowRule.builder()
            .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
            .forTable(SouthConstants.FABRIC_INGRESS_SPGW_FARS)
            .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                    .matchExact(SouthConstants.HDR_FAR_ID, UPLINK_PHYSICAL_FAR_ID)
                    .build()).build())
            .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                    .withId(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_NORMAL_FAR)
                    .withParameters(Arrays.asList(
                            new PiActionParam(SouthConstants.DROP, 0),
                            new PiActionParam(SouthConstants.NOTIFY_CP, 0)
                    ))
                    .build()).build())
            .withPriority(DEFAULT_PRIORITY)
            .build();

    public static final FlowRule FABRIC_DOWNLINK_FAR = DefaultFlowRule.builder()
            .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
            .forTable(SouthConstants.FABRIC_INGRESS_SPGW_FARS)
            .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                    .matchExact(SouthConstants.HDR_FAR_ID, DOWNLINK_PHYSICAL_FAR_ID)
                    .build()).build())
            .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                    .withId(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_TUNNEL_FAR)
                    .withParameters(Arrays.asList(
                            new PiActionParam(SouthConstants.DROP, 0),
                            new PiActionParam(SouthConstants.NOTIFY_CP, 0),
                            new PiActionParam(SouthConstants.TEID, TEID),
                            new PiActionParam(SouthConstants.TUNNEL_SRC_ADDR, S1U_ADDR.toInt()),
                            new PiActionParam(SouthConstants.TUNNEL_DST_ADDR, ENB_ADDR.toInt()),
                            new PiActionParam(SouthConstants.TUNNEL_SRC_PORT, TUNNEL_SPORT)
                    ))
                    .build()).build())
            .withPriority(DEFAULT_PRIORITY)
            .build();

    public static final FlowRule FABRIC_UPLINK_INTERFACE = DefaultFlowRule.builder()
            .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
            .forTable(SouthConstants.FABRIC_INGRESS_SPGW_INTERFACES)
            .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                    .matchLpm(SouthConstants.HDR_IPV4_DST_ADDR,
                            S1U_IFACE.address().toInt(),
                            S1U_IFACE.prefixLength())
                    .matchExact(SouthConstants.HDR_GTPU_IS_VALID, 1)
                    .build()).build())
            .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                    .withId(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_IFACE)
                    .withParameter(new PiActionParam(SouthConstants.SRC_IFACE, SouthConstants.INTERFACE_ACCESS))
                    .build()).build())
            .withPriority(DEFAULT_PRIORITY)
            .build();

    public static final FlowRule FABRIC_DOWNLINK_INTERFACE = DefaultFlowRule.builder()
            .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
            .forTable(SouthConstants.FABRIC_INGRESS_SPGW_INTERFACES)
            .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                    .matchLpm(SouthConstants.HDR_IPV4_DST_ADDR,
                            UE_POOL.address().toInt(),
                            UE_POOL.prefixLength())
                    .matchExact(SouthConstants.HDR_GTPU_IS_VALID, 0)
                    .build()).build())
            .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                    .withId(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_IFACE)
                    .withParameter(new PiActionParam(SouthConstants.SRC_IFACE, SouthConstants.INTERFACE_CORE))
                    .build()).build())
            .withPriority(DEFAULT_PRIORITY)
            .build();

    /**
     * Hidden constructor for utility class.
     */
    private TestConstants() {
    }

    private static ImmutableByteSequence toSessionId(long value) {
        try {
            return ImmutableByteSequence.copyFrom(value).fit(NorthConstants.SESSION_ID_BITWIDTH);
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            return ImmutableByteSequence.ofZeros(NorthConstants.SESSION_ID_BITWIDTH / 8);
        }
    }
}
