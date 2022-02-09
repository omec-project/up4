/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.collect.Range;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.behaviour.upf.UpfApplication;
import org.onosproject.net.behaviour.upf.UpfGtpTunnelPeer;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.behaviour.upf.UpfMeter;
import org.onosproject.net.behaviour.upf.UpfSessionDownlink;
import org.onosproject.net.behaviour.upf.UpfSessionUplink;
import org.onosproject.net.behaviour.upf.UpfTerminationDownlink;
import org.onosproject.net.behaviour.upf.UpfTerminationUplink;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiMatchKey;
import org.onosproject.net.pi.runtime.PiMeterBand;
import org.onosproject.net.pi.runtime.PiMeterBandType;
import org.onosproject.net.pi.runtime.PiMeterCellConfig;
import org.onosproject.net.pi.runtime.PiMeterCellId;
import org.onosproject.net.pi.runtime.PiRangeFieldMatch;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;

import java.util.Arrays;

import static org.omecproject.up4.impl.ExtraP4InfoConstants.DIRECTION_DOWNLINK;
import static org.omecproject.up4.impl.ExtraP4InfoConstants.DIRECTION_UPLINK;
import static org.omecproject.up4.impl.ExtraP4InfoConstants.IFACE_ACCESS;
import static org.omecproject.up4.impl.ExtraP4InfoConstants.IFACE_CORE;
import static org.omecproject.up4.impl.Up4P4InfoConstants.APP_METER_IDX;
import static org.omecproject.up4.impl.Up4P4InfoConstants.CTR_IDX;
import static org.omecproject.up4.impl.Up4P4InfoConstants.DIRECTION;
import static org.omecproject.up4.impl.Up4P4InfoConstants.DST_ADDR;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_APP_ID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_APP_IP_ADDR;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_APP_IP_PROTO;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_APP_L4_PORT;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_IPV4_DST_PREFIX;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_N3_ADDRESS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_TEID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_TUNNEL_PEER_ID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_UE_ADDRESS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_APPLICATIONS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_APP_METER;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_DOWNLINK_TERM_DROP;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_DOWNLINK_TERM_FWD;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_DOWNLINK_TERM_FWD_NO_TC;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_INTERFACES;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_LOAD_TUNNEL_PARAM;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SESSIONS_DOWNLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SESSIONS_UPLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SESSION_METER;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_APP_ID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SESSION_DOWNLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SESSION_DOWNLINK_BUFF;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SESSION_UPLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SOURCE_IFACE;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_TERMINATIONS_DOWNLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_TERMINATIONS_UPLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_TUNNEL_PEERS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_UPLINK_TERM_DROP;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_UPLINK_TERM_FWD;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_UPLINK_TERM_FWD_NO_TC;
import static org.omecproject.up4.impl.Up4P4InfoConstants.QFI;
import static org.omecproject.up4.impl.Up4P4InfoConstants.SESSION_METER_IDX;
import static org.omecproject.up4.impl.Up4P4InfoConstants.SLICE_ID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.SPORT;
import static org.omecproject.up4.impl.Up4P4InfoConstants.SRC_ADDR;
import static org.omecproject.up4.impl.Up4P4InfoConstants.SRC_IFACE;
import static org.omecproject.up4.impl.Up4P4InfoConstants.TC;
import static org.omecproject.up4.impl.Up4P4InfoConstants.TUNNEL_PEER_ID;

// TODO: add the drop and buffering sessions/terminations
public final class TestImplConstants {
    public static final DeviceId DEVICE_ID = DeviceId.deviceId("CoolSwitch91");
    public static final ApplicationId APP_ID = new DefaultApplicationId(5000, "up4");
    public static final int DEFAULT_PRIORITY = 10;
    public static final ImmutableByteSequence ALL_ONES_32 = ImmutableByteSequence.ofOnes(4);
    public static final int UPLINK_COUNTER_CELL_ID = 1;
    public static final int DOWNLINK_COUNTER_CELL_ID = 2;

