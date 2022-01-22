/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.grpc.Context;
import org.omecproject.dbuf.client.DbufClient;
import org.omecproject.dbuf.client.DefaultDbufClient;
import org.omecproject.up4.Up4Event;
import org.omecproject.up4.Up4EventListener;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.config.Up4Config;
import org.omecproject.up4.config.Up4DbufConfig;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.behaviour.upf.GtpTunnelPeer;
import org.onosproject.net.behaviour.upf.SessionDownlink;
import org.onosproject.net.behaviour.upf.UpfCounter;
import org.onosproject.net.behaviour.upf.UpfDevice;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.behaviour.upf.UpfProgrammable;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;
import org.onosproject.net.behaviour.upf.UpfTerminationDownlink;
import org.onosproject.net.behaviour.upf.UpfTerminationUplink;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.flow.FlowRuleListener;
import org.onosproject.net.flow.FlowRuleOperations;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.pi.service.PiPipeconfEvent;
import org.onosproject.net.pi.service.PiPipeconfListener;
import org.onosproject.net.pi.service.PiPipeconfService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.omecproject.up4.impl.OsgiPropertyConstants.UPF_RECONCILE_INTERVAL;
import static org.omecproject.up4.impl.OsgiPropertyConstants.UPF_RECONCILE_INTERVAL_DEFAULT;
import static org.onlab.util.Tools.getLongProperty;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.net.behaviour.upf.UpfEntityType.SESSION_DOWNLINK;
import static org.onosproject.net.behaviour.upf.UpfEntityType.TERMINATION_DOWNLINK;
import static org.onosproject.net.behaviour.upf.UpfEntityType.TERMINATION_UPLINK;
import static org.onosproject.net.behaviour.upf.UpfEntityType.TUNNEL_PEER;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;


/**
 * Draft UP4 ONOS application component.
 */
@Component(immediate = true, service = {Up4Service.class, Up4AdminService.class},
        property = {
                UPF_RECONCILE_INTERVAL + ":Long=" + UPF_RECONCILE_INTERVAL_DEFAULT,
        })
