/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.omecproject.up4.Up4Translator;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.behaviour.upf.GtpTunnelPeer;
import org.onosproject.net.behaviour.upf.UeSession;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.behaviour.upf.UpfTermination;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiEntity;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiMatchKey;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.omecproject.up4.impl.ExtraP4InfoConstants.DIRECTION_DOWNLINK;
import static org.omecproject.up4.impl.ExtraP4InfoConstants.DIRECTION_UPLINK;
import static org.omecproject.up4.impl.ExtraP4InfoConstants.IFACE_ACCESS;
import static org.omecproject.up4.impl.ExtraP4InfoConstants.IFACE_CORE;
import static org.omecproject.up4.impl.Up4DeviceManager.SLICE_MOBILE;
import static org.omecproject.up4.impl.Up4P4InfoConstants.CTR_IDX;
import static org.omecproject.up4.impl.Up4P4InfoConstants.DIRECTION;
import static org.omecproject.up4.impl.Up4P4InfoConstants.DST_ADDR;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_IPV4_DST;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_IPV4_DST_PREFIX;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_SRC_IFACE;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_TEID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_TUNNEL_PEER_ID;
import static org.omecproject.up4.impl.Up4P4InfoConstants.HDR_UE_ADDRESS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.POST_QOS_PIPE_POST_QOS_COUNTER;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_INTERFACES;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_LOAD_TUNNEL_PARAM;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_PRE_QOS_COUNTER;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SESSIONS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_PARAMS_BUFFERING;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_PARAMS_DOWNLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_PARAMS_UPLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SET_SOURCE_IFACE;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_TERMINATIONS;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_TERM_DOWNLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_TERM_UPLINK;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_TUNNEL_PEERS;
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

    private final ImmutableByteSequence allOnes32 = ImmutableByteSequence.ofOnes(4);
    private final ImmutableByteSequence allOnes8 = ImmutableByteSequence.ofOnes(1);

    public static final byte FALSE = (byte) 0x00;
    public static final byte TRUE = (byte) 0x01;

    @Override
    public UpfEntityType getEntityType(PiEntity entry) {
        switch (entry.piEntityType()) {
            case TABLE_ENTRY:
                PiTableEntry tableEntry = (PiTableEntry) entry;
                if (tableEntry.table().equals(PRE_QOS_PIPE_INTERFACES)) {
                    return UpfEntityType.INTERFACE;
                } else if (tableEntry.table().equals(PRE_QOS_PIPE_SESSIONS)) {
                    return UpfEntityType.SESSION;
                } else if (tableEntry.table().equals(PRE_QOS_PIPE_TERMINATIONS)) {
                    return UpfEntityType.TERMINATION;
                } else if (tableEntry.table().equals(PRE_QOS_PIPE_TUNNEL_PEERS)) {
                    return UpfEntityType.TUNNEL_PEER;
                }
                break;
            case COUNTER_CELL:
                PiCounterCell counterCell = (PiCounterCell) entry;
                if (counterCell.cellId().equals(POST_QOS_PIPE_POST_QOS_COUNTER) ||
                        counterCell.cellId().equals(PRE_QOS_PIPE_PRE_QOS_COUNTER)) {
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
            case SESSION: {
                UeSession.Builder builder = UeSession.builder();
                if (entry.matchKey().fieldMatch(HDR_TEID).isPresent()) {
                    builder.withTeid(Up4TranslatorUtil.getFieldInt(entry, HDR_TEID));
                }
                builder.withIpv4Address(Ip4Address.valueOf(
                        Up4TranslatorUtil.getFieldValue(entry, HDR_IPV4_DST).asArray()));
                PiAction action = (PiAction) entry.action();
                if (action.id().equals(PRE_QOS_PIPE_SET_PARAMS_DOWNLINK)) {
                    builder.withGtpTunnelPeerId(Up4TranslatorUtil.getParamByte(entry, TUNNEL_PEER_ID));
                    // Matching on QFI currently not supported in UP4 logical pipeline
                } else if (action.id().equals(PRE_QOS_PIPE_SET_PARAMS_BUFFERING)) {
                    builder.withBuffering(true);
                }
                return builder.build();
            }
            case TERMINATION: {
                UpfTermination.Builder builder = UpfTermination.builder();
                builder.withUeSessionId(Up4TranslatorUtil.getFieldAddress(entry, HDR_UE_ADDRESS));
                builder.withCounterId(Up4TranslatorUtil.getParamInt(entry, CTR_IDX));
                builder.withTrafficClass(Up4TranslatorUtil.getParamByte(entry, TC));
                PiAction action = (PiAction) entry.action();
                if (action.id().equals(PRE_QOS_PIPE_TERM_DOWNLINK)) {
                    builder.withTeid(Up4TranslatorUtil.getParamInt(entry, TEID));
                    builder.withQfi(Up4TranslatorUtil.getParamByte(entry, QFI));
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
            case SESSION:
                tableEntryBuilder.forTable(PRE_QOS_PIPE_SESSIONS);
                UeSession ueSession = (UeSession) entity;
                matchBuilder.addFieldMatch(new PiExactFieldMatch(
                        HDR_SRC_IFACE,
                        ueSession.isUplink() ? ImmutableByteSequence.copyFrom(IFACE_ACCESS) :
                                ImmutableByteSequence.copyFrom(IFACE_CORE)))
                        .addFieldMatch(new PiTernaryFieldMatch(
                                HDR_IPV4_DST,
                                ImmutableByteSequence.copyFrom(ueSession.ipv4Address().toOctets()),
                                allOnes32)
                        );
                if (ueSession.isUplink()) {
                    matchBuilder.addFieldMatch(new PiTernaryFieldMatch(
                            HDR_TEID, ImmutableByteSequence.copyFrom(ueSession.teid()), allOnes32));
                    actionBuilder.withId(PRE_QOS_PIPE_SET_PARAMS_UPLINK);
                } else if (!ueSession.needsBuffering()) {
                    actionBuilder.withId(PRE_QOS_PIPE_SET_PARAMS_DOWNLINK)
                            .withParameter(new PiActionParam(TUNNEL_PEER_ID, ueSession.tunPeerId()));
                } else {
                    // buffering
                    actionBuilder.withId(PRE_QOS_PIPE_SET_PARAMS_BUFFERING);
                }
                break;
            case TERMINATION:
                tableEntryBuilder.forTable(PRE_QOS_PIPE_TERMINATIONS);
                UpfTermination upfTermination = (UpfTermination) entity;
                matchBuilder.addFieldMatch(
                                new PiExactFieldMatch(HDR_SRC_IFACE, upfTermination.isUplink() ?
                                        ImmutableByteSequence.copyFrom(IFACE_ACCESS) :
                                        ImmutableByteSequence.copyFrom(IFACE_CORE)))
                        .addFieldMatch(
                                new PiExactFieldMatch(HDR_UE_ADDRESS,
                                                      ImmutableByteSequence.copyFrom(
                                                              upfTermination.ueSessionId().toOctets()))
                        );
                actionBuilder.withParameter(new PiActionParam(CTR_IDX, upfTermination.counterId()))
                        .withParameter(new PiActionParam(TC, upfTermination.trafficClass()));
                if (upfTermination.isUplink()) {
                    actionBuilder.withId(PRE_QOS_PIPE_TERM_UPLINK);
                } else {
                    actionBuilder.withId(PRE_QOS_PIPE_TERM_DOWNLINK)
                            .withParameter(new PiActionParam(TEID, upfTermination.teid()))
                            .withParameter(new PiActionParam(QFI, upfTermination.qfi()));
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
            default:
                throw new Up4TranslationException(
                        "Attempting to translate an unsupported UPF entity to a table entry! " + entity);
        }
        return tableEntryBuilder.withMatchKey(matchBuilder.build())
                .withAction(actionBuilder.build())
                .build();
    }

    private ImmutableByteSequence toImmutableByte(int value) {
        try {
            return ImmutableByteSequence.copyFrom(value).fit(8);
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            log.error("Attempted to convert an integer larger than 255 to a byte!: {}", e.getMessage());
            return ImmutableByteSequence.ofZeros(1);
        }
    }
}
