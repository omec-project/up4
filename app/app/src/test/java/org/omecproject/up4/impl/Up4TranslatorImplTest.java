/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.omecproject.up4.Up4Translator;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.pi.runtime.PiEntity;
import org.onosproject.net.pi.runtime.PiMeterCellConfig;
import org.onosproject.net.pi.runtime.PiMeterCellId;
import org.onosproject.net.pi.runtime.PiTableEntry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.omecproject.up4.impl.TestImplConstants.METER_ID;
import static org.omecproject.up4.impl.TestImplConstants.PBURST;
import static org.omecproject.up4.impl.TestImplConstants.PIR;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_APP_METER;
import static org.omecproject.up4.impl.Up4P4InfoConstants.PRE_QOS_PIPE_SESSION_METER;

public class Up4TranslatorImplTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private final Up4TranslatorImpl up4Translator = new Up4TranslatorImpl();

    public UpfEntity up4ToUpfEntity(UpfEntity expected, PiEntity up4Entry) {
        UpfEntity translatedEntity;
        try {
            switch (up4Entry.piEntityType()) {
                case TABLE_ENTRY:
                    translatedEntity = up4Translator.up4TableEntryToUpfEntity((PiTableEntry) up4Entry);
                    break;
                case METER_CELL_CONFIG:
                    translatedEntity = up4Translator.up4MeterEntryToUpfEntity((PiMeterCellConfig) up4Entry);
                    break;
                default:
                    assertThat("Unsupported PI entity!", false);
                    return null;
            }
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UP4 table entry should translate to UPF entity without error.", false);
            return null;
        }
        assertThat(translatedEntity, equalTo(expected));
        return translatedEntity;
    }

    public PiEntity upfEntityToUp4(PiEntity expected, UpfEntity up4Entry) {
        PiEntity translatedEntity;
        try {
            switch (up4Entry.type()) {
                case INTERFACE:
                case TERMINATION_DOWNLINK:
                case TERMINATION_UPLINK:
                case SESSION_DOWNLINK:
                case SESSION_UPLINK:
                case TUNNEL_PEER:
                case COUNTER:
                case APPLICATION:
                    translatedEntity = up4Translator.upfEntityToUp4TableEntry(up4Entry);
                    break;
                case SESSION_METER:
                case APPLICATION_METER:
                    translatedEntity = up4Translator.upfEntityToUp4MeterEntry(up4Entry);
                    break;
                default:
                    assertThat("Unsupported UPF entity!", false);
                    return null;
            }
        } catch (Up4Translator.Up4TranslationException e) {
            assertThat("UPF entity should correctly translate to UP4 table " +
                               "entry or meter entry without error.\n" + e.getMessage(),
                       false);
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
    public void up4EntryToApplicationFilteringTest() {
        up4ToUpfEntity(TestImplConstants.APPLICATION_FILTERING, TestImplConstants.UP4_APPLICATION_FILTERING);
    }

    @Test
    public void up4MeterEntryToApplicationMeterTest() {
        up4ToUpfEntity(TestImplConstants.APP_METER, TestImplConstants.UP4_APP_METER);
    }

    @Test
    public void up4MeterEntryToApplicationMeterResetTest() {
        up4ToUpfEntity(TestImplConstants.APP_METER_RESET, TestImplConstants.UP4_APP_METER_RESET);
    }

    @Test
    public void up4MeterEntryToSessionMeterTest() {
        up4ToUpfEntity(TestImplConstants.SESSION_METER, TestImplConstants.UP4_SESSION_METER);
    }

    @Test
    public void up4MeterEntryToSessionMeterResetTest() {
        up4ToUpfEntity(TestImplConstants.SESSION_METER_RESET, TestImplConstants.UP4_SESSION_METER_RESET);
    }

    @Test
    public void missingPeakBandToAppMeterTest() throws Exception {
        exceptionRule.expect(Up4Translator.Up4TranslationException.class);
        up4Translator.up4MeterEntryToUpfEntity(
                PiMeterCellConfig.builder()
                        .withMeterCellId(PiMeterCellId.ofIndirect(PRE_QOS_PIPE_APP_METER, METER_ID))
                        .withCommittedBand(10, 10)
                        .build());
    }

    @Test
    public void missingCommittedBandToAppMeterTest() throws Exception {
        exceptionRule.expect(Up4Translator.Up4TranslationException.class);
        up4Translator.up4MeterEntryToUpfEntity(
                PiMeterCellConfig.builder()
                        .withMeterCellId(PiMeterCellId.ofIndirect(PRE_QOS_PIPE_APP_METER, METER_ID))
                        .withPeakBand(10, 10)
                        .build());
    }

    @Test
    public void missingPeakBandToSessionMeterTest() throws Exception {
        exceptionRule.expect(Up4Translator.Up4TranslationException.class);
        up4Translator.up4MeterEntryToUpfEntity(
                PiMeterCellConfig.builder()
                        .withMeterCellId(PiMeterCellId.ofIndirect(PRE_QOS_PIPE_SESSION_METER, METER_ID))
                        .withCommittedBand(10, 10)
                        .build());
    }

    @Test
    public void sessionMeterWithNonUnspecifiedCommitted() throws Exception {
        /**
         * Unspecified Rate: {@link AppConstants#ZERO_BAND_RATE)
         * Unspecified Burst: {@link AppConstants#ZERO_BAND_BURST)
         */
        exceptionRule.expect(Up4Translator.Up4TranslationException.class);
        exceptionRule.expectMessage(
                "Session meters supports only peak bands (committed = PiMeterBand{type=COMMITTED, rate=10, burst=10})");
        up4Translator.up4MeterEntryToUpfEntity(
                PiMeterCellConfig.builder()
                        .withMeterCellId(PiMeterCellId.ofIndirect(PRE_QOS_PIPE_SESSION_METER, METER_ID))
                        .withCommittedBand(10, 10)
                        .withPeakBand(PIR, PBURST)
                        .build());
    }

    // -------------------------------------------------------------------------

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

    @Test
    public void applicationFilteringToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_APPLICATION_FILTERING, TestImplConstants.APPLICATION_FILTERING);
    }

    @Test
    public void applicationMeterToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_APP_METER, TestImplConstants.APP_METER);
    }

    @Test
    public void applicationMeterResetToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_APP_METER_RESET, TestImplConstants.APP_METER_RESET);
    }

    @Test
    public void sessionMeterToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_SESSION_METER, TestImplConstants.SESSION_METER);
    }

    @Test
    public void sessionMeterResetToUp4EntryTest() {
        upfEntityToUp4(TestImplConstants.UP4_SESSION_METER_RESET, TestImplConstants.SESSION_METER_RESET);
    }
}
