/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.behavior;

import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Translator;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.impl.NorthConstants;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiMatchKey;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Utility class for transforming PiTableEntries to classes more specific to the UPF pipelines,
 * like PacketDetectionRule and ForwardingActionRule.
 */
public class Up4TranslatorImpl implements Up4Translator {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ImmutableByteSequence allOnes32 = ImmutableByteSequence.ofOnes(4);

    @Override
    public boolean isUp4Pdr(PiTableEntry entry) {
        return entry.table().equals(NorthConstants.PDR_TBL);
    }

    @Override
    public boolean isUp4Far(PiTableEntry entry) {
        return entry.table().equals(NorthConstants.FAR_TBL);
    }

    @Override
    public boolean isUp4Interface(PiTableEntry entry) {
        return entry.table().equals(NorthConstants.IFACE_TBL);
    }

    @Override
    public PacketDetectionRule up4EntryToPdr(PiTableEntry entry)
            throws Up4TranslationException {
        var pdrBuilder = PacketDetectionRule.builder();

        int srcInterface = TranslatorUtil.getFieldInt(entry, NorthConstants.SRC_IFACE_KEY);
        if (srcInterface == NorthConstants.IFACE_ACCESS) {
            // GTP-matching PDRs will match on the F-TEID (tunnel destination address + TEID)
            pdrBuilder.withTunnel(TranslatorUtil.getFieldValue(entry, NorthConstants.TEID_KEY),
                    TranslatorUtil.getFieldAddress(entry, NorthConstants.TUNNEL_DST_KEY));
        } else if (srcInterface == NorthConstants.IFACE_CORE) {
            // Non-GTP-matching PDRs will match on the UE address
            pdrBuilder.withUeAddr(TranslatorUtil.getFieldAddress(entry, NorthConstants.UE_ADDR_KEY));
        } else {
            throw new Up4TranslationException("Flexible PDRs not yet supported.");
        }

        // Now get the action parameters, if they are present (entries from delete writes don't have parameters)
        PiAction action = (PiAction) entry.action();
        PiActionId actionId = action.id();
        if (actionId.equals(NorthConstants.LOAD_PDR) && !action.parameters().isEmpty()) {
            ImmutableByteSequence sessionId = TranslatorUtil.getParamValue(entry, NorthConstants.SESSION_ID_PARAM);
            int localFarId = TranslatorUtil.getParamInt(entry, NorthConstants.FAR_ID_PARAM);
            int schedulingPriority = TranslatorUtil.getParamInt(entry, NorthConstants.SCHEDULING_PRIORITY);
            pdrBuilder.withSessionId(sessionId)
                    .withCounterId(TranslatorUtil.getParamInt(entry, NorthConstants.CTR_ID))
                    .withLocalFarId(localFarId)
                    .withSchedulingPriority(schedulingPriority);
        }
        return pdrBuilder.build();
    }

    @Override
    public ForwardingActionRule up4EntryToFar(PiTableEntry entry)
            throws Up4TranslationException {
        // First get the match keys
        ImmutableByteSequence sessionId = TranslatorUtil.getFieldValue(entry, NorthConstants.SESSION_ID_KEY);
        int localFarId = TranslatorUtil.getFieldInt(entry, NorthConstants.FAR_ID_KEY);
        var farBuilder = ForwardingActionRule.builder()
                .setFarId(localFarId)
                .withSessionId(sessionId);

        // Now get the action parameters, if they are present (entries from delete writes don't have parameters)
        PiAction action = (PiAction) entry.action();
        PiActionId actionId = action.id();
        if (!action.parameters().isEmpty()) {
            // Parameters that all types of fars have
            farBuilder.setDropFlag(TranslatorUtil.getParamInt(entry, NorthConstants.DROP_FLAG) > 0)
                    .setNotifyFlag(TranslatorUtil.getParamInt(entry, NorthConstants.NOTIFY_FLAG) > 0);
            if (actionId.equals(NorthConstants.LOAD_FAR_TUNNEL)) {
                // Parameters exclusive to encapsulating FARs
                farBuilder.setTunnel(
                        TranslatorUtil.getParamAddress(entry, NorthConstants.TUNNEL_SRC_PARAM),
                        TranslatorUtil.getParamAddress(entry, NorthConstants.TUNNEL_DST_PARAM),
                        TranslatorUtil.getParamValue(entry, NorthConstants.TEID_PARAM),
                        (short) TranslatorUtil.getParamInt(entry, NorthConstants.TUNNEL_SPORT_PARAM))
                        .setBufferFlag(TranslatorUtil.getParamInt(entry, NorthConstants.BUFFER_FLAG) > 0);
            }
        }
        return farBuilder.build();
    }

