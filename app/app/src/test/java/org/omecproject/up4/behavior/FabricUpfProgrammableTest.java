package org.omecproject.up4.behavior;

import org.junit.Before;
import org.junit.Test;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.UpfInterface;

import java.util.Collection;

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
        setTranslationState();
    }

    private void setTranslationState() {
        up4Translator.farIdMapper.put(
                new Up4TranslatorImpl.RuleIdentifier(TestConstants.SESSION_ID, TestConstants.UPLINK_FAR_ID),
                TestConstants.UPLINK_PHYSICAL_FAR_ID);
        up4Translator.farIdMapper.put(
                new Up4TranslatorImpl.RuleIdentifier(TestConstants.SESSION_ID, TestConstants.DOWNLINK_FAR_ID),
                TestConstants.DOWNLINK_PHYSICAL_FAR_ID);
    }

    @Test
    public void testUplinkPdr() {
        assert (upfProgrammable.getInstalledPdrs().isEmpty());
        PacketDetectionRule expectedPdr = TestConstants.UPLINK_PDR;
        upfProgrammable.addPdr(expectedPdr);
        Collection<PacketDetectionRule> installedPdrs = upfProgrammable.getInstalledPdrs();
        assertThat(installedPdrs.size(), equalTo(1));
        for (var readPdr : installedPdrs) {
            assertThat(readPdr, equalTo(expectedPdr));
        }
        upfProgrammable.removePdr(expectedPdr);
        assert (upfProgrammable.getInstalledPdrs().isEmpty());
    }

    @Test
    public void testDownlinkPdr() {
        assert (upfProgrammable.getInstalledPdrs().isEmpty());
        PacketDetectionRule expectedPdr = TestConstants.DOWNLINK_PDR;
        upfProgrammable.addPdr(expectedPdr);
        Collection<PacketDetectionRule> installedPdrs = upfProgrammable.getInstalledPdrs();
        assertThat(installedPdrs.size(), equalTo(1));
        for (var readPdr : installedPdrs) {
            assertThat(readPdr, equalTo(expectedPdr));
        }
        upfProgrammable.removePdr(expectedPdr);
        assert (upfProgrammable.getInstalledPdrs().isEmpty());
    }

    @Test
    public void testUplinkFar() {
        assert (upfProgrammable.getInstalledFars().isEmpty());
        ForwardingActionRule expectedFar = TestConstants.UPLINK_FAR;
        upfProgrammable.addFar(expectedFar);
        Collection<ForwardingActionRule> installedFars = upfProgrammable.getInstalledFars();
        assertThat(installedFars.size(), equalTo(1));
        for (var readFar : installedFars) {
            assertThat(readFar, equalTo(expectedFar));
        }
        upfProgrammable.removeFar(expectedFar);
        assert (upfProgrammable.getInstalledFars().isEmpty());
    }

    @Test
    public void testDownlinkFar() {
        assert (upfProgrammable.getInstalledFars().isEmpty());
        ForwardingActionRule expectedFar = TestConstants.DOWNLINK_FAR;
        upfProgrammable.addFar(expectedFar);
        Collection<ForwardingActionRule> installedFars = upfProgrammable.getInstalledFars();
        assertThat(installedFars.size(), equalTo(1));
        for (var readFar : installedFars) {
            assertThat(readFar, equalTo(expectedFar));
        }
        upfProgrammable.removeFar(expectedFar);
        assert (upfProgrammable.getInstalledFars().isEmpty());
    }

    @Test
    public void testUplinkInterface() {
        assert (upfProgrammable.getInstalledInterfaces().isEmpty());
        UpfInterface expectedInterface = TestConstants.UPLINK_INTERFACE;
        upfProgrammable.addInterface(expectedInterface);
        Collection<UpfInterface> installedInterfaces = upfProgrammable.getInstalledInterfaces();
        assertThat(installedInterfaces.size(), equalTo(1));
        for (var readInterface : installedInterfaces) {
            assertThat(readInterface, equalTo(expectedInterface));
        }
        upfProgrammable.removeInterface(expectedInterface);
        assert (upfProgrammable.getInstalledInterfaces().isEmpty());
    }

    @Test
    public void testDownlinkInterface() {
        assert (upfProgrammable.getInstalledInterfaces().isEmpty());
        UpfInterface expectedInterface = TestConstants.DOWNLINK_INTERFACE;
        upfProgrammable.addInterface(expectedInterface);
        Collection<UpfInterface> installedInterfaces = upfProgrammable.getInstalledInterfaces();
        assertThat(installedInterfaces.size(), equalTo(1));
        for (var readInterface : installedInterfaces) {
            assertThat(readInterface, equalTo(expectedInterface));
        }
        upfProgrammable.removeInterface(expectedInterface);
        assert (upfProgrammable.getInstalledInterfaces().isEmpty());
    }

    @Test
    public void testClearInterfaces() {
        assert (upfProgrammable.getInstalledInterfaces().isEmpty());
        upfProgrammable.addInterface(TestConstants.UPLINK_INTERFACE);
        upfProgrammable.addInterface(TestConstants.DOWNLINK_INTERFACE);
        assertThat(upfProgrammable.getInstalledInterfaces().size(), equalTo(2));
        upfProgrammable.clearInterfaces();
        assert (upfProgrammable.getInstalledInterfaces().isEmpty());
    }

    @Test
    public void testFlows() {
        assert (upfProgrammable.getFlows().isEmpty());
        upfProgrammable.addPdr(TestConstants.UPLINK_PDR);
        upfProgrammable.addPdr(TestConstants.DOWNLINK_PDR);
        upfProgrammable.addFar(TestConstants.UPLINK_FAR);
        upfProgrammable.addFar(TestConstants.DOWNLINK_FAR);
        // TODO: Can't call getFlows when flows are present because we cannot currently read counters.
        // TODO: Need to mock PiPipeconfService and P4RuntimeController
        assertThat(upfProgrammable.getInstalledPdrs().size(), equalTo((2)));
        assertThat(upfProgrammable.getInstalledFars().size(), equalTo((2)));
        upfProgrammable.clearFlows();
        assert (upfProgrammable.getFlows().isEmpty());
    }
}