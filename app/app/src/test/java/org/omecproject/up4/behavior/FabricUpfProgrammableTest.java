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
import org.omecproject.up4.UpfProgrammable;
import org.omecproject.up4.UpfProgrammableException;

import java.util.Collection;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class FabricUpfProgrammableTest {

    private final DistributedFabricUpfStore upfStore = TestDistributedFabricUpfStore.build();
    private final FabricUpfProgrammable upfProgrammable = new FabricUpfProgrammable(
            new MockFlowRuleService(), new MockP4RuntimeController(),
            new MockPiPipeconfService(), upfStore, TestConstants.DEVICE_ID);

    @Before
    public void setUp() throws Exception {
        upfProgrammable.init(TestConstants.APP_ID, UpfProgrammable.NO_UE_LIMIT);
    }

    @Test
    public void testUplinkPdr() throws Exception {
        assertTrue(upfProgrammable.getPdrs().isEmpty());
        PacketDetectionRule expectedPdr = TestConstants.UPLINK_PDR;
        upfProgrammable.addPdr(expectedPdr);
        Collection<PacketDetectionRule> installedPdrs = upfProgrammable.getPdrs();
        assertThat(installedPdrs.size(), equalTo(1));
        for (var readPdr : installedPdrs) {
            assertThat(readPdr, equalTo(expectedPdr));
        }
        upfProgrammable.removePdr(expectedPdr.withoutActionParams());
        assertTrue(upfProgrammable.getPdrs().isEmpty());
    }

    @Test
    public void testDownlinkPdr() throws Exception {
        assertTrue(upfProgrammable.getPdrs().isEmpty());
        PacketDetectionRule expectedPdr = TestConstants.DOWNLINK_PDR;
        upfProgrammable.addPdr(expectedPdr);
        Collection<PacketDetectionRule> installedPdrs = upfProgrammable.getPdrs();
        assertThat(installedPdrs.size(), equalTo(1));
        for (var readPdr : installedPdrs) {
            assertThat(readPdr, equalTo(expectedPdr));
        }
        upfProgrammable.removePdr(expectedPdr.withoutActionParams());
        assertTrue(upfProgrammable.getPdrs().isEmpty());
    }

    @Test
    public void testUplinkFar() throws Exception {
        assertTrue(upfProgrammable.getFars().isEmpty());
        ForwardingActionRule expectedFar = TestConstants.UPLINK_FAR;
        upfProgrammable.addFar(expectedFar);
        Collection<ForwardingActionRule> installedFars = upfProgrammable.getFars();
        assertThat(installedFars.size(), equalTo(1));
        for (var readFar : installedFars) {
            assertThat(readFar, equalTo(expectedFar));
        }
        upfProgrammable.removeFar(expectedFar.withoutActionParams());
        assertTrue(upfProgrammable.getFars().isEmpty());
    }

    @Test
    public void testDownlinkFar() throws Exception {
        assertTrue(upfProgrammable.getFars().isEmpty());
        ForwardingActionRule expectedFar = TestConstants.DOWNLINK_FAR;
        upfProgrammable.addFar(expectedFar);
        Collection<ForwardingActionRule> installedFars = upfProgrammable.getFars();
        assertThat(installedFars.size(), equalTo(1));
        for (var readFar : installedFars) {
            assertThat(readFar, equalTo(expectedFar));
        }
        upfProgrammable.removeFar(expectedFar.withoutActionParams());
        assertTrue(upfProgrammable.getFars().isEmpty());
    }

    @Test
    public void testUplinkInterface() throws Exception {
        assertTrue(upfProgrammable.getInterfaces().isEmpty());
        UpfInterface expectedInterface = TestConstants.UPLINK_INTERFACE;
        upfProgrammable.addInterface(expectedInterface);
        Collection<UpfInterface> installedInterfaces = upfProgrammable.getInterfaces();
        assertThat(installedInterfaces.size(), equalTo(1));
        for (var readInterface : installedInterfaces) {
            assertThat(readInterface, equalTo(expectedInterface));
        }
        upfProgrammable.removeInterface(expectedInterface);
        assertTrue(upfProgrammable.getInterfaces().isEmpty());
    }

    @Test
    public void testDownlinkInterface() throws Exception {
        assertTrue(upfProgrammable.getInterfaces().isEmpty());
        UpfInterface expectedInterface = TestConstants.DOWNLINK_INTERFACE;
        upfProgrammable.addInterface(expectedInterface);
        Collection<UpfInterface> installedInterfaces = upfProgrammable.getInterfaces();
        assertThat(installedInterfaces.size(), equalTo(1));
        for (var readInterface : installedInterfaces) {
            assertThat(readInterface, equalTo(expectedInterface));
        }
        upfProgrammable.removeInterface(expectedInterface);
        assertTrue(upfProgrammable.getInterfaces().isEmpty());
    }

    @Test
    public void testClearInterfaces() throws Exception {
        assertTrue(upfProgrammable.getInterfaces().isEmpty());
        upfProgrammable.addInterface(TestConstants.UPLINK_INTERFACE);
        upfProgrammable.addInterface(TestConstants.DOWNLINK_INTERFACE);
        assertThat(upfProgrammable.getInterfaces().size(), equalTo(2));
        upfProgrammable.clearInterfaces();
        assertTrue(upfProgrammable.getInterfaces().isEmpty());
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
