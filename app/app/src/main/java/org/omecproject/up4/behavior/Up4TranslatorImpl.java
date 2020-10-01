package org.omecproject.up4.behavior;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.commons.lang3.tuple.Pair;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.GtpTunnel;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Translator;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfRuleIdentifier;
import org.omecproject.up4.impl.NorthConstants;
import org.omecproject.up4.impl.SouthConstants;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiMatchKey;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;

/**
 * Utility class for transforming PiTableEntries to classes more specific to the UPF pipelines,
 * like PacketDetectionRule and ForwardingActionRule.
 */
@Component(immediate = true,
        service = {Up4Translator.class})
public class Up4TranslatorImpl implements Up4Translator {
    private final Logger log = LoggerFactory.getLogger(getClass());
    // Maps local FAR IDs to global FAR IDs
    protected final BiMap<UpfRuleIdentifier, Integer> farIdMapper = HashBiMap.create();
    ;
    private int nextGlobalFarId = 1;

    private final ImmutableByteSequence allOnes32 = ImmutableByteSequence.ofOnes(4);
    private final ImmutableByteSequence allOnes16 = ImmutableByteSequence.ofOnes(2);
    private final ImmutableByteSequence allOnes8 = ImmutableByteSequence.ofOnes(1);

    @Activate
    protected void activate() {
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }

    @Override
    public void reset() {
        farIdMapper.clear();
        nextGlobalFarId = 0;
    }

    @Override
    public int globalFarIdOf(UpfRuleIdentifier farIdPair) {
        int globalFarId = farIdMapper.compute(farIdPair,
                (k, existingId) -> {
                    return Objects.requireNonNullElseGet(existingId, () -> nextGlobalFarId++);
                });
        log.info("{} translated to GlobalFarId={}", farIdPair, globalFarId);
        return globalFarId;
    }

    @Override
    public int globalFarIdOf(ImmutableByteSequence pfcpSessionId, int sessionLocalFarId) {
        UpfRuleIdentifier farId = new UpfRuleIdentifier(pfcpSessionId, sessionLocalFarId);
        return globalFarIdOf(farId);

    }

    /**
     * Get the corresponding PFCP session ID and session-local FAR ID from a globally unique FAR ID, or return
     * null if no such mapping is found.
     *
     * @param globalFarId globally unique FAR ID
     * @return the corresponding PFCP session ID and session-local FAR ID, as a RuleIdentifier
     */
    private UpfRuleIdentifier localFarIdOf(int globalFarId) {
        return farIdMapper.inverse().get(globalFarId);
    }

    @Override
    public boolean isUp4Pdr(PiTableEntry entry) {
        return entry.table().equals(NorthConstants.PDR_TBL);
    }

    @Override
    public boolean isFabricPdr(FlowRule entry) {
        return entry.table().equals(SouthConstants.PDR_UPLINK_TBL)
                || entry.table().equals(SouthConstants.PDR_DOWNLINK_TBL);
    }

    @Override
    public boolean isUp4Far(PiTableEntry entry) {
        return entry.table().equals(NorthConstants.FAR_TBL);
    }

    @Override
    public boolean isFabricFar(FlowRule entry) {
        return entry.table().equals(SouthConstants.FAR_TBL);
    }

    @Override
    public boolean isFabricInterface(FlowRule entry) {
        return entry.table().equals(SouthConstants.INTERFACE_LOOKUP);
    }

    @Override
    public boolean isUp4Interface(PiTableEntry entry) {
        return entry.table().equals(NorthConstants.IFACE_TBL);
    }

