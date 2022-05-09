/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiEntity;
import org.onosproject.net.pi.runtime.PiMeterCellConfig;
import org.onosproject.net.pi.runtime.PiTableEntry;

/**
 * This service provides stateful translation between UP4 p4 table entries, fabric.p4 table entries,
 * and the intermediate java structures PacketDetectionRule, ForwardingActionRule, and UpfInterface.
 */
public interface Up4Translator {

    /**
     * Get the UPF entity type from the given UP4 logical pipeline entry.
     *
     * @param entity the logical pipeline entry
     * @return the UPF entity type or null
     */
    UpfEntityType getEntityType(PiEntity entity);

    /**
     * Translates the given UP4 logical pipeline table entry into the UPF entity.
     *
     * @param entry the UP4 logical pipeline table entry
     * @return the UPF entity
     * @throws Up4TranslationException if the entry cannot be translated
     */
    UpfEntity up4TableEntryToUpfEntity(PiTableEntry entry) throws Up4TranslationException;

    /**
     * Translates the given UP4 logical meter entry into the UPF entity.
     *
     * @param meterEntry the UP4 logical meter entry
     * @return the UPF entity
     * @throws Up4TranslationException if the entry cannot be translated
     */
    UpfEntity up4MeterEntryToUpfEntity(PiMeterCellConfig meterEntry) throws Up4TranslationException;

    /**
     *  Translates the given UP4 logical counter entry into the UPF entity.
     *
     * @param meterEntry the UP4 logical counter entry
     * @return the UPF entity
     * @throws Up4TranslationException if the entry cannot be translated
     */
    UpfEntity up4CounterEntryToUpfEntity(PiCounterCell meterEntry) throws Up4TranslationException;

    /**
     * Translates the give UPF meter entity into a UP4 logical pipeline meter entry.
     *
     * @param entity the UPF meter entity
     * @return the UP4 logical pipeline meter entry
     * @throws Up4TranslationException if the entry cannot be translated
     */
    PiMeterCellConfig upfEntityToUp4MeterEntry(UpfEntity entity) throws Up4TranslationException;

    /**
     * Translates the given UPF entity into a UP4 logical pipeline entry.
     *
     * @param entity the UPF table entity
     * @return the UP4 logical pipeline table entry
     * @throws Up4TranslationException if the entry cannot be translated
     */
    PiTableEntry upfEntityToUp4TableEntry(UpfEntity entity) throws Up4TranslationException;


    class Up4TranslationException extends Exception {
        /**
         * Creates a new exception for the given message.
         *
         * @param message message
         */
        public Up4TranslationException(String message) {
            super(message);
        }
    }
}
