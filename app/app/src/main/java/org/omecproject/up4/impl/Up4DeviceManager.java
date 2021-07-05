/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
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
import org.onosproject.net.flow.FlowRuleService;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;


/**
 * Draft UP4 ONOS application component.
 */
@Component(immediate = true, service = {Up4Service.class})
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
    protected Up4Store up4Store;

    private ApplicationId appId;
    private InternalDeviceListener deviceListener;
    private InternalConfigListener netCfgListener;
    private PiPipeconfListener piPipeconfListener;
    private Map<DeviceId, UpfProgrammable> upfProgrammables;
    private Up4Config config;
    private DbufClient dbufClient;

    private GtpTunnel dbufTunnel;

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
        upfProgrammables = Maps.newHashMap();
        // Still need this in case both netcfg and pipeconf event happen before UP4 activation
        updateConfig();

        deviceService.addListener(deviceListener);
        piPipeconfService.addListener(piPipeconfListener);

        log.info("Started.");
    }

    protected void preDeactivate() {
        // Only clean up the state when the deactivation is triggered by ApplicationService
        log.info("Running Up4DeviceManager preDeactivation hook.");
        upfProgrammables.values().stream().filter(Objects::nonNull).forEach(UpfDevice::cleanUp);
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
        upfProgrammables = null;
        log.info("Stopped.");
    }

    @Override
    public boolean configIsLoaded() {
        return config != null;
    }

    /**
     * Checks that all UPF programmable devices can be programmed.
     * This doesn't mean that all UPF programmable devices are available, but only
     * that we called the init() method on all UPF programmable.
     *
     * @return True if UPF programmables can be programmed, False otherwise
     */
    private boolean canProgramUpf() {
        if (!upfAvailable()) {
            if (this.config == null) {
                throw new IllegalStateException(
                        "No UpfProgrammable set because no app config is available!");
            } else {
                this.upfProgrammables.forEach((deviceId, upfProg) -> {
                    if (upfProg == null && !isUpfProgrammable(deviceId)) {
                        throw new IllegalStateException(
                                "No UpfProgrammable set because deviceId present in config is not a valid UPF!");
                    }
                });
                if (!upfInitialized.get()) {
                    throw new IllegalStateException("UPF not initialized!");
                } else {
                    throw new IllegalStateException(
                            format("No UpfProgrammable is set for an unknown reason. Are devices %s available?",
                                   upfProgrammables.keySet().toString()));
                }
            }
        }
        return true;
    }

    public boolean upfAvailable() {
        return !(this.upfProgrammables.isEmpty() || this.upfProgrammables.containsValue(null));
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
                return;
            }
            UpfProgrammable upfProgrammable = upfProgrammables.get(deviceId);
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
            upfProgrammable = deviceService.getDevice(deviceId).as(UpfProgrammable.class);

            if (!upfProgrammable.init()) {
                // error message will be printed by init()
                return;
            }

            installInterfaces(upfProgrammable);

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

            try {
                if (configIsLoaded() && config.pscEncapEnabled()) {
                    upfProgrammable.enablePscEncap(config.defaultQfi());
                } else {
                    upfProgrammable.disablePscEncap();
                }
            } catch (UpfProgrammableException e) {
                log.info(e.getMessage());
            }
            upfProgrammables.put(deviceId, upfProgrammable);
            log.info("UPF device {} setup successful!", deviceId);
            if (upfProgrammables.values().stream().noneMatch(Objects::isNull)) {
                // Currently we don't support dynamic UPF configuration.
                // At the beginning, all UPF devices must be available before
                // starting to program them.
                upfInitialized.set(true);
                log.info("UPF devices setup successful!");
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
        for (UpfInterface iface : configFileInterfaces()) {
            for (UpfProgrammable upfProg : upfProgrammables.values()) {
                try {
                    this.installInterface(upfProg, iface);
                } catch (UpfProgrammableException e) {
                    log.warn("Failed to insert interface: {}", e.getMessage());
                }
            }

        }
    }

    private void installInterfaces(UpfProgrammable upfProgrammable) {
        for (UpfInterface iface : configFileInterfaces()) {
            try {
                this.installInterface(upfProgrammable, iface);
            } catch (UpfProgrammableException e) {
                log.warn("Failed to insert interface: {}", e.getMessage());
            }
        }
    }

    private void installInterface(UpfProgrammable upfProgrammable, UpfInterface intf)
            throws UpfProgrammableException {
        upfProgrammable.addInterface(intf);
    }

    public void postEvent(Up4Event event) {
        post(event);
    }

    /**
     * Unset the UPF dataplane devices. If available they will be cleaned-up.
     */
    private void unsetUpfDevices() {
        synchronized (upfInitialized) {
            upfProgrammables.replaceAll((deviceId, upfProg) -> {
                if (upfProg != null) {
                    log.info("UPF was removed. Cleaning up.");
                    if (deviceService.isAvailable(deviceId)) {
                        upfProg.cleanUp();
                    }
                }
                return null;
            });
            upfProgrammables = null;
            upfInitialized.set(false);
        }
    }

    private void upfUpdateConfig(Up4Config config) {
        if (config == null) {
            unsetUpfDevices();
            this.config = null;
        } else if (config.isValid()) {
            List<DeviceId> upfDeviceIds = config.upfDeviceIds();
            this.config = config;
            upfDeviceIds.forEach(deviceId -> upfProgrammables.putIfAbsent(deviceId, null));
            upfDeviceIds.forEach(this::setUpfDevice);
        } else {
            log.error("Invalid UP4 config loaded! Cannot set up UPF.");
        }
        log.info("Up4Config updated");
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
            if (configIsLoaded()) {
                this.dbufTunnel = GtpTunnel.builder()
                        .setSrc(config.dbufDrainAddr())
                        .setDst(dbufClient.dataplaneIp4Addr())
                        .setSrcPort((short) GTP_PORT)
                        .setTeid(0)
                        .build();
            }
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
        canProgramUpf();
        upfProgrammables.values().forEach(UpfProgrammable::cleanUp);
        up4Store.reset();
    }

    @Override
    public void clearInterfaces() {
        canProgramUpf();
        upfProgrammables.values().forEach(UpfProgrammable::clearInterfaces);
    }

    @Override
    public void clearFlows() {
        canProgramUpf();
        upfProgrammables.values().forEach(UpfProgrammable::clearFlows);
    }

    @Override
    public Collection<ForwardingActionRule> getFars() throws UpfProgrammableException {
        canProgramUpf();
        return upfProgrammables.values().stream().findFirst().get().getFars();
    }

    @Override
    public Collection<PacketDetectionRule> getPdrs() throws UpfProgrammableException {
        canProgramUpf();
        return upfProgrammables.values().stream().findFirst().get().getPdrs();
    }

    @Override
    public Collection<UpfInterface> getInterfaces() throws UpfProgrammableException {
        canProgramUpf();
        return upfProgrammables.values().stream().findFirst().get().getInterfaces();
    }

    @Override
    public void addPdr(PacketDetectionRule pdr) throws UpfProgrammableException {
        if (isMaxUeSet() && pdr.counterId() >= getMaxUe() * 2) {
            throw new UpfProgrammableException(
                    "Counter cell index referenced by PDR above max supported UE value.",
                    UpfProgrammableException.Type.COUNTER_INDEX_OUT_OF_RANGE);
        }
        canProgramUpf();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            upfProg.addPdr(pdr);
        }
        if (pdr.matchesUnencapped()) {
            up4Store.learnFarIdToUeAddrs(pdr);
        }
    }

    @Override
    public void removePdr(PacketDetectionRule pdr) throws UpfProgrammableException {
        canProgramUpf();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            upfProg.removePdr(pdr);
        }
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
        canProgramUpf();
        // TODO: what happens if we get an exception? Should we be able to do a rollback?
        //  We should keep state consistent between devices, if fails, we should remove the fars
        //  on the other devices already programmed. This is valid for all other methods (remove and add).
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            upfProg.addFar(far);
        }
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
        canProgramUpf();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            upfProg.removeFar(far);
        }
    }

    @Override
    public void addInterface(UpfInterface upfInterface) throws UpfProgrammableException {
        canProgramUpf();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            upfProg.addInterface(upfInterface);
        }
    }

    @Override
    public void removeInterface(UpfInterface upfInterface) throws UpfProgrammableException {
        canProgramUpf();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            upfProg.removeInterface(upfInterface);
        }
    }

    @Override
    public PdrStats readCounter(int counterIdx) throws UpfProgrammableException {
        if (isMaxUeSet() && counterIdx >= getMaxUe() * 2) {
            throw new UpfProgrammableException(
                    "Requested PDR counter cell index above max supported UE value.",
                    UpfProgrammableException.Type.COUNTER_INDEX_OUT_OF_RANGE);
        }
        canProgramUpf();
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
        canProgramUpf();
        long pdrCounterSize = upfProgrammables.values().stream().findFirst().get().pdrCounterSize();
        if (isMaxUeSet()) {
            return Math.min(config.maxUes() * 2, pdrCounterSize);
        }
        return pdrCounterSize;
    }

    @Override
    public long farTableSize() {
        canProgramUpf();
        long farTableSize = upfProgrammables.values().stream().findFirst().get().farTableSize();
        if (isMaxUeSet()) {
            return Math.min(config.maxUes() * 2, farTableSize);
        }
        return farTableSize;
    }

    @Override
    public long pdrTableSize() {
        canProgramUpf();
        long pdrTableSize = upfProgrammables.values().stream().findFirst().get().pdrTableSize();
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
        canProgramUpf();
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
        canProgramUpf();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            upfProg.enablePscEncap(defaultQfi);
        }
    }

    @Override
    public void disablePscEncap() throws UpfProgrammableException {
        canProgramUpf();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            upfProg.disablePscEncap();
        }
    }

    @Override
    public void sendPacketOut(ByteBuffer data) {
        canProgramUpf();
        for (UpfProgrammable upfProg : upfProgrammables.values()) {
            upfProg.sendPacketOut(data);
        }
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
     * React to new devices. The first device recognized to have UPF functionality is taken as the
     * UPF device.
     */
    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceId deviceId = event.subject().id();
            if (upfProgrammables.containsKey(deviceId)) {
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
                        // TODO: I should probably do NOTHING!
                        //log.debug("Event: {}, unsetting UPF", event.type());
                        //unsetUpfDevices();
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
