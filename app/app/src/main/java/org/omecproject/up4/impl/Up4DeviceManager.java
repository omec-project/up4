/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.omecproject.up4.Up4Service;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammable;
import org.omecproject.up4.config.Up4Config;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;


/**
 * Draft UP4 ONOS application component.
 */
@Component(immediate = true,
        service = {Up4Service.class})
public class Up4DeviceManager implements Up4Service {

    private static final long DEFAULT_P4_DEVICE_ID = 1;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final AtomicBoolean upfInitialized = new AtomicBoolean(false);
    private final ConfigFactory<ApplicationId, Up4Config> appConfigFactory = new ConfigFactory<>(
            APP_SUBJECT_FACTORY,
            Up4Config.class,
            Up4Config.KEY) {
        @Override
        public Up4Config createConfig() {
            log.info("Creating UPF Config");
            return new Up4Config();
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
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected UpfProgrammable upfProgrammableService;
    private ApplicationId appId;
    private InternalDeviceListener deviceListener;
    private InternalConfigListener netCfgListener;
    private UpfProgrammable upfProgrammable;
    private DeviceId upfDeviceId;
    private Up4Config config;

    @Activate
    protected void activate() {
        log.info("Starting...");
        appId = coreService.registerApplication(AppConstants.APP_NAME,
                () -> log.info("Periscope down."));
        deviceListener = new InternalDeviceListener();
        netCfgListener = new InternalConfigListener();
        netCfgService.addListener(netCfgListener);
        netCfgService.registerConfigFactory(appConfigFactory);

        updateConfig();

        deviceService.addListener(deviceListener);
        log.info("Started.");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopping...");
        if (upfProgrammableAvailable()) {
            upfProgrammable.cleanUp(appId);
        }
        deviceService.removeListener(deviceListener);
        netCfgService.removeListener(netCfgListener);
        netCfgService.unregisterConfigFactory(appConfigFactory);
        upfInitialized.set(false);
        log.info("Stopped.");
    }

    public UpfProgrammable getUpfProgrammable() {
        return this.upfProgrammable;
    }

    public boolean upfProgrammableAvailable() {
        return upfProgrammable != null;
    }

    public DeviceId getUpfDeviceId() {
        return upfDeviceId;
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
        upfProgrammable.cleanUp(appId);
    }

    private void setUpfDevice(DeviceId deviceId) {
        synchronized (upfInitialized) {
            if (upfInitialized.get()) {
                log.info("UPF {} already initialized, skipping setup.", deviceId);
                return;
            }
            if (!deviceService.isAvailable(deviceId)) {
                log.info("UPF is currently unavailable, skip setup.");
                return;
            }
            if (!isUpfDevice(deviceId)) {
                log.warn("{} is not a UPF, cannot setup.", deviceId);
                return;
            }
            if (upfProgrammable != null && !upfProgrammable.deviceId().equals(deviceId)) {
                log.warn("Change of the UPF while UPF device is available is not supported!");
                return;
            }

            log.info("Setup UPF device: {}", deviceId);
            upfProgrammable = upfProgrammableService; // TODO: change this once UpfProgrammable moves to the onos core

            upfProgrammable.cleanUp(appId);
            upfProgrammable.init(appId, deviceId);

            setInterfaces();

            upfInitialized.set(true);
            log.info("UPF device setup successful!");
        }
    }

    /**
     * Install the UPF dataplane interfaces.
     */
    private void setInterfaces() {
        log.info("Installing interfaces from config.");
        upfProgrammable.addInterface(UpfInterface.createUePoolFrom(config.s1uPrefix()));
        for (Ip4Prefix uePool : config.uePools()) {
            upfProgrammable.addInterface(UpfInterface.createUePoolFrom(uePool));
        }
    }

    /**
     * Unset the UPF dataplane device. If available it will be cleaned-up.
     */
    private void unsetUpfDevice() {
        synchronized (upfInitialized) {
            if (upfProgrammable != null) {
                log.info("UPF cleanup");
                upfProgrammable.cleanUp(appId);
                upfProgrammable = null;
                upfInitialized.set(false);
            }
        }
    }

    private void upfUpdateConfig(Up4Config config) {
        if (config.isValid()) {
            upfDeviceId = config.up4DeviceId();
            this.config = config;
            setUpfDevice(upfDeviceId);
        }

    }

    private void updateConfig() {
        Up4Config config = netCfgService.getConfig(appId, Up4Config.class);
        if (config != null) {
            upfUpdateConfig(config);
        }
    }

    /**
     * React to new devices. The first device recognized to have UPF
     * functionality is taken as the UPF device.
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
                    event.config().ifPresent(config -> {
                        upfUpdateConfig((Up4Config) config);
                        log.info("{} updated", config.getClass().getSimpleName());
                    });
                    break;
                case CONFIG_REMOVED:
                    event.prevConfig().ifPresent(config -> {
                        unsetUpfDevice();
                        log.info("{} removed", config.getClass().getSimpleName());
                    });
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
            if (event.configClass().equals(Up4Config.class)) {
                return true;
            }
            log.debug("Ignore irrelevant event class {}", event.configClass().getName());
            return false;
        }
    }
}
