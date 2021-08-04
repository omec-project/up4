/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.omecproject.up4.Up4Translator;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.behaviour.upf.ForwardingActionRule;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;
import org.onosproject.net.behaviour.upf.QosEnforcementRule;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.pi.model.PiActionId;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.omecproject.up4.impl.Up4P4InfoConstants.QER_METER;

/**
 * Utility class for transforming PiTableEntries to classes more specific to the UPF pipelines,
 * like PacketDetectionRule and ForwardingActionRule.
 */
public class Up4TranslatorImpl implements Up4Translator {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ImmutableByteSequence allOnes32 = ImmutableByteSequence.ofOnes(4);

    @Override
    public boolean isUp4Pdr(PiTableEntry entry) {
        return entry.table().equals(Up4P4InfoConstants.PDR_TBL);
    }

    @Override
    public boolean isUp4Far(PiTableEntry entry) {
        return entry.table().equals(Up4P4InfoConstants.FAR_TBL);
    }

    @Override
    public boolean isUp4Interface(PiTableEntry entry) {
        return entry.table().equals(Up4P4InfoConstants.IFACE_TBL);
    }

    @Override
    public PacketDetectionRule up4EntryToPdr(PiTableEntry entry)
            throws Up4TranslationException {
        var pdrBuilder = PacketDetectionRule.builder();

        int srcInterface = Up4TranslatorUtil.getFieldInt(entry, Up4P4InfoConstants.SRC_IFACE_KEY);
        if (srcInterface == Up4P4InfoConstants.IFACE_ACCESS) {
            // GTP-matching PDRs will match on the F-TEID (tunnel destination address + TEID)
            pdrBuilder.withTunnel(Up4TranslatorUtil.getFieldValue(entry, Up4P4InfoConstants.TEID_KEY),
                                  Up4TranslatorUtil.getFieldAddress(entry, Up4P4InfoConstants.TUNNEL_DST_KEY));
        } else if (srcInterface == Up4P4InfoConstants.IFACE_CORE) {
            // Non-GTP-matching PDRs will match on the UE address
            pdrBuilder.withUeAddr(Up4TranslatorUtil.getFieldAddress(entry, Up4P4InfoConstants.UE_ADDR_KEY));
        } else {
            throw new Up4TranslationException("Flexible PDRs not yet supported.");
        }

        // Now get the action parameters, if they are present (entries from delete writes don't have parameters)
        PiAction action = (PiAction) entry.action();
        PiActionId actionId = action.id();
        if (actionId.equals(Up4P4InfoConstants.LOAD_PDR) && !action.parameters().isEmpty()) {
            ImmutableByteSequence sessionId = Up4TranslatorUtil.getParamValue(
                    entry, Up4P4InfoConstants.SESSION_ID_PARAM);
            int localFarId = Up4TranslatorUtil.getParamInt(
                    entry, Up4P4InfoConstants.FAR_ID_PARAM);
            int qerId = Up4TranslatorUtil.getParamInt(entry, Up4P4InfoConstants.QER_ID_PARAM);
            int schedulingPriority = 0;
            pdrBuilder.withSessionId(sessionId)
                    .withCounterId(Up4TranslatorUtil.getParamInt(
                            entry, Up4P4InfoConstants.CTR_ID))
                    .withSchedulingPriority(schedulingPriority)
                    .withLocalFarId(localFarId)
                    .withQerId(qerId)
                    .withSchedulingPriority(schedulingPriority);
        } else if (actionId.equals(Up4P4InfoConstants.LOAD_PDR_QOS) && !action.parameters().isEmpty()) {
            ImmutableByteSequence sessionId = Up4TranslatorUtil.getParamValue(
                    entry, Up4P4InfoConstants.SESSION_ID_PARAM);
            int qerId = Up4TranslatorUtil.getParamInt(entry, Up4P4InfoConstants.QER_ID_PARAM);
            int localFarId = Up4TranslatorUtil.getParamInt(entry, Up4P4InfoConstants.FAR_ID_PARAM);
            int schedulingPriority = Up4TranslatorUtil.getParamInt(
                    entry, Up4P4InfoConstants.SCHEDULING_PRIORITY);
            pdrBuilder.withSessionId(sessionId)
                    .withCounterId(Up4TranslatorUtil.getParamInt(
                            entry, Up4P4InfoConstants.CTR_ID))
                    .withLocalFarId(localFarId)
                    .withQerId(qerId)
                    .withSchedulingPriority(schedulingPriority);
        }
        return pdrBuilder.build();
    }

