package org.omecproject.up4.behavior;

import org.apache.commons.lang3.tuple.Pair;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.GtpTunnel;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Translator;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.impl.NorthConstants;
import org.omecproject.up4.impl.SouthConstants;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onlab.util.KryoNamespace;
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
import org.onosproject.net.pi.runtime.PiTableAction;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.AtomicCounter;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
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

    private EventuallyConsistentMap<RuleIdentifier, Integer> localToGlobalFarId;
    private EventuallyConsistentMap<Integer, RuleIdentifier> globalToLocalFarId;
    private AtomicCounter globalFarIdCounter;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    @Activate
    protected void activate() {
        log.info("Starting...");
        globalFarIdCounter = storageService.getAtomicCounter("global-far-id-counter");

        KryoNamespace.Builder globalFarIdSerializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(RuleIdentifier.class);
        localToGlobalFarId = storageService.<RuleIdentifier, Integer>eventuallyConsistentMapBuilder()
                .withName("global-to-local-far-ids")
                .withSerializer(globalFarIdSerializer)
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .build();
        globalToLocalFarId = storageService.<Integer, RuleIdentifier>eventuallyConsistentMapBuilder()
                .withName("local-to-global-far-ids")
                .withSerializer(globalFarIdSerializer)
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .build();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }

    @Override
    public void reset() {
        localToGlobalFarId.clear();
        globalFarIdCounter.set(0);
        globalToLocalFarId.clear();
    }

    /**
     * Get a globally unique integer identifier for the FAR identified by the given (Session ID, Far ID) pair.
     *
     * @param farIdPair a RuleIdentifier instance uniquely identifying the FAR
     * @return A globally unique integer identifier
     */
    private int globalFarIdOf(RuleIdentifier farIdPair) {
        int globalFarId = localToGlobalFarId.compute(farIdPair,
                (k, existingId) -> {
                    if (existingId == null) {
                        return (int) globalFarIdCounter.incrementAndGet();
                    } else {
                        return existingId;
                    }
                });
        // use compute to avoid unnecessary writes, even though we ignore the return value
        globalToLocalFarId.compute(globalFarId,
                (k, existingId) -> {
                    if (existingId == null) {
                        return farIdPair;
                    } else {
                        return existingId;
                    }
                });
        log.info("{} translated to GlobalFarId={}", farIdPair, globalFarId);
        return globalFarId;
    }

    /**
     * Get a globally unique integer identifier for the FAR identified by the given (Session ID, Far ID) pair.
     *
     * @param pfcpSessionId     The ID of the PFCP session that produced the FAR ID.
     * @param sessionLocalFarId The FAR ID.
     * @return A globally unique integer identifier
     */
    private int globalFarIdOf(ImmutableByteSequence pfcpSessionId, int sessionLocalFarId) {
        RuleIdentifier farId = new RuleIdentifier(pfcpSessionId, sessionLocalFarId);
        return globalFarIdOf(farId);

    }

    public void assignGlobalFarId(PacketDetectionRule pdr) {
        pdr.setGlobalFarId(globalFarIdOf(pdr.sessionId(), pdr.localFarId()));
    }

    public void assignGlobalFarId(ForwardingActionRule far) {
        far.setGlobalFarId(globalFarIdOf(far.sessionId(), far.localFarId()));
    }

    /**
     * Get the corresponding PFCP session ID and session-local FAR ID from a globally unique FAR ID, or return
     * null if no such mapping is found.
     *
     * @param globalFarId globally unique FAR ID
     * @return the corresponding PFCP session ID and session-local FAR ID, as a RuleIdentifier
     */
    private RuleIdentifier localFarIdOf(int globalFarId) {
        return globalToLocalFarId.get(globalFarId);
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

        // UE Address key should always be present for fabric entries
        int globalFarId = TranslatorUtil.getParamInt(action, SouthConstants.FAR_ID_PARAM);
        RuleIdentifier farId = localFarIdOf(globalFarId);

        // So should all the action parameters
        pdrBuilder.withUeAddr(TranslatorUtil.getFieldAddress(match, SouthConstants.UE_ADDR_KEY))
                .withCounterId(TranslatorUtil.getParamInt(action, SouthConstants.CTR_ID))
                .withLocalFarId(farId.getSessionlocalId())
                .withGlobalFarId(globalFarId)
                .withSessionId(farId.getPfcpSessionId());

        // These keys are only present for uplink entries
        if (TranslatorUtil.fieldIsPresent(match, SouthConstants.TEID_KEY)) {
            ImmutableByteSequence teid = TranslatorUtil.getFieldValue(match, SouthConstants.TEID_KEY);
            Ip4Address tunnelDst = TranslatorUtil.getFieldAddress(match, SouthConstants.TUNNEL_DST_KEY);
            pdrBuilder.withTeid(teid)
                    .withTunnelDst(tunnelDst);
        }

        return pdrBuilder.build();
    }

    @Override
    public PacketDetectionRule up4EntryToPdr(PiTableEntry entry)
            throws Up4TranslationException {
        var pdrBuilder = PacketDetectionRule.builder();
        // Uplink and downlink both have a UE address key
        pdrBuilder.withUeAddr(TranslatorUtil.getFieldAddress(entry, NorthConstants.UE_ADDR_KEY));

        int srcInterface = TranslatorUtil.getFieldInt(entry, NorthConstants.SRC_IFACE_KEY);
        if (srcInterface == NorthConstants.IFACE_ACCESS) {
            // uplink entries will also have a F-TEID key (tunnel destination address + TEID)
            pdrBuilder.withTunnel(TranslatorUtil.getFieldValue(entry, NorthConstants.TEID_KEY),
                    TranslatorUtil.getFieldAddress(entry, NorthConstants.TUNNEL_DST_KEY));
        } else if (srcInterface != NorthConstants.IFACE_CORE) {
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
                    .withLocalFarId(localFarId)
                    .withGlobalFarId(globalFarIdOf(sessionId, localFarId));
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
        RuleIdentifier farId = localFarIdOf(globalFarId);

        boolean dropFlag = TranslatorUtil.getParamInt(action, SouthConstants.DROP_FLAG) > 0;
        boolean notifyFlag = TranslatorUtil.getParamInt(action, SouthConstants.NOTIFY_FLAG) > 0;

        // Match keys
        farBuilder.withGlobalFarId(globalFarId)
                .withSessionId(farId.getPfcpSessionId())
                .withFarId(farId.getSessionlocalId());

        // Parameters common to uplink and downlink should always be present
        farBuilder.withDropFlag(dropFlag)
                .withNotifyFlag(notifyFlag);

        if (TranslatorUtil.paramIsPresent(action, SouthConstants.TEID_PARAM)) {
            // Grab parameters specific to downlink FARs if they're present
            Ip4Address tunnelSrc = TranslatorUtil.getParamAddress(action, SouthConstants.TUNNEL_SRC_PARAM);
            Ip4Address tunnelDst = TranslatorUtil.getParamAddress(action, SouthConstants.TUNNEL_DST_PARAM);
            ImmutableByteSequence teid = TranslatorUtil.getParamValue(action, SouthConstants.TEID_PARAM);

            farBuilder.withTunnel(GtpTunnel.builder()
                    .setSrc(tunnelSrc)
                    .setDst(tunnelDst)
                    .setTeid(teid)
                    .build());
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
                .withSessionId(sessionId)
                .withGlobalFarId(globalFarIdOf(sessionId, localFarId));

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
                        TranslatorUtil.getParamValue(entry, NorthConstants.TEID_PARAM));
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
        if (!far.hasGlobalFarId()) {
            log.warn("FAR received with no global FAR ID!");
            assignGlobalFarId(far);
        }
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
            action = PiAction.builder()
                    .withId(SouthConstants.LOAD_FAR_TUNNEL)
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
                .matchExact(SouthConstants.FAR_ID_KEY, far.getGlobalFarId())
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
        if (!pdr.hasGlobalFarId()) {
            log.warn("PDR received with no global FAR ID!");
            assignGlobalFarId(pdr);
        }
        PiCriterion match;
        PiTableId tableId;
        if (pdr.isUplink()) {
            match = PiCriterion.builder()
                    .matchExact(SouthConstants.UE_ADDR_KEY, pdr.ueAddress().toInt())
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
                        new PiActionParam(SouthConstants.FAR_ID_PARAM, pdr.getGlobalFarId()),
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

    /**
     * Wrapper for identifying information of FARs and PDRs.
     */
    public static final class RuleIdentifier {
        final int sessionlocalId;
        final ImmutableByteSequence pfcpSessionId;

        /**
         * A PDR or FAR can be globally uniquely identified by the combination of the ID of the PFCP session that
         * produced it, and the ID that the rule was assigned in that PFCP session.
         *
         * @param pfcpSessionId  The PFCP session that produced the rule ID
         * @param sessionlocalId The rule ID
         */
        public RuleIdentifier(ImmutableByteSequence pfcpSessionId, int sessionlocalId) {
            this.pfcpSessionId = pfcpSessionId;
            this.sessionlocalId = sessionlocalId;
        }

        public int getSessionlocalId() {
            return sessionlocalId;
        }

        public ImmutableByteSequence getPfcpSessionId() {
            return pfcpSessionId;
        }

        @Override
        public String toString() {
            return "RuleIdentifier{" +
                    "sessionlocalId=" + sessionlocalId +
                    ", pfcpSessionId=" + pfcpSessionId +
                    '}';
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RuleIdentifier that = (RuleIdentifier) obj;
            return (this.sessionlocalId == that.sessionlocalId) && (this.pfcpSessionId.equals(that.pfcpSessionId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.sessionlocalId, this.pfcpSessionId);
        }
    }
}
