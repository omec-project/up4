/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.collect.Lists;
import io.grpc.Context;
import org.omecproject.dbuf.client.DbufClient;
import org.omecproject.dbuf.client.DefaultDbufClient;
import org.omecproject.up4.Up4Event;
import org.omecproject.up4.Up4EventListener;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.UpfFlow;
import org.omecproject.up4.config.Up4Config;
import org.omecproject.up4.config.Up4DbufConfig;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.behaviour.upf.ForwardingActionRule;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;
import org.onosproject.net.behaviour.upf.PdrStats;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.behaviour.upf.UpfProgrammable;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.pi.service.PiPipeconfEvent;
import org.onosproject.net.pi.service.PiPipeconfListener;
import org.onosproject.net.pi.service.PiPipeconfService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stratumproject.fabric.tna.behaviour.upf.FabricUpfStore;
import org.stratumproject.fabric.tna.behaviour.upf.UpfRuleIdentifier;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;


/**
 * Draft UP4 ONOS application component.
 */
@Component(immediate = true, service = {Up4Service.class})
public class Up4DeviceManager extends AbstractListenerManager<Up4Event, Up4EventListener>
        implements Up4Service {

    private final long NO_UE_LIMIT = -1;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final AtomicBoolean upfInitialized = new AtomicBoolean(false);
    private final ConfigFactory<ApplicationId, Up4Config> up4ConfigFactory = new ConfigFactory<>(
            APP_SUBJECT_FACTORY, Up4Config.class, Up4Config.KEY) {
        @Override
        public Up4Config createConfig() {
            log.debug("Creating UP4 config");
            return new Up4Config();
        }
    };
    private final ConfigFactory<ApplicationId, Up4DbufConfig> dbufConfigFactory = new ConfigFactory<>(
            APP_SUBJECT_FACTORY, Up4DbufConfig.class, Up4DbufConfig.KEY) {
        @Override
        public Up4DbufConfig createConfig() {
            log.debug("Creating dbuf config");
            return new Up4DbufConfig();
        }
    };
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry netCfgService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PiPipeconfService piPipeconfService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    private ApplicationId appId;
    private InternalDeviceListener deviceListener;
    private InternalConfigListener netCfgListener;
    private PiPipeconfListener piPipeconfListener;
    private UpfProgrammable upfProgrammable;
    private DeviceId upfDeviceId;
    private Up4Config config;
    private DbufClient dbufClient;

    @Activate
    protected void activate() {
        log.info("Starting...");
        appId = coreService.registerApplication(AppConstants.APP_NAME, this::preDeactivate);
        eventDispatcher.addSink(Up4Event.class, listenerRegistry);
        deviceListener = new InternalDeviceListener();
        netCfgListener = new InternalConfigListener();
        piPipeconfListener = new InternalPiPipeconfListener();
        netCfgService.addListener(netCfgListener);
        netCfgService.registerConfigFactory(up4ConfigFactory);
        netCfgService.registerConfigFactory(dbufConfigFactory);

        // Still need this in case both netcfg and pipeconf event happen before UP4 activation
        updateConfig();

        deviceService.addListener(deviceListener);
        piPipeconfService.addListener(piPipeconfListener);

        log.info("Started.");
    }

    protected void preDeactivate() {
        // Only clean up the state when the deactivation is triggered by ApplicationService
        log.info("Running Up4DeviceManager preDeactivation hook.");
        if (upfProgrammableAvailable()) {
            getUpfProgrammable().cleanUp();
        }
        teardownDbufClient();
        upfInitialized.set(false);
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopping...");
        deviceService.removeListener(deviceListener);
        netCfgService.removeListener(netCfgListener);
        netCfgService.unregisterConfigFactory(up4ConfigFactory);
        netCfgService.unregisterConfigFactory(dbufConfigFactory);
        eventDispatcher.removeSink(Up4Event.class);
        piPipeconfService.removeListener(piPipeconfListener);
        log.info("Stopped.");
    }

    @Override
    public boolean configIsLoaded() {
        return config != null;
    }

    private UpfProgrammable getUpfProgrammable() {
        if (this.upfProgrammable == null) {
            if (this.config == null) {
                throw new IllegalStateException(
                        "No UpfProgrammable set because no app config is available!");
            } else if (!isUpfProgrammable(upfDeviceId)) {
                throw new IllegalStateException(
                        "No UpfProgrammable set because deviceId present in config is not a valid UPF!");
            } else if (!upfInitialized.get()) {
                throw new IllegalStateException("UPF not initialized!");
            } else {
                throw new IllegalStateException(
                        String.format("No UpfProgrammable is set for an unknown reason. Is device %s available?",
                                      upfDeviceId.toString()));
            }
        }
        return this.upfProgrammable;
    }

    public boolean upfProgrammableAvailable() {
        return upfProgrammable != null;
    }

    private boolean isUpfProgrammable(DeviceId deviceId) {
        final Device device = deviceService.getDevice(deviceId);
        return device != null &&
                piPipeconfService.getPipeconf(device.id())
                        .map(piPipeconf -> piPipeconf.id().toString()
                                .contains(AppConstants.SUPPORTED_PIPECONF_STRING))
                        .orElse(false) &&
                device.is(UpfProgrammable.class);
    }

    private void setUpfDevice(DeviceId deviceId) {
        synchronized (upfInitialized) {
            if (upfInitialized.get()) {
                log.info("UPF {} already initialized, skipping setup.", deviceId);
                // FIXME: this is merely a hotfix for interface entries disappearing when a device becomes available.
                ensureInterfacesInstalled();
                return;
            }
            if (!deviceService.isAvailable(deviceId)) {
                log.info("UPF is currently unavailable, skip setup.");
                return;
            }
            if (!isUpfProgrammable(deviceId)) {
                log.warn("{} is not UPF device!", deviceId);
                return;
            }
            if (upfProgrammable != null && !upfProgrammable.data().deviceId().equals(deviceId)) {
                log.warn("Change of the UPF while UPF device is available is not supported!");
                return;
            }

            log.info("Setup UPF device: {}", deviceId);
            upfDeviceId = deviceId;
            upfProgrammable = deviceService.getDevice(deviceId).as(UpfProgrammable.class);

            if (!upfProgrammable.init()) {
                // error message will be printed by init()
                return;
            }

            installInterfaces();

            if (dbufClient != null && configIsLoaded() && config.dbufDrainAddr() != null) {
                addDbufStateToUpfProgrammable();
            } else {
                removeDbufStateFromUpfProgrammable();
            }

            if (configIsLoaded() && config.pscEncapEnabled()) {
                upfProgrammable.enablePscEncap(config.defaultQfi());
            } else {
                upfProgrammable.disablePscEncap();
            }

            upfInitialized.set(true);
            log.info("UPF device setup successful!");
        }
    }

    /**
     * Gets the collection of interfaces present in the UP4 config file.
     *
     * @return an interface collection
     */
    private Collection<UpfInterface> configFileInterfaces() {
        Collection<UpfInterface> interfaces = new ArrayList<>();
        interfaces.add(UpfInterface.createS1uFrom(config.s1uAddress()));
        for (Ip4Prefix uePool : config.uePools()) {
            interfaces.add(UpfInterface.createUePoolFrom(uePool));
        }
        Ip4Address dbufDrainAddr = config.dbufDrainAddr();
        if (dbufDrainAddr != null) {
            interfaces.add(UpfInterface.createDbufReceiverFrom(dbufDrainAddr));
        }
        return interfaces;
    }

    /**
     * Ensure that all interfaces present in the UP4 config file are installed in the UPF device.
     */
    private void ensureInterfacesInstalled() {
        log.info("Ensuring all interfaces present in app config are present on device.");
        Set<UpfInterface> installedInterfaces;
        try {
            installedInterfaces = new HashSet<>(upfProgrammable.getInterfaces());
        } catch (UpfProgrammableException e) {
            log.warn("Failed to read interface: {}", e.getMessage());
            return;
        }
        for (UpfInterface iface : configFileInterfaces()) {
            if (!installedInterfaces.contains(iface)) {
                log.warn("{} is missing from device! Installing", iface);
                try {
                    upfProgrammable.addInterface(iface);
                } catch (UpfProgrammableException e) {
                    log.warn("Failed to insert interface: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void installInterfaces() {
        log.info("Installing interfaces from config.");
        for (UpfInterface iface : configFileInterfaces()) {
            try {
                upfProgrammable.addInterface(iface);
            } catch (UpfProgrammableException e) {
                log.warn("Failed to insert interface: {}", e.getMessage());
            }
        }
    }

    public void postEvent(Up4Event event) {
        post(event);
    }

    /**
     * Unset the UPF dataplane device. If available it will be cleaned-up.
     */
    private void unsetUpfDevice() {
        synchronized (upfInitialized) {
            if (upfProgrammable != null) {
                log.info("UPF was removed. Cleaning up.");
                if (deviceService.isAvailable(upfDeviceId)) {
                    upfProgrammable.cleanUp();
                }
                upfProgrammable = null;
                upfInitialized.set(false);
            }
        }
    }

    private void upfUpdateConfig(Up4Config config) {
        if (config == null) {
            unsetUpfDevice();
            this.config = null;
        } else if (config.isValid()) {
            upfDeviceId = config.up4DeviceId();
            this.config = config;
            setUpfDevice(upfDeviceId);
        } else {
            log.error("Invalid UP4 config loaded! Cannot set up UPF.");
        }
        log.info("Up4Config updated");
    }

    private void dbufUpdateConfig(Up4DbufConfig config) {
        if (config == null) {
            teardownDbufClient();
            removeDbufStateFromUpfProgrammable();
        } else if (config.isValid()) {
            setUpDbufClient(config.serviceAddr(), config.dataplaneAddr());
            addDbufStateToUpfProgrammable();
        } else {
            log.error("Invalid UP4 config loaded! Cannot set up UPF.");
        }
        log.info("Up4DbufConfig updated");
    }

    private void addDbufStateToUpfProgrammable() {
        if (dbufClient == null) {
            log.warn("Cannot add dbuf state to UpfProgrammable, dbufClient is null");
            return;
        }
        if (config == null || config.dbufDrainAddr() == null) {
            log.warn("Cannot add dbuf state to UpfProgrammable, dbufDrainAddr is null");
            return;
        }
        // FIXME: update existing tunnels if dataplane addr changes
        upfProgrammable.setDbufTunnel(config.dbufDrainAddr(), dbufClient.dataplaneIp4Addr());
        upfProgrammable.setBufferDrainer(ueAddr -> {
            // Run the outbound rpc in a forked context so it doesn't cancel if it was called
            // by an inbound rpc that completes faster than the drain call
            Context ctx = Context.current().fork();
            ctx.run(() -> {
                if (dbufClient == null) {
                    log.error("Cannot start dbuf drain for {}, dbufClient is null", ueAddr);
                    return;
                }
                if (config == null || config.dbufDrainAddr() == null) {
                    log.error("Cannot start dbuf drain for {}, dbufDrainAddr is null", ueAddr);
                    return;
                }
                log.info("Started dbuf drain for {}", ueAddr);
                dbufClient.drain(ueAddr, config.dbufDrainAddr(), 2152)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Exception while draining dbuf for {}: {}", ueAddr, ex);
                            } else if (result) {
                                log.info("Dbuf drain completed for {}", ueAddr);
                            } else {
                                log.warn("Unknown error while draining dbuf for {}", ueAddr);
                            }
                        });
            });
        });
    }

    private void removeDbufStateFromUpfProgrammable() {
        if (upfProgrammable != null) {
            upfProgrammable.unsetBufferDrainer();
            upfProgrammable.unsetDbufTunnel();
        }
    }

    private void setUpDbufClient(String serviceAddr, String dataplaneAddr) {
        synchronized (this) {
            if (dbufClient != null) {
                teardownDbufClient();
            }
            if (dbufClient == null) {
                dbufClient = new DefaultDbufClient(serviceAddr, dataplaneAddr, this);
            }
            if (upfProgrammable != null) {
                addDbufStateToUpfProgrammable();
            }
        }
    }

    private void teardownDbufClient() {
        synchronized (this) {
            if (dbufClient != null) {
                dbufClient.shutdown();
                dbufClient = null;
            }
            if (upfProgrammable != null) {
                removeDbufStateFromUpfProgrammable();
            }
        }
    }

    private void updateConfig() {
        Up4Config up4Config = netCfgService.getConfig(appId, Up4Config.class);
        if (up4Config != null) {
            upfUpdateConfig(up4Config);
        }

        Up4DbufConfig dbufConfig = netCfgService.getConfig(appId, Up4DbufConfig.class);
        if (dbufConfig != null) {
            dbufUpdateConfig(dbufConfig);
        }
    }

    @Override
    public void cleanUp() {
        getUpfProgrammable().cleanUp();
    }

    @Override
    public void clearInterfaces() {
        getUpfProgrammable().clearInterfaces();
    }

    @Override
    public void clearFlows() {
        getUpfProgrammable().clearFlows();
    }

    @Override
    public Collection<ForwardingActionRule> getFars() throws UpfProgrammableException {
        return getUpfProgrammable().getFars();
    }

    @Override
    public Collection<PacketDetectionRule> getPdrs() throws UpfProgrammableException {
        return getUpfProgrammable().getPdrs();
    }

    @Override
    public Collection<UpfInterface> getInterfaces() throws UpfProgrammableException {
        return getUpfProgrammable().getInterfaces();
    }

    @Override
    public void addPdr(PacketDetectionRule pdr) throws UpfProgrammableException {
        if (isMaxUeSet() && pdr.counterId() >= getMaxUe() * 2) {
            throw new UpfProgrammableException(
                    "Counter cell index referenced by PDR above max supported UE value.",
                    UpfProgrammableException.Type.COUNTER_INDEX_OUT_OF_RANGE);
        }
        getUpfProgrammable().addPdr(pdr);
    }

    @Override
    public void removePdr(PacketDetectionRule pdr) throws UpfProgrammableException {
        getUpfProgrammable().removePdr(pdr);
    }

    @Override
    public void addFar(ForwardingActionRule far) throws UpfProgrammableException {
        getUpfProgrammable().addFar(far);
    }

    @Override
    public void removeFar(ForwardingActionRule far) throws UpfProgrammableException {
        getUpfProgrammable().removeFar(far);
    }

    @Override
    public void addInterface(UpfInterface upfInterface) throws UpfProgrammableException {
        getUpfProgrammable().addInterface(upfInterface);
    }

    @Override
    public void removeInterface(UpfInterface upfInterface) throws UpfProgrammableException {
        getUpfProgrammable().removeInterface(upfInterface);
    }

    @Override
    public PdrStats readCounter(int counterIdx) throws UpfProgrammableException {
        if (isMaxUeSet() && counterIdx >= getMaxUe() * 2) {
            throw new UpfProgrammableException(
                    "Requested PDR counter cell index above max supported UE value.",
                    UpfProgrammableException.Type.COUNTER_INDEX_OUT_OF_RANGE);
        }
        return getUpfProgrammable().readCounter(counterIdx);
    }

    @Override
    public long pdrCounterSize() {
        long pdrCounterSize = getUpfProgrammable().pdrCounterSize();
        if (isMaxUeSet()) {
            return Math.min(config.maxUes() * 2, pdrCounterSize);
        }
        return pdrCounterSize;
    }

    @Override
    public long farTableSize() {
        long farTableSize = getUpfProgrammable().farTableSize();
        if (isMaxUeSet()) {
            return Math.min(config.maxUes() * 2, farTableSize);
        }
        return farTableSize;
    }

    @Override
    public long pdrTableSize() {
        long pdrTableSize = getUpfProgrammable().pdrTableSize();
        if (isMaxUeSet()) {
            return Math.min(config.maxUes() * 2, pdrTableSize);
        }
        return pdrTableSize;
    }

    @Override
    public Collection<PdrStats> readAllCounters(long maxCounterId) throws UpfProgrammableException {
        if (isMaxUeSet()) {
            if (maxCounterId == -1) {
                maxCounterId = getMaxUe() * 2;
            } else {
                maxCounterId = Math.min(maxCounterId, getMaxUe() * 2);
            }
        }
        return getUpfProgrammable().readAllCounters(maxCounterId);
    }

    @Override
    public void setDbufTunnel(Ip4Address switchAddr, Ip4Address dbufAddr) {
        getUpfProgrammable().setDbufTunnel(switchAddr, dbufAddr);
    }

    @Override
    public void unsetDbufTunnel() {
        getUpfProgrammable().unsetDbufTunnel();
    }

    @Override
    public void setBufferDrainer(BufferDrainer drainer) {
        getUpfProgrammable().setBufferDrainer(drainer);
    }

    @Override
    public void unsetBufferDrainer() {
        getUpfProgrammable().unsetBufferDrainer();
    }

    @Override
    public void enablePscEncap(int defaultQfi) {
        getUpfProgrammable().enablePscEncap(defaultQfi);
    }

    @Override
    public void disablePscEncap() {
        getUpfProgrammable().disablePscEncap();
    }

    @Override
    public void sendPacketOut(ByteBuffer data) {
        getUpfProgrammable().sendPacketOut(data);
    }

    @Override
    public Collection<UpfFlow> getFlows() throws UpfProgrammableException {
        Map<Integer, PdrStats> counterStats = new HashMap<>();
        this.readAllCounters(-1).forEach(
                stats -> counterStats.put(stats.getCellId(), stats));

        // A flow is made of a PDR and the FAR that should apply to packets that
        // hit the PDR. Multiple PDRs can map to the same FAR, so create a
        // one->many mapping of FAR Identifier to flow builder.
        Map<UpfRuleIdentifier, List<UpfFlow.Builder>> globalFarToSessionBuilder = new HashMap<>();
        Collection<ForwardingActionRule> fars = this.getFars();
        Collection<PacketDetectionRule> pdrs = this.getPdrs();
        pdrs.forEach(pdr -> globalFarToSessionBuilder.compute(
                new UpfRuleIdentifier(pdr.sessionId(), pdr.farId()),
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
                }));
        fars.forEach(far -> globalFarToSessionBuilder.compute(
                new UpfRuleIdentifier(far.sessionId(), far.farId()),
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
                }));

        List<UpfFlow> results = new ArrayList<>();
        for (var builderList : globalFarToSessionBuilder.values()) {
            for (var builder : builderList) {
                try {
                    results.add(builder.build());
                } catch (java.lang.IllegalArgumentException e) {
                    log.warn("Corrupt UPF flow found in dataplane: {}",
                             e.getMessage());
                }
            }
        }
        return results;
    }

    private boolean isMaxUeSet() {
        return configIsLoaded() && config.maxUes() > 0;
    }

    private long getMaxUe() {
        return isMaxUeSet() ? config.maxUes() : NO_UE_LIMIT;
    }

    /**
     * React to new devices. The first device recognized to have UPF functionality is taken as the
     * UPF device.
     */
    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceId deviceId = event.subject().id();
            if (deviceId.equals(upfDeviceId)) {
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
                        log.debug("Event: {}, unsetting UPF", event.type());
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
                case CONFIG_UPDATED:
                case CONFIG_ADDED:
                    if (event.config().isEmpty()) {
                        return;
                    }
                    if (event.configClass().equals(Up4Config.class)) {
                        upfUpdateConfig((Up4Config) event.config().get());
                    } else if (event.configClass().equals(Up4DbufConfig.class)) {
                        dbufUpdateConfig((Up4DbufConfig) event.config().get());
                    }
                    log.info("{} updated", event.configClass().getSimpleName());
                    break;
                case CONFIG_REMOVED:
                    if (event.configClass().equals(Up4Config.class)) {
                        upfUpdateConfig(null);
                    } else if (event.configClass().equals(Up4DbufConfig.class)) {
                        dbufUpdateConfig(null);
                    }
                    log.info("{} removed", event.configClass().getSimpleName());
                    break;
                case CONFIG_REGISTERED:
                case CONFIG_UNREGISTERED:
                    break;
                default:
                    log.warn("Unsupported event type {}", event.type());
                    break;
            }
        }

        @Override
        public boolean isRelevant(NetworkConfigEvent event) {
            if (Up4Config.class.equals(event.configClass()) ||
                    Up4DbufConfig.class.equals(event.configClass())) {
                return true;
            }
            log.debug("Ignore irrelevant event class {}", event.configClass().getName());
            return false;
        }
    }

    private class InternalPiPipeconfListener implements PiPipeconfListener {
        @Override
        public void event(PiPipeconfEvent event) {
            switch (event.type()) {
                case REGISTERED:
                    // Recover the case where pipeconf was not ready while we initialized upfProgrammable
                    // TODO: each pipeconf will trigger update but the subsequent ones are redundant. To be optimized
                    updateConfig();
                    break;
                case UNREGISTERED:
                default:
                    // TODO: we do not handle UNREGISTERED event for now
                    break;
            }
        }
    }
}
