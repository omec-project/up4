/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.junit.Test;
import org.omecproject.up4.Up4Translator;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.pi.runtime.PiTableEntry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class Up4TranslatorImplTest {

    private final Up4TranslatorImpl up4Translator = new Up4TranslatorImpl();

    public UpfEntity up4ToUpfEntity(UpfEntity expected, PiTableEntry up4Entry) {
        UpfEntity translatedEntity;
        try {
            translatedEntity = up4Translator.up4TableEntryToUpfEntity(up4Entry);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 table entry should translate to UPF entity without error.", false);
            return null;
        }
        assertThat(translatedEntity, equalTo(expected));
        return translatedEntity;
    }

    public PiTableEntry upfEntityToUp4(PiTableEntry expected, UpfEntity up4Entry) {
        PiTableEntry translatedEntity;
        try {
            translatedEntity = up4Translator.entityToUp4TableEntry(up4Entry);
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UPF entity should correctly translate to UP4 table entry without error.", false);
            return null;
        }
        assertThat(translatedEntity, equalTo(expected));
        return translatedEntity;
    }

    @Test
    public void up4EntryToTunnelPeerTest() {
        up4ToUpfEntity(TestImplConstants.TUNNEL_PEER, TestImplConstants.UP4_TUNNEL_PEER);
    }

    @Test
    public void up4EntryToUplinkSessionTest() {
        up4ToUpfEntity(TestImplConstants.UPLINK_SESSION, TestImplConstants.UP4_UPLINK_SESSION);
    }

    @Test
    public void up4EntryToDownlinkSessionTest() {
        up4ToUpfEntity(TestImplConstants.DOWNLINK_SESSION, TestImplConstants.UP4_DOWNLINK_SESSION);
    }

    @Test
    public void up4EntryToDownlinkSessionDbufTest() {
        up4ToUpfEntity(TestImplConstants.DOWNLINK_SESSION_DBUF, TestImplConstants.UP4_DOWNLINK_SESSION_DBUF);
    }

    @Test
    public void up4EntryToUplinkTerminationTest() {
        up4ToUpfEntity(TestImplConstants.UPLINK_TERMINATION, TestImplConstants.UP4_UPLINK_TERMINATION);
    }

    @Test
    public void up4EntryToUplinkTerminationNoTcTest() {
        up4ToUpfEntity(TestImplConstants.UPLINK_TERMINATION_NO_TC, TestImplConstants.UP4_UPLINK_TERMINATION_NO_TC);
    }

    @Test
    public void up4EntryToUplinkTerminationDropTest() {
        up4ToUpfEntity(TestImplConstants.UPLINK_TERMINATION_DROP, TestImplConstants.UP4_UPLINK_TERMINATION_DROP);
    }

    @Test
    public void up4EntryToDownlinkTerminationTest() {
        up4ToUpfEntity(TestImplConstants.DOWNLINK_TERMINATION, TestImplConstants.UP4_DOWNLINK_TERMINATION);
    }

    @Test
    public void up4EntryToDownlinkTerminationNoTcTest() {
        up4ToUpfEntity(TestImplConstants.DOWNLINK_TERMINATION_NO_TC, TestImplConstants.UP4_DOWNLINK_TERMINATION_NO_TC);
    }

    @Test
    public void up4EntryToDownlinkTerminationDropTest() {
        up4ToUpfEntity(TestImplConstants.DOWNLINK_TERMINATION_DROP, TestImplConstants.UP4_DOWNLINK_TERMINATION_DROP);
    }

    @Test
    public void up4EntryToUplinkInterfaceTest() {
        up4ToUpfEntity(TestImplConstants.UPLINK_INTERFACE, TestImplConstants.UP4_UPLINK_INTERFACE);
    }

    @Test
    public void up4EntryToDownlinkInterfaceTest() {
        up4ToUpfEntity(TestImplConstants.DOWNLINK_INTERFACE, TestImplConstants.UP4_DOWNLINK_INTERFACE);
    }

    @Test
    public void tunnelPeerToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_TUNNEL_PEER, TestImplConstants.TUNNEL_PEER);
    }

    @Test
    public void uplinkInterfaceToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_UPLINK_INTERFACE, TestImplConstants.UPLINK_INTERFACE);
    }

    @Test
    public void downlinkInterfaceToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_DOWNLINK_INTERFACE, TestImplConstants.DOWNLINK_INTERFACE);
    }

    @Test
    public void uplinkSessionToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_UPLINK_SESSION, TestImplConstants.UPLINK_SESSION);
    }

    @Test
    public void downlinkSessionToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_DOWNLINK_SESSION, TestImplConstants.DOWNLINK_SESSION);
    }

    @Test
    public void downlinkSessionDbufToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_DOWNLINK_SESSION_DBUF, TestImplConstants.DOWNLINK_SESSION_DBUF);
    }

    @Test
    public void uplinkTerminationToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_UPLINK_TERMINATION, TestImplConstants.UPLINK_TERMINATION);
    }

    @Test
    public void uplinkTerminationNoTcToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_UPLINK_TERMINATION_NO_TC, TestImplConstants.UPLINK_TERMINATION_NO_TC);
    }

    @Test
    public void uplinkTerminationDropToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_UPLINK_TERMINATION_DROP, TestImplConstants.UPLINK_TERMINATION_DROP);
    }

    @Test
    public void downlinkTerminationToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_DOWNLINK_TERMINATION, TestImplConstants.DOWNLINK_TERMINATION);
    }

    @Test
    public void downlinkTerminationNoTcToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_DOWNLINK_TERMINATION_NO_TC, TestImplConstants.DOWNLINK_TERMINATION_NO_TC);
    }

    @Test
    public void downlinkTerminationDropToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_DOWNLINK_TERMINATION_DROP, TestImplConstants.DOWNLINK_TERMINATION_DROP);
    }
}