    @Override
    public PacketDetectionRule fabricEntryToPdr(FlowRule entry)
            throws Up4TranslationException {
        var pdrBuilder = PacketDetectionRule.builder();
        Pair<PiCriterion, PiTableAction> matchActionPair = TranslatorUtil.fabricEntryToPiPair(entry);
        PiCriterion match = matchActionPair.getLeft();
        PiAction action = (PiAction) matchActionPair.getRight();

        // Grab keys and parameters that are present for all PDRs
        int globalFarId = TranslatorUtil.getParamInt(action, SouthConstants.FAR_ID_PARAM);
        UpfRuleIdentifier farId = localFarIdOf(globalFarId);
        pdrBuilder.withCounterId(TranslatorUtil.getParamInt(action, SouthConstants.CTR_ID))
                .withLocalFarId(farId.getSessionlocalId())
                .withSessionId(farId.getPfcpSessionId());

        if (TranslatorUtil.fieldIsPresent(match, SouthConstants.TEID_KEY)) {
            // F-TEID is only present for uplink PDRs
            ImmutableByteSequence teid = TranslatorUtil.getFieldValue(match, SouthConstants.TEID_KEY);
            Ip4Address tunnelDst = TranslatorUtil.getFieldAddress(match, SouthConstants.TUNNEL_DST_KEY);
            pdrBuilder.withTeid(teid)
                    .withTunnelDst(tunnelDst);
        } else if (TranslatorUtil.fieldIsPresent(match, SouthConstants.UE_ADDR_KEY)) {
            // And UE address is only present for downlink PDRs
            pdrBuilder.withUeAddr(TranslatorUtil.getFieldAddress(match, SouthConstants.UE_ADDR_KEY));
        } else {
            throw new Up4TranslationException("Read malformed PDR from dataplane!:" + entry);
        }

        return pdrBuilder.build();
    }

    @Override
    public PacketDetectionRule up4EntryToPdr(PiTableEntry entry)
            throws Up4TranslationException {
        var pdrBuilder = PacketDetectionRule.builder();

        int srcInterface = TranslatorUtil.getFieldInt(entry, NorthConstants.SRC_IFACE_KEY);
        if (srcInterface == NorthConstants.IFACE_ACCESS) {
            // Uplink entries will match on the F-TEID (tunnel destination address + TEID)
            pdrBuilder.withTunnel(TranslatorUtil.getFieldValue(entry, NorthConstants.TEID_KEY),
                    TranslatorUtil.getFieldAddress(entry, NorthConstants.TUNNEL_DST_KEY));
        } else if (srcInterface == NorthConstants.IFACE_CORE) {
            // Downlink entries will match on the UE address
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
            pdrBuilder.withSessionId(sessionId)
                    .withCounterId(TranslatorUtil.getParamInt(entry, NorthConstants.CTR_ID))
                    .withLocalFarId(localFarId);
        }
        return pdrBuilder.build();
    }


    @Override
    public ForwardingActionRule fabricEntryToFar(FlowRule entry)
            throws Up4TranslationException {
        var farBuilder = ForwardingActionRule.builder();
        Pair<PiCriterion, PiTableAction> matchActionPair = TranslatorUtil.fabricEntryToPiPair(entry);
        PiCriterion match = matchActionPair.getLeft();
        PiAction action = (PiAction) matchActionPair.getRight();

        int globalFarId = TranslatorUtil.getFieldInt(match, SouthConstants.FAR_ID_KEY);
        UpfRuleIdentifier farId = localFarIdOf(globalFarId);

        boolean dropFlag = TranslatorUtil.getParamInt(action, SouthConstants.DROP_FLAG) > 0;
        boolean notifyFlag = TranslatorUtil.getParamInt(action, SouthConstants.NOTIFY_FLAG) > 0;

        // Match keys
        farBuilder.withSessionId(farId.getPfcpSessionId())
                .withFarId(farId.getSessionlocalId());

        // Parameters common to uplink and downlink should always be present
        farBuilder.withDropFlag(dropFlag)
                .withNotifyFlag(notifyFlag);

        PiActionId actionId = action.id();

        if (actionId.equals(SouthConstants.LOAD_FAR_TUNNEL) || actionId.equals(SouthConstants.LOAD_FAR_BUFFER)) {
            // Grab parameters specific to downlink FARs if they're present
            Ip4Address tunnelSrc = TranslatorUtil.getParamAddress(action, SouthConstants.TUNNEL_SRC_PARAM);
            Ip4Address tunnelDst = TranslatorUtil.getParamAddress(action, SouthConstants.TUNNEL_DST_PARAM);
            ImmutableByteSequence teid = TranslatorUtil.getParamValue(action, SouthConstants.TEID_PARAM);

            boolean farBuffers = actionId.equals(SouthConstants.LOAD_FAR_BUFFER);

            farBuilder.withTunnel(
                    GtpTunnel.builder()
                            .setSrc(tunnelSrc)
                            .setDst(tunnelDst)
                            .setTeid(teid)
                            .build())
                    .withBufferFlag(farBuffers);
        }
        return farBuilder.build();
    }

