package org.omecproject.up4.behavior;

import org.junit.Before;
import org.junit.Test;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfRuleIdentifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class FabricUpfProgrammableTest {
    private final FabricUpfProgrammable upfProgrammable = new FabricUpfProgrammable();
    private final Up4TranslatorImpl up4Translator = new Up4TranslatorImpl();

    @Before
    public void setUp() throws Exception {
        upfProgrammable.init(TestConstants.APP_ID, TestConstants.DEVICE_ID);
        upfProgrammable.flowRuleService = new MockFlowRuleService();
        upfProgrammable.up4Translator = up4Translator;
        upfProgrammable.piPipeconfService = new MockPiPipeconfService();
        upfProgrammable.controller = new MockP4RuntimeController();
        setTranslationState();
    }

    private void setTranslationState() {
        up4Translator.farIdMapper.put(
                new UpfRuleIdentifier(TestConstants.SESSION_ID, TestConstants.UPLINK_FAR_ID),
                TestConstants.UPLINK_PHYSICAL_FAR_ID);
        up4Translator.farIdMapper.put(
                new UpfRuleIdentifier(TestConstants.SESSION_ID, TestConstants.DOWNLINK_FAR_ID),
                TestConstants.DOWNLINK_PHYSICAL_FAR_ID);
    }

    @Test
    public void testUplinkPdr() {
        assertTrue(upfProgrammable.getInstalledPdrs().isEmpty());
        PacketDetectionRule expectedPdr = TestConstants.UPLINK_PDR;
        upfProgrammable.addPdr(expectedPdr);
        Collection<PacketDetectionRule> installedPdrs = upfProgrammable.getInstalledPdrs();
        assertThat(installedPdrs.size(), equalTo(1));
        for (var readPdr : installedPdrs) {
            assertThat(readPdr, equalTo(expectedPdr));
        }
        upfProgrammable.removePdr(expectedPdr);
        assertTrue(upfProgrammable.getInstalledPdrs().isEmpty());
    }

    @Test
    public void testDownlinkPdr() {
        assertTrue(upfProgrammable.getInstalledPdrs().isEmpty());
        PacketDetectionRule expectedPdr = TestConstants.DOWNLINK_PDR;
        upfProgrammable.addPdr(expectedPdr);
        Collection<PacketDetectionRule> installedPdrs = upfProgrammable.getInstalledPdrs();
        assertThat(installedPdrs.size(), equalTo(1));
        for (var readPdr : installedPdrs) {
            assertThat(readPdr, equalTo(expectedPdr));
        }
        upfProgrammable.removePdr(expectedPdr);
        assertTrue(upfProgrammable.getInstalledPdrs().isEmpty());
    }

    @Test
    public void testUplinkFar() {
        assertTrue(upfProgrammable.getInstalledFars().isEmpty());
        ForwardingActionRule expectedFar = TestConstants.UPLINK_FAR;
        upfProgrammable.addFar(expectedFar);
        Collection<ForwardingActionRule> installedFars = upfProgrammable.getInstalledFars();
        assertThat(installedFars.size(), equalTo(1));
        for (var readFar : installedFars) {
            assertThat(readFar, equalTo(expectedFar));
        }
        upfProgrammable.removeFar(expectedFar);
        assertTrue(upfProgrammable.getInstalledFars().isEmpty());
    }

    @Test
    public void testDownlinkFar() {
        assertTrue(upfProgrammable.getInstalledFars().isEmpty());
        ForwardingActionRule expectedFar = TestConstants.DOWNLINK_FAR;
        upfProgrammable.addFar(expectedFar);
        Collection<ForwardingActionRule> installedFars = upfProgrammable.getInstalledFars();
        assertThat(installedFars.size(), equalTo(1));
        for (var readFar : installedFars) {
            assertThat(readFar, equalTo(expectedFar));
        }
        upfProgrammable.removeFar(expectedFar);
        assertTrue(upfProgrammable.getInstalledFars().isEmpty());
    }

    @Test
    public void testUplinkInterface() {
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
    public void testDownlinkInterface() {
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
    public void testClearInterfaces() {
        assertTrue(upfProgrammable.getInstalledInterfaces().isEmpty());
        upfProgrammable.addInterface(TestConstants.UPLINK_INTERFACE);
        upfProgrammable.addInterface(TestConstants.DOWNLINK_INTERFACE);
        assertThat(upfProgrammable.getInstalledInterfaces().size(), equalTo(2));
        upfProgrammable.clearInterfaces();
        assertTrue(upfProgrammable.getInstalledInterfaces().isEmpty());
    }

    @Test
    public void testFlows() {
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
    public void testReadAllCounters() {
        assertTrue(upfProgrammable.readAllCounters().isEmpty());
        upfProgrammable.addPdr(TestConstants.UPLINK_PDR);
        upfProgrammable.addPdr(TestConstants.DOWNLINK_PDR);
        Collection<PdrStats> allStats = upfProgrammable.readAllCounters();
        assertThat(allStats.size(), equalTo(2));
        Map<Integer, PdrStats> cellIdToStats = new HashMap<>();
        for (PdrStats stat : allStats) {
            cellIdToStats.put(stat.getCellId(), stat);
        }

        // Should only be two counters available
        assertThat(Set.copyOf(cellIdToStats.keySet()),
                equalTo(Set.of(TestConstants.UPLINK_COUNTER_CELL_ID, TestConstants.DOWNLINK_COUNTER_CELL_ID)));
        for (PdrStats stat : cellIdToStats.values()) {
            assertThat(stat.getIngressBytes(), equalTo(TestConstants.COUNTER_BYTES));
            assertThat(stat.getEgressBytes(), equalTo(TestConstants.COUNTER_BYTES));
            assertThat(stat.getIngressPkts(), equalTo(TestConstants.COUNTER_PKTS));
            assertThat(stat.getEgressPkts(), equalTo(TestConstants.COUNTER_PKTS));
        }
    }
}