public class Up4DeviceManager extends AbstractListenerManager<Up4Event, Up4EventListener>
        implements Up4Service, Up4AdminService {

    private static final long NO_UE_LIMIT = -1;
    public static final int GTP_PORT = 2152;
    public static final byte DBUF_TUNNEL_ID = 1;
    // Hard coding the mobile slice value, when supporting multiple slices, we
    // will remove this, and get the slice id from the north.
    public static final byte SLICE_MOBILE = 0xF;

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
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService componentConfigService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected Up4Store up4Store;

    private ExecutorService eventExecutor;
    private ScheduledExecutorService reconciliationExecutor;
    private Future<?> reconciliationTask;

    /**
     * Interval (in seconds) for reconciling state between UPF devices.
     **/
    private long upfReconcileInterval = UPF_RECONCILE_INTERVAL_DEFAULT;

    private ApplicationId appId;
    private InternalDeviceListener deviceListener;
    private InternalConfigListener netCfgListener;
    private PiPipeconfListener piPipeconfListener;
    private FlowRuleListener flowRuleListener;

    private Map<DeviceId, UpfProgrammable> upfProgrammables;
    private Set<DeviceId> upfDevices;
    private DeviceId leaderUpfDevice;
    private Up4Config config;
    private DbufClient dbufClient;

    private GtpTunnelPeer dbufTunnel;

    @Activate
    protected void activate() {
        log.info("Starting...");
        componentConfigService.registerProperties(getClass());
        appId = coreService.registerApplication(AppConstants.APP_NAME, this::preDeactivate);
        eventDispatcher.addSink(Up4Event.class, listenerRegistry);
        deviceListener = new InternalDeviceListener();
        netCfgListener = new InternalConfigListener();
        piPipeconfListener = new InternalPiPipeconfListener();
        flowRuleListener = new InternalFlowRuleListener();
        upfProgrammables = Maps.newConcurrentMap();
        upfDevices = Sets.newConcurrentHashSet();
        eventExecutor = newSingleThreadScheduledExecutor(groupedThreads(
                "omec/up4", "event-%d", log));
        reconciliationExecutor = newSingleThreadScheduledExecutor(groupedThreads(
                "omec/up4/reconcile", "executor", log));

        flowRuleService.addListener(flowRuleListener);
        netCfgService.addListener(netCfgListener);
        netCfgService.registerConfigFactory(up4ConfigFactory);
        netCfgService.registerConfigFactory(dbufConfigFactory);

        // Still need this in case both netcfg and pipeconf event happen before UP4 activation
        updateConfig();

        deviceService.addListener(deviceListener);
        piPipeconfService.addListener(piPipeconfListener);

        log.info("Started.");
    }

    @Modified
    protected void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        Long reconcileInterval = getLongProperty(properties, UPF_RECONCILE_INTERVAL);
        if (reconcileInterval != null && reconcileInterval != upfReconcileInterval) {
            upfReconcileInterval = reconcileInterval;
            synchronized (upfInitialized) {
                if (reconciliationTask != null) {
                    reconciliationTask.cancel(false);
                    if (upfInitialized.get()) {
                        reconciliationTask = reconciliationExecutor.scheduleAtFixedRate(
                                new ReconcileUpfDevices(), 0, upfReconcileInterval, TimeUnit.SECONDS);
                    }
                }
            }
        }
    }

    protected void preDeactivate() {
        // Only clean up the state when the deactivation is triggered by ApplicationService
        log.info("Running Up4DeviceManager preDeactivation hook.");
        // Stop reconcile thread when UPF is being uninitialized
        stopReconcile();
        if (isReady()) {
            upfProgrammables.values().forEach(UpfDevice::cleanUp);
        }
        teardownDbufClient();
        upfInitialized.set(false);
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopping...");
        componentConfigService.unregisterProperties(getClass(), false);
        deviceService.removeListener(deviceListener);
        netCfgService.removeListener(netCfgListener);
        netCfgService.unregisterConfigFactory(up4ConfigFactory);
        netCfgService.unregisterConfigFactory(dbufConfigFactory);
        eventDispatcher.removeSink(Up4Event.class);
        piPipeconfService.removeListener(piPipeconfListener);
        flowRuleService.removeListener(flowRuleListener);

        eventExecutor.shutdownNow();
        reconciliationExecutor.shutdown();

        reconciliationExecutor = null;
        eventExecutor = null;
        leaderUpfDevice = null;
        upfProgrammables = null;
        upfDevices = null;
        log.info("Stopped.");
    }

    @Override
    public boolean configIsLoaded() {
        return config != null;
    }

    private UpfProgrammable getLeaderUpfProgrammable() {
        assertUpfIsReady();
        return upfProgrammables.get(leaderUpfDevice);
    }

    /**
     * Asserts that UPF data plane is ready and try a lazy setup if possible.
     * This doesn't mean that all UPF physical devices are available, but only
     * that we called the init() method on all UPF programmable.
     */
    private void assertUpfIsReady() {
        if (!upfInitialized.get()) {
            if (this.config == null) {
                throw new IllegalStateException(
                        "No UpfProgrammable set because no app config is available!");
            }
            if (leaderUpfDevice == null) {
                throw new IllegalStateException(
                        "Leader UpfProgrammable is not set!");
            }
            if (upfDevices.isEmpty()) {
                throw new IllegalStateException("UPF Devices are not set");
            }
            upfDevices.forEach(deviceId -> {
                if (deviceService.getDevice(deviceId) == null) {
                    throw new IllegalStateException(
                            "No UpfProgrammable set because deviceId is not present in the device store!");
                }
            });
            upfDevices.forEach(deviceId -> {
                if (!isUpfProgrammable(deviceId)) {
                    throw new IllegalStateException(
                            "No UpfProgrammable set because deviceId present in config is not a valid UPF!");
                }
            });
            // setUpfDevice is called during events (Device/Netcfg/Pipeconf)
            // however those events might not be enough to setup the UPF
            // physical devices, especially during ONOS Reboot (i.e.,
            // when the P4RT client is not created before calling setUpfDevice).
            // FIXME: always do lazy setup, instead of relying on events.
            log.info("UPF data plane not initialized, try lazy setup");
            upfDevices.forEach(this::setUpfDevice);
            if (!upfInitialized.get()) {
                throw new IllegalStateException("UPF data plane not initialized after lazy setup!");
            }
        }
    }

    @Override
    public boolean isReady() {
        try {
            assertUpfIsReady();
        } catch (IllegalStateException e) {
            log.info(e.getMessage());
            return false;
        }
        return true;
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
            UpfProgrammable upfProgrammable = upfProgrammables.get(deviceId);
            if (upfInitialized.get()) {
                log.info("UPF {} already initialized, skipping setup.", deviceId);
                // FIXME: this is merely a hotfix for interface entries disappearing when a device becomes available
                // Moreover, if the initialization is done at the very beginning, mastership could change. There could
                // be small intervals without a master and interfaces pushed could be lost. Having this call here should
                // provide more guarantee as this is also called when the pipeline is ready
                ensureInterfacesInstalled();
            } else if (!upfDevices.contains(deviceId)) {
                log.warn("UPF {} is not in the configuration!", deviceId);
            } else if (deviceService.getDevice(deviceId) == null) {
                log.warn("UPF {} currently does not exist in the device store, skip setup.", deviceId);
            } else if (!isUpfProgrammable(deviceId)) {
                log.warn("{} is not UPF physical device!", deviceId);
            } else if (upfProgrammable != null && !upfProgrammable.data().deviceId().equals(deviceId)) {
                log.warn("Change of the UPF while UPF data plane is available is not supported!");
            } else if (upfProgrammable == null) {
                log.info("Setup UPF physical device: {}", deviceId);
                upfProgrammable = deviceService.getDevice(deviceId).as(UpfProgrammable.class);
                if (!upfProgrammable.init()) {
                    // error message will be printed by init()
                    return;
                }
                upfProgrammables.putIfAbsent(deviceId, upfProgrammable);
                log.info("UPF physical device {} setup successful!", deviceId);

                if (upfProgrammables.keySet().containsAll(upfDevices)) {
                    // Currently we don't support dynamic UPF configuration.
                    // The UPF data plane is initialized when all UPF physical
                    // devices have been initialized properly.
                    upfInitialized.set(true);

                    // Do the initial device configuration required
                    installUpfEntities();
                    try {
                        if (configIsLoaded() && config.pscEncapEnabled()) {
                            this.enablePscEncap();
                        } else {
                            this.disablePscEncap();
                        }
                    } catch (UpfProgrammableException e) {
                        log.info(e.getMessage());
                    }
                    // Start reconcile thread only when UPF data plane is initialized
                    reconciliationTask = reconciliationExecutor.scheduleAtFixedRate(
                            new ReconcileUpfDevices(), 0, upfReconcileInterval, TimeUnit.SECONDS);
                    log.info("UPF data plane setup successful!");
                }
            }
        }
    }

    /**
     * Ensure that all interfaces present in the UP4 config file are installed in the UPF leader device.
     */
    private void ensureInterfacesInstalled() {
        log.info("Ensuring all interfaces present in app config are present on the leader device.");
        Collection<? extends UpfEntity> installedInterfaces;
        UpfProgrammable leader = getLeaderUpfProgrammable();
        try {
            installedInterfaces = leader.readAll(UpfEntityType.INTERFACE);
        } catch (UpfProgrammableException e) {
            log.warn("Failed to read interface: {}", e.getMessage());
            return;
        }
        for (UpfInterface iface : configFileInterfaces()) {
            if (!installedInterfaces.contains(iface)) {
                log.warn("{} is missing from leader device! Installing", iface);
                try {
                    leader.apply(iface);
                } catch (UpfProgrammableException e) {
                    log.warn("Failed to insert interface: {}", e.getMessage());
                }
            }
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

    @Override
    public Collection<UplinkUpfFlow> getUplinkFlows() throws UpfProgrammableException {
        Collection<UplinkUpfFlow> uplinkFlows = Lists.newArrayList();
        Collection<? extends UpfEntity> uplinkTerm = this.adminReadAll(TERMINATION_UPLINK);
        for (UpfEntity t : uplinkTerm) {
            UpfTerminationUplink term = (UpfTerminationUplink) t;
            uplinkFlows.add(UplinkUpfFlow.builder().withTerminationUplink(term)
                                    .withCounter(this.readCounter(term.counterId()))
                                    .build());
        }
        return uplinkFlows;
    }

    @Override
    public Collection<DownlinkUpfFlow> getDownlinkFlows() throws UpfProgrammableException {
        Collection<DownlinkUpfFlow> downlinkFlows = Lists.newArrayList();
        Map<Ip4Address, SessionDownlink> ueToSess = Maps.newHashMap();
        Map<Byte, GtpTunnelPeer> idToTunn = Maps.newHashMap();

        Collection<? extends UpfEntity> downlinkTerm = this.adminReadAll(TERMINATION_DOWNLINK);
        this.adminReadAll(SESSION_DOWNLINK).forEach(
                s -> ueToSess.put(((SessionDownlink) s).ueAddress(), (SessionDownlink) s));
        this.adminReadAll(TUNNEL_PEER).forEach(
                t -> idToTunn.put(((GtpTunnelPeer) t).tunPeerId(), (GtpTunnelPeer) t));

        for (UpfEntity t : downlinkTerm) {
            UpfTerminationDownlink term = (UpfTerminationDownlink) t;
            SessionDownlink sess = ueToSess.getOrDefault(term.ueSessionId(), null);
            GtpTunnelPeer tunn = null;
            if (sess != null) {
                tunn = idToTunn.getOrDefault(sess.tunPeerId(), null);
            }
            downlinkFlows.add(DownlinkUpfFlow.builder()
                                      .withTerminationDownlink(term)
                                      .withSessionDownlink(sess)
                                      .withTunnelPeer(tunn)
                                      .withCounter(this.readCounter(term.counterId()))
                                      .build());

        }
        return downlinkFlows;
    }

    @Override
    public void installUpfEntities() {
        ensureInterfacesInstalled();
        installDbufTunnel();
    }

    private void deleteDbufTunnel() {
        if (this.dbufTunnel != null) {
            try {
                log.debug("Remove DBUF GTP tunnel peer.");
                getLeaderUpfProgrammable().delete(dbufTunnel);
            } catch (UpfProgrammableException e) {
                log.warn("Failed to delete DBUF GTP tunnel peer: {}", e.getMessage());
            }
        }
    }

    private void installDbufTunnel() {
        if (this.dbufTunnel != null) {
            try {
                log.debug("Install DBUF GTP tunnel peer.");
                getLeaderUpfProgrammable().apply(dbufTunnel);
            } catch (UpfProgrammableException e) {
                log.warn("Failed to insert DBUF GTP tunnel peer: {}", e.getMessage());
            }
        }
    }

    public void postEvent(Up4Event event) {
        post(event);
    }

    /**
     * Unset and clean-up the UPF data plane.
     */
    private void unsetUpfDataPlane() {
        synchronized (upfInitialized) {
            // Stop reconcile thread when UPF is being uninitialized
            stopReconcile();
            try {
                assertUpfIsReady();
                upfProgrammables.replaceAll((deviceId, upfProg) -> {
                    if (upfProg != null) {
                        log.info("UPF was unset. Cleaning up.");
                        upfProg.cleanUp();
                    }
                    return null;
                });
            } catch (IllegalStateException e) {
                log.error("Error while unsetting UPF physical devices, resetting UP4 " +
                                  "internal state anyway: {}", e.getMessage());
            }
            leaderUpfDevice = null;
            upfProgrammables = Maps.newConcurrentMap();
            upfDevices = Sets.newConcurrentHashSet();
            up4Store.reset();
            upfInitialized.set(false);
        }
    }

    /**
     * Unset a UPF physical device. This method does not clean-up the UpfProgrammable
     * state and should be called only when the given device is not present in
     * the device store.
     *
     * @param deviceId device identifier
     */
    private void unsetUpfDevice(DeviceId deviceId) {
        synchronized (upfInitialized) {
            if (deviceService.getDevice(deviceId) != null) {
                log.error("unsetUpfDevice(DeviceId) should be called when device is not in the store!");
                return;
            }
            // Stop reconcile thread when UPF is being uninitialized
            stopReconcile();
            upfProgrammables.remove(deviceId);
            upfInitialized.set(false);
        }
    }

    private void upfUpdateConfig(Up4Config config) {
        if (config == null) {
            unsetUpfDataPlane();
            this.config = null;
        } else if (config.isValid()) {
            List<DeviceId> upfDeviceIds = config.upfDeviceIds();
            this.config = config;
            leaderUpfDevice = upfDeviceIds.isEmpty() ? null : upfDeviceIds.get(0);
            upfDevices.addAll(upfDeviceIds);
            upfDeviceIds.forEach(this::setUpfDevice);
            updateDbufTunnel();
        } else {
            log.error("Invalid UP4 config loaded! Cannot set up UPF.");
        }
        log.info("Up4Config updated");
    }

    private void updateDbufTunnel() {
        deleteDbufTunnel();
        if (dbufClient != null && configIsLoaded() && config.dbufDrainAddr() != null) {
            this.dbufTunnel = GtpTunnelPeer.builder()
                    .withSrcAddr(config.dbufDrainAddr())
                    .withDstAddr(dbufClient.dataplaneIp4Addr())
                    .withSrcPort((short) GTP_PORT)
                    .withTunnelPeerId(DBUF_TUNNEL_ID)
                    .build();
            installDbufTunnel();
        } else {
            this.dbufTunnel = null;
        }
    }

    private void dbufUpdateConfig(Up4DbufConfig config) {
        if (config == null) {
            teardownDbufClient();
        } else if (config.isValid()) {
            setUpDbufClient(config.serviceAddr(), config.dataplaneAddr());
        } else {
            log.error("Invalid DBUF config loaded! Cannot set up DBUF.");
        }
        log.info("Up4DbufConfig updated");
    }

    private void setUpDbufClient(String serviceAddr, String dataplaneAddr) {
        synchronized (this) {
            if (dbufClient != null) {
                teardownDbufClient();
            }
            if (dbufClient == null) {
                dbufClient = new DefaultDbufClient(serviceAddr, dataplaneAddr, this);
            }
            updateDbufTunnel();
        }
    }

    private void teardownDbufClient() {
        synchronized (this) {
            if (dbufClient != null) {
                dbufClient.shutdown();
                dbufClient = null;
            }
            deleteDbufTunnel();
            dbufTunnel = null;
        }
    }

    private void updateConfig() {
        Up4Config up4Config = netCfgService.getConfig(appId, Up4Config.class);
        if (up4Config != null) {
            upfUpdateConfig(up4Config);
        }

        // We might end-up with requests for buffering but DBUF is not ready/configured yet.
        // Being DBUF best effort should not be a big deal if that happens at very beginning
        Up4DbufConfig dbufConfig = netCfgService.getConfig(appId, Up4DbufConfig.class);
        if (dbufConfig != null) {
            dbufUpdateConfig(dbufConfig);
        }
    }

    @Override
    public void cleanUp() {
        getLeaderUpfProgrammable().cleanUp();
        up4Store.reset();
    }

    private SessionDownlink convertToBuffering(SessionDownlink sess) {
        SessionDownlink.Builder sessBuilder = SessionDownlink.builder()
                .needsBuffering(true)
                .withUeAddress(sess.ueAddress());

        if (dbufTunnel != null) {
            sessBuilder.withGtpTunnelPeerId(DBUF_TUNNEL_ID);
        } else {
            // When we don't have dbuf deployed, we need to drop traffic.
            sessBuilder.needsDropping(true);
        }
        return sessBuilder.build();
    }

    @Override
    public void apply(UpfEntity entity) throws UpfProgrammableException {
        switch (entity.type()) {
            case SESSION_DOWNLINK:
                SessionDownlink sessDl = (SessionDownlink) entity;
                if (sessDl.needsBuffering()) {
                    // Override tunnel peer id with the DBUF
                    entity = convertToBuffering(sessDl);
                    up4Store.learnBufferingUe(sessDl.ueAddress());
                }
                break;
            case TERMINATION_UPLINK:
                UpfTerminationUplink termUl = (UpfTerminationUplink) entity;
                if (isMaxUeSet() && termUl.counterId() >= getMaxUe() * 2) {
                    throw new UpfProgrammableException(
                            "Counter cell index referenced by uplink UPF termination " +
                                    "rule above max supported UE value.",
                            UpfProgrammableException.Type.ENTITY_OUT_OF_RANGE,
                            UpfEntityType.TERMINATION_UPLINK
                    );
                }
                break;
            case TERMINATION_DOWNLINK:
                UpfTerminationDownlink termDl = (UpfTerminationDownlink) entity;
                if (isMaxUeSet() && termDl.counterId() >= getMaxUe() * 2) {
                    throw new UpfProgrammableException(
                            "Counter cell index referenced by downlink UPF termination " +
                                    "rule above max supported UE value.",
                            UpfProgrammableException.Type.ENTITY_OUT_OF_RANGE,
                            UpfEntityType.TERMINATION_DOWNLINK
                    );
                }
                break;
            case INTERFACE:
                UpfInterface intf = (UpfInterface) entity;
                if (intf.isDbufReceiver()) {
                    throw new UpfProgrammableException("Cannot apply the DBUF interface entry!");
                }
                break;
            case TUNNEL_PEER:
                GtpTunnelPeer tunnelPeer = (GtpTunnelPeer) entity;
                if (tunnelPeer.tunPeerId() == DBUF_TUNNEL_ID) {
                    throw new UpfProgrammableException("Cannot apply the DBUF GTP Tunnel Peer");
                }
                break;
            default:
                break;
        }
        getLeaderUpfProgrammable().apply(entity);
        // Drain from DBUF if necessary
        if (entity.type().equals(SESSION_DOWNLINK)) {
            SessionDownlink sess = (SessionDownlink) entity;
            if (!sess.needsBuffering() && up4Store.forgetBufferingUe(sess.ueAddress())) {
                // TODO: Should we wait for rules to be installed on all devices before
                //   triggering drain?
                // Run the outbound rpc in a forked context so it doesn't cancel if it was called
                // by an inbound rpc that completes faster than the drain call
                Ip4Address ueAddr = sess.ueAddress();
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
                    dbufClient.drain(ueAddr, config.dbufDrainAddr(), GTP_PORT)
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
            }
        }
    }

    public void adminApply(UpfEntity entity) throws UpfProgrammableException {
        getLeaderUpfProgrammable().apply(entity);
    }

    @Override
    public Collection<? extends UpfEntity> readAll(UpfEntityType entityType) throws UpfProgrammableException {
        Collection<? extends UpfEntity> entities = getLeaderUpfProgrammable().readAll(entityType);
        switch (entityType) {
            case SESSION_DOWNLINK:
                // TODO: this might be an overkill, however reads are required
                //  only during reconciliation, so this shouldn't affect the
                //  attachment and detachment of UEs.
                // Map the DBUF entities back to be BUFFERING entities.
                return entities.stream().map(e -> {
                    SessionDownlink sess = (SessionDownlink) e;
                    if (sess.tunPeerId() == DBUF_TUNNEL_ID) {
                        return SessionDownlink.builder()
                                .needsBuffering(true)
                                // Towards northbound, do not specify tunnel peer id
                                .withUeAddress(sess.ueAddress())
                                .build();
                    }
                    return e;
                }).collect(Collectors.toList());
            case INTERFACE:
                // Don't expose DBUF interface
                return entities.stream()
                        .filter(e -> !((UpfInterface) e).isDbufReceiver())
                        .collect(Collectors.toList());
            case TUNNEL_PEER:
                // Don't expose DBUF GTP tunnel peer
                return entities.stream()
                        .filter(e -> ((GtpTunnelPeer) e).tunPeerId() != DBUF_TUNNEL_ID)
                        .collect(Collectors.toList());
            default:
                return entities;
        }
    }

    public Collection<? extends UpfEntity> adminReadAll(UpfEntityType entityType)
            throws UpfProgrammableException {
        return getLeaderUpfProgrammable().readAll(entityType);
    }

    @Override
    public UpfCounter readCounter(int counterIdx) throws UpfProgrammableException {
        if (isMaxUeSet() && counterIdx >= getMaxUe() * 2) {
            throw new UpfProgrammableException(
                    "Requested PDR counter cell index above max supported UE value.",
                    UpfProgrammableException.Type.ENTITY_OUT_OF_RANGE, UpfEntityType.COUNTER);
        }
        // When reading counters we need to explicitly read on all UPF physical
        // devices and aggregate counter values.
        assertUpfIsReady();
        // TODO: add get on builder can simply this, by removing the need for building the PdrStat every time.
        UpfCounter.Builder builder = UpfCounter.builder();
        builder.withCellId(counterIdx);
        UpfCounter prevStats = builder.build();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            UpfCounter pdrStat = upfProg.readCounter(counterIdx);
            builder.setIngress(pdrStat.getIngressPkts() + prevStats.getIngressPkts(),
                               pdrStat.getIngressBytes() + prevStats.getIngressBytes());
            builder.setEgress(pdrStat.getEgressPkts() + prevStats.getEgressPkts(),
                              pdrStat.getEgressBytes() + prevStats.getEgressBytes());
            prevStats = builder.build();
        }
        builder.withCellId(counterIdx);
        return builder.build();
    }

    @Override
    public void delete(UpfEntity entity) throws UpfProgrammableException {
        switch (entity.type()) {
            case SESSION_DOWNLINK:
                SessionDownlink sess = (SessionDownlink) entity;
                if (sess.needsBuffering()) {
                    entity = convertToBuffering(sess);
                }
                break;
            case INTERFACE:
                UpfInterface intf = (UpfInterface) entity;
                if (intf.isDbufReceiver()) {
                    throw new UpfProgrammableException("Cannot delete the DBUF interface rule!");
                }
                break;
            case TUNNEL_PEER:
                GtpTunnelPeer tunnel = (GtpTunnelPeer) entity;
                if (tunnel.tunPeerId() == DBUF_TUNNEL_ID) {
                    throw new UpfProgrammableException("Cannot delete the DBUF GTP tunnel peer");
                }
                break;
            default:
                break;
        }
        getLeaderUpfProgrammable().delete(entity);
        forgetBufferingUeIfRequired(entity);
    }

    public void adminDelete(UpfEntity entity) throws UpfProgrammableException {
        getLeaderUpfProgrammable().delete(entity);
        forgetBufferingUeIfRequired(entity);
    }

    private void forgetBufferingUeIfRequired(UpfEntity entity) {
        // if it was used to be a buffer - we need to clean it as we will not see
        // the drain trigger
        if (entity.type().equals(SESSION_DOWNLINK)) {
            up4Store.forgetBufferingUe(((SessionDownlink) entity).ueAddress());
        }
    }

    @Override
    public void deleteAll(UpfEntityType entityType) throws UpfProgrammableException {
        switch (entityType) {
            case TERMINATION_DOWNLINK:
                getLeaderUpfProgrammable().deleteAll(entityType);
                up4Store.reset();
                break;
            case INTERFACE:
                Collection<? extends UpfEntity> intfs =
                        getLeaderUpfProgrammable().readAll(UpfEntityType.INTERFACE).stream()
                                .filter(t -> !((UpfInterface) t).isDbufReceiver())
                                .collect(Collectors.toList());
                for (UpfEntity i : intfs) {
                    getLeaderUpfProgrammable().delete(i);
                }
                break;
            case TUNNEL_PEER:
                Collection<? extends UpfEntity> tunnels =
                        getLeaderUpfProgrammable().readAll(UpfEntityType.TUNNEL_PEER).stream()
                                .filter(t -> ((GtpTunnelPeer) t).tunPeerId() != DBUF_TUNNEL_ID)
                                .collect(Collectors.toList());
                for (UpfEntity tun : tunnels) {
                    getLeaderUpfProgrammable().delete(tun);
                }
                break;
            default:
                getLeaderUpfProgrammable().deleteAll(entityType);
        }
    }

    public void adminDeleteAll(UpfEntityType entityType) throws UpfProgrammableException {
        getLeaderUpfProgrammable().deleteAll(entityType);
    }

    @Override
    public long tableSize(UpfEntityType entityType) throws UpfProgrammableException {
        long entitySize = getLeaderUpfProgrammable().tableSize(entityType);
        switch (entityType) {
            case TERMINATION_UPLINK:
            case TERMINATION_DOWNLINK:
            case SESSION_UPLINK:
            case SESSION_DOWNLINK:
                if (isMaxUeSet()) {
                    return Math.min(config.maxUes(), entitySize);
                }
                break;
            case COUNTER:
                if (isMaxUeSet()) {
                    return Math.min(config.maxUes() * 2, entitySize);
                }
                break;
            default:

        }
        return entitySize;
    }

    @Override
    public Collection<UpfCounter> readCounters(long maxCounterId) throws UpfProgrammableException {
        if (isMaxUeSet()) {
            if (maxCounterId == -1) {
                maxCounterId = getMaxUe() * 2;
            } else {
                maxCounterId = Math.min(maxCounterId, getMaxUe() * 2);
            }
        }
        // When reading counters we need to explicitly read on all UPF physical
        // devices and aggregate counter values.
        assertUpfIsReady();
        // TODO: add get on builder can simply this, by removing the need for building the PdrStat every time.
        UpfCounter.Builder builder = UpfCounter.builder();
        Map<Integer, UpfCounter> mapCounterIdStats = Maps.newHashMap();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            Collection<UpfCounter> pdrStats = upfProg.readCounters(maxCounterId);
            pdrStats.forEach(currStat -> {
                mapCounterIdStats.compute(currStat.getCellId(), (counterId, prevStats) -> {
                    if (prevStats == null) {
                        return currStat;
                    }
                    builder.setIngress(currStat.getIngressPkts() + prevStats.getIngressPkts(),
                                       currStat.getIngressBytes() + prevStats.getIngressBytes());
                    builder.setEgress(currStat.getEgressPkts() + prevStats.getEgressPkts(),
                                      currStat.getEgressBytes() + prevStats.getEgressBytes());
                    builder.withCellId(counterId);
                    return builder.build();
                });
            });
        }
        return mapCounterIdStats.values();
    }

    @Override
    public void enablePscEncap() throws UpfProgrammableException {
        getLeaderUpfProgrammable().enablePscEncap();
    }

    @Override
    public void disablePscEncap() throws UpfProgrammableException {
        getLeaderUpfProgrammable().disablePscEncap();
    }

    /**
     * Send packet out via the UPF data plane.
     * No guarantee on the selected physical device is given, the implementation
     * sends the packet through one of the available UPF physical devices. Data
     * is expected to contain an Ethernet frame.
     * <p>
     * The selected device should process the packet through the pipeline tables
     * to select an output port and to apply eventual modifications (e.g.,
     * MAC rewrite for routing, pushing a VLAN tag, etc.).
     *
     * @param data Ethernet frame bytes
     * @throws UpfProgrammableException if the UPF data plane cannot send the packet
     */
    @Override
    public void sendPacketOut(ByteBuffer data) throws UpfProgrammableException {
        assertUpfIsReady();
        var sendDevice = upfProgrammables.keySet().stream()
                .filter(deviceId -> deviceService.isAvailable(deviceId)).findFirst();
        if (sendDevice.isPresent()) {
            upfProgrammables.get(sendDevice.get()).sendPacketOut(data);
        } else {
            throw new UpfProgrammableException(
                    "Unable to send packet-out, no UPF physical device available!");
        }
    }

    private boolean isMaxUeSet() {
        return configIsLoaded() && config.maxUes() > 0;
    }

    private long getMaxUe() {
        return isMaxUeSet() ? config.maxUes() : NO_UE_LIMIT;
    }

    private void stopReconcile() {
        if (reconciliationTask != null) {
            reconciliationTask.cancel(true);
            reconciliationTask = null;
        }
    }

    /**
     * React to new devices. Setup the UPF physical device when it shows up on the
     * device store.
     */
    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            eventExecutor.execute(() -> internalEventHandler(event));
        }

        private void internalEventHandler(DeviceEvent event) {
            DeviceId deviceId = event.subject().id();
            if (upfDevices.contains(deviceId)) {
                switch (event.type()) {
                    case DEVICE_ADDED:
                    case DEVICE_UPDATED:
                    case DEVICE_AVAILABILITY_CHANGED:
                        log.debug("Event: {}, setting UPF physical device", event.type());
                        setUpfDevice(deviceId);
                        break;
                    case DEVICE_REMOVED:
                    case DEVICE_SUSPENDED:
                        // TODO: DEVICE_SUSPENDED is never generated in ONOS. What is the actual behaviour?
                        log.debug("Event: {}, unsetting UPF physical device", event.type());
                        unsetUpfDevice(deviceId);
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
            eventExecutor.execute(() -> internalEventHandler(event));
        }

        private void internalEventHandler(NetworkConfigEvent event) {
            if (Up4Config.class.equals(event.configClass()) ||
                    Up4DbufConfig.class.equals(event.configClass())) {
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
            } else {
                log.debug("Ignore irrelevant event class {}", event.configClass().getName());
            }
        }
    }

    private class InternalPiPipeconfListener implements PiPipeconfListener {
        @Override
        public void event(PiPipeconfEvent event) {
            eventExecutor.execute(() -> internalEventHandler(event));
        }

        private void internalEventHandler(PiPipeconfEvent event) {
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

    private class InternalFlowRuleListener implements FlowRuleListener {

        @Override
        public void event(FlowRuleEvent event) {
            eventExecutor.execute(() -> internalEventHandler(event));
        }

        private void internalEventHandler(FlowRuleEvent event) {
            if ((event.type() == FlowRuleEvent.Type.RULE_ADD_REQUESTED ||
                    event.type() == FlowRuleEvent.Type.RULE_REMOVE_REQUESTED) &&
                    event.subject().deviceId().equals(leaderUpfDevice)) {
                try {
                    assertUpfIsReady();
                } catch (IllegalStateException e) {
                    log.warn("While handling event type {}: {}", event.type(), e.getMessage());
                    return;
                }
                if (upfProgrammables.get(leaderUpfDevice).fromThisUpf(event.subject())) {
                    log.debug("Relevant FlowRuleEvent {}: {}", event.type(), event.subject());
                    FlowRule rule = event.subject();
                    List<FlowRule> flowRules = Lists.newArrayList();
                    upfProgrammables.keySet().stream()
                            .filter(deviceId -> !deviceId.equals(leaderUpfDevice))
                            .forEach(deviceId -> flowRules.add(copyFlowRuleForDevice(rule, deviceId)));
                    switch (event.type()) {
                        case RULE_ADD_REQUESTED:
                            flowRuleService.applyFlowRules(flowRules.toArray(new FlowRule[0]));
                            break;
                        case RULE_REMOVE_REQUESTED:
                            flowRuleService.removeFlowRules(flowRules.toArray(new FlowRule[0]));
                            break;
                        default:
                            log.error("I should never reach this point on {}", event);
                    }
                }
            }
        }
    }

    private FlowRule copyFlowRuleForDevice(FlowRule original, DeviceId newDevice) {
        var flowRuleBuilder = DefaultFlowRule.builder()
                .fromApp(coreService.getAppId(original.appId()))
                .forDevice(newDevice)
                .forTable(original.table())
                .withSelector(original.selector())
                .withTreatment(original.treatment())
                .withPriority(original.priority());
        if (original.isPermanent()) {
            flowRuleBuilder.makePermanent();
        }
        return flowRuleBuilder.build();
    }

    private class ReconcileUpfDevices implements Runnable {

        @Override
        public void run() {
            try {
                checkStateAndReconcile();
            } catch (Exception e) {
                log.error("Error during reconciliation: {}", e.getMessage());
            }
        }

        private void checkStateAndReconcile() throws UpfProgrammableException {
            log.trace("Running reconciliation task...");
            assertUpfIsReady(); // Use assertUpfIsReady to generate exception and log it on the caller

            for (var entry : upfProgrammables.entrySet()) {
                var deviceId = entry.getKey();
                var upfProg = entry.getValue();
                if (deviceId.equals(leaderUpfDevice)) {
                    continue;
                }
                if (!mastershipService.isLocalMaster(deviceId)) {
                    continue;
                }

                Set<FlowRule> leaderRules =
                    StreamSupport.stream(flowRuleService.getFlowEntries(leaderUpfDevice).spliterator(), false)
                        .filter(r -> getLeaderUpfProgrammable().fromThisUpf(r))
                        .collect(Collectors.toSet());

                // Replace the follower's device id with leader's id,
                // so that we can re-use the exact match function to compare the state
                Set<FlowRule> followerRules =
                    StreamSupport.stream(flowRuleService.getFlowEntries(deviceId).spliterator(), false)
                        .filter(r -> upfProg.fromThisUpf(r))
                        .map(r -> copyFlowRuleForDevice(r, leaderUpfDevice))
                        .collect(Collectors.toSet());

                // Collect the difference between leader and followers
                // There are 3 situations
                // Remove unexpected: Rule is in the follower but not in the leader
                // Update stale: Rule is both on follower and leader but treatments are different
                // Add missing: Rule is in the leader but not in the follower
                FlowRuleOperations.Builder ops = FlowRuleOperations.builder();

                Set<FlowRule> unexpectedRules =
                    followerRules.stream()
                        .filter(fr -> leaderRules.stream().noneMatch(lr -> lr.equals(fr)))
                        .collect(Collectors.toSet());
                followerRules.removeAll(unexpectedRules);
                ops.newStage();
                unexpectedRules.forEach(r -> ops.remove(copyFlowRuleForDevice(r, deviceId)));

                Set<FlowRule> staleRules =
                    leaderRules.stream()
                        .filter(lr -> followerRules.stream().noneMatch(fr -> fr.exactMatch(lr)))
                        .filter(lr -> followerRules.stream().anyMatch(fr -> fr.equals(lr)))
                        .collect(Collectors.toSet());
                leaderRules.removeAll(staleRules);
                ops.newStage();
                staleRules.forEach(r -> ops.modify(copyFlowRuleForDevice(r, deviceId)));

                Set<FlowRule> missingRules =
                    leaderRules.stream()
                        .filter(lr -> followerRules.stream().noneMatch(fr -> fr.equals(lr)))
                        .collect(Collectors.toSet());
                ops.newStage();
                missingRules.forEach(r -> ops.add(copyFlowRuleForDevice(r, deviceId)));

                flowRuleService.apply(ops.build());
            }
        }
    }
}
