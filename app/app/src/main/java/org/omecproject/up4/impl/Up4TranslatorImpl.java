/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.apache.commons.lang3.tuple.Pair;
import org.omecproject.up4.Up4Translator;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.behaviour.upf.Application;
import org.onosproject.net.behaviour.upf.GtpTunnelPeer;
import org.onosproject.net.behaviour.upf.SessionDownlink;
import org.onosproject.net.behaviour.upf.SessionUplink;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.behaviour.upf.UpfTerminationDownlink;
import org.onosproject.net.behaviour.upf.UpfTerminationUplink;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiEntity;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiMatchKey;
import org.onosproject.net.pi.runtime.PiRangeFieldMatch;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.omecproject.up4.impl.ExtraP4InfoConstants.DIRECTION_DOWNLINK;
import static org.omecproject.up4.impl.ExtraP4InfoConstants.DIRECTION_UPLINK;
import static org.omecproject.up4.impl.ExtraP4InfoConstants.IFACE_ACCESS;
import static org.omecproject.up4.impl.ExtraP4InfoConstants.IFACE_CORE;
import static org.omecproject.up4.impl.Up4DeviceManager.SLICE_MOBILE;
import static org.omecproject.up4.impl.Up4P4InfoConstants.APP_ID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.CTR_IDX;
import static org.omecproject.up4.impl.Up4P4InfoConstants.DIRECTION;
import static org.omecproject.up4.impl.Up4P4InfoConstants.DST_ADDR;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_APP_IP_ADDRESS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_APP_IP_PROTO;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_APP_L4_PORT;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_IPV4_DST_PREFIX;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_N3_ADDRESS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_SLICE_ID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_TEID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_TUNNEL_PEER_ID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_UE_ADDRESS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.POST_QOS_PIPE_POST_QOS_COUNTER;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_APPLICATIONS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_DOWNLINK_TERM_DROP;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_DOWNLINK_TERM_FWD;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_INTERFACES;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_LOAD_TUNNEL_PARAM;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_PRE_QOS_COUNTER;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SESSIONS_DOWNLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SESSIONS_UPLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_APP_ID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SESSION_DOWNLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SESSION_DOWNLINK_BUFF;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SESSION_DOWNLINK_DROP;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SESSION_UPLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SESSION_UPLINK_DROP;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SOURCE_IFACE;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_TERMINATIONS_DOWNLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_TERMINATIONS_UPLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_TUNNEL_PEERS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_UPLINK_TERM_DROP;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_UPLINK_TERM_FWD;
import static org.omecproject.up4.impl.Up4P4InfoConstants.QFI;
import static org.omecproject.up4.impl.Up4P4InfoConstants.SLICE_ID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.SPORT;
import static org.omecproject.up4.impl.Up4P4InfoConstants.SRC_ADDR;
import static org.omecproject.up4.impl.Up4P4InfoConstants.SRC_IFACE;
import static org.omecproject.up4.impl.Up4P4InfoConstants.TC;
import static org.omecproject.up4.impl.Up4P4InfoConstants.TEID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.TUNNEL_PEER_ID;

/**
 * Utility class for transforming PiTableEntries to classes more specific to the UPF pipelines,
 * like PacketDetectionRule and ForwardingActionRule.
 */