    @Override
    public ForwardingActionRule up4EntryToFar(PiTableEntry entry)
            throws Up4TranslationException {
        // First get the match keys
        ImmutableByteSequence sessionId = TranslatorUtil.getFieldValue(entry, NorthConstants.SESSION_ID_KEY);
        int localFarId = TranslatorUtil.getFieldInt(entry, NorthConstants.FAR_ID_KEY);
        var farBuilder = ForwardingActionRule.builder()
                .withFarId(localFarId)
                .withSessionId(sessionId);

        // Now get the action parameters, if they are present (entries from delete writes don't have parameters)
        PiAction action = (PiAction) entry.action();
        PiActionId actionId = action.id();
        if (!action.parameters().isEmpty()) {
            // Parameters that both types of FAR have
            farBuilder.withDropFlag(TranslatorUtil.getParamInt(entry, NorthConstants.DROP_FLAG) > 0)
                    .withNotifyFlag(TranslatorUtil.getParamInt(entry, NorthConstants.NOTIFY_FLAG) > 0);
            if (actionId.equals(NorthConstants.LOAD_FAR_TUNNEL)) {
                // Parameters exclusive to a downlink FAR
                farBuilder.withTunnel(
                                TranslatorUtil.getParamAddress(entry, NorthConstants.TUNNEL_SRC_PARAM),
                                TranslatorUtil.getParamAddress(entry, NorthConstants.TUNNEL_DST_PARAM),
                                TranslatorUtil.getParamValue(entry, NorthConstants.TEID_PARAM))
                        .withBufferFlag(TranslatorUtil.getParamInt(entry, NorthConstants.BUFFER_FLAG) > 0);
            }
        }
        return farBuilder.build();
    }

    @Override
    public UpfInterface up4EntryToInterface(PiTableEntry entry) throws Up4TranslationException {
        var builder = UpfInterface.builder();
        int srcIfaceTypeInt = TranslatorUtil.getParamInt(entry, NorthConstants.SRC_IFACE_PARAM);
        if (srcIfaceTypeInt == NorthConstants.IFACE_ACCESS) {
            builder.setUplink();
        } else if (srcIfaceTypeInt == NorthConstants.IFACE_CORE) {
            builder.setDownlink();
        } else {
            throw new Up4TranslationException("Attempting to translate an unsupported UP4 interface type! " +
                    srcIfaceTypeInt);
        }
        Ip4Prefix prefix = TranslatorUtil.getFieldPrefix(entry, NorthConstants.IFACE_DST_PREFIX_KEY);
        builder.setPrefix(prefix);
        return builder.build();
    }

    @Override
    public UpfInterface fabricEntryToInterface(FlowRule entry)
            throws Up4TranslationException {
        Pair<PiCriterion, PiTableAction> matchActionPair = TranslatorUtil.fabricEntryToPiPair(entry);
        PiCriterion match = matchActionPair.getLeft();
        PiAction action = (PiAction) matchActionPair.getRight();

        var ifaceBuilder = UpfInterface.builder()
                .setPrefix(TranslatorUtil.getFieldPrefix(match, SouthConstants.IPV4_DST_ADDR));

        int interfaceType = TranslatorUtil.getParamInt(action, SouthConstants.SRC_IFACE_PARAM);
        if (interfaceType == SouthConstants.INTERFACE_ACCESS) {
            ifaceBuilder.setUplink();
        } else if (interfaceType == SouthConstants.INTERFACE_CORE) {
            ifaceBuilder.setDownlink();
        }
        return ifaceBuilder.build();
    }


