/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import io.grpc.Context;
import org.omecproject.dbuf.client.DbufClient;
import org.omecproject.dbuf.client.DefaultDbufClient;
import org.omecproject.up4.Up4Event;
import org.omecproject.up4.Up4EventListener;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammable;
import org.omecproject.up4.UpfProgrammableException;
import org.omecproject.up4.behavior.FabricUpfProgrammable;
import org.omecproject.up4.behavior.FabricUpfStore;
import org.omecproject.up4.config.Up4Config;
import org.omecproject.up4.config.Up4DbufConfig;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.service.PiPipeconfService;
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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;


/**
 * Draft UP4 ONOS application component.
 */
@Component(immediate = true, service = {Up4Service.class})
public class Up4DeviceManager extends AbstractListenerManager<Up4Event, Up4EventListener>
        implements Up4Service {

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

    // FIXME: remove after we make FabricUpfProgrammable a proper behavior
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected P4RuntimeController p4RuntimeController;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FabricUpfStore upfStore;

    private ApplicationId appId;
    private InternalDeviceListener deviceListener;
    private InternalConfigListener netCfgListener;
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
        netCfgService.addListener(netCfgListener);
        netCfgService.registerConfigFactory(up4ConfigFactory);
        netCfgService.registerConfigFactory(dbufConfigFactory);

        updateConfig();

        deviceService.addListener(deviceListener);
        log.info("Started.");
    }

    protected void preDeactivate() {
        // Only clean up the state when the deactivation is triggered by ApplicationService
        log.info("Running Up4DeviceManager preDeactivation hook.");
        if (upfProgrammableAvailable()) {
            upfProgrammable.cleanUp();
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
        log.info("Stopped.");
    }

    @Override
    public boolean configIsLoaded() {
        return config != null;
    }

    public UpfProgrammable getUpfProgrammable() {
        if (this.upfProgrammable == null) {
            if (this.config == null) {
                throw new IllegalStateException(
                        "No UpfProgrammable set because no app config is available!");
            } else if (!isUpfDevice(upfDeviceId)) {
                throw new IllegalStateException(
                        "No UpfProgrammable set because deviceId present in config is not a valid UPF!");
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

    @Override
    public boolean isUpfDevice(DeviceId deviceId) {
        final Device device = deviceService.getDevice(deviceId);

        Optional<PiPipeconf> opt = piPipeconfService.getPipeconf(device.id());
        return opt.map(piPipeconf ->
                piPipeconf.id().toString().contains(AppConstants.SUPPORTED_PIPECONF_STRING)).orElse(false);
    }

    @Override
    public void clearUpfProgrammable() {
        if (upfProgrammable == null || !upfInitialized.get()) {
            log.warn("Attempting to clear UPF before it has been initialized!");
            return;
        }
        upfProgrammable.cleanUp();
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
            if (upfProgrammable != null && !upfProgrammable.deviceId().equals(deviceId)) {
                log.warn("Change of the UPF while UPF device is available is not supported!");
                return;
            }

            log.info("Setup UPF device: {}", deviceId);
            upfDeviceId = deviceId;
            // FIXME: change this once UpfProgrammable moves to the onos core
            upfProgrammable = new FabricUpfProgrammable(flowRuleService, p4RuntimeController,
                    piPipeconfService, upfStore, deviceId);

            upfProgrammable.init(appId,
                    config.maxUes() > 0 ? config.maxUes() : UpfProgrammable.NO_UE_LIMIT);

            installInterfaces();

            if (dbufClient != null && config != null && config.dbufDrainAddr() != null) {
                addDbufStateToUpfProgrammable();
            } else {
                removeDbufStateFromUpfProgrammable();
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
        upfProgrammable.unsetBufferDrainer();
        upfProgrammable.unsetDbufTunnel();
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
        Up4Config config = netCfgService.getConfig(appId, Up4Config.class);
        if (config != null) {
            upfUpdateConfig(config);
        }
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
}
