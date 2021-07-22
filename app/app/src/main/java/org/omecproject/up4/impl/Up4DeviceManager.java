/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.grpc.Context;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.behaviour.upf.ForwardingActionRule;
import org.onosproject.net.behaviour.upf.GtpTunnel;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;
import org.onosproject.net.behaviour.upf.PdrStats;
import org.onosproject.net.behaviour.upf.UpfDevice;
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
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.flow.FlowRuleListener;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.omecproject.up4.impl.OsgiPropertyConstants.UPF_RECONCILE_INTERVAL;
import static org.omecproject.up4.impl.OsgiPropertyConstants.UPF_RECONCILE_INTERVAL_DEFAULT;
import static org.onlab.util.Tools.getLongProperty;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;


/**
 * Draft UP4 ONOS application component.
 */
@Component(immediate = true, service = {Up4Service.class},
        property = {
                UPF_RECONCILE_INTERVAL + ":Long=" + UPF_RECONCILE_INTERVAL_DEFAULT,
        })
public class Up4DeviceManager extends AbstractListenerManager<Up4Event, Up4EventListener>
        implements Up4Service {

    private static final long NO_UE_LIMIT = -1;
    public static final int GTP_PORT = 2152;

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
    protected Up4Store up4Store;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService componentConfigService;

    private ExecutorService eventExecutor;
    private ScheduledExecutorService reconciliationExecutor;
    private Future<?> reconciliationTask;

    /** Interval (in seconds) for reconciling state between UPF devices. **/
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

    private GtpTunnel dbufTunnel;

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
            if (reconciliationTask != null) {
                reconciliationTask.cancel(false);
                reconciliationTask = reconciliationExecutor.scheduleAtFixedRate(
                        new ReconcileUpfDevices(), 0, upfReconcileInterval, TimeUnit.SECONDS);
            }
        }
    }

    protected void preDeactivate() {
        // Only clean up the state when the deactivation is triggered by ApplicationService
        log.info("Running Up4DeviceManager preDeactivation hook.");
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

        if (reconciliationTask != null) {
            reconciliationTask.cancel(true);
            reconciliationTask = null;
        }
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
     * Asserts that UPF data plane is ready
     * This doesn't mean that all UPF physical devices are available, but only
     * that we called the init() method on all UPF programmable.
     *
     * @return True if UPF data plane can be programmed, False otherwise
     */
    private boolean assertUpfIsReady() {
        if (!isReady()) {
            if (this.config == null) {
                throw new IllegalStateException(
                        "No UpfProgrammable set because no app config is available!");
            } else {
                if (leaderUpfDevice == null) {
                    throw new IllegalStateException(
                            "Leader UpfProgrammable not set!");
                }
                this.upfProgrammables.forEach((deviceId, upfProg) -> {
                    if (deviceService.getDevice(deviceId) == null) {
                        throw new IllegalStateException(
                                "No UpfProgrammable set because deviceId is not present in the device store!");
                    }
                });
                this.upfProgrammables.forEach((deviceId, upfProg) -> {
                    if (!isUpfProgrammable(deviceId)) {
                        throw new IllegalStateException(
                                "No UpfProgrammable set because deviceId present in config is not a valid UPF!");
                    }
                });
                if (!upfInitialized.get()) {
                    throw new IllegalStateException("UPF data plane not initialized!");
                } else {
                    throw new IllegalStateException(
                            format("No UpfProgrammable is set for an unknown reason. Are devices %s available?",
                                   upfProgrammables.keySet().toString()));
                }
            }
        }
        return true;
    }

    @Override
    public boolean isReady() {
        return !(leaderUpfDevice == null ||
                this.upfProgrammables.isEmpty() ||
                !this.upfProgrammables.keySet().containsAll(upfDevices));
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
                    installInterfaces();
                    try {
                        if (configIsLoaded() && config.pscEncapEnabled()) {
                            this.enablePscEncap(config.defaultQfi());
                        } else {
                            this.disablePscEncap();
                        }
                    } catch (UpfProgrammableException e) {
                        log.info(e.getMessage());
                    }
                    log.info("UPF data plane setup successful!");
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
    public void installInterfaces() {
        log.info("Installing interfaces from config.");
        UpfProgrammable leader = getLeaderUpfProgrammable();
        for (UpfInterface iface : configFileInterfaces()) {
            try {
                leader.addInterface(iface);
            } catch (UpfProgrammableException e) {
                log.warn("Failed to insert interface: {}", e.getMessage());
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
            upfProgrammables.remove(deviceId);
            upfInitialized.set(false);
        }
    }

    private void upfUpdateConfig(Up4Config config) {
        if (config == null) {
            // Stop reconcile thread when up4 config is removed
            if (reconciliationTask != null) {
                reconciliationTask.cancel(true);
                reconciliationTask = null;
            }
            unsetUpfDataPlane();
            this.config = null;
        } else if (config.isValid()) {
            List<DeviceId> upfDeviceIds = config.upfDeviceIds();
            this.config = config;
            leaderUpfDevice = upfDeviceIds.isEmpty() ? null : upfDeviceIds.get(0);
            upfDevices.addAll(upfDeviceIds);
            upfDeviceIds.forEach(this::setUpfDevice);
            updateDbufTunnel();
            // Start reconcile thread only when up4 config is available
            reconciliationTask = reconciliationExecutor.scheduleAtFixedRate(
                    new ReconcileUpfDevices(), 0, upfReconcileInterval, TimeUnit.SECONDS);
        } else {
            log.error("Invalid UP4 config loaded! Cannot set up UPF.");
        }
        log.info("Up4Config updated");
    }

    private void updateDbufTunnel() {
        if (dbufClient != null && configIsLoaded() && config.dbufDrainAddr() != null) {
            this.dbufTunnel = GtpTunnel.builder()
                    .setSrc(config.dbufDrainAddr())
                    .setDst(dbufClient.dataplaneIp4Addr())
                    .setSrcPort((short) GTP_PORT)
                    .setTeid(0)
                    .build();
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

    @Override
    public void clearInterfaces() {
        getLeaderUpfProgrammable().clearInterfaces();
    }

    @Override
    public void clearFlows() {
        getLeaderUpfProgrammable().clearFlows();
    }

    @Override
    public Collection<ForwardingActionRule> getFars() throws UpfProgrammableException {
        return getLeaderUpfProgrammable().getFars();
    }

    @Override
    public Collection<PacketDetectionRule> getPdrs() throws UpfProgrammableException {
        return getLeaderUpfProgrammable().getPdrs();
    }

    @Override
    public Collection<UpfInterface> getInterfaces() throws UpfProgrammableException {
        return getLeaderUpfProgrammable().getInterfaces();
    }

    @Override
    public void addPdr(PacketDetectionRule pdr) throws UpfProgrammableException {
        if (isMaxUeSet() && pdr.counterId() >= getMaxUe() * 2) {
            throw new UpfProgrammableException(
                    "Counter cell index referenced by PDR above max supported UE value.",
                    UpfProgrammableException.Type.COUNTER_INDEX_OUT_OF_RANGE);
        }
        getLeaderUpfProgrammable().addPdr(pdr);
        if (pdr.matchesUnencapped()) {
            up4Store.learnFarIdToUeAddrs(pdr);
        }
    }

    @Override
    public void removePdr(PacketDetectionRule pdr) throws UpfProgrammableException {
        getLeaderUpfProgrammable().removePdr(pdr);
        if (pdr.matchesUnencapped()) {
            // Should we remove just from the map entry with key == far ID?
            up4Store.forgetUeAddr(pdr.ueAddress());
        }
    }

    @Override
    public void addFar(ForwardingActionRule far) throws UpfProgrammableException {
        var ruleId = ImmutablePair.of(far.sessionId(), far.farId());
        if (far.buffers()) {
            // If the far has the buffer flag, modify its tunnel so it directs to dbuf
            far = convertToDbufFar(far);
            up4Store.learnBufferingFarId(ruleId);
        }
        getLeaderUpfProgrammable().addFar(far);
        // TODO: Should we wait for rules to be installed on all devices before
        //   triggering drain?
        if (!far.buffers() && up4Store.isFarIdBuffering(ruleId)) {
            // If this FAR does not buffer but used to, then drain the buffer for every UE address
            // that hits this FAR.
            up4Store.forgetBufferingFarId(ruleId);
            for (var ueAddr : up4Store.ueAddrsOfFarId(ruleId)) {
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

    @Override
    public void removeFar(ForwardingActionRule far) throws UpfProgrammableException {
        getLeaderUpfProgrammable().removeFar(far);
    }

    @Override
    public void addInterface(UpfInterface upfInterface) throws UpfProgrammableException {
        getLeaderUpfProgrammable().addInterface(upfInterface);
    }

    @Override
    public void removeInterface(UpfInterface upfInterface) throws UpfProgrammableException {
        getLeaderUpfProgrammable().removeInterface(upfInterface);
    }

    @Override
    public PdrStats readCounter(int counterIdx) throws UpfProgrammableException {
        if (isMaxUeSet() && counterIdx >= getMaxUe() * 2) {
            throw new UpfProgrammableException(
                    "Requested PDR counter cell index above max supported UE value.",
                    UpfProgrammableException.Type.COUNTER_INDEX_OUT_OF_RANGE);
        }
        // When reading counters we need to explicitly read on all UPF physical
        // devices and aggregate counter values.
        assertUpfIsReady();
        // TODO: add get on builder can simply this, by removing the need for building the PdrStat every time.
        PdrStats.Builder builder = PdrStats.builder();
        PdrStats prevStats = builder.build();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            PdrStats pdrStat = upfProg.readCounter(counterIdx);
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
    public long pdrCounterSize() {
        long pdrCounterSize = getLeaderUpfProgrammable().pdrCounterSize();
        if (isMaxUeSet()) {
            return Math.min(config.maxUes() * 2, pdrCounterSize);
        }
        return pdrCounterSize;
    }

    @Override
    public long farTableSize() {
        long farTableSize = getLeaderUpfProgrammable().farTableSize();
        if (isMaxUeSet()) {
            return Math.min(config.maxUes() * 2, farTableSize);
        }
        return farTableSize;
    }

    @Override
    public long pdrTableSize() {
        long pdrTableSize = getLeaderUpfProgrammable().pdrTableSize();
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
        // When reading counters we need to explicitly read on all UPF physical
        // devices and aggregate counter values.
        assertUpfIsReady();
        // TODO: add get on builder can simply this, by removing the need for building the PdrStat every time.
        PdrStats.Builder builder = PdrStats.builder();
        Map<Integer, PdrStats> mapCounterIdStats = Maps.newHashMap();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            Collection<PdrStats> pdrStats = upfProg.readAllCounters(maxCounterId);
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
    public void enablePscEncap(int defaultQfi) throws UpfProgrammableException {
        getLeaderUpfProgrammable().enablePscEncap(defaultQfi);
    }

    @Override
    public void disablePscEncap() throws UpfProgrammableException {
        getLeaderUpfProgrammable().disablePscEncap();
    }

    @Override
    public Collection<UpfFlow> getFlows() throws UpfProgrammableException {
        Map<Integer, PdrStats> counterStats = new HashMap<>();
        this.readAllCounters(-1).forEach(
                stats -> counterStats.put(stats.getCellId(), stats));

        // A flow is made of a PDR and the FAR that should apply to packets that
        // hit the PDR. Multiple PDRs can map to the same FAR, so create a
        // one->many mapping of FAR Identifier to flow builder.
        Map<Pair<ImmutableByteSequence, Integer>, List<UpfFlow.Builder>> globalFarToSessionBuilder = new HashMap<>();
        Collection<ForwardingActionRule> fars = this.getFars();
        Collection<PacketDetectionRule> pdrs = this.getPdrs();
        pdrs.forEach(pdr -> globalFarToSessionBuilder.compute(
                Pair.of(pdr.sessionId(), pdr.farId()),
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
                Pair.of(far.sessionId(), far.farId()),
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
                    log.warn("Corrupt UPF flow found in data plane: {}",
                             e.getMessage());
                }
            }
        }
        return results;
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
        // dbufTunnel can be null at this point. This will be translated into
        // a load_far_normal action with notify and drop flags set
        return ForwardingActionRule.builder()
                .setFarId(far.farId())
                .withSessionId(far.sessionId())
                .setNotifyFlag(far.notifies())
                .setBufferFlag(true)
                .setTunnel(dbufTunnel)
                .build();
    }

    /**
     * React to new devices. Setup the UPF physical device when it shows up on the
     * device store.
     */
    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
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
            Collection<PacketDetectionRule> leaderPdrs = getLeaderUpfProgrammable().getPdrs();
            Collection<ForwardingActionRule> leaderFars = getLeaderUpfProgrammable().getFars();
            Collection<UpfInterface> leaderInterfaces = getLeaderUpfProgrammable().getInterfaces();

            for (var entry : upfProgrammables.entrySet()) {
                var deviceId = entry.getKey();
                var upfProg = entry.getValue();
                if (deviceId.equals(leaderUpfDevice)) {
                    continue;
                }
                if (!mastershipService.isLocalMaster(deviceId)) {
                    continue;
                }
                Collection<PacketDetectionRule> pdrs = upfProg.getPdrs();
                Collection<ForwardingActionRule> fars = upfProg.getFars();
                Collection<UpfInterface> interfaces = upfProg.getInterfaces();

                // Do not deal with errors on UPFProgrammable calls, we will
                // eventually converge in the next reconciliation cycle.

                // First remove stale entries and then add the missing ones
                // otherwise when an entry needs update, it won't be updated.
                // (e.g., if a FAR needs to be updated, doing add and then remove
                // translates in only a remove, delete then remove first remove
                // the entry and the push the new one).

                // ----- PDRs ----
                if (!leaderPdrs.containsAll(pdrs)) {
                    log.debug("Removing stale PDRs from {}", deviceId);
                    Collection<PacketDetectionRule> toRemovePdrs = new HashSet<>(pdrs);
                    toRemovePdrs.removeAll(leaderPdrs);
                    for (var pdr : toRemovePdrs) {
                        try {
                            upfProg.removePdr(pdr);
                        } catch (UpfProgrammableException e) {
                            log.error("Error while reconciling {}: {} [{}]",
                                      deviceId, e.getMessage(), pdr);
                        }
                    }
                }
                if (!pdrs.containsAll(leaderPdrs)) {
                    log.debug("Adding missing PDRs on {}", deviceId);
                    Collection<PacketDetectionRule> toAddPdrs = new HashSet<>(leaderPdrs);
                    toAddPdrs.removeAll(pdrs);
                    for (var pdr : toAddPdrs) {
                        try {
                            upfProg.addPdr(pdr);
                        } catch (UpfProgrammableException e) {
                            log.error("Error while reconciling {}: {} [{}]",
                                      deviceId, e.getMessage(), pdr);
                        }
                    }
                }
                // ----- FARs ----
                if (!leaderFars.containsAll(fars)) {
                    log.debug("Removing stale FARs from {}", deviceId);
                    Collection<ForwardingActionRule> toRemoveFars = new HashSet<>(fars);
                    toRemoveFars.removeAll(leaderFars);
                    for (var far : toRemoveFars) {
                        try {
                            upfProg.removeFar(far);
                        } catch (UpfProgrammableException e) {
                            log.error("Error while reconciling {}: {} [{}]",
                                      deviceId, e.getMessage(), far);
                        }
                    }
                }
                if (!fars.containsAll(leaderFars)) {
                    log.debug("Adding missing FARs on {}", deviceId);
                    Collection<ForwardingActionRule> toAddFars = new HashSet<>(leaderFars);
                    toAddFars.removeAll(fars);
                    for (var far : toAddFars) {
                        try {
                            upfProg.addFar(far);
                        } catch (UpfProgrammableException e) {
                            log.error("Error while reconciling {}: {} [{}]",
                                      deviceId, e.getMessage(), far);
                        }
                    }
                }
                // ----- Interfaces ----
                if (!leaderInterfaces.containsAll(interfaces)) {
                    log.debug("Removing stale UPF Interfaces from {}", deviceId);
                    Collection<UpfInterface> toRemoveIntfs = new HashSet<>(interfaces);
                    toRemoveIntfs.removeAll(leaderInterfaces);
                    for (var intf : toRemoveIntfs) {
                        try {
                            upfProg.removeInterface(intf);
                        } catch (UpfProgrammableException e) {
                            log.error("Error while reconciling {}: {} [{}]",
                                      deviceId, e.getMessage(), intf);
                        }
                    }
                }
                if (!interfaces.containsAll(leaderInterfaces)) {
                    log.debug("Adding missing UPF Interfaces on {}", deviceId);
                    Collection<UpfInterface> toAddIntfs = new HashSet<>(leaderInterfaces);
                    toAddIntfs.removeAll(interfaces);
                    for (var intf : toAddIntfs) {
                        try {
                            upfProg.addInterface(intf);
                        } catch (UpfProgrammableException e) {
                            log.error("Error while reconciling {}: {} [{}]",
                                      deviceId, e.getMessage(), intf);
                        }
                    }
                }
            }
        }
    }
}
