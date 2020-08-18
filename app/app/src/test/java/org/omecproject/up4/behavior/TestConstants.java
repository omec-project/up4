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
    public static final int COUNTER_ID = 1;
    public static final int PDR_ID = 1;
    public static final int FAR_ID = 1;
    public static final int PHYSICAL_FAR_ID = 1;
    public static final ImmutableByteSequence TEID = ImmutableByteSequence.copyFrom(0xff);
    public static final Ip4Address UE_ADDR = Ip4Address.valueOf("17.0.0.1");
    public static final Ip4Address S1U_ADDR = Ip4Address.valueOf("192.168.0.1");
    public static final Ip4Address ENB_ADDR = Ip4Address.valueOf("192.168.0.2");
    public static final Ip4Prefix S1U_IFACE = Ip4Prefix.valueOf(S1U_ADDR, 24);
    public static final Ip4Prefix UE_POOL = Ip4Prefix.valueOf("17.0.0.0/16");
    public static final ImmutableByteSequence TUNNEL_DPORT = ImmutableByteSequence.copyFrom((short) 1024);

    public static final ImmutableByteSequence FALSE_BYTE = ImmutableByteSequence.copyFrom((byte) 0);
    public static final ImmutableByteSequence TRUE_BYTE = ImmutableByteSequence.copyFrom((byte) 1);

    public static PacketDetectionRule getUplinkPdr() {
        return PacketDetectionRule.builder()
                .withTunnelDst(S1U_ADDR)
                .withTeid(TEID)
                .withLocalFarId(FAR_ID)
                .withGlobalFarId(PHYSICAL_FAR_ID)
                .withSessionId(SESSION_ID)
                .withCounterId(COUNTER_ID)
                .build();
    }

    public static PacketDetectionRule getDownlinkPdr() {
        return PacketDetectionRule.builder()
                .withUeAddr(UE_ADDR)
                .withLocalFarId(FAR_ID)
                .withGlobalFarId(PHYSICAL_FAR_ID)
                .withSessionId(SESSION_ID)
                .withCounterId(COUNTER_ID)
                .build();
    }

    public static ForwardingActionRule getUplinkFar() {
        return ForwardingActionRule.builder()
                .withFarId(FAR_ID)
                .withGlobalFarId(PHYSICAL_FAR_ID)
                .withFlags(false, false)
                .withSessionId(SESSION_ID).build();
    }

    public static ForwardingActionRule getDownlinkFar() {
        return ForwardingActionRule.builder()
                .withFarId(FAR_ID)
                .withGlobalFarId(PHYSICAL_FAR_ID)
                .withFlags(false, false)
                .withSessionId(SESSION_ID)
                .withTunnel(S1U_ADDR, ENB_ADDR, TEID)
                .build();
    }

    public static UpfInterface getUplinkInterface() {
        return UpfInterface.createS1uFrom(S1U_IFACE);
    }

    public static UpfInterface getDownlinkInterface() {
        return UpfInterface.createUePoolFrom(UE_POOL);
    }


    public static PiTableEntry getUp4UplinkPdr() {
        return PiTableEntry.builder()
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
                                new PiActionParam(NorthConstants.CTR_ID, COUNTER_ID),
                                new PiActionParam(NorthConstants.FAR_ID_PARAM, FAR_ID),
                                new PiActionParam(NorthConstants.DECAP_FLAG_PARAM, TRUE_BYTE)
                        ))
                        .build())
                .build();
    }

    public static PiTableEntry getUp4DownlinkPdr() {
        return PiTableEntry.builder()
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
                                new PiActionParam(NorthConstants.CTR_ID, COUNTER_ID),
                                new PiActionParam(NorthConstants.FAR_ID_PARAM, FAR_ID),
                                new PiActionParam(NorthConstants.DECAP_FLAG_PARAM, FALSE_BYTE)
                        ))
                        .build())
                .build();
    }


    public static PiTableEntry getUp4UplinkFar() {
        return PiTableEntry.builder()
                .forTable(NorthConstants.FAR_TBL)
                .withMatchKey(PiMatchKey.builder()
                        .addFieldMatch(new PiExactFieldMatch(NorthConstants.FAR_ID_KEY,
                                ImmutableByteSequence.copyFrom(FAR_ID)))
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
    }

    public static PiTableEntry getUp4DownlinkFar() {
        return PiTableEntry.builder()
                .forTable(NorthConstants.FAR_TBL)
                .withMatchKey(PiMatchKey.builder()
                        .addFieldMatch(new PiExactFieldMatch(NorthConstants.FAR_ID_KEY,
                                ImmutableByteSequence.copyFrom(FAR_ID)))
                        .addFieldMatch(new PiExactFieldMatch(NorthConstants.SESSION_ID_KEY, SESSION_ID))
                        .build())
                .withAction(PiAction.builder()
                        .withId(NorthConstants.LOAD_FAR_TUNNEL)
                        .withParameters(Arrays.asList(
                                new PiActionParam(NorthConstants.DROP_FLAG, FALSE_BYTE),
                                new PiActionParam(NorthConstants.NOTIFY_FLAG, FALSE_BYTE),
                                new PiActionParam(NorthConstants.TUNNEL_TYPE_PARAM, NorthConstants.TUNNEL_TYPE_GTPU),
                                new PiActionParam(NorthConstants.TUNNEL_SRC_PARAM, S1U_ADDR.toInt()),
                                new PiActionParam(NorthConstants.TUNNEL_DST_PARAM, ENB_ADDR.toInt()),
                                new PiActionParam(NorthConstants.TEID_PARAM, TEID),
                                new PiActionParam(NorthConstants.TUNNEL_DPORT_PARAM, TUNNEL_DPORT)))
                        .build())
                .build();
    }

    public static PiTableEntry getUp4UplinkInterface() {
        return PiTableEntry.builder()
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
    }

    public static PiTableEntry getUp4DownlinkInterface() {
        return PiTableEntry.builder()
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
    }

    public static FlowRule getFabricUplinkPdr() {
        return DefaultFlowRule.builder()
                .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
                .forTable(SouthConstants.PDR_UPLINK_TBL)
                .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                        .matchExact(SouthConstants.TEID_KEY, TEID.asArray())
                        .matchExact(SouthConstants.TUNNEL_DST_KEY, S1U_ADDR.toInt())
                        .build()).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                        .withId(SouthConstants.LOAD_PDR)
                        .withParameters(Arrays.asList(
                                new PiActionParam(SouthConstants.CTR_ID, COUNTER_ID),
                                new PiActionParam(SouthConstants.FAR_ID_PARAM, PHYSICAL_FAR_ID),
                                new PiActionParam(SouthConstants.NEEDS_GTPU_DECAP_PARAM, 1)
                        ))
                        .build()).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
    }

    public static FlowRule getFabricDownlinkPdr() {
        return DefaultFlowRule.builder()
                .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
                .forTable(SouthConstants.PDR_DOWNLINK_TBL)
                .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                        .matchExact(SouthConstants.UE_ADDR_KEY, UE_ADDR.toInt())
                        .build()).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                        .withId(SouthConstants.LOAD_PDR)
                        .withParameters(Arrays.asList(
                                new PiActionParam(SouthConstants.CTR_ID, COUNTER_ID),
                                new PiActionParam(SouthConstants.FAR_ID_PARAM, PHYSICAL_FAR_ID),
                                new PiActionParam(SouthConstants.NEEDS_GTPU_DECAP_PARAM, 0)
                        ))
                        .build()).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
    }

    public static FlowRule getFabricUplinkFar() {
        return DefaultFlowRule.builder()
                .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
                .forTable(SouthConstants.FAR_TBL)
                .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                        .matchExact(SouthConstants.FAR_ID_KEY, PHYSICAL_FAR_ID)
                        .build()).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                        .withId(SouthConstants.LOAD_FAR_NORMAL)
                        .withParameters(Arrays.asList(
                                new PiActionParam(SouthConstants.DROP_FLAG, 0),
                                new PiActionParam(SouthConstants.NOTIFY_FLAG, 0)
                        ))
                        .build()).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
    }

    public static FlowRule getFabricDownlinkFar() {
        return DefaultFlowRule.builder()
                .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
                .forTable(SouthConstants.FAR_TBL)
                .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                        .matchExact(SouthConstants.FAR_ID_KEY, PHYSICAL_FAR_ID)
                        .build()).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                        .withId(SouthConstants.LOAD_FAR_TUNNEL)
                        .withParameters(Arrays.asList(
                                new PiActionParam(SouthConstants.DROP_FLAG, 0),
                                new PiActionParam(SouthConstants.NOTIFY_FLAG, 0),
                                new PiActionParam(SouthConstants.TEID_PARAM, TEID),
                                new PiActionParam(SouthConstants.TUNNEL_SRC_PARAM, S1U_ADDR.toInt()),
                                new PiActionParam(SouthConstants.TUNNEL_DST_PARAM, ENB_ADDR.toInt()),
                                new PiActionParam(SouthConstants.TUNNEL_DST_PORT_PARAM, TUNNEL_DPORT)
                        ))
                        .build()).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
    }

    public static FlowRule getFabricUplinkInterface() {
        return DefaultFlowRule.builder()
                .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
                .forTable(SouthConstants.INTERFACE_LOOKUP)
                .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                        .matchLpm(SouthConstants.IPV4_DST_ADDR,
                                S1U_IFACE.address().toInt(),
                                S1U_IFACE.prefixLength())
                        .matchExact(SouthConstants.GTPU_IS_VALID, 1)
                        .build()).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                        .withId(SouthConstants.SET_SOURCE_IFACE)
                        .withParameters(Arrays.asList(
                                new PiActionParam(SouthConstants.SRC_IFACE_PARAM, SouthConstants.INTERFACE_ACCESS),
                                new PiActionParam(SouthConstants.DIRECTION_PARAM, SouthConstants.DIRECTION_UPLINK),
                                new PiActionParam(SouthConstants.SKIP_SPGW_PARAM, 0)
                        ))
                        .build()).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
    }

    public static FlowRule getFabricDownlinkInterface() {
        return DefaultFlowRule.builder()
                .forDevice(DEVICE_ID).fromApp(APP_ID).makePermanent()
                .forTable(SouthConstants.INTERFACE_LOOKUP)
                .withSelector(DefaultTrafficSelector.builder().matchPi(PiCriterion.builder()
                        .matchLpm(SouthConstants.IPV4_DST_ADDR,
                                UE_POOL.address().toInt(),
                                UE_POOL.prefixLength())
                        .matchExact(SouthConstants.GTPU_IS_VALID, 0)
                        .build()).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(PiAction.builder()
                        .withId(SouthConstants.SET_SOURCE_IFACE)
                        .withParameters(Arrays.asList(
                                new PiActionParam(SouthConstants.SRC_IFACE_PARAM, SouthConstants.INTERFACE_CORE),
                                new PiActionParam(SouthConstants.DIRECTION_PARAM, SouthConstants.DIRECTION_DOWNLINK),
                                new PiActionParam(SouthConstants.SKIP_SPGW_PARAM, 0)
                        ))
                        .build()).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
    }


    private static ImmutableByteSequence toSessionId(long value) {
        try {
            return ImmutableByteSequence.copyFrom(value).fit(NorthConstants.SESSION_ID_BITWIDTH);
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            return ImmutableByteSequence.ofZeros(NorthConstants.SESSION_ID_BITWIDTH / 8);
        }
    }

    /**
     * Hidden constructor for utility class.
     */
    private TestConstants() {
    }
}