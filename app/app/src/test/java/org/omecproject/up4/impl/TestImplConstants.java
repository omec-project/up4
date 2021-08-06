/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.behaviour.upf.ForwardingActionRule;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;
import org.onosproject.net.behaviour.upf.QosEnforcementRule;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiMatchKey;
import org.onosproject.net.pi.runtime.PiMeterBand;
import org.onosproject.net.pi.runtime.PiMeterCellConfig;
import org.onosproject.net.pi.runtime.PiMeterCellId;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;

import java.util.Arrays;

import static org.omecproject.up4.impl.Up4P4InfoConstants.QER_METER;

public final class TestImplConstants {
    public static final DeviceId DEVICE_ID = DeviceId.deviceId("CoolSwitch91");
    public static final ApplicationId APP_ID = new DefaultApplicationId(5000, "up4");
    public static final int DEFAULT_PRIORITY = 10;
    public static final ImmutableByteSequence ALL_ONES_32 = ImmutableByteSequence.ofOnes(4);
    public static final ImmutableByteSequence SESSION_ID =
            ImmutableByteSequence.ofOnes(Up4P4InfoConstants.SESSION_ID_BITWIDTH / 8);
    public static final int UPLINK_COUNTER_CELL_ID = 1;
    public static final int DOWNLINK_COUNTER_CELL_ID = 2;
    public static final int PDR_ID = 0;  // TODO: PDR ID currently not stored on writes, so all reads are 0
    public static final int UPLINK_FAR_ID = 1;
    public static final int UPLINK_PHYSICAL_FAR_ID = 4;
    public static final int DOWNLINK_FAR_ID = 2;
    public static final int DOWNLINK_PHYSICAL_FAR_ID = 5;

    public static final int QER_ID = 1;
    public static final int CIR_1 = 10;
    public static final int CBURST_1 = 10;
    public static final int PIR_1 = 20;
    public static final int PBURST_1 = 20;

    public static final int UPLINK_PRIORITY = 9;
    public static final int DOWNLINK_PRIORITY = 1;
    public static final int UPLINK_QID = 1;
    public static final int DOWNLINK_QID = 5;
    public static final int DEFAULT_SCHEDULING_PRIORITY = 0;

    public static final ImmutableByteSequence TEID = ImmutableByteSequence.copyFrom(0xff);
    public static final Ip4Address UE_ADDR = Ip4Address.valueOf("17.0.0.1");
    public static final Ip4Address S1U_ADDR = Ip4Address.valueOf("192.168.0.1");
    public static final Ip4Address ENB_ADDR = Ip4Address.valueOf("192.168.0.2");
    public static final Ip4Prefix UE_POOL = Ip4Prefix.valueOf("17.0.0.0/16");
    // TODO: tunnel source port currently not stored on writes, so all reads are 0
    public static final short TUNNEL_SPORT = 2160;
    public static final int PHYSICAL_COUNTER_SIZE = 512;
    public static final int PHYSICAL_MAX_PDRS = 512;
    public static final int PHYSICAL_MAX_FARS = 512;
    public static final int PHYSICAL_MAX_QERS = 512;

    public static final long COUNTER_BYTES = 12;
    public static final long COUNTER_PKTS = 15;


    public static final ImmutableByteSequence FALSE_BYTE = ImmutableByteSequence.copyFrom((byte) 0);
    public static final ImmutableByteSequence TRUE_BYTE = ImmutableByteSequence.copyFrom((byte) 1);

    public static final PacketDetectionRule UPLINK_PDR = PacketDetectionRule.builder()
            .withTunnelDst(S1U_ADDR)
            .withTeid(TEID)
            .withLocalFarId(UPLINK_FAR_ID)
            .withSessionId(SESSION_ID)
            .withQerId(QER_ID)
            .withCounterId(UPLINK_COUNTER_CELL_ID)
            .withSchedulingPriority(DEFAULT_SCHEDULING_PRIORITY)
            .build();

    public static final PacketDetectionRule DOWNLINK_PDR = PacketDetectionRule.builder()
            .withUeAddr(UE_ADDR)
            .withLocalFarId(DOWNLINK_FAR_ID)
            .withSessionId(SESSION_ID)
            .withQerId(QER_ID)
            .withCounterId(DOWNLINK_COUNTER_CELL_ID)
            .withSchedulingPriority(DEFAULT_SCHEDULING_PRIORITY)
            .build();

