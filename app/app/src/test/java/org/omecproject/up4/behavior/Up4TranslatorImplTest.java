package org.omecproject.up4.behavior;

import org.junit.Before;
import org.junit.Test;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Translator;
import org.omecproject.up4.UpfInterface;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class Up4TranslatorImplTest {

    private final Up4TranslatorImpl up4Translator = new Up4TranslatorImpl();

    @Before
    public void setUp() throws Exception {
        up4Translator.activate();
    }

    @Test
    public void up4EntryToUplinkPdrTest() {
        PacketDetectionRule expectedPdr = TestConstants.getUplinkPdr();
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = up4Translator.up4EntryToPdr(TestConstants.getUp4UplinkPdr());
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink PDR should translate to abstract PDR without error.", false);
            return;
        }
        assertThat("Translated PDR should be uplink.", translatedPdr.isUplink());
        assertThat(translatedPdr, equalTo(expectedPdr));
    }

    @Test
    public void up4EntryToDownlinkPdrTest() {
        PacketDetectionRule expectedPdr = TestConstants.getDownlinkPdr();
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = up4Translator.up4EntryToPdr(TestConstants.getUp4DownlinkPdr());
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink PDR should translate to abstract PDR without error.", false);
            return;
        }

        assertThat("Translated PDR should be downlink.", translatedPdr.isDownlink());
        assertThat(translatedPdr, equalTo(expectedPdr));
    }

    @Test
    public void up4EntryToUplinkFarTest() {
        ForwardingActionRule translatedFar;
        ForwardingActionRule expectedFar = TestConstants.getUplinkFar();
        try {
            translatedFar = up4Translator.up4EntryToFar(TestConstants.getUp4UplinkFar());
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink FAR should correctly translate to abstract FAR without error",
                    false);
            return;
        }
        assertThat("Translated FAR should be uplink.", translatedFar.isUplink());
        assertThat(translatedFar, equalTo(expectedFar));
    }

    @Test
    public void up4EntryToDownlinkFarTest() {
        ForwardingActionRule translatedFar;
        ForwardingActionRule expectedFar = TestConstants.getDownlinkFar();
        try {
            translatedFar = up4Translator.up4EntryToFar(TestConstants.getUp4DownlinkFar());
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink FAR should correctly translate to abstract FAR without error",
                    false);
            return;
        }
        assertThat("Translated FAR should be downlink.", translatedFar.isDownlink());
        assertThat(translatedFar, equalTo(expectedFar));
    }

    @Test
    public void up4EntryToUplinkInterfaceTest() {
        UpfInterface translatedInterface;
        UpfInterface expectedInterface = TestConstants.getUplinkInterface();
        try {
            translatedInterface = up4Translator.up4EntryToInterface(TestConstants.getUp4UplinkInterface());
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink interface should correctly translate to abstract interface without error",
                    false);
            return;
        }
        assertThat("Translated interface should be uplink.", translatedInterface.isUplink());
        assertThat(translatedInterface, equalTo(expectedInterface));
    }

    @Test
    public void up4EntryToDownlinkInterfaceTest() {
        UpfInterface translatedInterface;
        UpfInterface expectedInterface = TestConstants.getDownlinkInterface();
        try {
            translatedInterface = up4Translator.up4EntryToInterface(TestConstants.getUp4DownlinkInterface());
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink interface should correctly translate to abstract interface without error",
                    false);
            return;
        }
        assertThat("Translated interface should be downlink.", translatedInterface.isDownlink());
        assertThat(translatedInterface, equalTo(expectedInterface));
    }
}