public class Up4TranslatorImpl implements Up4Translator {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public UpfEntityType getEntityType(PiEntity entry) {
        switch (entry.piEntityType()) {
            case TABLE_ENTRY:
                PiTableEntry tableEntry = (PiTableEntry) entry;
                if (tableEntry.table().equals(PRE_QOS_PIPE_INTERFACES)) {
                    return UpfEntityType.INTERFACE;
                } else if (tableEntry.table().equals(PRE_QOS_PIPE_SESSIONS_UPLINK)) {
                    return UpfEntityType.SESSION_UPLINK;
                } else if (tableEntry.table().equals(PRE_QOS_PIPE_SESSIONS_DOWNLINK)) {
                    return UpfEntityType.SESSION_DOWNLINK;
                } else if (tableEntry.table().equals(PRE_QOS_PIPE_TERMINATIONS_UPLINK)) {
                    return UpfEntityType.TERMINATION_UPLINK;
                } else if (tableEntry.table().equals(PRE_QOS_PIPE_TERMINATIONS_DOWNLINK)) {
                    return UpfEntityType.TERMINATION_DOWNLINK;
                } else if (tableEntry.table().equals(PRE_QOS_PIPE_TUNNEL_PEERS)) {
                    return UpfEntityType.TUNNEL_PEER;
                } else if (tableEntry.table().equals(PRE_QOS_PIPE_APPLICATIONS)) {
                    return UpfEntityType.APPLICATION;
                }
                break;
            case COUNTER_CELL:
                PiCounterCell counterCell = (PiCounterCell) entry;
                if (counterCell.cellId().counterId().equals(POST_QOS_PIPE_POST_QOS_COUNTER) ||
                        counterCell.cellId().counterId().equals(PRE_QOS_PIPE_PRE_QOS_COUNTER)) {
                    return UpfEntityType.COUNTER;
                }
                break;
            default:
                break;
        }
        return null;
    }