    public static final PacketDetectionRule UPLINK_PRIORITY_PDR = PacketDetectionRule.builder()
            .withTunnelDst(S1U_ADDR)
            .withTeid(TEID)
            .withLocalFarId(UPLINK_FAR_ID)
            .withSessionId(SESSION_ID)
            .withQerId(QER_ID)
            .withCounterId(UPLINK_COUNTER_CELL_ID)
            .withSchedulingPriority(UPLINK_PRIORITY)
            .build();

    public static final PacketDetectionRule DOWNLINK_PRIORITY_PDR = PacketDetectionRule.builder()
            .withUeAddr(UE_ADDR)
            .withLocalFarId(DOWNLINK_FAR_ID)
            .withSessionId(SESSION_ID)
            .withQerId(QER_ID)
            .withCounterId(DOWNLINK_COUNTER_CELL_ID)
            .withSchedulingPriority(DOWNLINK_PRIORITY)
            .build();

    public static final ForwardingActionRule UPLINK_FAR = ForwardingActionRule.builder()
            .setFarId(UPLINK_FAR_ID)
            .withSessionId(SESSION_ID).build();

    public static final ForwardingActionRule DOWNLINK_FAR = ForwardingActionRule.builder()
            .setFarId(DOWNLINK_FAR_ID)
            .withSessionId(SESSION_ID)
            .setTunnel(S1U_ADDR, ENB_ADDR, TEID, TUNNEL_SPORT)
            .build();

    public static final QosEnforcementRule QER_1 = QosEnforcementRule.builder()
            .withQerId(QER_ID)
            .withCir(CIR_1)
            .withCburst(CBURST_1)
            .withPir(PIR_1)
            .withPburst(PBURST_1)
            .build();

    public static final PiMeterCellConfig UP4_QER_1 = PiMeterCellConfig.builder()
            .withMeterCellId(PiMeterCellId.ofIndirect(QER_METER, QER_ID))
            .withMeterBand(new PiMeterBand(CIR_1, CBURST_1))
            .withMeterBand(new PiMeterBand(PIR_1, PBURST_1))
            .build();

    public static final UpfInterface UPLINK_INTERFACE = UpfInterface.createS1uFrom(S1U_ADDR);

    public static final UpfInterface DOWNLINK_INTERFACE = UpfInterface.createUePoolFrom(UE_POOL);