    public static final byte GTP_TUNNEL_ID = 10;
    public static final byte TRAFFIC_CLASS_UL = 2;
    public static final byte TRAFFIC_CLASS_DL = 2;

    public static final byte DOWNLINK_QFI = 5;
    public static final byte QFI_ZERO = 0;

    public static final int TEID = 0xff;
    public static final Ip4Address UE_ADDR = Ip4Address.valueOf("17.0.0.1");
    public static final Ip4Address S1U_ADDR = Ip4Address.valueOf("192.168.0.1");
    public static final Ip4Address ENB_ADDR = Ip4Address.valueOf("192.168.0.2");
    public static final Ip4Prefix UE_POOL = Ip4Prefix.valueOf("17.0.0.0/16");
    // TODO: tunnel source port currently not stored on writes, so all reads are 0
    public static final short TUNNEL_SPORT = 2160;
    public static final int PHYSICAL_COUNTER_SIZE = 512;
    public static final int PHYSICAL_MAX_SESSIONS = 512;
    public static final int PHYSICAL_MAX_TERMINATIONS = 512;
    public static final int PHYSICAL_MAX_TUNNEL_PEERS = 256;
    public static final int PHYSICAL_MAX_INTERFACES = 256;
    public static final int PHYSICAL_APPLICATIONS_SIZE = 100;
    public static final int PHYSICAL_MAX_METERS = 256;

    public static final long COUNTER_BYTES = 12;
    public static final long COUNTER_PKTS = 15;

    public static final byte APP_FILTER_ID = 10;
    public static final byte DEFAULT_APP_ID = 0;
    public static final int APP_FILTER_PRIORITY = 10;
    public static final Ip4Prefix APP_IP_PREFIX = Ip4Prefix.valueOf("10.0.0.0/24");
    public static final Range<Short> APP_L4_RANGE = Range.closed((short) 100, (short) 1000);
    public static final byte APP_IP_PROTO = 6;

    public static final long CIR = 100000;
    public static final long CBURST = 1000;
    public static final long PIR = 50000;
    public static final long PBURST = 500;
    public static final int METER_ID = 300;
    public static final int DEFAULT_APP_METER_IDX = 0;
    public static final int DEFAULT_SESSION_METER_IDX = 0;

    public static final int MOBILE_SLICE = 0;

    public static final UpfGtpTunnelPeer TUNNEL_PEER = UpfGtpTunnelPeer.builder()
            .withTunnelPeerId(GTP_TUNNEL_ID)
            .withSrcAddr(S1U_ADDR)
            .withDstAddr(ENB_ADDR)
            .withSrcPort(TUNNEL_SPORT)
            .build();

    public static final UpfSessionUplink UPLINK_SESSION = UpfSessionUplink.builder()
            .withTeid(TEID)
            .withTunDstAddr(S1U_ADDR)
            .withSessionMeterIdx(METER_ID)
            .build();

    public static final UpfTerminationUplink UPLINK_TERMINATION = UpfTerminationUplink.builder()
            .withUeSessionId(UE_ADDR)
            .withApplicationId(APP_FILTER_ID)
            .withCounterId(UPLINK_COUNTER_CELL_ID)
            .withTrafficClass(TRAFFIC_CLASS_UL)
            .withAppMeterIdx(METER_ID)
            .build();

    public static final UpfTerminationUplink UPLINK_TERMINATION_NO_TC = UpfTerminationUplink.builder()
            .withUeSessionId(UE_ADDR)
            .withCounterId(UPLINK_COUNTER_CELL_ID)
            .build();

    public static final UpfTerminationUplink UPLINK_TERMINATION_DROP = UpfTerminationUplink.builder()
            .withUeSessionId(UE_ADDR)
            .withCounterId(UPLINK_COUNTER_CELL_ID)
            .needsDropping(true)
            .build();

