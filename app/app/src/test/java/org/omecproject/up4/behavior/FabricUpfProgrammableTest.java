/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.behavior;

import org.junit.Before;
import org.junit.Test;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammableException;
import org.omecproject.up4.UpfRuleIdentifier;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.TestConsistentMap;

import java.util.Collection;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.omecproject.up4.behavior.Up4TranslatorImpl.FAR_ID_MAP_NAME;
import static org.omecproject.up4.behavior.Up4TranslatorImpl.SERIALIZER;

public class FabricUpfProgrammableTest {
    private final FabricUpfProgrammable upfProgrammable = new FabricUpfProgrammable();
    private final Up4TranslatorImpl up4Translator = new Up4TranslatorImpl();

    @Before
    public void setUp() throws Exception {
        upfProgrammable.flowRuleService = new MockFlowRuleService();
        upfProgrammable.up4Translator = up4Translator;
        upfProgrammable.piPipeconfService = new MockPiPipeconfService();
        upfProgrammable.controller = new MockP4RuntimeController();
        upfProgrammable.init(TestConstants.APP_ID, TestConstants.DEVICE_ID);

        TestConsistentMap.Builder<UpfRuleIdentifier, Integer> testConsistentMapBuilder = TestConsistentMap.builder();
        testConsistentMapBuilder
                .withName(FAR_ID_MAP_NAME)
                .withRelaxedReadConsistency()
                .withSerializer(Serializer.using(SERIALIZER.build()));
        up4Translator.farIdMap = testConsistentMapBuilder.build();
        up4Translator.activate();
        setTranslationState();
    }

    private void setTranslationState() {
        up4Translator.farIdMap.put(
                new UpfRuleIdentifier(TestConstants.SESSION_ID, TestConstants.UPLINK_FAR_ID),
                TestConstants.UPLINK_PHYSICAL_FAR_ID);
        up4Translator.farIdMap.put(
                new UpfRuleIdentifier(TestConstants.SESSION_ID, TestConstants.DOWNLINK_FAR_ID),
                TestConstants.DOWNLINK_PHYSICAL_FAR_ID);
    }

    @Test
    public void testUplinkPdr() throws Exception {
        assertTrue(upfProgrammable.getInstalledPdrs().isEmpty());
        PacketDetectionRule expectedPdr = TestConstants.UPLINK_PDR;
        upfProgrammable.addPdr(expectedPdr);
        Collection<PacketDetectionRule> installedPdrs = upfProgrammable.getInstalledPdrs();
        assertThat(installedPdrs.size(), equalTo(1));
        for (var readPdr : installedPdrs) {
            assertThat(readPdr, equalTo(expectedPdr));
        }
        upfProgrammable.removePdr(expectedPdr.withoutActionParams());
        assertTrue(upfProgrammable.getInstalledPdrs().isEmpty());
    }

    @Test
    public void testDownlinkPdr() throws Exception {
        assertTrue(upfProgrammable.getInstalledPdrs().isEmpty());
        PacketDetectionRule expectedPdr = TestConstants.DOWNLINK_PDR;
        upfProgrammable.addPdr(expectedPdr);
        Collection<PacketDetectionRule> installedPdrs = upfProgrammable.getInstalledPdrs();
        assertThat(installedPdrs.size(), equalTo(1));
        for (var readPdr : installedPdrs) {
            assertThat(readPdr, equalTo(expectedPdr));
        }
        upfProgrammable.removePdr(expectedPdr.withoutActionParams());
        assertTrue(upfProgrammable.getInstalledPdrs().isEmpty());
    }

    @Test
    public void testUplinkFar() throws Exception {
        assertTrue(upfProgrammable.getInstalledFars().isEmpty());
        ForwardingActionRule expectedFar = TestConstants.UPLINK_FAR;
        upfProgrammable.addFar(expectedFar);
        Collection<ForwardingActionRule> installedFars = upfProgrammable.getInstalledFars();
        assertThat(installedFars.size(), equalTo(1));
        for (var readFar : installedFars) {
            assertThat(readFar, equalTo(expectedFar));
        }
        upfProgrammable.removeFar(expectedFar.withoutActionParams());
        assertTrue(upfProgrammable.getInstalledFars().isEmpty());
    }

