/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.behavior;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.GtpTunnel;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.UpfFlow;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammable;
import org.omecproject.up4.UpfProgrammableException;
import org.omecproject.up4.UpfRuleIdentifier;
import org.omecproject.up4.impl.SouthConstants;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiCounterModel;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.model.PiTableModel;
import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiCounterCellHandle;
import org.onosproject.net.pi.runtime.PiCounterCellId;
import org.onosproject.net.pi.service.PiPipeconfService;
import org.onosproject.p4runtime.api.P4RuntimeClient;
import org.onosproject.p4runtime.api.P4RuntimeController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.onosproject.net.pi.model.PiCounterType.INDIRECT;

/**
 * Implementation of a UPF programmable device behavior. TODO: this needs to be moved to
 * onos/pipelines/fabric/impl/src/main/java/org/onosproject/pipelines/fabric/impl/behaviour/up4/ and
 * referenced as upfProgrammable = deviceService.getDevice(deviceId).as(UpfProgrammable.class);
 */
public class FabricUpfProgrammable implements UpfProgrammable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final int DEFAULT_PRIORITY = 128;
    private static final long DEFAULT_P4_DEVICE_ID = 1;

    // Initialized in constructor
    protected final FlowRuleService flowRuleService;
    protected final PacketService packetService;
    protected final P4RuntimeController controller;
    protected final PiPipeconfService piPipeconfService;
    protected final FabricUpfStore upfStore;
    protected final FabricUpfTranslator upfTranslator;
    private final DeviceId deviceId;

    // Initialized in init()
    private PiPipeconf pipeconf;
    private long farTableSize;
    private long encappedPdrTableSize;
    private long unencappedPdrTableSize;
    private long pdrCounterSize;
    private ApplicationId appId;
    private long ueLimit = NO_UE_LIMIT;

    // FIXME: remove, buffer drain should be triggered by Up4Service
    private BufferDrainer bufferDrainer;

    // FIXME: dbuf tunnel should be managed by Up4Service
    //  Up4Service should be responsible of setting up such tunnel, then transforming FARs for this
    //  device accordingly. When the tunnel endpoint change, it should be up to Up4Service to update
    //  the FAR on the device.
    private GtpTunnel dbufTunnel;

    // FIXME: remove constructor once we make this a driver behavior. Services and device ID can be
    // derived from driver context.
    public FabricUpfProgrammable(
            FlowRuleService flowRuleService, PacketService packetService, P4RuntimeController
            controller, PiPipeconfService piPipeconfService, FabricUpfStore upfStore, DeviceId
            deviceId) {
        this.flowRuleService = flowRuleService;
        this.packetService = packetService;
        this.controller = controller;
        this.piPipeconfService = piPipeconfService;
        this.upfStore = upfStore;
        this.upfTranslator = new FabricUpfTranslator(upfStore);
        this.deviceId = deviceId;
    }

    @Override
    public boolean init(ApplicationId appId, long ueLimit) {
        this.appId = appId;

        Optional<PiPipeconf> optPipeconf = piPipeconfService.getPipeconf(deviceId);
        if (optPipeconf.isEmpty()) {
            log.error("Unable to load piPipeconf for {}", deviceId);
            return false;
        }
        pipeconf = optPipeconf.get();

        if (!computeHardwareResourceSizes()) {
            // error message will be printed by computeHardwareResourceSizes()
            return false;
        }

        String limitStr = ueLimit < 0 ? "unlimited" : Long.toString(ueLimit);
        log.info("Setting UE limit of UPF on {} to {}", deviceId, limitStr);
        this.ueLimit = ueLimit;

        log.info("UpfProgrammable initialized for appId {} and deviceId {}", appId, deviceId);

        return true;
    }

    /**
     * Grab the capacities for the PDR and FAR tables from the pipeconf. Runs only once, on initialization.
     *
     * @return true if resource is fetched successfully, false otherwise.
     * @throws IllegalStateException when FAR or PDR table can't be found in the pipeline model.
     */
    private boolean computeHardwareResourceSizes() {
        long farTableSize = 0;
        long encappedPdrTableSize = 0;
        long unencappedPdrTableSize = 0;

        // Get table sizes of interest
        for (PiTableModel piTable : pipeconf.pipelineModel().tables()) {
            if (piTable.id().equals(SouthConstants.FABRIC_INGRESS_SPGW_UPLINK_PDRS)) {
                encappedPdrTableSize = piTable.maxSize();
            } else if (piTable.id().equals(SouthConstants.FABRIC_INGRESS_SPGW_DOWNLINK_PDRS)) {
                unencappedPdrTableSize = piTable.maxSize();
            } else if (piTable.id().equals(SouthConstants.FABRIC_INGRESS_SPGW_FARS)) {
                farTableSize = piTable.maxSize();
            }
        }
        if (encappedPdrTableSize == 0) {
            throw new IllegalStateException("Unable to find uplink PDR table in pipeline model.");
        }
        if (unencappedPdrTableSize == 0) {
            throw new IllegalStateException("Unable to find downlink PDR table in pipeline model.");
        }
        if (encappedPdrTableSize != unencappedPdrTableSize) {
            log.warn("The uplink and downlink PDR tables don't have equal sizes! Using the minimum of the two.");
        }
        if (farTableSize == 0) {
            throw new IllegalStateException("Unable to find FAR table in pipeline model.");
        }
        // Get counter sizes of interest
        long ingressCounterSize = 0;
        long egressCounterSize = 0;
        for (PiCounterModel piCounter : pipeconf.pipelineModel().counters()) {
            if (piCounter.id().equals(SouthConstants.FABRIC_INGRESS_SPGW_PDR_COUNTER)) {
                ingressCounterSize = piCounter.size();
            } else if (piCounter.id().equals(SouthConstants.FABRIC_EGRESS_SPGW_PDR_COUNTER)) {
                egressCounterSize = piCounter.size();
            }
        }
        if (ingressCounterSize != egressCounterSize) {
            log.warn("PDR ingress and egress counter sizes are not equal! Using the minimum of the two.");
        }
        this.farTableSize = farTableSize;
        this.encappedPdrTableSize = encappedPdrTableSize;
        this.unencappedPdrTableSize = unencappedPdrTableSize;
        this.pdrCounterSize = Math.min(ingressCounterSize, egressCounterSize);
        return true;
    }

    @Override
    public void setBufferDrainer(BufferDrainer drainer) {
        this.bufferDrainer = drainer;
    }

    @Override
    public void unsetBufferDrainer() {
        this.bufferDrainer = null;
    }

    @Override
    public void enablePscEncap(int defaultQfi) {
        if (pipeconf.pipelineModel().table(SouthConstants.FABRIC_EGRESS_SPGW_GTPU_ENCAP).isEmpty()) {
            log.error("Missing {} table in {}, cannot enable PSC encap",
                    SouthConstants.FABRIC_EGRESS_SPGW_GTPU_ENCAP, deviceId);
            return;
        }
        flowRuleService.applyFlowRules(upfTranslator.buildGtpuWithPscEncapRule(
                deviceId, appId, defaultQfi));
    }

    @Override
    public void disablePscEncap() {
        if (pipeconf.pipelineModel().table(SouthConstants.FABRIC_EGRESS_SPGW_GTPU_ENCAP).isEmpty()) {
            log.debug("Missing {} table in {}, assuming PSC encap is disabled by default",
                    SouthConstants.FABRIC_EGRESS_SPGW_GTPU_ENCAP, deviceId);
            return;
        }
        flowRuleService.applyFlowRules(upfTranslator.buildGtpuOnlyEncapRule(
                deviceId, appId));
    }

    @Override
    public void sendPacketOut(ByteBuffer data) {
        final OutboundPacket pkt = new DefaultOutboundPacket(
                deviceId,
                // Use TABLE logical port to have pkt routed via pipeline tables.
                DefaultTrafficTreatment.builder()
                        .setOutput(PortNumber.TABLE)
                        .build(),
                data);
        packetService.emit(pkt);
    }

    @Override
    public void setDbufTunnel(Ip4Address switchAddr, Ip4Address dbufAddr) {
        this.dbufTunnel = GtpTunnel.builder()
                .setSrc(switchAddr)
                .setDst(dbufAddr)
                .setSrcPort((short) 2152)
                .setTeid(0)
                .build();
    }

    @Override
    public void unsetDbufTunnel() {
        this.dbufTunnel = null;
    }

    /**
     * Convert the given buffering FAR to a FAR that tunnels the packet to dbuf.
     *
     * @param far the FAR to convert
     * @return the converted FAR
     */
    private ForwardingActionRule convertToDbufFar(ForwardingActionRule far) {
        if (!far.buffers()) {
            throw new IllegalArgumentException("Converting a non-buffering FAR to a dbuf FAR! This shouldn't happen.");
        }
        return ForwardingActionRule.builder()
                .setFarId(far.farId())
                .withSessionId(far.sessionId())
                .setNotifyFlag(far.notifies())
                .setBufferFlag(true)
                .setTunnel(dbufTunnel)
                .build();
    }

    @Override
    public void cleanUp() {
        log.info("Clearing all UPF-related table entries.");
        flowRuleService.removeFlowRulesById(appId);
        upfStore.reset();
    }

    @Override
    public void clearInterfaces() {
        log.info("Clearing all UPF interfaces.");
        for (FlowRule entry : flowRuleService.getFlowEntriesById(appId)) {
            if (upfTranslator.isFabricInterface(entry)) {
                try {
                    var iface = upfTranslator.fabricEntryToInterface(entry);
                    if (iface.isCore()) {
                        applyUplinkRecirculation(iface.prefix(), true);
                    }
                } catch (UpfProgrammableException e) {
                    log.error("Error when translating interface entry, " +
                            "will skip removing uplink recirculation rules: {} [{}]", e.getMessage(), entry);
                }
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
            if (upfTranslator.isFabricPdr(entry)) {
                pdrsCleared++;
                flowRuleService.removeFlowRules(entry);
            } else if (upfTranslator.isFabricFar(entry)) {
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
    public Collection<PdrStats> readAllCounters() throws UpfProgrammableException {
        // Get client and pipeconf.
        P4RuntimeClient client = controller.get(deviceId);
        if (client == null) {
            throw new UpfProgrammableException("Unable to find p4runtime client for reading from device "
                    + deviceId.toString());
        }
        Optional<PiPipeconf> optPipeconf = piPipeconfService.getPipeconf(deviceId);
        if (optPipeconf.isEmpty()) {
            throw new UpfProgrammableException("Unable to load pipeconf for device "
                    + deviceId.toString());
        }
        PiPipeconf pipeconf = optPipeconf.get();

        // Prepare PdrStats object builders, one for each counter ID currently in use
        Map<Integer, PdrStats.Builder> pdrStatBuilders = Maps.newHashMap();
        for (int cellId = 0; cellId < pdrCounterSize(); cellId++) {
            pdrStatBuilders.put(cellId, PdrStats.builder().withCellId(cellId));
        }

        // Generate the counter cell IDs.
        Set<PiCounterId> counterIds = ImmutableSet.of(
                SouthConstants.FABRIC_INGRESS_SPGW_PDR_COUNTER,
                SouthConstants.FABRIC_EGRESS_SPGW_PDR_COUNTER
        );

        // Query the device.
        Collection<PiCounterCell> counterEntryResponse = client.read(
                DEFAULT_P4_DEVICE_ID, pipeconf)
                .counterCells(counterIds)
                .submitSync()
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
    public long pdrCounterSize() {
        if (ueLimit >= 0) {
            return Math.min(ueLimit * 2, pdrCounterSize);
        }
        return pdrCounterSize;
    }

    @Override
    public long farTableSize() {
        if (ueLimit >= 0) {
            return Math.min(ueLimit * 2, farTableSize);
        }
        return farTableSize;
    }


    @Override
    public long pdrTableSize() {
        long physicalSize = Math.min(encappedPdrTableSize, unencappedPdrTableSize) * 2;
        if (ueLimit >= 0) {
            return Math.min(ueLimit * 2, physicalSize);
        }
        return physicalSize;
    }

    @Override
    public PdrStats readCounter(int cellId) throws UpfProgrammableException {
        if (cellId >= pdrCounterSize() || cellId < 0) {
            throw new UpfProgrammableException("Requested PDR counter cell index is out of bounds.",
                    UpfProgrammableException.Type.COUNTER_INDEX_OUT_OF_RANGE);
        }
        PdrStats.Builder stats = PdrStats.builder().withCellId(cellId);

        // Get client and pipeconf.
        P4RuntimeClient client = controller.get(deviceId);
        if (client == null) {
            throw new UpfProgrammableException(
                    "Unable to find p4runtime client for device " + deviceId.toString());
        }
        Optional<PiPipeconf> optPipeconf = piPipeconfService.getPipeconf(deviceId);
        if (optPipeconf.isEmpty()) {
            throw new UpfProgrammableException(
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
    public void addPdr(PacketDetectionRule pdr) throws UpfProgrammableException {
        if (pdr.counterId() >= pdrCounterSize() || pdr.counterId() < 0) {
            throw new UpfProgrammableException("Counter cell index referenced by PDR is out of bounds.",
                    UpfProgrammableException.Type.COUNTER_INDEX_OUT_OF_RANGE);
        }
        FlowRule fabricPdr = upfTranslator.pdrToFabricEntry(pdr, deviceId, appId, DEFAULT_PRIORITY);
        log.info("Installing {}", pdr.toString());
        flowRuleService.applyFlowRules(fabricPdr);
        log.debug("PDR added with flowID {}", fabricPdr.id().value());

        // If the flow rule was applied and the PDR is downlink, add the PDR to the farID->PDR mapping
        if (pdr.matchesUnencapped()) {
            upfStore.learnFarIdToUeAddrs(pdr);
        }
    }


    @Override
    public void addFar(ForwardingActionRule far) throws UpfProgrammableException {
        UpfRuleIdentifier ruleId = UpfRuleIdentifier.of(far.sessionId(), far.farId());
        if (far.buffers()) {
            // If the far has the buffer flag, modify its tunnel so it directs to dbuf
            far = convertToDbufFar(far);
            upfStore.learBufferingFarId(ruleId);
        }
        FlowRule fabricFar = upfTranslator.farToFabricEntry(far, deviceId, appId, DEFAULT_PRIORITY);
        log.info("Installing {}", far.toString());
        flowRuleService.applyFlowRules(fabricFar);
        log.debug("FAR added with flowID {}", fabricFar.id().value());
        if (!far.buffers() && upfStore.isFarIdBuffering(ruleId)) {
            // If this FAR does not buffer but used to, then drain the buffer for every UE address
            // that hits this FAR.
            upfStore.forgetBufferingFarId(ruleId);
            for (var ueAddr : upfStore.ueAddrsOfFarId(ruleId)) {
                if (bufferDrainer == null) {
                    log.warn("Unable to drain downlink buffer for UE {}, bufferDrainer is null", ueAddr);
                } else {
                    bufferDrainer.drain(ueAddr);
                }
            }
        }
    }

    @Override
    public void addInterface(UpfInterface upfInterface) throws UpfProgrammableException {
        FlowRule flowRule = upfTranslator.interfaceToFabricEntry(upfInterface, deviceId, appId, DEFAULT_PRIORITY);
        log.info("Installing {}", upfInterface);
        flowRuleService.applyFlowRules(flowRule);
        log.debug("Interface added with flowID {}", flowRule.id().value());
        // By default we enable UE-to-UE communication on the UE subnet identified by the CORE interface.
        // TODO: allow enabling/disabling UE-to-UE via netcfg or other API.
        if (upfInterface.isCore()) {
            applyUplinkRecirculation(upfInterface.prefix(), false);
        }
    }

    private boolean removeEntry(PiCriterion match, PiTableId tableId, boolean failSilent)
            throws UpfProgrammableException {
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
            throw new UpfProgrammableException("Match criterion " + match.toString() +
                    " not found in table " + tableId.toString());
        }
        return false;
    }

    @Override
    public Collection<UpfFlow> getFlows() throws UpfProgrammableException {
        Map<Integer, PdrStats> counterStats = new HashMap<>();
        readAllCounters().forEach(stats -> counterStats.put(stats.getCellId(), stats));
        // A flow is made of a PDR and the FAR that should apply to packets that hit the PDR.
        // Multiple PDRs can map to the same FAR, so create a one->many mapping of FAR Identifier to flow builder
        Map<UpfRuleIdentifier, List<UpfFlow.Builder>> globalFarToSessionBuilder = new HashMap<>();
        List<ForwardingActionRule> fars = new ArrayList<>();
        for (FlowRule flowRule : flowRuleService.getFlowEntriesById(appId)) {
            if (upfTranslator.isFabricFar(flowRule)) {
                // If its a far, save it for later
                fars.add(upfTranslator.fabricEntryToFar(flowRule));
            } else if (upfTranslator.isFabricPdr(flowRule)) {
                // If its a PDR, create a flow builder for it
                PacketDetectionRule pdr = upfTranslator.fabricEntryToPdr(flowRule);
                globalFarToSessionBuilder.compute(new UpfRuleIdentifier(pdr.sessionId(), pdr.farId()),
                        (k, existingVal) -> {
                            final var builder = UpfFlow.builder()
                                    .setPdr(pdr)
                                    .addStats(counterStats.get(pdr.counterId()));
                            if (existingVal == null) {
                                return Lists.newArrayList(builder);
                            } else {
                                existingVal.add(builder);
                                return existingVal;
                            }
                        });

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
                    results.add(builder.build());
                } catch (java.lang.IllegalArgumentException e) {
                    log.warn("Corrupt UPF flow found in dataplane: {}", e.getMessage());
                }
            }
        }
        return results;
    }

    @Override
    public Collection<PacketDetectionRule> getPdrs() throws UpfProgrammableException {
        ArrayList<PacketDetectionRule> pdrs = new ArrayList<>();
        for (FlowRule flowRule : flowRuleService.getFlowEntriesById(appId)) {
            if (upfTranslator.isFabricPdr(flowRule)) {
                pdrs.add(upfTranslator.fabricEntryToPdr(flowRule));
            }
        }
        return pdrs;
    }

    @Override
    public Collection<ForwardingActionRule> getFars() throws UpfProgrammableException {
        ArrayList<ForwardingActionRule> fars = new ArrayList<>();
        for (FlowRule flowRule : flowRuleService.getFlowEntriesById(appId)) {
            if (upfTranslator.isFabricFar(flowRule)) {
                fars.add(upfTranslator.fabricEntryToFar(flowRule));
            }
        }
        return fars;
    }

    @Override
    public Collection<UpfInterface> getInterfaces() throws UpfProgrammableException {
        ArrayList<UpfInterface> ifaces = new ArrayList<>();
        for (FlowRule flowRule : flowRuleService.getFlowEntriesById(appId)) {
            if (upfTranslator.isFabricInterface(flowRule)) {
                ifaces.add(upfTranslator.fabricEntryToInterface(flowRule));
            }
        }
        return ifaces;
    }

    @Override
    public void removePdr(PacketDetectionRule pdr) throws UpfProgrammableException {
        PiCriterion match;
        PiTableId tableId;
        if (pdr.matchesEncapped()) {
            match = PiCriterion.builder()
                    .matchExact(SouthConstants.HDR_TEID, pdr.teid().asArray())
                    .matchExact(SouthConstants.HDR_TUNNEL_IPV4_DST, pdr.tunnelDest().toInt())
                    .build();
            tableId = SouthConstants.FABRIC_INGRESS_SPGW_UPLINK_PDRS;
        } else {
            match = PiCriterion.builder()
                    .matchExact(SouthConstants.HDR_UE_ADDR, pdr.ueAddress().toInt())
                    .build();
            tableId = SouthConstants.FABRIC_INGRESS_SPGW_DOWNLINK_PDRS;
        }
        log.info("Removing {}", pdr.toString());
        removeEntry(match, tableId, false);

        // Remove the PDR from the farID->PDR mapping
        // This is an inefficient hotfix FIXME: remove UE addrs from the mapping in sublinear time
        if (pdr.matchesUnencapped()) {
            // Should we remove just from the map entry with key == far ID?
            upfStore.forgetUeAddr(pdr.ueAddress());
        }
    }

    @Override
    public void removeFar(ForwardingActionRule far) throws UpfProgrammableException {
        log.info("Removing {}", far.toString());

        PiCriterion match = PiCriterion.builder()
                .matchExact(SouthConstants.HDR_FAR_ID, upfStore.globalFarIdOf(far.sessionId(), far.farId()))
                .build();

        removeEntry(match, SouthConstants.FABRIC_INGRESS_SPGW_FARS, false);
    }

    @Override
    public void removeInterface(UpfInterface upfInterface) throws UpfProgrammableException {
        Ip4Prefix ifacePrefix = upfInterface.getPrefix();
        if (upfInterface.isCore()) {
            applyUplinkRecirculation(ifacePrefix, true);
        }
        // If it isn't a core interface (so it is either access or unknown), try removing core
        if (!upfInterface.isCore()) {
            PiCriterion match1 = PiCriterion.builder()
                    .matchLpm(SouthConstants.HDR_IPV4_DST_ADDR, ifacePrefix.address().toInt(),
                            ifacePrefix.prefixLength())
                    .matchExact(SouthConstants.HDR_GTPU_IS_VALID, 1)
                    .build();
            if (removeEntry(match1, SouthConstants.FABRIC_INGRESS_SPGW_INTERFACES, true)) {
                return;
            }
        }
        // If that didn't work or didn't execute, try removing access
        PiCriterion match2 = PiCriterion.builder()
                .matchLpm(SouthConstants.HDR_IPV4_DST_ADDR, ifacePrefix.address().toInt(),
                        ifacePrefix.prefixLength())
                .matchExact(SouthConstants.HDR_GTPU_IS_VALID, 0)
                .build();
        removeEntry(match2, SouthConstants.FABRIC_INGRESS_SPGW_INTERFACES, false);
    }

    private void applyUplinkRecirculation(Ip4Prefix subnet, boolean remove) {
        // FIXME: remove pipeconf check once we will have proper pipeconf behavior for fabric-tna
        if (pipeconf.pipelineModel().table(SouthConstants.FABRIC_INGRESS_SPGW_UPLINK_RECIRC_RULES).isEmpty()) {
            log.warn("Missing uplink recirculation table in {}, will not apply UE-to-Ue rules for {}",
                    deviceId, subnet);
            return;
        }
        log.warn("{} uplink recirculation rules on {} for subnet {}",
                remove ? "Removing" : "Installing", deviceId, subnet);
        // By default deny all uplink traffic with IP dst on the given UE subnet
        FlowRule denyRule = upfTranslator.buildFabricUplinkRecircEntry(
                deviceId, appId, null, subnet, false, DEFAULT_PRIORITY);
        // Allow recirculation only for packets with source on the same UE subnet
        FlowRule allowRule = upfTranslator.buildFabricUplinkRecircEntry(
                deviceId, appId, subnet, subnet, true, DEFAULT_PRIORITY + 10);
        if (!remove) {
            flowRuleService.applyFlowRules(denyRule, allowRule);
        } else {
            flowRuleService.removeFlowRules(denyRule, allowRule);
        }
    }
}
