/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.pi.runtime.PiEntity;
import org.onosproject.net.pi.runtime.PiTableEntry;

/**
 * This service provides stateful translation between UP4 p4 table entries, fabric.p4 table entries,
 * and the intermediate java structures PacketDetectionRule, ForwardingActionRule, and UpfInterface.
 */
public interface Up4Translator {

    UpfEntityType getEntityType(PiEntity entity) throws Up4TranslationException;

    UpfEntity up4TableEntryToUpfEntity(PiTableEntry entry) throws Up4TranslationException;

    PiTableEntry entityToUp4TableEntry(UpfEntity entity) throws Up4TranslationException;


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
