/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.behavior;

import org.junit.Test;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Translator;
import org.omecproject.up4.UpfInterface;
import org.onosproject.net.pi.runtime.PiTableEntry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class Up4TranslatorImplTest {

    private final Up4TranslatorImpl up4Translator = new Up4TranslatorImpl();

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
        assertThat("Translated PDR should be uplink.", translatedPdr.matchesEncapped());
        assertThat(translatedPdr, equalTo(expectedPdr));
    }

    @Test
    public void up4EntryToUplinkPriorityPdrTest() {
        PacketDetectionRule expectedPdr = TestConstants.UPLINK_PRIORITY_PDR;
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = up4Translator.up4EntryToPdr(TestConstants.UP4_UPLINK_PRIORITY_PDR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 uplink PDR should translate to abstract PDR without error.", false);
            return;
        }
        assertThat("Translated PDR should be uplink.", translatedPdr.matchesEncapped());
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

        assertThat("Translated PDR should be downlink.", translatedPdr.matchesUnencapped());
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
        assertThat("Translated FAR should be uplink.", translatedFar.forwards());
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
        assertThat("Translated FAR should be downlink.", translatedFar.encaps());
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
        assertThat("Translated interface should be uplink.", translatedInterface.isAccess());
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
        assertThat("Translated interface should be downlink.", translatedInterface.isCore());
        assertThat(translatedInterface, equalTo(expectedInterface));
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
    public void uplinkPriorityPdrToUp4EntryTest() {
        PiTableEntry translatedRule;
        PiTableEntry expectedRule = TestConstants.UP4_UPLINK_PRIORITY_PDR;
        try {
            translatedRule = up4Translator.pdrToUp4Entry(TestConstants.UPLINK_PRIORITY_PDR);
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
    public void downlinkPriorityPdrToUp4EntryTest() {
        PiTableEntry translatedRule;
        PiTableEntry expectedRule = TestConstants.UP4_DOWNLINK_PRIORITY_PDR;
        try {
            translatedRule = up4Translator.pdrToUp4Entry(TestConstants.DOWNLINK_PRIORITY_PDR);
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

    @Test
    public void invalidUp4EntryToDownlinkFarTest() {
        try {
            up4Translator.up4EntryToFar(TestConstants.INVALID_UP4_DOWNLINK_FAR);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat(e.getMessage(), equalTo("Forward + NotifyCP action is not allowed."));
            return;
        }
        assertThat("The translator should throw an exception", false);
    }
}
