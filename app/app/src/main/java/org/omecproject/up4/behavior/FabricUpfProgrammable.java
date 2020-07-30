package org.omecproject.up4.behavior;

import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.UpfProgrammable;
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
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiCounterCellHandle;
import org.onosproject.net.pi.runtime.PiCounterCellId;
import org.onosproject.net.pi.service.PiPipeconfService;
import org.onosproject.p4runtime.api.P4RuntimeClient;
import org.onosproject.p4runtime.api.P4RuntimeController;
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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.onosproject.net.pi.model.PiCounterType.INDIRECT;

/**
 * Implementation of a UPF programmable device behavior.
 * TODO: this needs to be moved to
 * onos/pipelines/fabric/impl/src/main/java/org/onosproject/pipelines/fabric/impl/behaviour/up4/
 * and referenced as upfProgrammable = deviceService.getDevice(deviceId).as(UpfProgrammable.class);
 */
@Component(immediate = true,
        service = {UpfProgrammable.class})
public class FabricUpfProgrammable implements UpfProgrammable {

    private static final int DEFAULT_PRIORITY = 128;
    private static final long DEFAULT_P4_DEVICE_ID = 1;
    private final Logger log = LoggerFactory.getLogger(getClass());
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected P4RuntimeController controller;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PiPipeconfService piPipeconfService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;
    DeviceId deviceId;
    private EventuallyConsistentMap<FarIdPair, Integer> globalFarIds;
    private AtomicCounter globalFarIdCounter;
    private ApplicationId appId;

    @Activate
    protected void activate() {
        globalFarIdCounter = storageService.getAtomicCounter("global-far-id-counter");

        KryoNamespace.Builder globalFarIdSerializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(FarIdPair.class);
        globalFarIds = storageService.<FarIdPair, Integer>eventuallyConsistentMapBuilder()
                .withName("global-far-ids")
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
    public boolean init(ApplicationId appId, DeviceId deviceId) {
        this.appId = appId;
        this.deviceId = deviceId;
        return true;
    }

    @Override
    public void cleanUp(ApplicationId appId) {
        log.info("Clearing all UPF-related table entries.");
        flowRuleService.removeFlowRulesById(appId);
        globalFarIds.clear();
        globalFarIdCounter.set(0);
    }

    @Override
    public DeviceId deviceId() {
        return this.deviceId;
    }


    @Override
    public PdrStats readCounter(int cellId) {
        PdrStats.Builder stats = PdrStats.builder().withCellId(cellId);

        // Get client and pipeconf.
        P4RuntimeClient client = controller.get(deviceId);
        if (client == null) {
            log.warn("Unable to find client for {}, aborting operation", deviceId);
            return stats.build();
        }
        Optional<PiPipeconf> optPipeconf = piPipeconfService.getPipeconf(deviceId);
        if (optPipeconf.isEmpty()) {
            log.warn("Unable to load piPipeconf for {}, aborting operation", deviceId);
            return stats.build();
        }
        PiPipeconf pipeconf = optPipeconf.get();


        // Make list of cell handles we want to read.
        List<PiCounterCellHandle> counterCellHandles = List.of(
                PiCounterCellHandle.of(deviceId,
                        PiCounterCellId.ofIndirect(SouthConstants.INGRESS_COUNTER_ID, cellId)),
                PiCounterCellHandle.of(deviceId,
                        PiCounterCellId.ofIndirect(SouthConstants.EGRESS_COUNTER_ID, cellId)));

        // Query the device.
        Collection<PiCounterCell> counterEntryResponse = client.read(
                DEFAULT_P4_DEVICE_ID, pipeconf)
                .handles(counterCellHandles).submitSync()
                .all(PiCounterCell.class);

        // Process response.
        counterEntryResponse.forEach(counterCell -> {
            if (counterCell.cellId().counterType() != INDIRECT) {
                log.warn("Invalid counter data type {}, skipping", counterCell.cellId().counterType());
                return;
            }
            if (cellId != counterCell.cellId().index()) {
                log.warn("Unrecognized counter index {}, skipping", counterCell);
                return;
            }
            if (counterCell.cellId().counterId().equals(SouthConstants.INGRESS_COUNTER_ID)) {
                stats.setIngress(counterCell.data().packets(), counterCell.data().bytes());
            } else if (counterCell.cellId().counterId().equals(SouthConstants.EGRESS_COUNTER_ID)) {
                stats.setEgress(counterCell.data().packets(), counterCell.data().bytes());
            } else {
                log.warn("Unrecognized counter ID {}, skipping", counterCell);
            }
        });
        return stats.build();
    }


    @Override
    public void addPdr(PacketDetectionRule pdr) {
        int globalFarId = globalFarIdOf(pdr.sessionId(), pdr.localFarId());
        pdr.setGlobalFarId(globalFarId);
        log.info("Installing {}", pdr.toString());
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
            log.error("Flexible PDRs not yet supported! Ignoring.");
            return;
        }

        PiAction action = PiAction.builder()
                .withId(SouthConstants.LOAD_PDR)
                .withParameters(Arrays.asList(
                        new PiActionParam(SouthConstants.CTR_ID, pdr.counterId()),
                        new PiActionParam(SouthConstants.FAR_ID_PARAM, pdr.getGlobalFarId()),
                        new PiActionParam(SouthConstants.NEEDS_GTPU_DECAP_PARAM, pdr.isUplink() ? 1 : 0)
                ))
                .build();

        FlowRule pdrEntry = DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(tableId)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();

        flowRuleService.applyFlowRules(pdrEntry);
        log.debug("Added PDR with flowID {}", pdrEntry.id().value());
    }