    public static final UpfSessionDownlink DOWNLINK_SESSION = UpfSessionDownlink.builder()
            .withUeAddress(UE_ADDR)
            .withGtpTunnelPeerId(GTP_TUNNEL_ID)
            .withSessionMeterIdx(METER_ID)
            .build();

    public static final UpfSessionDownlink DOWNLINK_SESSION_DBUF = UpfSessionDownlink.builder()
            .withUeAddress(UE_ADDR)
            .needsBuffering(true)
            .withSessionMeterIdx(METER_ID)
            .build();

    public static final UpfTerminationDownlink DOWNLINK_TERMINATION = UpfTerminationDownlink.builder()
            .withUeSessionId(UE_ADDR)
            .withApplicationId(APP_FILTER_ID)
            .withTeid(TEID)
            .withQfi(DOWNLINK_QFI)
            .withCounterId(DOWNLINK_COUNTER_CELL_ID)
            .withTrafficClass(TRAFFIC_CLASS_DL)
            .withAppMeterIdx(METER_ID)
            .build();

    public static final UpfTerminationDownlink DOWNLINK_TERMINATION_NO_TC = UpfTerminationDownlink.builder()
            .withUeSessionId(UE_ADDR)
            .withTeid(TEID)
            .withQfi(DOWNLINK_QFI)
            .withCounterId(DOWNLINK_COUNTER_CELL_ID)
            .build();

    public static final UpfTerminationDownlink DOWNLINK_TERMINATION_DROP = UpfTerminationDownlink.builder()
            .withUeSessionId(UE_ADDR)
            .withCounterId(DOWNLINK_COUNTER_CELL_ID)
            .needsDropping(true)
            .build();

    public static final UpfInterface UPLINK_INTERFACE = UpfInterface.createS1uFrom(S1U_ADDR, MOBILE_SLICE);

    public static final UpfInterface DOWNLINK_INTERFACE = UpfInterface.createUePoolFrom(UE_POOL, MOBILE_SLICE);

    public static final UpfApplication APPLICATION_FILTERING = UpfApplication.builder()
            .withAppId(APP_FILTER_ID)
            .withIp4Prefix(APP_IP_PREFIX)
            .withL4PortRange(APP_L4_RANGE)
            .withIpProto(APP_IP_PROTO)
            .withPriority(APP_FILTER_PRIORITY)
            .withSliceId(MOBILE_SLICE)
            .build();

    public static final UpfMeter SESSION_METER = UpfMeter.builder()
            .setSession()
            .setCellId(METER_ID)
            .setPeakBand(PIR, PBURST)
            .build();

    public static final UpfMeter SESSION_METER_RESET = UpfMeter.builder()
            .setSession()
            .setCellId(METER_ID)
            .build();

    public static final UpfMeter APP_METER = UpfMeter.builder()
            .setApplication()
            .setCellId(METER_ID)
            .setCommittedBand(CIR, CBURST)
            .setPeakBand(PIR, PBURST)
            .build();

    public static final UpfMeter APP_METER_RESET = UpfMeter.builder()
            .setApplication()
            .setCellId(METER_ID)
            .build();