    public static final PiTableEntry UP4_UPLINK_PRIORITY_PDR = PiTableEntry.builder()
            .forTable(Up4P4InfoConstants.PDR_TBL)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiExactFieldMatch(
                                          Up4P4InfoConstants.SRC_IFACE_KEY,
                                          ImmutableByteSequence.copyFrom(Up4P4InfoConstants.IFACE_ACCESS)))
                                  .addFieldMatch(new PiTernaryFieldMatch(
                                          Up4P4InfoConstants.TEID_KEY, TEID, ALL_ONES_32))
                                  .addFieldMatch(new PiTernaryFieldMatch(
                                          Up4P4InfoConstants.TUNNEL_DST_KEY,
                                          ImmutableByteSequence.copyFrom(S1U_ADDR.toOctets()), ALL_ONES_32))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(Up4P4InfoConstants.LOAD_PDR_QOS)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(Up4P4InfoConstants.SESSION_ID_PARAM, SESSION_ID),
                                        new PiActionParam(Up4P4InfoConstants.CTR_ID, UPLINK_COUNTER_CELL_ID),
                                        new PiActionParam(Up4P4InfoConstants.FAR_ID_PARAM, UPLINK_FAR_ID),
                                        new PiActionParam(Up4P4InfoConstants.QER_ID_PARAM, QER_ID),
                                        new PiActionParam(Up4P4InfoConstants.DECAP_FLAG_PARAM, TRUE_BYTE),
                                        new PiActionParam(Up4P4InfoConstants.SCHEDULING_PRIORITY, UPLINK_PRIORITY)
                                ))
                                .build())
            .build();

    public static final PiTableEntry UP4_DOWNLINK_PRIORITY_PDR = PiTableEntry.builder()
            .forTable(Up4P4InfoConstants.PDR_TBL)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiExactFieldMatch(
                                          Up4P4InfoConstants.SRC_IFACE_KEY,
                                          ImmutableByteSequence.copyFrom((byte) Up4P4InfoConstants.IFACE_CORE)))
                                  .addFieldMatch(new PiTernaryFieldMatch(
                                          Up4P4InfoConstants.UE_ADDR_KEY,
                                          ImmutableByteSequence.copyFrom(UE_ADDR.toOctets()), ALL_ONES_32))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(Up4P4InfoConstants.LOAD_PDR_QOS)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(Up4P4InfoConstants.SESSION_ID_PARAM, SESSION_ID),
                                        new PiActionParam(Up4P4InfoConstants.CTR_ID, DOWNLINK_COUNTER_CELL_ID),
                                        new PiActionParam(Up4P4InfoConstants.FAR_ID_PARAM, DOWNLINK_FAR_ID),
                                        new PiActionParam(Up4P4InfoConstants.QER_ID_PARAM, QER_ID),
                                        new PiActionParam(Up4P4InfoConstants.DECAP_FLAG_PARAM, FALSE_BYTE),
                                        new PiActionParam(Up4P4InfoConstants.SCHEDULING_PRIORITY, DOWNLINK_PRIORITY)
                                ))
                                .build())
            .build();

    public static final PiTableEntry UP4_UPLINK_PDR = PiTableEntry.builder()
            .forTable(Up4P4InfoConstants.PDR_TBL)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiExactFieldMatch(
                                          Up4P4InfoConstants.SRC_IFACE_KEY,
                                          ImmutableByteSequence.copyFrom(Up4P4InfoConstants.IFACE_ACCESS)))
                                  .addFieldMatch(new PiTernaryFieldMatch(
                                          Up4P4InfoConstants.TEID_KEY, TEID, ALL_ONES_32))
                                  .addFieldMatch(new PiTernaryFieldMatch(
                                          Up4P4InfoConstants.TUNNEL_DST_KEY,
                                          ImmutableByteSequence.copyFrom(S1U_ADDR.toOctets()), ALL_ONES_32))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(Up4P4InfoConstants.LOAD_PDR)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(Up4P4InfoConstants.SESSION_ID_PARAM, SESSION_ID),
                                        new PiActionParam(Up4P4InfoConstants.CTR_ID, UPLINK_COUNTER_CELL_ID),
                                        new PiActionParam(Up4P4InfoConstants.FAR_ID_PARAM, UPLINK_FAR_ID),
                                        new PiActionParam(Up4P4InfoConstants.QER_ID_PARAM, QER_ID),
                                        new PiActionParam(Up4P4InfoConstants.DECAP_FLAG_PARAM, TRUE_BYTE)
                                ))
                                .build())
            .build();

    public static final PiTableEntry UP4_DOWNLINK_PDR = PiTableEntry.builder()
            .forTable(Up4P4InfoConstants.PDR_TBL)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiExactFieldMatch(
                                          Up4P4InfoConstants.SRC_IFACE_KEY,
                                          ImmutableByteSequence.copyFrom((byte) Up4P4InfoConstants.IFACE_CORE)))
                                  .addFieldMatch(new PiTernaryFieldMatch(
                                          Up4P4InfoConstants.UE_ADDR_KEY,
                                          ImmutableByteSequence.copyFrom(UE_ADDR.toOctets()), ALL_ONES_32))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(Up4P4InfoConstants.LOAD_PDR)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(Up4P4InfoConstants.SESSION_ID_PARAM, SESSION_ID),
                                        new PiActionParam(Up4P4InfoConstants.CTR_ID, DOWNLINK_COUNTER_CELL_ID),
                                        new PiActionParam(Up4P4InfoConstants.FAR_ID_PARAM, DOWNLINK_FAR_ID),
                                        new PiActionParam(Up4P4InfoConstants.QER_ID_PARAM, QER_ID),
                                        new PiActionParam(Up4P4InfoConstants.DECAP_FLAG_PARAM, FALSE_BYTE)
                                ))
                                .build())
            .build();

    public static final PiTableEntry UP4_UPLINK_FAR = PiTableEntry.builder()
            .forTable(Up4P4InfoConstants.FAR_TBL)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiExactFieldMatch(
                                          Up4P4InfoConstants.FAR_ID_KEY,
                                          ImmutableByteSequence.copyFrom(UPLINK_FAR_ID)))
                                  .addFieldMatch(new PiExactFieldMatch(
                                          Up4P4InfoConstants.SESSION_ID_KEY, SESSION_ID))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(Up4P4InfoConstants.LOAD_FAR_NORMAL)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(Up4P4InfoConstants.DROP_FLAG, FALSE_BYTE),
                                        new PiActionParam(Up4P4InfoConstants.NOTIFY_FLAG, FALSE_BYTE)
                                ))
                                .build())
            .build();

    public static final PiTableEntry UP4_DOWNLINK_FAR = PiTableEntry.builder()
            .forTable(Up4P4InfoConstants.FAR_TBL)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiExactFieldMatch(
                                          Up4P4InfoConstants.FAR_ID_KEY,
                                          ImmutableByteSequence.copyFrom(DOWNLINK_FAR_ID)))
                                  .addFieldMatch(new PiExactFieldMatch(
                                          Up4P4InfoConstants.SESSION_ID_KEY, SESSION_ID))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(Up4P4InfoConstants.LOAD_FAR_TUNNEL)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(Up4P4InfoConstants.DROP_FLAG, FALSE_BYTE),
                                        new PiActionParam(Up4P4InfoConstants.NOTIFY_FLAG, FALSE_BYTE),
                                        new PiActionParam(Up4P4InfoConstants.BUFFER_FLAG, FALSE_BYTE),
                                        new PiActionParam(Up4P4InfoConstants.TUNNEL_TYPE_PARAM,
                                                          Up4P4InfoConstants.TUNNEL_TYPE_GTPU),
                                        new PiActionParam(Up4P4InfoConstants.TUNNEL_SRC_PARAM, S1U_ADDR.toInt()),
                                        new PiActionParam(Up4P4InfoConstants.TUNNEL_DST_PARAM, ENB_ADDR.toInt()),
                                        new PiActionParam(Up4P4InfoConstants.TEID_PARAM, TEID),
                                        new PiActionParam(Up4P4InfoConstants.TUNNEL_SPORT_PARAM, TUNNEL_SPORT)))
                                .build())
            .build();

    public static final PiTableEntry INVALID_UP4_DOWNLINK_FAR = PiTableEntry.builder()
            .forTable(Up4P4InfoConstants.FAR_TBL)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiExactFieldMatch(
                                          Up4P4InfoConstants.FAR_ID_KEY,
                                          ImmutableByteSequence.copyFrom(DOWNLINK_FAR_ID)))
                                  .addFieldMatch(new PiExactFieldMatch(Up4P4InfoConstants.SESSION_ID_KEY, SESSION_ID))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(Up4P4InfoConstants.LOAD_FAR_NORMAL)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(Up4P4InfoConstants.DROP_FLAG, FALSE_BYTE),
                                        new PiActionParam(Up4P4InfoConstants.NOTIFY_FLAG, TRUE_BYTE)))
                                .build())
            .build();

    public static final PiTableEntry UP4_UPLINK_INTERFACE = PiTableEntry.builder()
            .forTable(Up4P4InfoConstants.IFACE_TBL)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiLpmFieldMatch(
                                          Up4P4InfoConstants.IFACE_DST_PREFIX_KEY,
                                          ImmutableByteSequence.copyFrom(S1U_ADDR.toOctets()),
                                          32))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(Up4P4InfoConstants.LOAD_IFACE)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(
                                                Up4P4InfoConstants.SRC_IFACE_PARAM,
                                                Up4P4InfoConstants.IFACE_ACCESS),
                                        new PiActionParam(
                                                Up4P4InfoConstants.DIRECTION,
                                                Up4P4InfoConstants.DIRECTION_UPLINK)
                                ))
                                .build()).build();

    public static final PiTableEntry UP4_DOWNLINK_INTERFACE = PiTableEntry.builder()
            .forTable(Up4P4InfoConstants.IFACE_TBL)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiLpmFieldMatch(
                                          Up4P4InfoConstants.IFACE_DST_PREFIX_KEY,
                                          ImmutableByteSequence.copyFrom(UE_POOL.address().toOctets()),
                                          UE_POOL.prefixLength()))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(Up4P4InfoConstants.LOAD_IFACE)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(
                                                Up4P4InfoConstants.SRC_IFACE_PARAM,
                                                Up4P4InfoConstants.IFACE_CORE),
                                        new PiActionParam(
                                                Up4P4InfoConstants.DIRECTION,
                                                Up4P4InfoConstants.DIRECTION_DOWNLINK)
                                ))
                                .build()).build();

    /**
     * Hidden constructor for utility class.
     */
    private TestImplConstants() {
    }

    private static ImmutableByteSequence toSessionId(long value) {
        try {
            return ImmutableByteSequence.copyFrom(value).fit(Up4P4InfoConstants.SESSION_ID_BITWIDTH);
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            return ImmutableByteSequence.ofZeros(Up4P4InfoConstants.SESSION_ID_BITWIDTH / 8);
        }
    }
}