    @Override
    public UpfInterface up4EntryToInterface(PiTableEntry entry) throws Up4TranslationException {
        var builder = UpfInterface.builder();
        int srcIfaceTypeInt = TranslatorUtil.getParamInt(entry, NorthConstants.SRC_IFACE_PARAM);
        if (srcIfaceTypeInt == NorthConstants.IFACE_ACCESS) {
            builder.setAccess();
        } else if (srcIfaceTypeInt == NorthConstants.IFACE_CORE) {
            builder.setCore();
        } else {
            throw new Up4TranslationException("Attempting to translate an unsupported UP4 interface type! " +
                    srcIfaceTypeInt);
        }
        Ip4Prefix prefix = TranslatorUtil.getFieldPrefix(entry, NorthConstants.IFACE_DST_PREFIX_KEY);
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
                    .withId(NorthConstants.LOAD_FAR_NORMAL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.DROP_FLAG, far.drops() ? oneByte : zeroByte),
                            new PiActionParam(NorthConstants.NOTIFY_FLAG, far.notifies() ? oneByte : zeroByte)
                    ))
                    .build();
        } else {
            if (far.tunnelSrc() == null || far.tunnelDst() == null
                    || far.teid() == null || far.tunnel().srcPort() == null) {
                throw new Up4TranslationException(
                        "Not all action parameters present when translating intermediate encap FAR to logical FAR!");
            }
            action = PiAction.builder()
                    .withId(NorthConstants.LOAD_FAR_TUNNEL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.DROP_FLAG, far.drops() ? oneByte : zeroByte),
                            new PiActionParam(NorthConstants.NOTIFY_FLAG, far.notifies() ? oneByte : zeroByte),
                            new PiActionParam(NorthConstants.BUFFER_FLAG, far.buffers() ? oneByte : zeroByte),
                            new PiActionParam(NorthConstants.TUNNEL_TYPE_PARAM,
                                    toImmutableByte(NorthConstants.TUNNEL_TYPE_GTPU)),
                            new PiActionParam(NorthConstants.TUNNEL_SRC_PARAM, far.tunnelSrc().toInt()),
                            new PiActionParam(NorthConstants.TUNNEL_DST_PARAM, far.tunnelDst().toInt()),
                            new PiActionParam(NorthConstants.TEID_PARAM, far.teid()),
                            new PiActionParam(NorthConstants.TUNNEL_SPORT_PARAM, far.tunnel().srcPort())
                    ))
                    .build();
        }
        matchKey = PiMatchKey.builder()
                .addFieldMatch(new PiExactFieldMatch(NorthConstants.FAR_ID_KEY,
                        ImmutableByteSequence.copyFrom(far.farId())))
                .addFieldMatch(new PiExactFieldMatch(NorthConstants.SESSION_ID_KEY, far.sessionId()))
                .build();

        return PiTableEntry.builder()
                .forTable(NorthConstants.FAR_TBL)
                .withMatchKey(matchKey)
                .withAction(action)
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
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SRC_IFACE_KEY,
                            toImmutableByte(NorthConstants.IFACE_ACCESS)))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.TEID_KEY, pdr.teid(), allOnes32))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.TUNNEL_DST_KEY,
                            ImmutableByteSequence.copyFrom(pdr.tunnelDest().toOctets()), allOnes32))
                    .build();
        } else {
            decapFlag = 0;
            matchKey = PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SRC_IFACE_KEY,
                            toImmutableByte(NorthConstants.IFACE_CORE)))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.UE_ADDR_KEY,
                            ImmutableByteSequence.copyFrom(pdr.ueAddress().toOctets()), allOnes32))
                    .build();
        }
        // FIXME: pdr_id is not yet stored on writes so it cannot be read
        action = PiAction.builder()
                .withId(NorthConstants.LOAD_PDR)
                .withParameters(Arrays.asList(
                        new PiActionParam(NorthConstants.PDR_ID_PARAM, 0),
                        new PiActionParam(NorthConstants.SESSION_ID_PARAM, pdr.sessionId()),
                        new PiActionParam(NorthConstants.CTR_ID, pdr.counterId()),
                        new PiActionParam(NorthConstants.FAR_ID_PARAM, pdr.farId()),
                        new PiActionParam(NorthConstants.SCHEDULING_PRIORITY, pdr.schedulingPriority()),
                        new PiActionParam(NorthConstants.DECAP_FLAG_PARAM, toImmutableByte(decapFlag))
                ))
                .build();

        return PiTableEntry.builder()
                .forTable(NorthConstants.PDR_TBL)
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
        int srcIface = upfInterface.isAccess() ? NorthConstants.IFACE_ACCESS : NorthConstants.IFACE_CORE;
        int direction = upfInterface.isAccess() ? NorthConstants.DIRECTION_UPLINK : NorthConstants.DIRECTION_DOWNLINK;
        return PiTableEntry.builder()
                .forTable(NorthConstants.IFACE_TBL)
                .withMatchKey(PiMatchKey.builder()
                        .addFieldMatch(new PiLpmFieldMatch(
                                NorthConstants.IFACE_DST_PREFIX_KEY,
                                ImmutableByteSequence.copyFrom(upfInterface.prefix().address().toOctets()),
                                upfInterface.prefix().prefixLength()))
                        .build())
                .withAction(PiAction.builder()
                        .withId(NorthConstants.LOAD_IFACE)
                        .withParameters(Arrays.asList(
                                new PiActionParam(NorthConstants.SRC_IFACE_PARAM, toImmutableByte(srcIface)),
                                new PiActionParam(NorthConstants.DIRECTION, toImmutableByte(direction))
                        ))
                        .build()).build();
    }
}
