/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.up4.impl;

import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip4Address;
import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.onosproject.net.DeviceId;

import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.core.CoreService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.core.ApplicationId;

import java.util.Dictionary;
import java.util.Properties;
import java.util.Arrays;

import static org.onlab.util.Tools.get;

import org.apache.commons.lang3.tuple.Pair;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * Draft UPF ONOS application component.
 */
@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class UpfComponent implements SomeInterface {
    public static final String UPF_APP = "org.onosproject.up4";


    /** Some configurable property. */
    // Leaving in for now as a reference
    private String someProperty;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    // TODO: Use EventuallyConsistentMap instead
    // TODO: Store PDR IDs for the flow rules somehow,
    // since they arent actually part of the flow rules but need to be read
    private BiMap<Pair<Integer, Integer>, Integer> farIds;

    private AtomicInteger lastGlobalFarId;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(UPF_APP,
                                                () -> log.info("Periscope down."));
        cfgService.registerProperties(getClass());

        farIds = HashBiMap.create();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);

        farIds = null;
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }


    private int getGlobalFarId(int sessionId, int farId) {
        return farIds.computeIfAbsent(
            Pair.of(sessionId, farId),
            k -> lastGlobalFarId.incrementAndGet()
        );
    }


    private void addPdr(DeviceId deviceId, int sessionId, int ctrId, int farId, PiCriterion match, PiTableId tableID) {
        int globalFarId = getGlobalFarId(sessionId, farId);
        PiAction action = PiAction.builder()
                .withId(PiActionId.of("spgw_ingress.set_pdr_attributes"))
                .withParameters(Arrays.asList(
                    new PiActionParam(PiActionParamId.of("ctr_id"), ctrId),
                    new PiActionParam(PiActionParamId.of("far_id"), globalFarId)
                ))
                .build();

        FlowRule pdrEntry = DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(tableID)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .build();

        flowRuleService.applyFlowRules(pdrEntry);
        log.info("Added PDR table entry with flowID {}", pdrEntry.id().value());
    }

    @Override
    public void addPdr(DeviceId deviceId, int sessionId, int ctrId, int farId,
                        Ip4Address ueAddr, int teid, Ip4Address tunnelDst) {
        log.info("Adding uplink PDR");
        PiCriterion match = PiCriterion.builder()
            .matchExact(PiMatchFieldId.of("ue_addr"), ueAddr.toInt())
            .matchExact(PiMatchFieldId.of("teid"), teid)
            .matchExact(PiMatchFieldId.of("tunnel_ipv4_dst"), tunnelDst.toInt())
            .build();
        this.addPdr(deviceId, sessionId, ctrId, farId, match, PiTableId.of("spgw_ingress.uplink_pdr_lookup"));
    }

    @Override
    public void addPdr(DeviceId deviceId, int sessionId, int ctrId, int farId, Ip4Address ueAddr) {
        log.info("Adding downlink PDR");
        PiCriterion match = PiCriterion.builder()
            .matchExact(PiMatchFieldId.of("ue_addr"), ueAddr.toInt())
            .build();

        this.addPdr(deviceId, sessionId, ctrId, farId, match, PiTableId.of("spgw_ingress.downlink_pdr_lookup"));
    }


    private void addFar(DeviceId deviceId, int sessionId, int farId, PiAction action) {
        int globalFarId = getGlobalFarId(sessionId, farId);

        PiCriterion match = PiCriterion.builder()
            .matchExact(PiMatchFieldId.of("far_id"), globalFarId)
            .build();
        FlowRule farEntry = DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(PiTableId.of("spgw_ingress.far_lookup"))
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .build();
        flowRuleService.applyFlowRules(farEntry);
        log.info("Added FAR table entry with flowID {}", farEntry.id().value());
    }

    @Override
    public void addFar(DeviceId deviceId, int sessionId, int farId, boolean drop, boolean notifyCp, TunnelDesc tunnelDesc) {
        log.info("Adding simple downlink FAR entry");

        PiAction action = PiAction.builder()
                .withId(PiActionId.of("spgw_ingress.load_tunnel_far_attributes"))
                .withParameters(Arrays.asList(
                    new PiActionParam(PiActionParamId.of("drop"), drop ? 1 : 0),
                    new PiActionParam(PiActionParamId.of("notify_cp"), notifyCp ? 1 : 0),
                    new PiActionParam(PiActionParamId.of("teid"), tunnelDesc.teid),
                    new PiActionParam(PiActionParamId.of("tunnel_src_addr"), tunnelDesc.src.toInt()),
                    new PiActionParam(PiActionParamId.of("tunnel_dst_addr"), tunnelDesc.dst.toInt())
                ))
                .build();
        this.addFar(deviceId, sessionId, farId, action);
    }

    @Override
    public void addFar(DeviceId deviceId, int sessionId, int farId, boolean drop, boolean notifyCp) {
        log.info("Adding simple uplink FAR entry");
        PiAction action = PiAction.builder()
                .withId(PiActionId.of("spgw_ingress.load_normal_far_attributes"))
                .withParameters(Arrays.asList(
                    new PiActionParam(PiActionParamId.of("drop"), drop ? 1 : 0),
                    new PiActionParam(PiActionParamId.of("notify_cp"), notifyCp ? 1 : 0)
                ))
                .build();
        this.addFar(deviceId, sessionId, farId, action);
    }

    @Override
    public void addS1uInterface(DeviceId deviceId, Ip4Address s1uAddr) {
        log.info("Adding S1U interface table entry");
        PiCriterion match = PiCriterion.builder()
            .matchExact(PiMatchFieldId.of("gtp_ipv4_dst"), s1uAddr.toInt())
            .build();
        PiAction action = PiAction.builder()
                .withId(PiActionId.of("nop"))
                .build();
        FlowRule s1uEntry = DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(PiTableId.of("spgw_ingress.uplink_filter_table"))
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .build();
        flowRuleService.applyFlowRules(s1uEntry);
        log.info("Added S1U entry with flowID {}", s1uEntry.id().value());
    }

    @Override
    public void addUePool(DeviceId deviceId, Ip4Prefix poolPrefix) {
        log.info("Adding UE IPv4 Pool prefix");
        PiCriterion match = PiCriterion.builder()
            .matchLpm(PiMatchFieldId.of("ipv4_prefix"), poolPrefix.address().toInt(), poolPrefix.prefixLength())
            .build();
        PiAction action = PiAction.builder()
                .withId(PiActionId.of("nop"))
                .build();
        FlowRule uePoolEntry = DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(PiTableId.of("spgw_ingress.downlink_filter_table"))
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .build();
        flowRuleService.applyFlowRules(uePoolEntry);
        log.info("Added UE IPv4 pool entry with flowID {}", uePoolEntry.id().value());
    }

    public void readPdrCounter() {
        log.info("readPdrCounter called. Currently does nothing.");
    }


}
