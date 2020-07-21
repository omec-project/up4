/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.upf;

import org.omecproject.upf.Up4Service;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.commons.lang3.tuple.Pair;
import org.omecproject.upf.config.UpfAppConfig;
import org.omecproject.upf.config.UpfDeviceConfig;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.cfg.ComponentConfigService;
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
import org.onosproject.net.flow.FlowRuleStore;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.service.PiPipeconfService;
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
import java.util.Dictionary;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.onosproject.net.config.basics.SubjectFactories.DEVICE_SUBJECT_FACTORY;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import static org.onlab.util.Tools.get;


/**
 * Draft UP4 ONOS application component.
 */
@Component(immediate = true,
           service = {Up4Service.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class UpfDeviceManager implements Up4Service {


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
    private ComponentConfigService compCfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public NetworkConfigRegistry netCfgService;

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
        netCfgService.registerConfigFactory(deviceConfigFactory);
        netCfgService.registerConfigFactory(appConfigFactory);
        compCfgService.registerProperties(getClass());
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
        compCfgService.unregisterProperties(getClass(), false);
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

    private final ConfigFactory<DeviceId, UpfDeviceConfig> deviceConfigFactory =
            new ConfigFactory<>(
                    DEVICE_SUBJECT_FACTORY,
                    UpfDeviceConfig.class, AppConstants.CONFIG_KEY) {
                @Override
                public UpfDeviceConfig createConfig() {
                    return new UpfDeviceConfig();
                }
            };


    private final ConfigFactory<ApplicationId, UpfAppConfig> appConfigFactory =
            new ConfigFactory<>(
                    APP_SUBJECT_FACTORY,
                    UpfAppConfig.class, AppConstants.CONFIG_KEY) {
                @Override
                public UpfAppConfig createConfig() {
                    return new UpfAppConfig();
                }
            };



}