    @Override
    public ForwardingActionRule up4EntryToFar(PiTableEntry entry)
            throws Up4TranslationException {
        // First get the match keys
        ImmutableByteSequence sessionId = Up4TranslatorUtil.getFieldValue(
                entry, Up4P4InfoConstants.SESSION_ID_KEY);
        int localFarId = Up4TranslatorUtil.getFieldInt(entry, Up4P4InfoConstants.FAR_ID_KEY);
        var farBuilder = ForwardingActionRule.builder()
                .setFarId(localFarId)
                .withSessionId(sessionId);

        // Now get the action parameters, if they are present (entries from delete writes don't have parameters)
        PiAction action = (PiAction) entry.action();
        PiActionId actionId = action.id();
        if (!action.parameters().isEmpty()) {
            // Parameters that all types of fars have
            boolean dropFlag = Up4TranslatorUtil.getParamInt(entry, Up4P4InfoConstants.DROP_FLAG) > 0;
            boolean notifyFlag = Up4TranslatorUtil.getParamInt(entry, Up4P4InfoConstants.NOTIFY_FLAG) > 0;
            boolean tunnelFlag = actionId.equals(Up4P4InfoConstants.LOAD_FAR_TUNNEL);
            boolean bufferFlag = false;
            farBuilder.setDropFlag(dropFlag).setNotifyFlag(notifyFlag);
            if (tunnelFlag) {
                // Parameters exclusive to encapsulating FARs
                bufferFlag = Up4TranslatorUtil.getParamInt(entry, Up4P4InfoConstants.BUFFER_FLAG) > 0;
                farBuilder.setTunnel(
                        Up4TranslatorUtil.getParamAddress(entry, Up4P4InfoConstants.TUNNEL_SRC_PARAM),
                        Up4TranslatorUtil.getParamAddress(entry, Up4P4InfoConstants.TUNNEL_DST_PARAM),
                        Up4TranslatorUtil.getParamValue(entry, Up4P4InfoConstants.TEID_PARAM),
                        (short) Up4TranslatorUtil.getParamInt(entry, Up4P4InfoConstants.TUNNEL_SPORT_PARAM))
                        .setBufferFlag(bufferFlag);
            }
            if (!dropFlag && !tunnelFlag && !bufferFlag && notifyFlag) {
                // Forward + NotifyCP is not allowed.
                throw new Up4TranslationException("Forward + NotifyCP action is not allowed.");
            }
        }
        return farBuilder.build();
    }

    @Override
    public UpfInterface up4EntryToInterface(PiTableEntry entry) throws Up4TranslationException {
        var builder = UpfInterface.builder();
        int srcIfaceTypeInt = Up4TranslatorUtil.getParamInt(entry, Up4P4InfoConstants.SRC_IFACE_PARAM);
        if (srcIfaceTypeInt == Up4P4InfoConstants.IFACE_ACCESS) {
            builder.setAccess();
        } else if (srcIfaceTypeInt == Up4P4InfoConstants.IFACE_CORE) {
            builder.setCore();
        } else {
            throw new Up4TranslationException("Attempting to translate an unsupported UP4 interface type! " +
                                                      srcIfaceTypeInt);
        }
        Ip4Prefix prefix = Up4TranslatorUtil.getFieldPrefix(entry, Up4P4InfoConstants.IFACE_DST_PREFIX_KEY);
        builder.setPrefix(prefix);
        return builder.build();
    }

