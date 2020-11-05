package org.omecproject.up4.behavior;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.GtpTunnel;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.Up4Exception;
import org.omecproject.up4.Up4Translator;
import org.omecproject.up4.UpfFlow;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammable;
import org.omecproject.up4.UpfRuleIdentifier;
import org.omecproject.up4.impl.SouthConstants;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.pi.model.PiCounterModel;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiCounterCellHandle;
import org.onosproject.net.pi.runtime.PiCounterCellId;
import org.onosproject.net.pi.service.PiPipeconfService;
import org.onosproject.p4runtime.api.P4RuntimeClient;
import org.onosproject.p4runtime.api.P4RuntimeController;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    protected Up4Translator up4Translator;
    DeviceId deviceId;
    private ApplicationId appId;

    @VisibleForTesting
    int pdrCounterSize = -1;  // Cached value so we don't have to go through the pipeconf every time
    private BufferDrainer bufferDrainer;


    private Set<UpfRuleIdentifier> bufferFarIds;
    private Map<UpfRuleIdentifier, Set<PacketDetectionRule>> farIdToPdrs;

    @Activate
    protected void activate() {
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }

    @Override
    public boolean init(ApplicationId appId, DeviceId deviceId) {
        this.bufferFarIds = new HashSet<>();
        this.farIdToPdrs = new HashMap<>();
        this.appId = appId;
        this.deviceId = deviceId;
        log.info("UpfProgrammable initialized for appId {} and deviceId {}", appId, deviceId);
        return true;
    }

    @Override
    public void setBufferDrainer(BufferDrainer drainer) {
        this.bufferDrainer = drainer;
    }

    private GtpTunnel dbufTunnel;

    @Override
    public void setDbufTunnel(Ip4Address switchAddr, Ip4Address dbufAddr) {
        this.dbufTunnel = GtpTunnel.builder()
                .setSrc(switchAddr)
                .setDst(dbufAddr)
                .setSrcPort((short) 2152)
                .setTeid(0)
                .build();
    }

    /**
     * Convert the given buffering FAR to a FAR that tunnels the packet to dbuf.
     *
     * @param far the FAR to convert
     * @return the converted FAR
     */
    private ForwardingActionRule convertToDbufFar(ForwardingActionRule far) {
        if (!far.bufferFlag()) {
            log.error("Converting a non-buffering FAR to a dbuf FAR! This shouldn't happen.");
        }
        return ForwardingActionRule.builder()
                .withFarId(far.farId())
                .withSessionId(far.sessionId())
                .withDropFlag(far.dropFlag())
                .withNotifyFlag(far.notifyCpFlag())
                .withBufferFlag(true)
                .withTunnel(dbufTunnel)
                .build();
    }

    @Override
    public void cleanUp(ApplicationId appId) {
        log.info("Clearing all UPF-related table entries.");
        flowRuleService.removeFlowRulesById(appId);
        up4Translator.reset();
    }

    @Override
    public void clearInterfaces() {
        log.info("Clearing all UPF interfaces.");
        for (FlowRule entry : flowRuleService.getFlowEntriesById(appId)) {
            if (up4Translator.isFabricInterface(entry)) {
                flowRuleService.removeFlowRules(entry);
            }
        }
    }

    @Override
    public void clearFlows() {
        log.info("Clearing all UE sessions.");
        int pdrsCleared = 0;
        int farsCleared = 0;
        for (FlowRule entry : flowRuleService.getFlowEntriesById(appId)) {
            if (up4Translator.isFabricPdr(entry)) {
                pdrsCleared++;
                flowRuleService.removeFlowRules(entry);
            } else if (up4Translator.isFabricFar(entry)) {
                farsCleared++;
                flowRuleService.removeFlowRules(entry);
            }
        }
        log.info("Cleared {} PDRs and {} FARS.", pdrsCleared, farsCleared);
    }

    @Override
    public DeviceId deviceId() {
        return this.deviceId;
    }


    @Override
    public Collection<PdrStats> readAllCounters() {
        // Get client and pipeconf.
        P4RuntimeClient client = controller.get(deviceId);
        if (client == null) {
            log.warn("Unable to find client for {}, aborting operation", deviceId);
            return List.of();
        }
        Optional<PiPipeconf> optPipeconf = piPipeconfService.getPipeconf(deviceId);
        if (optPipeconf.isEmpty()) {
            log.warn("Unable to load piPipeconf for {}, aborting operation", deviceId);
            return List.of();
        }
        PiPipeconf pipeconf = optPipeconf.get();

        // Prepare PdrStats object builders, one for each counter ID currently in use
        Map<Integer, PdrStats.Builder> pdrStatBuilders = Maps.newHashMap();
        for (int cellId = 0; cellId < pdrCounterSize(); cellId++) {
            pdrStatBuilders.put(cellId, PdrStats.builder().withCellId(cellId));
        }

        // Generate the counter cell IDs.
        Set<PiCounterCellId> counterCellIds = Sets.newHashSet();
        pdrStatBuilders.keySet().forEach(cellId -> {
            counterCellIds.add(PiCounterCellId.ofIndirect(SouthConstants.FABRIC_INGRESS_SPGW_PDR_COUNTER, cellId));
            counterCellIds.add(PiCounterCellId.ofIndirect(SouthConstants.FABRIC_EGRESS_SPGW_PDR_COUNTER, cellId));
        });
        Set<PiCounterCellHandle> counterCellHandles = counterCellIds.stream()
                .map(id -> PiCounterCellHandle.of(deviceId, id))
                .collect(Collectors.toSet());

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
            if (!pdrStatBuilders.containsKey((int) counterCell.cellId().index())) {
                log.warn("Unrecognized counter index {}, skipping", counterCell);
                return;
            }
            PdrStats.Builder statsBuilder = pdrStatBuilders.get((int) counterCell.cellId().index());
            if (counterCell.cellId().counterId().equals(SouthConstants.FABRIC_INGRESS_SPGW_PDR_COUNTER)) {
                statsBuilder.setIngress(counterCell.data().packets(),
                        counterCell.data().bytes());
            } else if (counterCell.cellId().counterId().equals(SouthConstants.FABRIC_EGRESS_SPGW_PDR_COUNTER)) {
                statsBuilder.setEgress(counterCell.data().packets(),
                        counterCell.data().bytes());
            } else {
                log.warn("Unrecognized counter ID {}, skipping", counterCell);
            }
        });

        return pdrStatBuilders
                .values()
                .stream()
                .map(PdrStats.Builder::build)
                .collect(Collectors.toList());
    }

    @Override
    public int pdrCounterSize() {
        if (pdrCounterSize != -1) {
            return pdrCounterSize;
        }
        Optional<PiPipeconf> optPipeconf = piPipeconfService.getPipeconf(deviceId);
        if (optPipeconf.isEmpty()) {
            log.warn("Unable to load piPipeconf for {}, cannot read counter properties", deviceId);
            return -1;
        }
        PiPipeconf pipeconf = optPipeconf.get();

        int ingressCounterSize = -1;
        int egressCounterSize = -1;
        for (PiCounterModel piCounter : pipeconf.pipelineModel().counters()) {
            if (piCounter.id().equals(SouthConstants.FABRIC_INGRESS_SPGW_PDR_COUNTER)) {
                ingressCounterSize = (int) piCounter.size();
            } else if (piCounter.id().equals(SouthConstants.FABRIC_EGRESS_SPGW_PDR_COUNTER)) {
                egressCounterSize = (int) piCounter.size();
            }
        }
        if (ingressCounterSize != egressCounterSize) {
            log.warn("PDR ingress and egress counter sizes are not equal! Using the minimum of the two.");
        }
        pdrCounterSize = Math.min(ingressCounterSize, egressCounterSize);
        return pdrCounterSize;
    }

    @Override
    public PdrStats readCounter(int cellId) throws Up4Exception {
        if (cellId >= pdrCounterSize() || cellId < 0) {
            throw new Up4Exception(Up4Exception.Type.INVALID_COUNTER_INDEX,
                    "Requested PDR counter cell index is out of bounds.");
        }
        PdrStats.Builder stats = PdrStats.builder().withCellId(cellId);

        // Get client and pipeconf.
        P4RuntimeClient client = controller.get(deviceId);
        if (client == null) {
            log.warn("Unable to find p4runtime client for device {}.", deviceId);
            throw new Up4Exception(Up4Exception.Type.SWITCH_UNAVAILABLE,
                    "Unable to find p4runtime client for device " + deviceId.toString());
        }
        Optional<PiPipeconf> optPipeconf = piPipeconfService.getPipeconf(deviceId);
        if (optPipeconf.isEmpty()) {
            log.warn("Unable to load pipeconf for device {}", deviceId);
            throw new Up4Exception(Up4Exception.Type.SWITCH_UNAVAILABLE,
                    "Unable to load pipeconf for device " + deviceId.toString());
        }
        PiPipeconf pipeconf = optPipeconf.get();


        // Make list of cell handles we want to read.
        List<PiCounterCellHandle> counterCellHandles = List.of(
                PiCounterCellHandle.of(deviceId,
                        PiCounterCellId.ofIndirect(SouthConstants.FABRIC_INGRESS_SPGW_PDR_COUNTER, cellId)),
                PiCounterCellHandle.of(deviceId,
                        PiCounterCellId.ofIndirect(SouthConstants.FABRIC_EGRESS_SPGW_PDR_COUNTER, cellId)));

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
            if (counterCell.cellId().counterId().equals(SouthConstants.FABRIC_INGRESS_SPGW_PDR_COUNTER)) {
                stats.setIngress(counterCell.data().packets(), counterCell.data().bytes());
            } else if (counterCell.cellId().counterId().equals(SouthConstants.FABRIC_EGRESS_SPGW_PDR_COUNTER)) {
                stats.setEgress(counterCell.data().packets(), counterCell.data().bytes());
            } else {
                log.warn("Unrecognized counter ID {}, skipping", counterCell);
            }
        });
        return stats.build();
    }


    @Override
    public void addPdr(PacketDetectionRule pdr) throws Up4Exception {
        if (pdr.counterId() >= pdrCounterSize() || pdr.counterId() < 0) {
            throw new Up4Exception(Up4Exception.Type.INVALID_COUNTER_INDEX,
                    "Counter cell index referenced by PDR is out of bounds.");
        }
        try {
            FlowRule fabricPdr = up4Translator.pdrToFabricEntry(pdr, deviceId, appId, DEFAULT_PRIORITY);
            log.info("Installing {}", pdr.toString());
            flowRuleService.applyFlowRules(fabricPdr);
            log.debug("FAR added with flowID {}", fabricPdr.id().value());

            // If the flow rule was applied, add the PDR to the farID->PDR mapping
            UpfRuleIdentifier ruleId = UpfRuleIdentifier.of(pdr.sessionId(), pdr.farId());
            farIdToPdrs.compute(ruleId, (k, existingSet) -> {
                if (existingSet == null) {
                    Set<PacketDetectionRule> newSet = new HashSet<>();
                    newSet.add(pdr);
                    return newSet;
                } else {
                    existingSet.add(pdr);
                    return existingSet;
                }
            });
        } catch (Up4Translator.Up4TranslationException e) {
            log.warn("Unable to insert PDR {} to dataplane: {}", pdr, e.getMessage());
            throw new Up4Exception(Up4Exception.Type.INVALID_FAR,
                    e.getMessage());
        }
    }


    @Override
    public void addFar(ForwardingActionRule far) throws Up4Exception {
        try {
            UpfRuleIdentifier ruleId = UpfRuleIdentifier.of(far.sessionId(), far.farId());
            if (far.bufferFlag()) {
                // If the far has the buffer flag, modify its tunnel so it directs to dbuf
                far = convertToDbufFar(far);
                bufferFarIds.add(ruleId);
            }
            FlowRule fabricFar = up4Translator.farToFabricEntry(far, deviceId, appId, DEFAULT_PRIORITY);
            log.info("Installing {}", far.toString());
            flowRuleService.applyFlowRules(fabricFar);
            log.debug("FAR added with flowID {}", fabricFar.id().value());
            if (!far.bufferFlag() && bufferFarIds.contains(ruleId)) {
                // If this FAR does not buffer but used to, then drain all relevant buffers
                bufferFarIds.remove(ruleId);
                for (var pdr : farIdToPdrs.getOrDefault(ruleId, Set.of())) {
                    // Drain the buffer for every UE address that hits this FAR
                    bufferDrainer.drain(pdr.ueAddress());
                }
            }
        } catch (Up4Translator.Up4TranslationException e) {
            log.warn("Unable to insert FAR {} to dataplane: {}", far, e.getMessage());
            throw new Up4Exception(Up4Exception.Type.INVALID_PDR,
                    e.getMessage());
        }
    }

    @Override
    public void addInterface(UpfInterface upfInterface) throws Up4Exception {
        try {
            FlowRule flowRule = up4Translator.interfaceToFabricEntry(upfInterface, deviceId, appId, DEFAULT_PRIORITY);
            log.info("Installing {}", upfInterface);
            flowRuleService.applyFlowRules(flowRule);
            log.debug("Interface added with flowID {}", flowRule.id().value());
        } catch (Up4Translator.Up4TranslationException e) {
            log.warn("Unable to insert interface {} to dataplane: {}", upfInterface, e.getMessage());
            throw new Up4Exception(Up4Exception.Type.INVALID_INTERFACE, e.getMessage());
        }
    }

    private boolean removeEntry(PiCriterion match, PiTableId tableId, boolean failSilent) throws Up4Exception {
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
            log.warn("Did not find a flow rule with the given match conditions! Deleting nothing.");
            throw new Up4Exception(Up4Exception.Type.ENTRY_NOT_FOUND,
                    "Entry for table " + tableId.toString() + " not found");
        }
        return false;
    }

    @Override
    public Collection<UpfFlow> getFlows() {
        Map<Integer, PdrStats> counterStats = new HashMap<>();
        readAllCounters().forEach(stats -> {
            counterStats.put(stats.getCellId(), stats);
        });
        // A flow is made of a PDR and the FAR that should apply to packets that hit the PDR.
        // Multiple PDRs can map to the same FAR, so create a one->many mapping of FAR Identifier to flow builder
        Map<UpfRuleIdentifier, List<UpfFlow.Builder>> globalFarToSessionBuilder = new HashMap<>();
        List<ForwardingActionRule> fars = new ArrayList<>();
        for (FlowRule flowRule : flowRuleService.getFlowEntriesById(appId)) {
            if (up4Translator.isFabricFar(flowRule)) {
                // If its a far, save it for later
                try {
                    fars.add(up4Translator.fabricEntryToFar(flowRule));
                } catch (Up4Translator.Up4TranslationException e) {
                    log.warn("Found what appears to be a FAR but we can't translate it?? {}", flowRule);
                }
            } else if (up4Translator.isFabricPdr(flowRule)) {
                // If its a PDR, create a flow builder for it
                try {
                    PacketDetectionRule pdr = up4Translator.fabricEntryToPdr(flowRule);
                    globalFarToSessionBuilder.compute(new UpfRuleIdentifier(pdr.sessionId(), pdr.farId()),
                            (k, existingVal) -> {
                                final var builder = UpfFlow.builder()
                                        .setPdr(pdr)
                                        .addStats(counterStats.get(pdr.counterId()));
                                if (existingVal == null) {
                                    return List.of(builder);
                                } else {
                                    existingVal.add(builder);
                                    return existingVal;
                                }
                            });
                } catch (Up4Translator.Up4TranslationException e) {
                    log.warn("Found what appears to be a PDR but we can't translate it?? {}", flowRule);
                }
            }
        }
        for (ForwardingActionRule far : fars) {
            globalFarToSessionBuilder.compute(new UpfRuleIdentifier(far.sessionId(), far.farId()),
                    (k, builderList) -> {
                        // If no PDRs use this FAR, then create a new flow with no PDR
                        if (builderList == null) {
                            return List.of(UpfFlow.builder().setFar(far));
                        } else {
                            // Add the FAR to every flow with a PDR that references it
                            for (var builder : builderList) {
                                builder.setFar(far);
                            }
                            return builderList;
                        }
                    });
        }
        List<UpfFlow> results = new ArrayList<>();
        for (var builderList : globalFarToSessionBuilder.values()) {
            for (var builder : builderList) {
                try {
                    UpfFlow flow = builder.build();
                    results.add(builder.build());
                } catch (java.lang.IllegalArgumentException e) {
                    log.warn("Corrupt UPF flow found in dataplane: {}", e.getMessage());
                }
            }
        }
        return results;
    }

    @Override
    public Collection<PacketDetectionRule> getInstalledPdrs() {
        ArrayList<PacketDetectionRule> pdrs = new ArrayList<>();
        for (FlowRule flowRule : flowRuleService.getFlowEntriesById(appId)) {
            if (up4Translator.isFabricPdr(flowRule)) {
                try {
                    pdrs.add(up4Translator.fabricEntryToPdr(flowRule));
                } catch (Up4Translator.Up4TranslationException e) {
                    log.warn("Found what appears to be a PDR but we can't translate it?? {}", flowRule);
                }
            }
        }
        return pdrs;
    }

    @Override
    public Collection<ForwardingActionRule> getInstalledFars() {
        ArrayList<ForwardingActionRule> fars = new ArrayList<>();
        for (FlowRule flowRule : flowRuleService.getFlowEntriesById(appId)) {
            if (up4Translator.isFabricFar(flowRule)) {
                try {
                    fars.add(up4Translator.fabricEntryToFar(flowRule));
                } catch (Up4Translator.Up4TranslationException e) {
                    log.warn("Found what appears to be a FAR but we can't translate it?? {}", flowRule);
                }
            }
        }
        return fars;
    }

    @Override
    public Collection<UpfInterface> getInstalledInterfaces() {
        ArrayList<UpfInterface> ifaces = new ArrayList<>();
        for (FlowRule flowRule : flowRuleService.getFlowEntriesById(appId)) {
            if (up4Translator.isFabricInterface(flowRule)) {
                try {
                    ifaces.add(up4Translator.fabricEntryToInterface(flowRule));
                } catch (Up4Translator.Up4TranslationException e) {
                    log.warn("Found what appears to be a interface table entry but we can't translate it?? {}",
                            flowRule);
                }
            }
        }
        return ifaces;
    }


    @Override
    public void removePdr(PacketDetectionRule pdr) throws Up4Exception {
        PiCriterion match;
        PiTableId tableId;
        if (pdr.isUplink()) {
            match = PiCriterion.builder()
                    .matchExact(SouthConstants.HDR_TEID, pdr.teid().asArray())
                    .matchExact(SouthConstants.HDR_TUNNEL_IPV4_DST, pdr.tunnelDest().toInt())
                    .build();
            tableId = SouthConstants.FABRIC_INGRESS_SPGW_UPLINK_PDRS;
        } else if (pdr.isDownlink()) {
            match = PiCriterion.builder()
                    .matchExact(SouthConstants.HDR_UE_ADDR, pdr.ueAddress().toInt())
                    .build();
            tableId = SouthConstants.FABRIC_INGRESS_SPGW_DOWNLINK_PDRS;
        } else {
            log.warn("Removal of flexible PDRs not yet supported.");
            throw new Up4Exception(Up4Exception.Type.UNSUPPORTED_PDR,
                    "Removal of flexible PDRs not yet supported.");
        }
        log.info("Removing {}", pdr.toString());
        removeEntry(match, tableId, false);

        // Remove the PDR from the farID->PDR mapping
        UpfRuleIdentifier ruleId = UpfRuleIdentifier.of(pdr.sessionId(), pdr.farId());
        farIdToPdrs.compute(ruleId, (k, existingSet) -> {
            if (existingSet == null) {
                return null;
            } else {
                existingSet.remove(pdr);
                return existingSet;
            }
        });
    }

    @Override
    public void removeFar(ForwardingActionRule far) throws Up4Exception {
        log.info("Removing {}", far.toString());

        PiCriterion match = PiCriterion.builder()
                .matchExact(SouthConstants.HDR_FAR_ID, up4Translator.globalFarIdOf(far.sessionId(), far.farId()))
                .build();

        removeEntry(match, SouthConstants.FABRIC_INGRESS_SPGW_FARS, false);
    }

    @Override
    public void removeInterface(UpfInterface upfInterface) throws Up4Exception {
        Ip4Prefix ifacePrefix = upfInterface.prefix();
        // If it isn't a downlink interface (so it is either uplink or unknown), try removing uplink
        if (!upfInterface.isDownlink()) {
            PiCriterion match1 = PiCriterion.builder()
                    .matchLpm(SouthConstants.HDR_IPV4_DST_ADDR, ifacePrefix.address().toInt(),
                            ifacePrefix.prefixLength())
                    .matchExact(SouthConstants.HDR_GTPU_IS_VALID, 1)
                    .build();
            if (removeEntry(match1, SouthConstants.FABRIC_INGRESS_SPGW_INTERFACES, true)) {
                return;
            }
        }
        // If that didn't work or didn't execute, try removing downlink
        PiCriterion match2 = PiCriterion.builder()
                .matchLpm(SouthConstants.HDR_IPV4_DST_ADDR, ifacePrefix.address().toInt(),
                        ifacePrefix.prefixLength())
                .matchExact(SouthConstants.HDR_GTPU_IS_VALID, 0)
                .build();
        removeEntry(match2, SouthConstants.FABRIC_INGRESS_SPGW_INTERFACES, false);
    }
}