    @Test
    public void testDownlinkFar() throws Exception {
        assertTrue(upfProgrammable.getInstalledFars().isEmpty());
        ForwardingActionRule expectedFar = TestConstants.DOWNLINK_FAR;
        upfProgrammable.addFar(expectedFar);
        Collection<ForwardingActionRule> installedFars = upfProgrammable.getInstalledFars();
        assertThat(installedFars.size(), equalTo(1));
        for (var readFar : installedFars) {
            assertThat(readFar, equalTo(expectedFar));
        }
        upfProgrammable.removeFar(expectedFar.withoutActionParams());
        assertTrue(upfProgrammable.getInstalledFars().isEmpty());
    }

    @Test
    public void testUplinkInterface() throws Exception {
        assertTrue(upfProgrammable.getInstalledInterfaces().isEmpty());
        UpfInterface expectedInterface = TestConstants.UPLINK_INTERFACE;
        upfProgrammable.addInterface(expectedInterface);
        Collection<UpfInterface> installedInterfaces = upfProgrammable.getInstalledInterfaces();
        assertThat(installedInterfaces.size(), equalTo(1));
        for (var readInterface : installedInterfaces) {
            assertThat(readInterface, equalTo(expectedInterface));
        }
        upfProgrammable.removeInterface(expectedInterface);
        assertTrue(upfProgrammable.getInstalledInterfaces().isEmpty());
    }

    @Test
    public void testDownlinkInterface() throws Exception {
        assertTrue(upfProgrammable.getInstalledInterfaces().isEmpty());
        UpfInterface expectedInterface = TestConstants.DOWNLINK_INTERFACE;
        upfProgrammable.addInterface(expectedInterface);
        Collection<UpfInterface> installedInterfaces = upfProgrammable.getInstalledInterfaces();
        assertThat(installedInterfaces.size(), equalTo(1));
        for (var readInterface : installedInterfaces) {
            assertThat(readInterface, equalTo(expectedInterface));
        }
        upfProgrammable.removeInterface(expectedInterface);
        assertTrue(upfProgrammable.getInstalledInterfaces().isEmpty());
    }

    @Test
    public void testClearInterfaces() throws Exception {
        assertTrue(upfProgrammable.getInstalledInterfaces().isEmpty());
        upfProgrammable.addInterface(TestConstants.UPLINK_INTERFACE);
        upfProgrammable.addInterface(TestConstants.DOWNLINK_INTERFACE);
        assertThat(upfProgrammable.getInstalledInterfaces().size(), equalTo(2));
        upfProgrammable.clearInterfaces();
        assertTrue(upfProgrammable.getInstalledInterfaces().isEmpty());
    }

    @Test
    public void testFlows() throws Exception {
        assertTrue(upfProgrammable.getFlows().isEmpty());
        upfProgrammable.addPdr(TestConstants.UPLINK_PDR);
        upfProgrammable.addPdr(TestConstants.DOWNLINK_PDR);
        upfProgrammable.addFar(TestConstants.UPLINK_FAR);
        upfProgrammable.addFar(TestConstants.DOWNLINK_FAR);
        assertThat(upfProgrammable.getFlows().size(), equalTo(2));
        upfProgrammable.clearFlows();
        assertTrue(upfProgrammable.getFlows().isEmpty());
    }

    @Test
    public void testReadAllCounters() throws UpfProgrammableException {
        Collection<PdrStats> allStats = upfProgrammable.readAllCounters();
        assertThat(allStats.size(), equalTo(TestConstants.PHYSICAL_COUNTER_SIZE));
        for (PdrStats stat : allStats) {
            assertThat(stat.getIngressBytes(), equalTo(TestConstants.COUNTER_BYTES));
            assertThat(stat.getEgressBytes(), equalTo(TestConstants.COUNTER_BYTES));
            assertThat(stat.getIngressPkts(), equalTo(TestConstants.COUNTER_PKTS));
            assertThat(stat.getEgressPkts(), equalTo(TestConstants.COUNTER_PKTS));
        }
    }
}