    @Override
    public PiTableEntry farToUp4Entry(ForwardingActionRule far) throws Up4TranslationException {
        PiMatchKey matchKey;
        PiAction action;
        ImmutableByteSequence zeroByte = ImmutableByteSequence.ofZeros(1);
        ImmutableByteSequence oneByte = ImmutableByteSequence.ofOnes(1);
        if (!far.encaps()) {
            action = PiAction.builder()
                    .withId(Up4P4InfoConstants.LOAD_FAR_NORMAL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(Up4P4InfoConstants.DROP_FLAG, far.drops() ? oneByte : zeroByte),
                            new PiActionParam(Up4P4InfoConstants.NOTIFY_FLAG, far.notifies() ? oneByte : zeroByte)
                    ))
                    .build();
        } else {
            if (far.tunnelSrc() == null || far.tunnelDst() == null
                    || far.teid() == null || far.tunnel().srcPort() == null) {
                throw new Up4TranslationException(
                        "Not all action parameters present when translating intermediate encap FAR to logical FAR!");
            }
            action = PiAction.builder()
                    .withId(Up4P4InfoConstants.LOAD_FAR_TUNNEL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(Up4P4InfoConstants.DROP_FLAG, far.drops() ? oneByte : zeroByte),
                            new PiActionParam(Up4P4InfoConstants.NOTIFY_FLAG, far.notifies() ? oneByte : zeroByte),
                            new PiActionParam(Up4P4InfoConstants.BUFFER_FLAG, far.buffers() ? oneByte : zeroByte),
                            new PiActionParam(Up4P4InfoConstants.TUNNEL_TYPE_PARAM,
                                              toImmutableByte(Up4P4InfoConstants.TUNNEL_TYPE_GTPU)),
                            new PiActionParam(Up4P4InfoConstants.TUNNEL_SRC_PARAM, far.tunnelSrc().toInt()),
                            new PiActionParam(Up4P4InfoConstants.TUNNEL_DST_PARAM, far.tunnelDst().toInt()),
                            new PiActionParam(Up4P4InfoConstants.TEID_PARAM, far.teid()),
                            new PiActionParam(Up4P4InfoConstants.TUNNEL_SPORT_PARAM, far.tunnel().srcPort())
                    ))
                    .build();
        }
        matchKey = PiMatchKey.builder()
                .addFieldMatch(new PiExactFieldMatch(Up4P4InfoConstants.FAR_ID_KEY,
                                                     ImmutableByteSequence.copyFrom(far.farId())))
                .addFieldMatch(new PiExactFieldMatch(Up4P4InfoConstants.SESSION_ID_KEY, far.sessionId()))
                .build();

        return PiTableEntry.builder()
                .forTable(Up4P4InfoConstants.FAR_TBL)
                .withMatchKey(matchKey)
                .withAction(action)
                .build();
    }

    @Override
    public PiMeterCellConfig qerToUp4MeterEntry(QosEnforcementRule qer) throws Up4TranslationException {
        PiMeterCellId meterCellId = PiMeterCellId.ofIndirect(QER_METER, qer.getQerId());
        PiMeterBand commitedBand = new PiMeterBand(qer.getCir(), qer.getCburst());
        PiMeterBand peakBand = new PiMeterBand(qer.getPir(), qer.getPburst());
        return PiMeterCellConfig.builder()
                .withMeterCellId(meterCellId)
                .withMeterBand(commitedBand)
                .withMeterBand(peakBand)
                .build();
    }

    @Override
    public QosEnforcementRule up4MeterEntryToQer(PiMeterCellConfig meterEntry) throws Up4TranslationException {
        PiMeterBand committedBand;
        PiMeterBand peakBand;
        PiMeterBand[] bands = meterEntry.meterBands().toArray(new PiMeterBand[0]);
        if (bands.length != 2) {
            throw new Up4TranslationException("Meter must have 2 bands (committed and peak)!");
        }
        // Assume peak is the band with higher rate or equal rate but higher peak
        //CHECKSTYLE:OFF
        // from: https://github.com/opennetworkinglab/onos/blob/master/protocols/p4runtime/utils/src/main/java/org/onosproject/p4runtime/ctl/codec/MeterEntryCodec.java
        //CHECKSTYLE:ON
        if (bands[0].rate() > bands[1].rate() ||
                (bands[0].rate() == bands[1].rate() && bands[0].burst() >= bands[1].burst())) {
            committedBand = bands[1];
            peakBand = bands[0];
        } else {
            committedBand = bands[0];
            peakBand = bands[1];
        }
        return new QosEnforcementRule.Builder()
                .withQerId((int) meterEntry.cellId().index())
                .withCir(committedBand.rate())
                .withCburst(committedBand.burst())
                .withPir(peakBand.rate())
                .withPburst(peakBand.burst())
                .build();
    }

    @Override
    public PiTableEntry pdrToUp4Entry(PacketDetectionRule pdr) throws Up4TranslationException {
        PiMatchKey matchKey;
        PiAction action;
        int decapFlag;
        if (pdr.matchesEncapped()) {
            decapFlag = 1;
            matchKey = PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(
                            Up4P4InfoConstants.SRC_IFACE_KEY,
                            toImmutableByte(Up4P4InfoConstants.IFACE_ACCESS)))
                    .addFieldMatch(new PiTernaryFieldMatch(
                            Up4P4InfoConstants.TEID_KEY, pdr.teid(), allOnes32))
                    .addFieldMatch(new PiTernaryFieldMatch(
                            Up4P4InfoConstants.TUNNEL_DST_KEY,
                            ImmutableByteSequence.copyFrom(pdr.tunnelDest().toOctets()), allOnes32))
                    .build();
        } else {
            decapFlag = 0;
            matchKey = PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(
                            Up4P4InfoConstants.SRC_IFACE_KEY,
                            toImmutableByte(Up4P4InfoConstants.IFACE_CORE)))
                    .addFieldMatch(new PiTernaryFieldMatch(
                            Up4P4InfoConstants.UE_ADDR_KEY,
                            ImmutableByteSequence.copyFrom(pdr.ueAddress().toOctets()), allOnes32))
                    .build();
        }
        // FIXME: pdr_id is not yet stored on writes so it cannot be read
        PiAction.Builder builder = PiAction.builder()
                .withParameters(Arrays.asList(
                        new PiActionParam(Up4P4InfoConstants.SESSION_ID_PARAM, pdr.sessionId()),
                        new PiActionParam(Up4P4InfoConstants.CTR_ID, pdr.counterId()),
                        new PiActionParam(Up4P4InfoConstants.FAR_ID_PARAM, pdr.farId()),
                        new PiActionParam(Up4P4InfoConstants.QER_ID_PARAM, pdr.qerId()),
                        new PiActionParam(Up4P4InfoConstants.DECAP_FLAG_PARAM, toImmutableByte(decapFlag))
                ));
        if (pdr.hasSchedulingPriority()) {
            action = builder
                    .withId(Up4P4InfoConstants.LOAD_PDR_QOS)
                    .withParameter(new PiActionParam(Up4P4InfoConstants.SCHEDULING_PRIORITY, pdr.schedulingPriority()))
                    .build();
        } else {
            action = builder
                    .withId(Up4P4InfoConstants.LOAD_PDR)
                    .build();
        }
        return PiTableEntry.builder()
                .forTable(Up4P4InfoConstants.PDR_TBL)
                .withMatchKey(matchKey)
                .withAction(action)
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

    @Override
    public PiTableEntry interfaceToUp4Entry(UpfInterface upfInterface) throws Up4TranslationException {
        int srcIface = upfInterface.isAccess() ? Up4P4InfoConstants.IFACE_ACCESS :
                Up4P4InfoConstants.IFACE_CORE;
        int direction = upfInterface.isAccess() ? Up4P4InfoConstants.DIRECTION_UPLINK :
                Up4P4InfoConstants.DIRECTION_DOWNLINK;
        return PiTableEntry.builder()
                .forTable(Up4P4InfoConstants.IFACE_TBL)
                .withMatchKey(PiMatchKey.builder()
                                      .addFieldMatch(new PiLpmFieldMatch(
                                              Up4P4InfoConstants.IFACE_DST_PREFIX_KEY,
                                              ImmutableByteSequence.copyFrom(
                                                      upfInterface.prefix().address().toOctets()),
                                              upfInterface.prefix().prefixLength()))
                                      .build())
                .withAction(PiAction.builder()
                                    .withId(Up4P4InfoConstants.LOAD_IFACE)
                                    .withParameters(Arrays.asList(
                                            new PiActionParam(
                                                    Up4P4InfoConstants.SRC_IFACE_PARAM,
                                                    toImmutableByte(srcIface)),
                                            new PiActionParam(
                                                    Up4P4InfoConstants.DIRECTION,
                                                    toImmutableByte(direction))
                                    ))
                                    .build()).build();
    }
}
