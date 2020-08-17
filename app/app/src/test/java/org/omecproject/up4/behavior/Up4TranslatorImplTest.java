package org.omecproject.up4.behavior;

import org.junit.Before;
import org.junit.Test;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Translator;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.impl.NorthConstants;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;

import static org.hamcrest.MatcherAssert.assertThat;

public class Up4TranslatorImplTest {
    private final ImmutableByteSequence allOnes32 = ImmutableByteSequence.ofOnes(4);
    private final ImmutableByteSequence sessionId = toSessionId(1);
    private final int counterId = 1;
    private final int pdrId = 1;
    private final int farId = 1;
    private final ImmutableByteSequence teid = ImmutableByteSequence.copyFrom(0xff);
    private final Ip4Address ueAddr = Ip4Address.valueOf("10.0.0.1");
    private final Ip4Address s1uAddr = Ip4Address.valueOf("192.168.0.1");
    private final Ip4Address enbAddr = Ip4Address.valueOf("192.168.0.2");
    private final ImmutableByteSequence tunnelDPort = ImmutableByteSequence.copyFrom((short) 1024);


    private final Up4TranslatorImpl up4Translator = new Up4TranslatorImpl();

    @Before
    public void setUp() throws Exception {
        up4Translator.activate();
    }


    private static ImmutableByteSequence toImmutableByte(int value) {
        try {
            return ImmutableByteSequence.copyFrom(value).fit(8);
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            return ImmutableByteSequence.ofZeros(1);
        }
    }

    private static ImmutableByteSequence toSessionId(long value) {
        try {
            return ImmutableByteSequence.copyFrom(value).fit(NorthConstants.SESSION_ID_BITWIDTH);
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            return ImmutableByteSequence.ofZeros(NorthConstants.SESSION_ID_BITWIDTH / 8);
        }
    }

    @Test
    public void up4EntryToUplinkPdrTest() {
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = up4Translator.up4EntryToPdr(TestConstants.UP4_UPLINK_PDR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink PDR should translate to abstract PDR without error.", false);
            return;
        }
        assertThat("Translated PDR should be uplink.", translatedPdr.isUplink());
    }

    @Test
    public void up4EntryToDownlinkPdrTest() {
        PacketDetectionRule pdr;
        try {
            pdr = up4Translator.up4EntryToPdr(TestConstants.UP4_DOWNLINK_PDR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink PDR should translate to abstract PDR without error.", false);
            return;
        }
        assertThat("Translated PDR should be downlink.", pdr.isDownlink());
    }

    @Test
    public void up4EntryToUplinkFarTest() {
        ForwardingActionRule far;
        try {
            far = up4Translator.up4EntryToFar(TestConstants.UP4_UPLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink FAR should correctly translate to abstract FAR without error",
                    false);
            return;
        }
        assertThat("Translated FAR should be uplink.", far.isUplink());
    }

    @Test
    public void up4EntryToDownlinkFarTest() {
        ForwardingActionRule far;
        try {
            far = up4Translator.up4EntryToFar(TestConstants.UP4_DOWNLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink FAR should correctly translate to abstract FAR without error",
                    false);
            return;
        }
        assertThat("Translated FAR should be downlink.", far.isDownlink());
    }

    @Test
    public void up4EntryToUplinkInterfaceTest() {
        UpfInterface upfInterface;
        try {
            upfInterface = up4Translator.up4EntryToInterface(TestConstants.UP4_UPLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink interface should correctly translate to abstract interface without error",
                    false);
            return;
        }
        assertThat("Translated interface should be uplink.", upfInterface.isUplink());
    }

    @Test
    public void up4EntryToDownlinkInterfaceTest() {
        UpfInterface upfInterface;
        try {
            upfInterface = up4Translator.up4EntryToInterface(TestConstants.UP4_DOWNLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink interface should correctly translate to abstract interface without error",
                    false);
            return;
        }
        assertThat("Translated interface should be downlink.", upfInterface.isDownlink());
    }
}