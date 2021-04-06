/*
 * SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 * SPDX-FileCopyrightText: {year}-present Open Networking Foundation <info@opennetworking.org>
 */

package org.omecproject.up4.behavior;

import org.junit.Test;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammableException;
import org.onosproject.net.flow.FlowRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class FabricUpfTranslatorTest {

    private final FabricUpfTranslator upfTranslator = new FabricUpfTranslator(TestDistributedFabricUpfStore.build());

    @Test
    public void fabricEntryToUplinkPdrTest() {
        PacketDetectionRule expectedPdr = TestConstants.UPLINK_PDR;
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = upfTranslator.fabricEntryToPdr(TestConstants.FABRIC_UPLINK_PDR);
        } catch (UpfProgrammableException e) {
            assertThat("Fabric uplink PDR should translate to abstract PDR without error.", false);
            return;
        }
        assertThat("Translated PDR should be uplink.", translatedPdr.matchesEncapped());
        assertThat(translatedPdr, equalTo(expectedPdr));
    }

    @Test
    public void fabricEntryToDownlinkPdrTest() {
        PacketDetectionRule expectedPdr = TestConstants.DOWNLINK_PDR;
        PacketDetectionRule translatedPdr;
        try {
            translatedPdr = upfTranslator.fabricEntryToPdr(TestConstants.FABRIC_DOWNLINK_PDR);
        } catch (UpfProgrammableException e) {
            assertThat("Fabric downlink PDR should translate to abstract PDR without error.", false);
            return;
        }

        assertThat("Translated PDR should be downlink.", translatedPdr.matchesUnencapped());
        assertThat(translatedPdr, equalTo(expectedPdr));
    }

    @Test
    public void fabricEntryToUplinkFarTest() {
        ForwardingActionRule translatedFar;
        ForwardingActionRule expectedFar = TestConstants.UPLINK_FAR;
        try {
            translatedFar = upfTranslator.fabricEntryToFar(TestConstants.FABRIC_UPLINK_FAR);
        } catch (UpfProgrammableException e) {
            assertThat("Fabric uplink FAR should correctly translate to abstract FAR without error",
                    false);
            return;
        }
        assertThat("Translated FAR should be uplink.", translatedFar.forwards());
        assertThat(translatedFar, equalTo(expectedFar));
    }

    @Test
    public void fabricEntryToDownlinkFarTest() {
        ForwardingActionRule translatedFar;
        ForwardingActionRule expectedFar = TestConstants.DOWNLINK_FAR;
        try {
            translatedFar = upfTranslator.fabricEntryToFar(TestConstants.FABRIC_DOWNLINK_FAR);
        } catch (UpfProgrammableException e) {
            assertThat("Fabric downlink FAR should correctly translate to abstract FAR without error",
                    false);
            return;
        }
        assertThat("Translated FAR should be downlink.", translatedFar.encaps());
        assertThat(translatedFar, equalTo(expectedFar));
    }

    @Test
    public void fabricEntryToUplinkInterfaceTest() {
        UpfInterface translatedInterface;
        UpfInterface expectedInterface = TestConstants.UPLINK_INTERFACE;
        try {
            translatedInterface = upfTranslator.fabricEntryToInterface(TestConstants.FABRIC_UPLINK_INTERFACE);
        } catch (UpfProgrammableException e) {
            assertThat("Fabric uplink interface should correctly translate to abstract interface without error",
                    false);
            return;
        }
        assertThat("Translated interface should be uplink.", translatedInterface.isAccess());
        assertThat(translatedInterface, equalTo(expectedInterface));
    }

    @Test
    public void fabricEntryToDownlinkInterfaceTest() {
        UpfInterface translatedInterface;
        UpfInterface expectedInterface = TestConstants.DOWNLINK_INTERFACE;
        try {
            translatedInterface = upfTranslator.fabricEntryToInterface(TestConstants.FABRIC_DOWNLINK_INTERFACE);
        } catch (UpfProgrammableException e) {
            assertThat("Fabric downlink interface should correctly translate to abstract interface without error",
                    false);
            return;
        }
        assertThat("Translated interface should be downlink.", translatedInterface.isCore());
        assertThat(translatedInterface, equalTo(expectedInterface));
    }

    @Test
    public void uplinkInterfaceToFabricEntryTest() {
        FlowRule translatedRule;
        FlowRule expectedRule = TestConstants.FABRIC_UPLINK_INTERFACE;
        try {
            translatedRule = upfTranslator.interfaceToFabricEntry(TestConstants.UPLINK_INTERFACE,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (UpfProgrammableException e) {
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
            translatedRule = upfTranslator.interfaceToFabricEntry(TestConstants.DOWNLINK_INTERFACE,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (UpfProgrammableException e) {
            assertThat("Abstract downlink interface should correctly translate to Fabric interface without error",
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
            translatedRule = upfTranslator.pdrToFabricEntry(TestConstants.DOWNLINK_PDR,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (UpfProgrammableException e) {
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
            translatedRule = upfTranslator.farToFabricEntry(TestConstants.UPLINK_FAR,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (UpfProgrammableException e) {
            assertThat("Abstract uplink FAR should correctly translate to Fabric FAR without error",
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
            translatedRule = upfTranslator.pdrToFabricEntry(TestConstants.UPLINK_PDR,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (UpfProgrammableException e) {
            assertThat("Abstract uplink PDR should correctly translate to Fabric PDR without error",
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
            translatedRule = upfTranslator.farToFabricEntry(TestConstants.DOWNLINK_FAR,
                    TestConstants.DEVICE_ID,
                    TestConstants.APP_ID,
                    TestConstants.DEFAULT_PRIORITY);
        } catch (UpfProgrammableException e) {
            assertThat("Abstract downlink FAR should correctly translate to Fabric FAR without error",
                    false);
            return;
        }
        assertThat(translatedRule, equalTo(expectedRule));
    }
}