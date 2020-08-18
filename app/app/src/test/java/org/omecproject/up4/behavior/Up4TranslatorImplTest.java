package org.omecproject.up4.behavior;

import org.junit.Before;
import org.junit.Test;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Translator;
import org.omecproject.up4.UpfInterface;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.pi.runtime.PiTableEntry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class Up4TranslatorImplTest {

    private final Up4TranslatorImpl up4Translator = new Up4TranslatorImpl();

    @Before
    public void setUp() throws Exception {
        up4Translator.activate();
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
    public void up4EntryToUplinkPdrTest() {
        PacketDetectionRule expectedPdr = TestConstants.UPLINK_PDR;
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = up4Translator.up4EntryToPdr(TestConstants.UP4_UPLINK_PDR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink PDR should translate to abstract PDR without error.", false);
            return;
        }
        assertThat("Translated PDR should be uplink.", translatedPdr.isUplink());
        assertThat(translatedPdr, equalTo(expectedPdr));
    }

    @Test
    public void fabricEntryToUplinkPdrTest() {
        PacketDetectionRule expectedPdr = TestConstants.UPLINK_PDR;
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = up4Translator.fabricEntryToPdr(TestConstants.FABRIC_UPLINK_PDR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Fabric uplink PDR should translate to abstract PDR without error.", false);
            return;
        }
        assertThat("Translated PDR should be uplink.", translatedPdr.isUplink());
        assertThat(translatedPdr, equalTo(expectedPdr));
    }

    @Test
    public void up4EntryToDownlinkPdrTest() {
        PacketDetectionRule expectedPdr = TestConstants.DOWNLINK_PDR;
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = up4Translator.up4EntryToPdr(TestConstants.UP4_DOWNLINK_PDR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink PDR should translate to abstract PDR without error.", false);
            return;
        }

        assertThat("Translated PDR should be downlink.", translatedPdr.isDownlink());
        assertThat(translatedPdr, equalTo(expectedPdr));
    }

    @Test
    public void fabricEntryToDownlinkPdrTest() {
        PacketDetectionRule expectedPdr = TestConstants.DOWNLINK_PDR;
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = up4Translator.fabricEntryToPdr(TestConstants.FABRIC_DOWNLINK_PDR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Fabric downlink PDR should translate to abstract PDR without error.", false);
            return;
        }

        assertThat("Translated PDR should be downlink.", translatedPdr.isDownlink());
        assertThat(translatedPdr, equalTo(expectedPdr));
    }

    @Test
    public void up4EntryToUplinkFarTest() {
        ForwardingActionRule translatedFar;
        ForwardingActionRule expectedFar = TestConstants.UPLINK_FAR;
        try {
            translatedFar = up4Translator.up4EntryToFar(TestConstants.UP4_UPLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink FAR should correctly translate to abstract FAR without error",
                    false);
            return;
        }
        assertThat("Translated FAR should be uplink.", translatedFar.isUplink());
        assertThat(translatedFar, equalTo(expectedFar));
    }

    @Test
    public void fabricEntryToUplinkFarTest() {
        ForwardingActionRule translatedFar;
        ForwardingActionRule expectedFar = TestConstants.UPLINK_FAR;
        try {
            translatedFar = up4Translator.fabricEntryToFar(TestConstants.FABRIC_UPLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Fabric uplink FAR should correctly translate to abstract FAR without error",
                    false);
            return;
        }
        assertThat("Translated FAR should be uplink.", translatedFar.isUplink());
        assertThat(translatedFar, equalTo(expectedFar));
    }

    @Test
    public void up4EntryToDownlinkFarTest() {
        ForwardingActionRule translatedFar;
        ForwardingActionRule expectedFar = TestConstants.DOWNLINK_FAR;
        try {
            translatedFar = up4Translator.up4EntryToFar(TestConstants.UP4_DOWNLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink FAR should correctly translate to abstract FAR without error",
                    false);
            return;
        }
        assertThat("Translated FAR should be downlink.", translatedFar.isDownlink());
        assertThat(translatedFar, equalTo(expectedFar));
    }

    @Test
    public void fabricEntryToDownlinkFarTest() {
        ForwardingActionRule translatedFar;
        ForwardingActionRule expectedFar = TestConstants.DOWNLINK_FAR;
        try {
            translatedFar = up4Translator.fabricEntryToFar(TestConstants.FABRIC_DOWNLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Fabric downlink FAR should correctly translate to abstract FAR without error",
                    false);
            return;
        }
        assertThat("Translated FAR should be downlink.", translatedFar.isDownlink());
        assertThat(translatedFar, equalTo(expectedFar));
    }

    @Test
    public void up4EntryToUplinkInterfaceTest() {
        UpfInterface translatedInterface;
        UpfInterface expectedInterface = TestConstants.UPLINK_INTERFACE;
        try {
            translatedInterface = up4Translator.up4EntryToInterface(TestConstants.UP4_UPLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink interface should correctly translate to abstract interface without error",
                    false);
            return;
        }
        assertThat("Translated interface should be uplink.", translatedInterface.isUplink());
        assertThat(translatedInterface, equalTo(expectedInterface));
    }

    @Test
    public void fabricEntryToUplinkInterfaceTest() {
        UpfInterface translatedInterface;
        UpfInterface expectedInterface = TestConstants.UPLINK_INTERFACE;
        try {
            translatedInterface = up4Translator.fabricEntryToInterface(TestConstants.FABRIC_UPLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Fabric uplink interface should correctly translate to abstract interface without error",
                    false);
            return;
        }
        assertThat("Translated interface should be uplink.", translatedInterface.isUplink());
        assertThat(translatedInterface, equalTo(expectedInterface));
    }

    @Test
    public void up4EntryToDownlinkInterfaceTest() {
        UpfInterface translatedInterface;
        UpfInterface expectedInterface = TestConstants.DOWNLINK_INTERFACE;
        try {
            translatedInterface = up4Translator.up4EntryToInterface(TestConstants.UP4_DOWNLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink interface should correctly translate to abstract interface without error",
                    false);
            return;
        }
        assertThat("Translated interface should be downlink.", translatedInterface.isDownlink());
        assertThat(translatedInterface, equalTo(expectedInterface));
    }

    @Test
    public void fabricEntryToDownlinkInterfaceTest() {
        UpfInterface translatedInterface;
        UpfInterface expectedInterface = TestConstants.DOWNLINK_INTERFACE;
        try {
            translatedInterface = up4Translator.fabricEntryToInterface(TestConstants.FABRIC_DOWNLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Fabric downlink interface should correctly translate to abstract interface without error",
                    false);
            return;
        }
        assertThat("Translated interface should be downlink.", translatedInterface.isDownlink());
        assertThat(translatedInterface, equalTo(expectedInterface));
    }

    @Test
    public void uplinkInterfaceToFabricEntryTest() {
        FlowRule translatedRule;
        FlowRule expectedRule = TestConstants.FABRIC_UPLINK_INTERFACE;
        try {
            translatedRule = up4Translator.interfaceToFabricEntry(TestConstants.UPLINK_INTERFACE,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract uplink interface should correctly translate to Fabric interface without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void downlinkInterfaceToFabricEntryTest() {
        FlowRule translatedRule;
        FlowRule expectedRule = TestConstants.FABRIC_DOWNLINK_INTERFACE;
        try {
            translatedRule = up4Translator.interfaceToFabricEntry(TestConstants.DOWNLINK_INTERFACE,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract downlink interface should correctly translate to Fabric interface without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void uplinkPdrToFabricEntryTest() {
        FlowRule translatedRule;
        FlowRule expectedRule = TestConstants.FABRIC_UPLINK_PDR;
        try {
            translatedRule = up4Translator.pdrToFabricEntry(TestConstants.UPLINK_PDR,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract uplink PDR should correctly translate to Fabric PDR without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void downlinkPdrToFabricEntryTest() {
        FlowRule translatedRule;
        FlowRule expectedRule = TestConstants.FABRIC_DOWNLINK_PDR;
        try {
            translatedRule = up4Translator.pdrToFabricEntry(TestConstants.DOWNLINK_PDR,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract downlink PDR should correctly translate to Fabric PDR without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void uplinkFarToFabricEntryTest() {
        FlowRule translatedRule;
        FlowRule expectedRule = TestConstants.FABRIC_UPLINK_FAR;
        try {
            translatedRule = up4Translator.farToFabricEntry(TestConstants.UPLINK_FAR,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract uplink FAR should correctly translate to Fabric FAR without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void downlinkFarToFabricEntryTest() {
        FlowRule translatedRule;
        FlowRule expectedRule = TestConstants.FABRIC_DOWNLINK_FAR;
        try {
            translatedRule = up4Translator.farToFabricEntry(TestConstants.DOWNLINK_FAR,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract downlink FAR should correctly translate to Fabric FAR without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void uplinkInterfaceToUp4EntryTest() {
        PiTableEntry translatedRule;
        PiTableEntry expectedRule = TestConstants.UP4_UPLINK_INTERFACE;
        try {
            translatedRule = up4Translator.interfaceToUp4Entry(TestConstants.UPLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract uplink interface should correctly translate to UP4 interface without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void downlinkInterfaceToUp4EntryTest() {
        PiTableEntry translatedRule;
        PiTableEntry expectedRule = TestConstants.UP4_DOWNLINK_INTERFACE;
        try {
            translatedRule = up4Translator.interfaceToUp4Entry(TestConstants.DOWNLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract downlink interface should correctly translate to UP4 interface without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void uplinkPdrToUp4EntryTest() {
        PiTableEntry translatedRule;
        PiTableEntry expectedRule = TestConstants.UP4_UPLINK_PDR;
        try {
            translatedRule = up4Translator.pdrToUp4Entry(TestConstants.UPLINK_PDR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract uplink PDR should correctly translate to UP4 PDR without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void downlinkPdrToUp4EntryTest() {
        PiTableEntry translatedRule;
        PiTableEntry expectedRule = TestConstants.UP4_DOWNLINK_PDR;
        try {
            translatedRule = up4Translator.pdrToUp4Entry(TestConstants.DOWNLINK_PDR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract downlink PDR should correctly translate to UP4 PDR without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void uplinkFarToUp4EntryTest() {
        PiTableEntry translatedRule;
        PiTableEntry expectedRule = TestConstants.UP4_UPLINK_FAR;
        try {
            translatedRule = up4Translator.farToUp4Entry(TestConstants.UPLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract uplink FAR should correctly translate to UP4 FAR without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void downlinkFarToUp4EntryTest() {
        PiTableEntry translatedRule;
        PiTableEntry expectedRule = TestConstants.UP4_DOWNLINK_FAR;
        try {
            translatedRule = up4Translator.farToUp4Entry(TestConstants.DOWNLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract downlink FAR should correctly translate to UP4 FAR without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }


}