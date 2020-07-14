/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.commons.lang3.tuple.Pair;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.FlowRuleStore;
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
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.onlab.util.Tools.get;
import static org.onosproject.net.pi.model.PiCounterType.INDIRECT;


/**
 * Draft UP4 ONOS application component.
 */
@Component(immediate = true,
           service = {Up4Service.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class Up4Component implements Up4Service {


    private static final long DEFAULT_P4_DEVICE_ID = 1;


    /** Some configurable property. */
    // Leaving in for now as a reference
    private String someProperty;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId appId;
    private final AtomicBoolean upfInitialized = new AtomicBoolean(false);
    private InternalDeviceListener deviceListener;
    private InternalConfigListener cfgListener;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry netCfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleStore store;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected P4RuntimeController controller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PiPipeconfService piPipeconfService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    // TODO: Use EventuallyConsistentMap instead
    // TODO: Store PDR IDs for the flow rules somehow,
    // since they arent actually part of the flow rules but need to be read
    private BiMap<Pair<ImmutableByteSequence, Integer>, Integer> farIds;

    private AtomicInteger lastGlobalFarId;
    private static final int DEFAULT_PRIORITY = 128;

    private DeviceId upfDeviceId;


    @Activate
    protected void activate() {
        appId = coreService.registerApplication(AppConstants.APP_NAME,
                                                () -> log.info("Periscope down."));
        cfgService.registerProperties(getClass());
        farIds = HashBiMap.create();
        lastGlobalFarId = new AtomicInteger(0);
        deviceListener = new InternalDeviceListener();
        cfgListener = new InternalConfigListener();
        setFirstAvailableDevice();
        deviceService.addListener(deviceListener);
        netCfgService.addListener(cfgListener);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        farIds = null;
        upfInitialized.set(false);
        deviceService.removeListener(deviceListener);
        log.info("Stopped");
    }

    private void setFirstAvailableDevice() {
        List<Device> devices = getAvailableDevices();
        if (!devices.isEmpty()) {
            setUpfDevice(devices.get(0).id());
        }
    }

    @Override
    public List<Device> getAvailableDevices() {
        ArrayList<Device> foundDevices = new ArrayList<>();
        for (Device device : deviceService.getAvailableDevices()) {
            if (isUpfDevice(device.id())) {
                    foundDevices.add(device);
            }
        }
        return foundDevices;
    }


    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    /**
     * Check if the device is registered and is a valid UPF dataplane.
     *
     * @param deviceId ID of the device to check
     * @return True if the device is a valid UPF data plane, and False otherwise
     */
    private boolean isUpfDevice(DeviceId deviceId) {
        final Device device = deviceService.getDevice(deviceId);

        Optional<PiPipeconf> opt = piPipeconfService.getPipeconf(device.id());
        if (opt.isPresent()) {
            if (opt.get().id().toString().contains(AppConstants.SUPPORTED_PIPECONF_STRING)) {
                return true;
            }
        }
        return false;
    }

    private void setUpfDevice(DeviceId deviceId) {
        synchronized (upfInitialized) {
            if (upfInitialized.get()) {
                log.debug("UPF {} already initialized", deviceId);
                return;
            }
            if (!deviceService.isAvailable(deviceId)) {
                log.info("UPF is currently unavailable, skip setup");
                return;
            }
            if (!isUpfDevice(deviceId)) {
                log.warn("{} is not a UPF", deviceId);
                return;
            }
            if (upfDeviceId != null && upfDeviceId != deviceId) {
                log.error("Change of the UPF while UPF device is available is not supported!");
                return;
            }

            upfDeviceId = deviceId;
            upfInitialized.set(true);
            log.info("UPF device registered.");
        }
    }

    /**
     * Unset the UPF dataplane device. If available it will be cleaned-up.
     */
    private void unsetUpfDevice() {
        synchronized (upfInitialized) {
            if (upfDeviceId != null) {
                log.info("UPF cleanup");
                clearAllEntries();
                upfDeviceId = null;
                upfInitialized.set(false);
            }
        }
    }

    /**
     * React to new devices. The first device recognized to have BNG-U
     * functionality is taken as BNG-U device.
     */
    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceId deviceId = event.subject().id();
            if (isUpfDevice(deviceId)) {
                switch (event.type()) {
                    case DEVICE_ADDED:
                    case DEVICE_UPDATED:
                    case DEVICE_AVAILABILITY_CHANGED:
                        if (deviceService.isAvailable(deviceId)) {
                            log.debug("Event: {}, setting UPF", event.type());
                            setUpfDevice(deviceId);
                        }
                        break;
                    case DEVICE_REMOVED:
                    case DEVICE_SUSPENDED:
                        unsetUpfDevice();
                        break;
                    case PORT_ADDED:
                    case PORT_UPDATED:
                    case PORT_REMOVED:
                    case PORT_STATS_UPDATED:
                        break;
                    default:
                        log.warn("Unknown device event type {}", event.type());
                }
            }
        }
    }

    /**
     * Listener for network config events.
     */
    private class InternalConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            switch (event.type()) {
                case CONFIG_REGISTERED:
                case CONFIG_UPDATED:
                case CONFIG_ADDED:
                    setFirstAvailableDevice();
                    break;
                case CONFIG_REMOVED:
                case CONFIG_UNREGISTERED:
                    break;
                default:
                    log.warn("Unsupported event type {}", event.type());
                    break;
            }
        }
    }


    private int getGlobalFarId(ImmutableByteSequence sessionId, int farId) {
        return farIds.computeIfAbsent(
            Pair.of(sessionId, farId),
            k -> lastGlobalFarId.incrementAndGet()
        );
    }

    @Override
    public void clearAllEntries() {
        log.info("Clearing all UP4-related table entries.");
        flowRuleService.removeFlowRulesById(appId);
    }

    @Override
    public Up4Service.PdrStats readCounter(int cellId) {
        Up4Service.PdrStats.Builder stats = Up4Service.PdrStats.builder(cellId);

        // Get client and pipeconf.
        P4RuntimeClient client = controller.get(upfDeviceId);
        if (client == null) {
            log.warn("Unable to find client for {}, aborting operation", upfDeviceId);
            return stats.build();
        }
        Optional<PiPipeconf> optPipeconf = piPipeconfService.getPipeconf(upfDeviceId);
        if (optPipeconf.isEmpty()) {
            log.warn("Unable to load piPipeconf for {}, aborting operation", upfDeviceId);
            return stats.build();
        }
        PiPipeconf pipeconf = optPipeconf.get();


        // Make list of cell handles we want to read.
        List<PiCounterCellHandle> counterCellHandles = List.of(
                PiCounterCellHandle.of(upfDeviceId,
                        PiCounterCellId.ofIndirect(SouthConstants.INGRESS_COUNTER_ID, cellId)),
                PiCounterCellHandle.of(upfDeviceId,
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



    private void addPdr(ImmutableByteSequence sessionId, int ctrId, int farId, PiCriterion match, PiTableId tableId) {
        setFirstAvailableDevice();
        int globalFarId = getGlobalFarId(sessionId, farId);
        PiAction action = PiAction.builder()
                .withId(SouthConstants.LOAD_PDR)
                .withParameters(Arrays.asList(
                    new PiActionParam(SouthConstants.CTR_ID, ctrId),
                    new PiActionParam(SouthConstants.FAR_ID_PARAM, globalFarId)
                ))
                .build();

        FlowRule pdrEntry = DefaultFlowRule.builder()
                .forDevice(upfDeviceId).fromApp(appId).makePermanent()
                .forTable(tableId)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();

        flowRuleService.applyFlowRules(pdrEntry);
        log.info("Added PDR table entry with flowID {}", pdrEntry.id().value());
    }

    @Override
    public void addPdr(ImmutableByteSequence sessionId, int ctrId, int farId,
                       Ip4Address ueAddr, int teid, Ip4Address tunnelDst) {
        log.info("Adding uplink PDR");
        PiCriterion match = PiCriterion.builder()
            .matchExact(SouthConstants.UE_ADDR_KEY, ueAddr.toInt())
            .matchExact(SouthConstants.TEID_KEY, teid)
            .matchExact(SouthConstants.TUNNEL_DST_KEY, tunnelDst.toInt())
            .build();
        this.addPdr(sessionId, ctrId, farId, match,
                SouthConstants.PDR_UPLINK_TBL);
    }

    @Override
    public void addPdr(ImmutableByteSequence sessionId, int ctrId, int farId, Ip4Address ueAddr) {
        log.info("Adding downlink PDR");
        PiCriterion match = PiCriterion.builder()
            .matchExact(SouthConstants.UE_ADDR_KEY, ueAddr.toInt())
            .build();

        this.addPdr(sessionId, ctrId, farId, match,
                SouthConstants.PDR_DOWNLINK_TBL);
    }



    private void addFar(ImmutableByteSequence sessionId, int farId, PiAction action) {
        setFirstAvailableDevice();
        int globalFarId = getGlobalFarId(sessionId, farId);

        PiCriterion match = PiCriterion.builder()
            .matchExact(SouthConstants.FAR_ID_KEY, globalFarId)
            .build();
        FlowRule farEntry = DefaultFlowRule.builder()
                .forDevice(upfDeviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.FAR_TBL)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
        flowRuleService.applyFlowRules(farEntry);
        log.info("Added FAR table entry with flowID {}", farEntry.id().value());
    }

    @Override
    public void addFar(ImmutableByteSequence sessionId, int farId, boolean drop, boolean notifyCp,
                       TunnelDesc tunnelDesc) {
        log.info("Adding simple downlink FAR entry");

        PiAction action = PiAction.builder()
                .withId(SouthConstants.LOAD_FAR_TUNNEL)
                .withParameters(Arrays.asList(
                    new PiActionParam(SouthConstants.DROP_FLAG, drop ? 1 : 0),
                    new PiActionParam(SouthConstants.NOTIFY_FLAG, notifyCp ? 1 : 0),
                    new PiActionParam(SouthConstants.TEID_PARAM, tunnelDesc.teid),
                    new PiActionParam(SouthConstants.TUNNEL_SRC_PARAM, tunnelDesc.src.toInt()),
                    new PiActionParam(SouthConstants.TUNNEL_DST_PARAM, tunnelDesc.dst.toInt())
                ))
                .build();
        this.addFar(sessionId, farId, action);
    }

    @Override
    public void addFar(ImmutableByteSequence sessionId, int farId, boolean drop, boolean notifyCp) {
        log.info("Adding simple uplink FAR entry");
        PiAction action = PiAction.builder()
                .withId(SouthConstants.LOAD_FAR_NORMAL)
                .withParameters(Arrays.asList(
                    new PiActionParam(SouthConstants.DROP_FLAG, drop ? 1 : 0),
                    new PiActionParam(SouthConstants.NOTIFY_FLAG, notifyCp ? 1 : 0)
                ))
                .build();
        this.addFar(sessionId, farId, action);
    }

    @Override
    public void addS1uInterface(Ip4Address s1uAddr) {
        setFirstAvailableDevice();
        log.info("Adding S1U interface table entry");
        PiCriterion match = PiCriterion.builder()
            .matchExact(SouthConstants.IFACE_UPLINK_KEY, s1uAddr.toInt())
            .build();
        PiAction action = PiAction.builder()
                .withId(SouthConstants.NO_ACTION)
                .build();
        FlowRule s1uEntry = DefaultFlowRule.builder()
                .forDevice(upfDeviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.IFACE_UPLINK_TBL)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
        flowRuleService.applyFlowRules(s1uEntry);
        log.info("Added S1U entry with flowID {}", s1uEntry.id().value());
    }

    @Override
    public void addUePool(Ip4Prefix poolPrefix) {
        setFirstAvailableDevice();
        log.info("Adding UE IPv4 Pool prefix");
        PiCriterion match = PiCriterion.builder()
            .matchLpm(SouthConstants.IFACE_DOWNLINK_KEY, poolPrefix.address().toInt(), poolPrefix.prefixLength())
            .build();
        PiAction action = PiAction.builder()
                .withId(SouthConstants.NO_ACTION)
                .build();
        FlowRule uePoolEntry = DefaultFlowRule.builder()
                .forDevice(upfDeviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.IFACE_DOWNLINK_TBL)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(DEFAULT_PRIORITY)
                .build();
        flowRuleService.applyFlowRules(uePoolEntry);
        log.info("Added UE IPv4 pool entry with flowID {}", uePoolEntry.id().value());
    }


    private boolean removeEntry(PiCriterion match, PiTableId tableId, boolean failSilent) {
        setFirstAvailableDevice();
        FlowRule entry = DefaultFlowRule.builder()
                .forDevice(upfDeviceId).fromApp(appId).makePermanent()
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
    public void removePdr(Ip4Address ueAddr) {
        log.info("Removing downlink PDR");
        PiCriterion match = PiCriterion.builder()
                .matchExact(SouthConstants.UE_ADDR_KEY, ueAddr.toInt())
                .build();

        removeEntry(match, SouthConstants.PDR_DOWNLINK_TBL, false);
    }

    @Override
    public void removePdr(Ip4Address ueAddr, int teid, Ip4Address tunnelDst) {
        log.info("Removing uplink PDR");
        PiCriterion match = PiCriterion.builder()
                .matchExact(SouthConstants.UE_ADDR_KEY, ueAddr.toInt())
                .matchExact(SouthConstants.TEID_KEY, teid)
                .matchExact(SouthConstants.TUNNEL_DST_KEY, tunnelDst.toInt())
                .build();
        removeEntry(match, SouthConstants.PDR_UPLINK_TBL, false);
    }

    @Override
    public void removeFar(ImmutableByteSequence sessionId, int farId) {
        log.info("Removing FAR");
        int globalFarId = getGlobalFarId(sessionId, farId);

        PiCriterion match = PiCriterion.builder()
                .matchExact(SouthConstants.FAR_ID_KEY, globalFarId)
                .build();

        removeEntry(match, SouthConstants.FAR_TBL, false);
    }

    @Override
    public void removeUePool(Ip4Prefix poolPrefix) {
        log.info("Removing S1U interface table entry");
        PiCriterion match = PiCriterion.builder()
                .matchLpm(SouthConstants.IFACE_DOWNLINK_KEY, poolPrefix.address().toInt(), poolPrefix.prefixLength())
                .build();
        removeEntry(match, SouthConstants.IFACE_DOWNLINK_TBL, false);
    }

    @Override
    public void removeS1uInterface(Ip4Address s1uAddr) {
        log.info("Removing S1U interface table entry");
        PiCriterion match = PiCriterion.builder()
                .matchExact(SouthConstants.IFACE_UPLINK_KEY, s1uAddr.toInt())
                .build();
        removeEntry(match, SouthConstants.IFACE_UPLINK_TBL, false);
    }

    @Override
    public void removeUnknownInterface(Ip4Prefix ifacePrefix) {
        // For when you don't know if its a uePool or s1uInterface table entry
        PiCriterion match1 = PiCriterion.builder()
                .matchExact(SouthConstants.IFACE_UPLINK_KEY, ifacePrefix.address().toInt())
                .build();
        if (removeEntry(match1, SouthConstants.IFACE_UPLINK_TBL, true)) {
            return;
        }

        PiCriterion match2 = PiCriterion.builder()
                .matchLpm(SouthConstants.IFACE_DOWNLINK_KEY, ifacePrefix.address().toInt(), ifacePrefix.prefixLength())
                .build();
        if (!removeEntry(match2, SouthConstants.IFACE_DOWNLINK_TBL, true)) {
            log.error("Could not remove interface! No matching entry found!");
        }

    }
}