    @Override
    public FlowRule farToFabricEntry(ForwardingActionRule far, DeviceId deviceId, ApplicationId appId, int priority)
            throws Up4TranslationException {

        PiAction action;
        if (far.isUplink()) {
            action = PiAction.builder()
                    .withId(SouthConstants.LOAD_FAR_NORMAL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(SouthConstants.DROP_FLAG, far.dropFlag() ? 1 : 0),
                            new PiActionParam(SouthConstants.NOTIFY_FLAG, far.notifyCpFlag() ? 1 : 0)
                    ))
                    .build();

        } else if (far.isDownlink()) {
            // TODO: copy tunnel destination port from logical switch write requests, instead of hardcoding 2152
            PiActionId actionId = far.bufferFlag() ? SouthConstants.LOAD_FAR_BUFFER : SouthConstants.LOAD_FAR_TUNNEL;
            action = PiAction.builder()
                    .withId(actionId)
                    .withParameters(Arrays.asList(
                            new PiActionParam(SouthConstants.DROP_FLAG, far.dropFlag() ? 1 : 0),
                            new PiActionParam(SouthConstants.NOTIFY_FLAG, far.notifyCpFlag() ? 1 : 0),
                            new PiActionParam(SouthConstants.TEID_PARAM, far.teid()),
                            new PiActionParam(SouthConstants.TUNNEL_SRC_PARAM, far.tunnelSrc().toInt()),
                            new PiActionParam(SouthConstants.TUNNEL_DST_PARAM, far.tunnelDst().toInt()),
                            new PiActionParam(SouthConstants.TUNNEL_DST_PORT_PARAM, 2152)
                    ))
                    .build();
        } else {
            throw new Up4TranslationException("Attempting to translate a FAR of unknown direction to fabric entry!");
        }
        PiCriterion match = PiCriterion.builder()
                .matchExact(SouthConstants.FAR_ID_KEY, globalFarIdOf(far.sessionId(), far.farId()))
                .build();
        return DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.FAR_TBL)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(priority)
                .build();
    }

    @Override
    public FlowRule pdrToFabricEntry(PacketDetectionRule pdr, DeviceId deviceId, ApplicationId appId, int priority)
            throws Up4TranslationException {
        PiCriterion match;
        PiTableId tableId;
        if (pdr.isUplink()) {
            match = PiCriterion.builder()
                    .matchExact(SouthConstants.TEID_KEY, pdr.teid().asArray())
                    .matchExact(SouthConstants.TUNNEL_DST_KEY, pdr.tunnelDest().toInt())
                    .build();
            tableId = SouthConstants.PDR_UPLINK_TBL;
        } else if (pdr.isDownlink()) {
            match = PiCriterion.builder()
                    .matchExact(SouthConstants.UE_ADDR_KEY, pdr.ueAddress().toInt())
                    .build();
            tableId = SouthConstants.PDR_DOWNLINK_TBL;
        } else {
            throw new Up4TranslationException("Flexible PDRs not yet supported! Cannot translate " + pdr.toString());
        }

        PiAction action = PiAction.builder()
                .withId(SouthConstants.LOAD_PDR)
                .withParameters(Arrays.asList(
                        new PiActionParam(SouthConstants.CTR_ID, pdr.counterId()),
                        new PiActionParam(SouthConstants.FAR_ID_PARAM, globalFarIdOf(pdr.sessionId(), pdr.farId())),
                        new PiActionParam(SouthConstants.NEEDS_GTPU_DECAP_PARAM, pdr.isUplink() ? 1 : 0)
                ))
                .build();

        return DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(tableId)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(priority)
                .build();
    }

    @Override
    public FlowRule interfaceToFabricEntry(UpfInterface upfInterface, DeviceId deviceId,
                                           ApplicationId appId, int priority) throws Up4TranslationException {
        int interfaceTypeInt;
        int gtpuValidity;
        int direction;
        if (upfInterface.isUplink()) {
            interfaceTypeInt = SouthConstants.INTERFACE_ACCESS;
            gtpuValidity = 1;
            direction = SouthConstants.DIRECTION_UPLINK;
        } else {
            interfaceTypeInt = SouthConstants.INTERFACE_CORE;
            gtpuValidity = 0;
            direction = SouthConstants.DIRECTION_DOWNLINK;
        }

        PiCriterion match = PiCriterion.builder()
                .matchLpm(SouthConstants.IPV4_DST_ADDR,
                        upfInterface.prefix().address().toInt(),
                        upfInterface.prefix().prefixLength())
                .matchExact(SouthConstants.GTPU_IS_VALID, gtpuValidity)
                .build();
        PiAction action = PiAction.builder()
                .withId(SouthConstants.SET_SOURCE_IFACE)
                .withParameters(Arrays.asList(
                        new PiActionParam(SouthConstants.SRC_IFACE_PARAM, interfaceTypeInt),
                        new PiActionParam(SouthConstants.DIRECTION_PARAM, direction),
                        new PiActionParam(SouthConstants.SKIP_SPGW_PARAM, 0)
                ))
                .build();
        return DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.INTERFACE_LOOKUP)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(priority)
                .build();
    }

    @Override
    public PiTableEntry farToUp4Entry(ForwardingActionRule far) throws Up4TranslationException {
        PiMatchKey matchKey;
        PiAction action;
        ImmutableByteSequence zeroByte = ImmutableByteSequence.ofZeros(1);
        ImmutableByteSequence oneByte = ImmutableByteSequence.ofOnes(1);
        if (far.isUplink()) {
            action = PiAction.builder()
                    .withId(NorthConstants.LOAD_FAR_NORMAL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.DROP_FLAG, far.dropFlag() ? oneByte : zeroByte),
                            new PiActionParam(NorthConstants.NOTIFY_FLAG, far.notifyCpFlag() ? oneByte : zeroByte)
                    ))
                    .build();
        } else if (far.isDownlink()) {
            action = PiAction.builder()
                    .withId(NorthConstants.LOAD_FAR_TUNNEL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.DROP_FLAG, far.dropFlag() ? oneByte : zeroByte),
                            new PiActionParam(NorthConstants.NOTIFY_FLAG, far.notifyCpFlag() ? oneByte : zeroByte),
                            new PiActionParam(NorthConstants.BUFFER_FLAG, far.bufferFlag() ? oneByte : zeroByte),
                            new PiActionParam(NorthConstants.TUNNEL_TYPE_PARAM,
                                    toImmutableByte(NorthConstants.TUNNEL_TYPE_GTPU)),
                            new PiActionParam(NorthConstants.TUNNEL_SRC_PARAM, far.tunnelSrc().toInt()),
                            new PiActionParam(NorthConstants.TUNNEL_DST_PARAM, far.tunnelDst().toInt()),
                            new PiActionParam(NorthConstants.TEID_PARAM, far.teid()),
                            // TODO: tunnel dport should be included in all north-south and south-north translation
                            new PiActionParam(NorthConstants.TUNNEL_DPORT_PARAM, ImmutableByteSequence.ofZeros(2))
                    ))
                    .build();
        } else {
            throw new Up4TranslationException(
                    "FARs that are not uplink or downlink cannot yet be translated northward!");
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
        if (pdr.isUplink()) {
            decapFlag = 1;  // Decap is true for uplink
            matchKey = PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SRC_IFACE_KEY,
                            toImmutableByte(NorthConstants.IFACE_ACCESS)))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.TEID_KEY, pdr.teid(), allOnes32))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.TUNNEL_DST_KEY,
                            ImmutableByteSequence.copyFrom(pdr.tunnelDest().toOctets()), allOnes32))
                    .build();
        } else if (pdr.isDownlink()) {
            decapFlag = 0;  // Decap is false for downlink
            matchKey = PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SRC_IFACE_KEY,
                            toImmutableByte(NorthConstants.IFACE_CORE)))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.UE_ADDR_KEY,
                            ImmutableByteSequence.copyFrom(pdr.ueAddress().toOctets()), allOnes32))
                    .build();
        } else {
            throw new Up4TranslationException(
                    "PDRs that are not uplink or downlink cannot yet be translated northward!");
        }
        // FIXME: pdr_id is not yet stored on writes so it cannot be read
        action = PiAction.builder()
                .withId(NorthConstants.LOAD_PDR)
                .withParameters(Arrays.asList(
                        new PiActionParam(NorthConstants.PDR_ID_PARAM, 0),
                        new PiActionParam(NorthConstants.SESSION_ID_PARAM, pdr.sessionId()),
                        new PiActionParam(NorthConstants.CTR_ID, pdr.counterId()),
                        new PiActionParam(NorthConstants.FAR_ID_PARAM, pdr.farId()),
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
        int srcIface = upfInterface.isUplink() ? NorthConstants.IFACE_ACCESS : NorthConstants.IFACE_CORE;
        int direction = upfInterface.isUplink() ? NorthConstants.DIRECTION_UPLINK : NorthConstants.DIRECTION_DOWNLINK;
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
