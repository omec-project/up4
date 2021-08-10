/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.junit.Test;
import org.omecproject.up4.Up4Translator;
import org.onosproject.net.behaviour.upf.ForwardingActionRule;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.pi.runtime.PiTableEntry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class Up4TranslatorImplTest {

    private final Up4TranslatorImpl up4Translator = new Up4TranslatorImpl();

    @Test
    public void up4EntryToUplinkPdrTest() {
        up4ToPdrUplink(TestImplConstants.UPLINK_PDR, TestImplConstants.UP4_UPLINK_PDR);
    }

    @Test
    public void up4EntryToUplinkQosPdrTest() {
        up4ToPdrUplink(TestImplConstants.UPLINK_QOS_PDR, TestImplConstants.UP4_UPLINK_QOS_PDR);
        up4ToPdrUplink(TestImplConstants.UPLINK_QOS_4G_PDR, TestImplConstants.UP4_UPLINK_QOS_4G_PDR);
    }

    public void up4ToPdrUplink(PacketDetectionRule expected, PiTableEntry up4Entry) {
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = up4Translator.up4EntryToPdr(up4Entry);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink PDR should translate to abstract PDR without error.", false);
            return;
        }
        assertThat("Translated PDR should be uplink.", translatedPdr.matchesEncapped());
        assertThat(translatedPdr, equalTo(expected));
    }

    @Test
    public void up4EntryToDownlinkPdrTest() {
        up4ToPdrDownlink(TestImplConstants.DOWNLINK_PDR, TestImplConstants.UP4_DOWNLINK_PDR);
    }

    @Test
    public void up4EntryToDownlinkQosPdrTest() {
        up4ToPdrDownlink(TestImplConstants.DOWNLINK_QOS_PDR, TestImplConstants.UP4_DOWNLINK_QOS_PDR);
        up4ToPdrDownlink(TestImplConstants.DOWNLINK_QOS_4G_PDR, TestImplConstants.UP4_DOWNLINK_QOS_4G_PDR);
    }

    public void up4ToPdrDownlink(PacketDetectionRule expected, PiTableEntry up4Entry) {
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = up4Translator.up4EntryToPdr(up4Entry);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink PDR should translate to abstract PDR without error.", false);
            return;
        }

        assertThat("Translated PDR should be downlink.", translatedPdr.matchesUnencapped());
        assertThat(translatedPdr, equalTo(expected));
    }

    @Test
    public void up4EntryToUplinkFarTest() {
        ForwardingActionRule translatedFar;
        ForwardingActionRule expectedFar = TestImplConstants.UPLINK_FAR;
        try {
            translatedFar = up4Translator.up4EntryToFar(TestImplConstants.UP4_UPLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink FAR should correctly translate to abstract FAR without error",
                       false);
            return;
        }
        assertThat("Translated FAR should be uplink.", translatedFar.forwards());
        assertThat(translatedFar, equalTo(expectedFar));
    }

    @Test
    public void up4EntryToDownlinkFarTest() {
        ForwardingActionRule translatedFar;
        ForwardingActionRule expectedFar = TestImplConstants.DOWNLINK_FAR;
        try {
            translatedFar = up4Translator.up4EntryToFar(TestImplConstants.UP4_DOWNLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink FAR should correctly translate to abstract FAR without error",
                       false);
            return;
        }
        assertThat("Translated FAR should be downlink.", translatedFar.encaps());
        assertThat(translatedFar, equalTo(expectedFar));
    }

    @Test
    public void up4EntryToUplinkInterfaceTest() {
        UpfInterface translatedInterface;
        UpfInterface expectedInterface = TestImplConstants.UPLINK_INTERFACE;
        try {
            translatedInterface = up4Translator.up4EntryToInterface(TestImplConstants.UP4_UPLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink interface should correctly translate to abstract interface without error",
                       false);
            return;
        }
        assertThat("Translated interface should be uplink.", translatedInterface.isAccess());
        assertThat(translatedInterface, equalTo(expectedInterface));
    }

    @Test
    public void up4EntryToDownlinkInterfaceTest() {
        UpfInterface translatedInterface;
        UpfInterface expectedInterface = TestImplConstants.DOWNLINK_INTERFACE;
        try {
            translatedInterface = up4Translator.up4EntryToInterface(TestImplConstants.UP4_DOWNLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 downlink interface should correctly translate to abstract interface without error",
                       false);
            return;
        }
        assertThat("Translated interface should be downlink.", translatedInterface.isCore());
        assertThat(translatedInterface, equalTo(expectedInterface));
    }

    @Test
    public void uplinkInterfaceToUp4EntryTest() {
        PiTableEntry translatedRule;
        PiTableEntry expectedRule = TestImplConstants.UP4_UPLINK_INTERFACE;
        try {
            translatedRule = up4Translator.interfaceToUp4Entry(TestImplConstants.UPLINK_INTERFACE);
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
        PiTableEntry expectedRule = TestImplConstants.UP4_DOWNLINK_INTERFACE;
        try {
            translatedRule = up4Translator.interfaceToUp4Entry(TestImplConstants.DOWNLINK_INTERFACE);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract downlink interface should correctly translate to UP4 interface without error",
                       false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void uplinkPdrToUp4EntryTest() {
        pdrToUp4Uplink(TestImplConstants.UP4_UPLINK_PDR, TestImplConstants.UPLINK_PDR);
    }

    @Test
    public void uplinkQosPdrToUp4EntryTest() {
        pdrToUp4Uplink(TestImplConstants.UP4_UPLINK_QOS_PDR, TestImplConstants.UPLINK_QOS_PDR);
        pdrToUp4Uplink(TestImplConstants.UP4_UPLINK_QOS_4G_PDR, TestImplConstants.UPLINK_QOS_4G_PDR);
    }

    public void pdrToUp4Uplink(PiTableEntry expected, PacketDetectionRule pdr) {
        PiTableEntry translatedRule;
        try {
            translatedRule = up4Translator.pdrToUp4Entry(pdr);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract uplink PDR should correctly translate to UP4 PDR without error",
                       false);
            return;
        }
        assertThat(translatedRule, equalTo(expected));
    }

    @Test
    public void downlinkPdrToUp4EntryTest() {
        pdrToUp4Downlink(TestImplConstants.UP4_DOWNLINK_PDR, TestImplConstants.DOWNLINK_PDR);
    }

    @Test
    public void downlinkQosPdrToUp4EntryTest() {
        pdrToUp4Downlink(TestImplConstants.UP4_DOWNLINK_QOS_PDR, TestImplConstants.DOWNLINK_QOS_PDR);
        pdrToUp4Downlink(TestImplConstants.UP4_DOWNLINK_QOS_4G_PDR, TestImplConstants.DOWNLINK_QOS_4G_PDR);
    }

    public void pdrToUp4Downlink(PiTableEntry expected, PacketDetectionRule pdr) {
        PiTableEntry translatedRule;
        try {
            translatedRule = up4Translator.pdrToUp4Entry(pdr);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract downlink PDR should correctly translate to UP4 PDR without error",
                       false);
            return;
        }
        assertThat(translatedRule, equalTo(expected));
    }

    @Test
    public void uplinkFarToUp4EntryTest() {
        PiTableEntry translatedRule;
        PiTableEntry expectedRule = TestImplConstants.UP4_UPLINK_FAR;
        try {
            translatedRule = up4Translator.farToUp4Entry(TestImplConstants.UPLINK_FAR);
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
        PiTableEntry expectedRule = TestImplConstants.UP4_DOWNLINK_FAR;
        try {
            translatedRule = up4Translator.farToUp4Entry(TestImplConstants.DOWNLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("Abstract downlink FAR should correctly translate to UP4 FAR without error",
                       false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }

    @Test
    public void invalidUp4EntryToDownlinkFarTest() {
        try {
            up4Translator.up4EntryToFar(TestImplConstants.INVALID_UP4_DOWNLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat(e.getMessage(), equalTo("Forward + NotifyCP action is not allowed."));
            return;
        }
        assertThat("The translator should throw an exception", false);
    }
}