    @Override
    public UpfEntity up4TableEntryToUpfEntity(PiTableEntry entry)
            throws Up4TranslationException {
        switch (getEntityType(entry)) {
            case INTERFACE: {
                UpfInterface.Builder builder = UpfInterface.builder();
                int srcIfaceTypeInt = Up4TranslatorUtil.getParamInt(entry, SRC_IFACE);
                if (srcIfaceTypeInt == IFACE_ACCESS) {
                    builder.setAccess();
                } else if (srcIfaceTypeInt == ExtraP4InfoConstants.IFACE_CORE) {
                    builder.setCore();
                } else {
                    throw new Up4TranslationException(
                            "Attempting to translate an unsupported UP4 interface type! " + srcIfaceTypeInt);
                }
                Ip4Prefix prefix = Up4TranslatorUtil.getFieldPrefix(entry, HDR_IPV4_DST_PREFIX);
                builder.setPrefix(prefix);
                return builder.build();
            }
            case SESSION_UPLINK: {
                SessionUplink.Builder builder = SessionUplink.builder();
                builder.withTeid(Up4TranslatorUtil.getFieldInt(entry, HDR_TEID));
                builder.withTunDstAddr(Up4TranslatorUtil.getFieldAddress(entry, HDR_N3_ADDRESS));
                PiActionId actionId = ((PiAction) entry.action()).id();
                builder.needsDropping(actionId.equals(PRE_QOS_PIPE_SET_SESSION_UPLINK_DROP));
                return builder.build();
            }
            case SESSION_DOWNLINK: {
                SessionDownlink.Builder builder = SessionDownlink.builder();
                builder.withUeAddress(Up4TranslatorUtil.getFieldAddress(entry, HDR_UE_ADDRESS));
                PiActionId actionId = ((PiAction) entry.action()).id();
                if (actionId.equals(PRE_QOS_PIPE_SET_SESSION_DOWNLINK_DROP)) {
                    builder.needsDropping(true);
                } else if (actionId.equals(PRE_QOS_PIPE_SET_SESSION_DOWNLINK_BUFF)) {
                    builder.needsBuffering(true);
                } else {
                    builder.withGtpTunnelPeerId(Up4TranslatorUtil.getParamByte(entry, TUNNEL_PEER_ID));
                }
                return builder.build();
            }
            case TERMINATION_UPLINK: {
                UpfTerminationUplink.Builder builder = UpfTerminationUplink.builder();
                builder.withUeSessionId(Up4TranslatorUtil.getFieldAddress(entry, HDR_UE_ADDRESS));
                builder.withCounterId(Up4TranslatorUtil.getParamInt(entry, CTR_IDX));
                PiActionId actionId = ((PiAction) entry.action()).id();
                if (actionId.equals(PRE_QOS_PIPE_UPLINK_TERM_DROP)) {
                    builder.needsDropping(true);
                } else {
                    builder.withTrafficClass(Up4TranslatorUtil.getParamByte(entry, TC));
                }
                return builder.build();
            }
            case TERMINATION_DOWNLINK: {
                UpfTerminationDownlink.Builder builder = UpfTerminationDownlink.builder();
                builder.withUeSessionId(Up4TranslatorUtil.getFieldAddress(entry, HDR_UE_ADDRESS));
                builder.withCounterId(Up4TranslatorUtil.getParamInt(entry, CTR_IDX));
                PiActionId actionId = ((PiAction) entry.action()).id();
                if (actionId.equals(PRE_QOS_PIPE_DOWNLINK_TERM_DROP)) {
                    builder.needsDropping(true);
                } else {
                    builder.withTeid(Up4TranslatorUtil.getParamInt(entry, TEID));
                    builder.withQfi(Up4TranslatorUtil.getParamByte(entry, QFI));
                    builder.withTrafficClass(Up4TranslatorUtil.getParamByte(entry, TC));
                }
                return builder.build();
            }
            case TUNNEL_PEER: {
                GtpTunnelPeer.Builder builder = GtpTunnelPeer.builder();
                builder.withTunnelPeerId(Up4TranslatorUtil.getFieldByte(entry, HDR_TUNNEL_PEER_ID));
                builder.withSrcAddr(Up4TranslatorUtil.getParamAddress(entry, SRC_ADDR));
                builder.withDstAddr(Up4TranslatorUtil.getParamAddress(entry, DST_ADDR));
                builder.withSrcPort(Up4TranslatorUtil.getParamShort(entry, SPORT));
                return builder.build();
            }
            case APPLICATION: {
                Application.Builder builder = Application.builder();
                builder.withAppId(Up4TranslatorUtil.getParamByte(entry, APP_ID));
                if (Up4TranslatorUtil.fieldIsPresent(entry, HDR_APP_IP_ADDRESS)) {
                    builder.withIp4Prefix(Up4TranslatorUtil.getFieldPrefix(entry, HDR_APP_IP_ADDRESS));
                }
                if (Up4TranslatorUtil.fieldIsPresent(entry, HDR_APP_L4_PORT)) {
                    Pair<Short, Short> range = Up4TranslatorUtil.getFieldRangeShort(entry, HDR_APP_L4_PORT);
                    if (range != null) {
                        builder.withL4PortRange(range.getLeft(), range.getRight());
                    }
                }
                if (Up4TranslatorUtil.fieldIsPresent(entry, HDR_APP_IP_PROTO)) {
                    builder.withIpProto(Up4TranslatorUtil.getFieldByte(entry, HDR_APP_IP_PROTO));
                }
                return builder.build();
            }
            default:
                throw new Up4TranslationException(
                        "Attempting to translate an unsupported UP4 table entry! " + entry);
        }
    }