    public static final PiTableEntry UP4_TUNNEL_PEER = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_TUNNEL_PEERS)
            .withMatchKey(
                    PiMatchKey.builder()
                            .addFieldMatch(
                                    new PiExactFieldMatch(
                                            HDR_TUNNEL_PEER_ID, ImmutableByteSequence.copyFrom(GTP_TUNNEL_ID)))
                            .build())
            .withAction(
                    PiAction.builder()
                            .withId(PRE_QOS_PIPE_LOAD_TUNNEL_PARAM)
                            .withParameter(
                                    new PiActionParam(SRC_ADDR, ImmutableByteSequence.copyFrom(S1U_ADDR.toOctets())))
                            .withParameter(
                                    new PiActionParam(DST_ADDR, ImmutableByteSequence.copyFrom(ENB_ADDR.toOctets())))
                            .withParameter(
                                    new PiActionParam(SPORT, TUNNEL_SPORT))
                            .build())
            .build();

    public static final PiTableEntry UP4_UPLINK_SESSION = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_SESSIONS_UPLINK)
            .withMatchKey(
                    PiMatchKey.builder()
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_N3_ADDRESS, ImmutableByteSequence.copyFrom(S1U_ADDR.toOctets())))
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_TEID, ImmutableByteSequence.copyFrom(TEID)))
                            .build()
            )
            .withAction(
                    PiAction.builder()
                            .withId(PRE_QOS_PIPE_SET_SESSION_UPLINK)
                            .withParameter(new PiActionParam(SESSION_METER_IDX, METER_ID))
                            .build()
            )
            .build();

    public static final PiTableEntry UP4_DOWNLINK_SESSION = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_SESSIONS_DOWNLINK)
            .withMatchKey(
                    PiMatchKey.builder()
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_UE_ADDRESS, ImmutableByteSequence.copyFrom(UE_ADDR.toOctets())))
                            .build()
            )
            .withAction(
                    PiAction.builder()
                            .withId(PRE_QOS_PIPE_SET_SESSION_DOWNLINK)
                            .withParameter(new PiActionParam(TUNNEL_PEER_ID, GTP_TUNNEL_ID))
                            .withParameter(new PiActionParam(SESSION_METER_IDX, METER_ID))
                            .build()
            )
            .build();

    public static final PiTableEntry UP4_DOWNLINK_SESSION_DBUF = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_SESSIONS_DOWNLINK)
            .withMatchKey(
                    PiMatchKey.builder()
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_UE_ADDRESS, ImmutableByteSequence.copyFrom(UE_ADDR.toOctets())))
                            .build()
            )
            .withAction(
                    PiAction.builder()
                            .withId(PRE_QOS_PIPE_SET_SESSION_DOWNLINK_BUFF)
                            .withParameter(new PiActionParam(SESSION_METER_IDX, METER_ID))
                            .build()
            )
            .build();

    public static final PiTableEntry UP4_UPLINK_TERMINATION = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_TERMINATIONS_UPLINK)
            .withMatchKey(
                    PiMatchKey.builder()
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_UE_ADDRESS, ImmutableByteSequence.copyFrom(UE_ADDR.toOctets())))
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_APP_ID, ImmutableByteSequence.copyFrom(APP_FILTER_ID)
                            ))
                            .build()
            )
            .withAction(
                    PiAction.builder()
                            .withId(PRE_QOS_PIPE_UPLINK_TERM_FWD)
                            .withParameter(new PiActionParam(CTR_IDX, UPLINK_COUNTER_CELL_ID))
                            .withParameter(new PiActionParam(TC, TRAFFIC_CLASS_UL))
                            .withParameter(new PiActionParam(APP_METER_IDX, METER_ID))
                            .build()
            )
            .build();

    public static final PiTableEntry UP4_UPLINK_TERMINATION_NO_TC = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_TERMINATIONS_UPLINK)
            .withMatchKey(
                    PiMatchKey.builder()
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_UE_ADDRESS, ImmutableByteSequence.copyFrom(UE_ADDR.toOctets())))
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_APP_ID, ImmutableByteSequence.copyFrom(DEFAULT_APP_ID)
                            ))
                            .build()
            )
            .withAction(
                    PiAction.builder()
                            .withId(PRE_QOS_PIPE_UPLINK_TERM_FWD_NO_TC)
                            .withParameter(new PiActionParam(CTR_IDX, UPLINK_COUNTER_CELL_ID))
                            .withParameter(new PiActionParam(APP_METER_IDX, DEFAULT_APP_METER_IDX))
                            .build()
            )
            .build();

    public static final PiTableEntry UP4_UPLINK_TERMINATION_DROP = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_TERMINATIONS_UPLINK)
            .withMatchKey(
                    PiMatchKey.builder()
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_UE_ADDRESS, ImmutableByteSequence.copyFrom(UE_ADDR.toOctets())))
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_APP_ID, ImmutableByteSequence.copyFrom(DEFAULT_APP_ID)
                            ))
                            .build()
            )
            .withAction(
                    PiAction.builder()
                            .withId(PRE_QOS_PIPE_UPLINK_TERM_DROP)
                            .withParameter(new PiActionParam(CTR_IDX, UPLINK_COUNTER_CELL_ID))
                            .build()
            )
            .build();

    public static final PiTableEntry UP4_DOWNLINK_TERMINATION = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_TERMINATIONS_DOWNLINK)
            .withMatchKey(
                    PiMatchKey.builder()
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_UE_ADDRESS, ImmutableByteSequence.copyFrom(UE_ADDR.toOctets())))
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_APP_ID, ImmutableByteSequence.copyFrom(APP_FILTER_ID)
                            ))
                            .build()
            )
            .withAction(
                    PiAction.builder()
                            .withId(PRE_QOS_PIPE_DOWNLINK_TERM_FWD)
                            .withParameter(new PiActionParam(CTR_IDX, DOWNLINK_COUNTER_CELL_ID))
                            .withParameter(new PiActionParam(Up4P4InfoConstants.TEID, TEID))
                            .withParameter(new PiActionParam(QFI, DOWNLINK_QFI))
                            .withParameter(new PiActionParam(TC, TRAFFIC_CLASS_DL))
                            .withParameter(new PiActionParam(APP_METER_IDX, METER_ID))
                            .build()
            )
            .build();

    public static final PiTableEntry UP4_DOWNLINK_TERMINATION_NO_TC = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_TERMINATIONS_DOWNLINK)
            .withMatchKey(
                    PiMatchKey.builder()
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_UE_ADDRESS, ImmutableByteSequence.copyFrom(UE_ADDR.toOctets())))
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_APP_ID, ImmutableByteSequence.copyFrom(DEFAULT_APP_ID)
                            ))
                            .build()
            )
            .withAction(
                    PiAction.builder()
                            .withId(PRE_QOS_PIPE_DOWNLINK_TERM_FWD_NO_TC)
                            .withParameter(new PiActionParam(CTR_IDX, DOWNLINK_COUNTER_CELL_ID))
                            .withParameter(new PiActionParam(Up4P4InfoConstants.TEID, TEID))
                            .withParameter(new PiActionParam(QFI, DOWNLINK_QFI))
                            .withParameter(new PiActionParam(APP_METER_IDX, DEFAULT_APP_METER_IDX))
                            .build()
            )
            .build();

    public static final PiTableEntry UP4_DOWNLINK_TERMINATION_DROP = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_TERMINATIONS_DOWNLINK)
            .withMatchKey(
                    PiMatchKey.builder()
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_UE_ADDRESS, ImmutableByteSequence.copyFrom(UE_ADDR.toOctets())))
                            .addFieldMatch(new PiExactFieldMatch(
                                    HDR_APP_ID, ImmutableByteSequence.copyFrom(DEFAULT_APP_ID)
                            ))
                            .build()
            )
            .withAction(
                    PiAction.builder()
                            .withId(PRE_QOS_PIPE_DOWNLINK_TERM_DROP)
                            .withParameter(new PiActionParam(CTR_IDX, DOWNLINK_COUNTER_CELL_ID))
                            .build()
            )
            .build();


    public static final PiTableEntry UP4_UPLINK_INTERFACE = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_INTERFACES)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiLpmFieldMatch(
                                          HDR_IPV4_DST_PREFIX,
                                          ImmutableByteSequence.copyFrom(S1U_ADDR.toOctets()),
                                          32))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(PRE_QOS_PIPE_SET_SOURCE_IFACE)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(SRC_IFACE, IFACE_ACCESS),
                                        new PiActionParam(DIRECTION, DIRECTION_UPLINK),
                                        new PiActionParam(SLICE_ID, (byte) MOBILE_SLICE)
                                ))
                                .build()).build();

    public static final PiTableEntry UP4_DOWNLINK_INTERFACE = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_INTERFACES)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiLpmFieldMatch(
                                          HDR_IPV4_DST_PREFIX,
                                          ImmutableByteSequence.copyFrom(UE_POOL.address().toOctets()),
                                          UE_POOL.prefixLength()))
                                  .build())
            .withAction(PiAction.builder()
                                .withId(PRE_QOS_PIPE_SET_SOURCE_IFACE)
                                .withParameters(Arrays.asList(
                                        new PiActionParam(SRC_IFACE, IFACE_CORE),
                                        new PiActionParam(DIRECTION, DIRECTION_DOWNLINK),
                                        new PiActionParam(SLICE_ID, (byte) MOBILE_SLICE)
                                ))
                                .build()).build();

    public static final PiTableEntry UP4_APPLICATION_FILTERING = PiTableEntry.builder()
            .forTable(PRE_QOS_PIPE_APPLICATIONS)
            .withMatchKey(PiMatchKey.builder()
                                  .addFieldMatch(new PiLpmFieldMatch(
                                          HDR_APP_IP_ADDR,
                                          ImmutableByteSequence.copyFrom(APP_IP_PREFIX.address().toOctets()),
                                          APP_IP_PREFIX.prefixLength()))
                                  .addFieldMatch(new PiRangeFieldMatch(
                                          HDR_APP_L4_PORT,
                                          ImmutableByteSequence.copyFrom(APP_L4_RANGE.lowerEndpoint()),
                                          ImmutableByteSequence.copyFrom(APP_L4_RANGE.upperEndpoint())))
                                  .addFieldMatch(new PiTernaryFieldMatch(
                                          HDR_APP_IP_PROTO,
                                          ImmutableByteSequence.copyFrom(APP_IP_PROTO),
                                          ImmutableByteSequence.ofOnes(1)))
                                  .build()
            )
            .withAction(PiAction.builder()
                                .withId(PRE_QOS_PIPE_SET_APP_ID)
                                .withParameter(new PiActionParam(
                                        Up4P4InfoConstants.APP_ID,
                                        APP_FILTER_ID))
                                .build())
            .withPriority(APP_FILTER_PRIORITY)
            .build();

    public static final PiMeterCellConfig UP4_SESSION_METER = PiMeterCellConfig.builder()
            .withMeterBand(new PiMeterBand(PiMeterBandType.PEAK, PIR, PBURST))
            .withMeterBand(new PiMeterBand(PiMeterBandType.COMMITTED, 1, 1))
            .withMeterCellId(PiMeterCellId.ofIndirect(PRE_QOS_PIPE_SESSION_METER, METER_ID))
            .build();

    public static final PiMeterCellConfig UP4_SESSION_METER_RESET = PiMeterCellConfig
            .reset(PiMeterCellId.ofIndirect(PRE_QOS_PIPE_SESSION_METER, METER_ID));

    public static final PiMeterCellConfig UP4_APP_METER = PiMeterCellConfig.builder()
            .withMeterBand(new PiMeterBand(PiMeterBandType.PEAK, PIR, PBURST))
            .withMeterBand(new PiMeterBand(PiMeterBandType.COMMITTED, CIR, CBURST))
            .withMeterCellId(PiMeterCellId.ofIndirect(PRE_QOS_PIPE_APP_METER, METER_ID))
            .build();

    public static final PiMeterCellConfig UP4_APP_METER_RESET = PiMeterCellConfig
            .reset(PiMeterCellId.ofIndirect(PRE_QOS_PIPE_APP_METER, METER_ID));

    /**
     * Hidden constructor for utility class.
     */
    private TestImplConstants() {
    }
}