    @Override
    public void addFar(ForwardingActionRule far) {
        int globalFarId = globalFarIdOf(far.sessionId(), far.localFarId());
        far.setGlobalFarId(globalFarId);
        log.info("Installing {}", far.toString());
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
            log.error("Attempting to add unknown type of FAR!");
            return;
        }

        PiCriterion match = PiCriterion.builder()
                .matchExact(SouthConstants.FAR_ID_KEY, far.getGlobalFarId())
                .build();
        FlowRule farEntry = DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.FAR_TBL)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
        flowRuleService.applyFlowRules(farEntry);
        log.debug("FAR added with flowID {}", farEntry.id().value());
    }


    @Override
    public void addS1uInterface(Ip4Address s1uAddr) {
        log.info("Adding S1U interface with address {}", s1uAddr);
        // TODO: copy s1u address prefix len from logical swich writes, instead of hardcoding 32
        PiCriterion match = PiCriterion.builder()
                .matchLpm(SouthConstants.IPV4_DST_ADDR, s1uAddr.toInt(), 32)
                .matchExact(SouthConstants.GTPU_IS_VALID, 1)  // Tunnel present in uplink direction
                .build();
        PiAction action = PiAction.builder()
                .withId(SouthConstants.SET_SOURCE_IFACE)
                .withParameters(Arrays.asList(
                        new PiActionParam(SouthConstants.SRC_IFACE_PARAM, SouthConstants.INTERFACE_ACCESS),
                        new PiActionParam(SouthConstants.DIRECTION_PARAM, SouthConstants.DIRECTION_UPLINK),
                        new PiActionParam(SouthConstants.SKIP_SPGW_PARAM, 0)
                ))
                .build();
        FlowRule s1uEntry = DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.INTERFACE_LOOKUP)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
        flowRuleService.applyFlowRules(s1uEntry);
        log.debug("Added S1U entry with flowID {}", s1uEntry.id().value());
    }

    @Override
    public void addUePool(Ip4Prefix poolPrefix) {
        log.info("Adding UE IPv4 Pool prefix {}", poolPrefix);
        PiCriterion match = PiCriterion.builder()
                .matchLpm(SouthConstants.IPV4_DST_ADDR, poolPrefix.address().toInt(), poolPrefix.prefixLength())
                .matchExact(SouthConstants.GTPU_IS_VALID, 0)  // No tunnel in downlink direction
                .build();
        PiAction action = PiAction.builder()
                .withId(SouthConstants.SET_SOURCE_IFACE)
                .withParameters(Arrays.asList(
                        new PiActionParam(SouthConstants.SRC_IFACE_PARAM, SouthConstants.INTERFACE_CORE),
                        new PiActionParam(SouthConstants.DIRECTION_PARAM, SouthConstants.DIRECTION_DOWNLINK),
                        new PiActionParam(SouthConstants.SKIP_SPGW_PARAM, 0)
                ))
                .build();
        FlowRule uePoolEntry = DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.INTERFACE_LOOKUP)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
        flowRuleService.applyFlowRules(uePoolEntry);
        log.debug("Added UE IPv4 pool entry with flowID {}", uePoolEntry.id().value());
    }


    private boolean removeEntry(PiCriterion match, PiTableId tableId, boolean failSilent) {
        FlowRule entry = DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(tableId)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();

        /*
         *  FIXME: Stupid stupid slow hack, needed because removeFlowRules expects FlowRule objects
         *   with correct and complete actions and parameters, but P4Runtime deletion requests
         *   will not have those.
         */
        for (FlowEntry installedEntry : flowRuleService.getFlowEntriesById(appId)) {
            if (installedEntry.selector().equals(entry.selector())) {
                log.info("Found matching entry to remove, it has FlowID {}", installedEntry.id());
                flowRuleService.removeFlowRules(installedEntry);
                return true;
            }
        }
        if (!failSilent) {
            log.error("Did not find a flow rule with the given match conditions! Deleting nothing.");
        }
        return false;
    }

    @Override
    public void removePdr(PacketDetectionRule pdr) {
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
            log.error("Removal of flexible PDRs not yet supported.");
            return;
        }
        log.info("Removing {}", pdr.toString());
        removeEntry(match, tableId, false);
    }

    @Override
    public void removeFar(ForwardingActionRule far) {
        int globalFarId = globalFarIdOf(far.sessionId(), far.localFarId());
        far.setGlobalFarId(globalFarId);
        log.info("Removing {}", far.toString());

        PiCriterion match = PiCriterion.builder()
                .matchExact(SouthConstants.FAR_ID_KEY, far.getGlobalFarId())
                .build();

        removeEntry(match, SouthConstants.FAR_TBL, false);
    }

    @Override
    public void removeUePool(Ip4Prefix poolPrefix) {
        log.info("Removing S1U interface table entry");
        PiCriterion match = PiCriterion.builder()
                .matchLpm(SouthConstants.IPV4_DST_ADDR, poolPrefix.address().toInt(), poolPrefix.prefixLength())
                .matchExact(SouthConstants.GTPU_IS_VALID, 0)
                .build();
        removeEntry(match, SouthConstants.INTERFACE_LOOKUP, false);
    }

    @Override
    public void removeS1uInterface(Ip4Address s1uAddr) {
        log.info("Removing S1U interface table entry");
        PiCriterion match = PiCriterion.builder()
                .matchLpm(SouthConstants.IPV4_DST_ADDR, s1uAddr.toInt(), 32)
                .matchExact(SouthConstants.GTPU_IS_VALID, 1)
                .build();
        removeEntry(match, SouthConstants.INTERFACE_LOOKUP, false);
    }

    @Override
    public void removeUnknownInterface(Ip4Prefix ifacePrefix) {
        // For when you don't know if its a uePool or s1uInterface table entry
        // Try removing an S1U entry
        PiCriterion match1 = PiCriterion.builder()
                .matchLpm(SouthConstants.IPV4_DST_ADDR, ifacePrefix.address().toInt(), 32)
                .matchExact(SouthConstants.GTPU_IS_VALID, 1)
                .build();
        if (removeEntry(match1, SouthConstants.INTERFACE_LOOKUP, true)) {
            return;
        }
        // If that didn't work, try removing a UE pool entry
        PiCriterion match2 = PiCriterion.builder()
                .matchLpm(SouthConstants.IPV4_DST_ADDR, ifacePrefix.address().toInt(), ifacePrefix.prefixLength())
                .matchExact(SouthConstants.GTPU_IS_VALID, 0)
                .build();
        if (!removeEntry(match2, SouthConstants.INTERFACE_LOOKUP, true)) {
            log.error("Could not remove interface! No matching entry found!");
        }
    }

    /**
     * Wrapper for identifying information of Forwarding Action Rules.
     */
    private static final class FarIdPair {
        final int sessionlocalId;
        final ImmutableByteSequence pfcpSessionId;

        /**
         * A FAR can be globally uniquely identified by the combination of the ID of the PFCP session that
         * produced it, and the ID that the FAR was assigned in that PFCP session.
         *
         * @param pfcpSessionId  The PFCP session that produced the FAR ID
         * @param sessionlocalId The FAR ID
         */
        public FarIdPair(ImmutableByteSequence pfcpSessionId, int sessionlocalId) {
            this.pfcpSessionId = pfcpSessionId;
            this.sessionlocalId = sessionlocalId;
        }

        @Override
        public String toString() {
            return "FarIdPair{" +
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
            FarIdPair that = (FarIdPair) obj;
            return (this.sessionlocalId == that.sessionlocalId) && (this.pfcpSessionId.equals(that.pfcpSessionId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.sessionlocalId, this.pfcpSessionId);
        }
    }

    private int globalFarIdOf(FarIdPair farIdPair) {
        int globalFarId = globalFarIds.compute(farIdPair,
                (k, existingId) -> {
                    if (existingId == null) {
                        return (int) globalFarIdCounter.incrementAndGet();
                    } else {
                        return existingId;
                    }
                });
        log.debug("{} translated to GlobalFarId={}", farIdPair, globalFarId);
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
        FarIdPair farId = new FarIdPair(pfcpSessionId, sessionLocalFarId);
        return globalFarIdOf(farId);

    }


}