    @Override
    public PiTableEntry entityToUp4TableEntry(UpfEntity entity) throws Up4TranslationException {
        PiTableEntry.Builder tableEntryBuilder = PiTableEntry.builder();
        PiAction.Builder actionBuilder = PiAction.builder();
        PiMatchKey.Builder matchBuilder = PiMatchKey.builder();
        switch (entity.type()) {
            case INTERFACE:
                tableEntryBuilder.forTable(PRE_QOS_PIPE_INTERFACES);
                UpfInterface upfIntf = (UpfInterface) entity;
                byte direction;
                byte srcIface;

                actionBuilder.withId(PRE_QOS_PIPE_SET_SOURCE_IFACE);
                if (upfIntf.isAccess()) {
                    srcIface = IFACE_ACCESS;
                    direction = DIRECTION_UPLINK;
                } else if (upfIntf.isCore()) {
                    srcIface = IFACE_CORE;
                    direction = DIRECTION_DOWNLINK;
                } else {
                    throw new Up4TranslationException("UPF Interface is not Access nor CORE: " + upfIntf);
                }
                actionBuilder.withParameter(new PiActionParam(SRC_IFACE, srcIface))
                        .withParameter(new PiActionParam(DIRECTION, direction))
                        .withParameter(new PiActionParam(SLICE_ID, SLICE_MOBILE));
                matchBuilder.addFieldMatch(new PiLpmFieldMatch(
                        HDR_IPV4_DST_PREFIX,
                        ImmutableByteSequence.copyFrom(upfIntf.prefix().address().toOctets()),
                        upfIntf.prefix().prefixLength())
                );
                break;
            case SESSION_UPLINK:
                tableEntryBuilder.forTable(PRE_QOS_PIPE_SESSIONS_UPLINK);
                SessionUplink sessionUplink = (SessionUplink) entity;
                matchBuilder
                        .addFieldMatch(new PiExactFieldMatch(
                                HDR_N3_ADDRESS,
                                ImmutableByteSequence.copyFrom(sessionUplink.tunDstAddr().toOctets()))
                        ).addFieldMatch(new PiExactFieldMatch(
                        HDR_TEID,
                        ImmutableByteSequence.copyFrom(sessionUplink.teid()))
                );

                if (sessionUplink.needsDropping()) {
                    actionBuilder.withId(PRE_QOS_PIPE_SET_SESSION_UPLINK_DROP);
                } else {
                    actionBuilder.withId(PRE_QOS_PIPE_SET_SESSION_UPLINK);
                }
                break;
            case SESSION_DOWNLINK:
                tableEntryBuilder.forTable(PRE_QOS_PIPE_SESSIONS_DOWNLINK);
                SessionDownlink sessionDownlink = (SessionDownlink) entity;
                matchBuilder.addFieldMatch(
                        new PiExactFieldMatch(
                                HDR_UE_ADDRESS,
                                ImmutableByteSequence.copyFrom(sessionDownlink.ueAddress().toOctets()))
                );

                if (sessionDownlink.needsDropping() && sessionDownlink.needsBuffering()) {
                    log.error("We don't support DROP + BUFF on the UP4 northbound! Defaulting to only BUFF");
                    actionBuilder.withId(PRE_QOS_PIPE_SET_SESSION_DOWNLINK_BUFF);
                } else if (sessionDownlink.needsDropping()) {
                    actionBuilder.withId(PRE_QOS_PIPE_SET_SESSION_DOWNLINK_DROP);
                } else if (sessionDownlink.needsBuffering()) {
                    actionBuilder.withId(PRE_QOS_PIPE_SET_SESSION_DOWNLINK_BUFF);
                } else {
                    actionBuilder.withParameter(new PiActionParam(
                            TUNNEL_PEER_ID,
                            ImmutableByteSequence.copyFrom(sessionDownlink.tunPeerId()))
                    );
                    actionBuilder.withId(PRE_QOS_PIPE_SET_SESSION_DOWNLINK);
                }
                break;
            case TERMINATION_UPLINK:
                tableEntryBuilder.forTable(PRE_QOS_PIPE_TERMINATIONS_UPLINK);
                UpfTerminationUplink upfTerminationUl = (UpfTerminationUplink) entity;
                matchBuilder
                        .addFieldMatch(new PiExactFieldMatch(
                                HDR_UE_ADDRESS,
                                ImmutableByteSequence.copyFrom(upfTerminationUl.ueSessionId().toOctets()))
                        );
                actionBuilder.withParameter(new PiActionParam(CTR_IDX, upfTerminationUl.counterId()));
                if (upfTerminationUl.needsDropping()) {
                    actionBuilder.withId(PRE_QOS_PIPE_UPLINK_TERM_DROP);
                } else {
                    actionBuilder.withId(PRE_QOS_PIPE_UPLINK_TERM_FWD);
                    actionBuilder.withParameter(new PiActionParam(TC, upfTerminationUl.trafficClass()));
                }
                break;
            case TERMINATION_DOWNLINK:
                tableEntryBuilder.forTable(PRE_QOS_PIPE_TERMINATIONS_DOWNLINK);
                UpfTerminationDownlink upfTerminationDl = (UpfTerminationDownlink) entity;
                matchBuilder
                        .addFieldMatch(new PiExactFieldMatch(
                                HDR_UE_ADDRESS,
                                ImmutableByteSequence.copyFrom(upfTerminationDl.ueSessionId().toOctets()))
                        );
                actionBuilder.withParameter(new PiActionParam(CTR_IDX, upfTerminationDl.counterId()));
                if (upfTerminationDl.needsDropping()) {
                    actionBuilder.withId(PRE_QOS_PIPE_DOWNLINK_TERM_DROP);
                } else {
                    actionBuilder.withId(PRE_QOS_PIPE_DOWNLINK_TERM_FWD);
                    actionBuilder.withParameter(new PiActionParam(TEID, upfTerminationDl.teid()))
                            .withParameter(new PiActionParam(QFI, upfTerminationDl.qfi()))
                            .withParameter(new PiActionParam(TC, upfTerminationDl.trafficClass()));
                }
                break;
            case TUNNEL_PEER:
                tableEntryBuilder.forTable(PRE_QOS_PIPE_TUNNEL_PEERS);
                GtpTunnelPeer gtpTunnelPeer = (GtpTunnelPeer) entity;
                matchBuilder.addFieldMatch(
                        new PiExactFieldMatch(
                                HDR_TUNNEL_PEER_ID,
                                ImmutableByteSequence.copyFrom(gtpTunnelPeer.tunPeerId()))
                );
                actionBuilder.withId(PRE_QOS_PIPE_LOAD_TUNNEL_PARAM)
                        .withParameter(new PiActionParam(SRC_ADDR, gtpTunnelPeer.src().toOctets()))
                        .withParameter(new PiActionParam(DST_ADDR, gtpTunnelPeer.dst().toOctets()))
                        .withParameter(new PiActionParam(SPORT, gtpTunnelPeer.srcPort()));
                break;
            case APPLICATION:
                tableEntryBuilder.forTable(PRE_QOS_PIPE_APPLICATIONS);
                Application application = (Application) entity;
                actionBuilder.withId(PRE_QOS_PIPE_SET_APP_ID)
                        .withParameter(new PiActionParam(APP_ID, application.appId()));
                matchBuilder.addFieldMatch(new PiExactFieldMatch(
                        HDR_SLICE_ID, ImmutableByteSequence.copyFrom(SLICE_MOBILE)));
                if (application.ip4Prefix().isPresent()) {
                    Ip4Prefix ipPrefix = application.ip4Prefix().get();
                    matchBuilder.addFieldMatch(new PiLpmFieldMatch(
                            HDR_APP_IP_ADDRESS,
                            ImmutableByteSequence.copyFrom(ipPrefix.address().toOctets()),
                            ipPrefix.prefixLength()));
                }
                if (application.l4PortRange().isPresent()) {
                    Pair<Short, Short> portRange = application.l4PortRange().get();
                    matchBuilder.addFieldMatch(new PiRangeFieldMatch(
                            HDR_APP_L4_PORT,
                            ImmutableByteSequence.copyFrom(portRange.getLeft()),
                            ImmutableByteSequence.copyFrom(portRange.getRight())));
                }
                if (application.ipProto().isPresent()) {
                    byte ipProto = application.ipProto().get();
                    matchBuilder.addFieldMatch(new PiTernaryFieldMatch(
                            HDR_APP_IP_PROTO,
                            ImmutableByteSequence.copyFrom(ipProto),
                            ImmutableByteSequence.ofOnes(1)
                    ));
                }
                break;
            default:
                throw new Up4TranslationException(
                        "Attempting to translate an unsupported UPF entity to a table entry! " + entity);
        }
        return tableEntryBuilder.withMatchKey(matchBuilder.build())
                .withAction(actionBuilder.build())
                .build();
    }
}
