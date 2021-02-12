/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onosproject.net.pi.runtime.PiTableEntry;

/**
 * This service provides stateful translation between UP4 p4 table entries, fabric.p4 table entries,
 * and the intermediate java structures PacketDetectionRule, ForwardingActionRule, and UpfInterface.
 */
public interface Up4Translator {

    /**
     * Returns true if the given table entry is a Packet Detection Rule from the UP4 logical pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a logical PDR
     * @return true if the entry is a logical PDR
     */
    boolean isUp4Pdr(PiTableEntry entry);

    /**
     * Returns true if the given table entry is a Forwarding Action Rule from the UP4 logical pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a logical FAR
     * @return true if the entry is a UP4 FAR
     */
    boolean isUp4Far(PiTableEntry entry);

    /**
     * Returns true if the given table entry is an interface table entry from the UP4 logical pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a UP4 interface
     * @return true if the entry is a UP4 interface
     */
    boolean isUp4Interface(PiTableEntry entry);

    /**
     * Translate a UP4 PDR table entry to a PacketDetectionRule instance for easier handling.
     *
     * @param entry the UP4 entry to translate
     * @return the corresponding PacketDetectionRule
     * @throws Up4TranslationException if the entry cannot be translated
     */
    PacketDetectionRule up4EntryToPdr(PiTableEntry entry)
            throws Up4TranslationException;

    /**
     * Translate a UP4 FAR table entry to a ForwardActionRule instance for easier handling.
     *
     * @param entry the UP4 entry to translate
     * @return the corresponding ForwardingActionRule
     * @throws Up4TranslationException if the entry cannot be translated
     */
    ForwardingActionRule up4EntryToFar(PiTableEntry entry)
            throws Up4TranslationException;

    /**
     * Translate a UP4 interface table entry to a UpfInterface instance for easier handling.
     *
     * @param entry UP4 entry to translate
     * @return the corresponding UpfInterface
     * @throws Up4TranslationException if the entry cannot be translated
     */
    UpfInterface up4EntryToInterface(PiTableEntry entry)
            throws Up4TranslationException;


    /**
     * Translate a UpfInterface to a PiTableEntry for responding to UP4 logical switch read requests.
     *
     * @param upfInterface The interface to be translated
     * @return the UPF interface translated to a PiTableEntry
     * @throws Up4TranslationException if the interface cannot be translated
     */
    PiTableEntry interfaceToUp4Entry(UpfInterface upfInterface) throws Up4TranslationException;

    /**
     * Translate a PacketDetectionRule to a PiTableEntry for responding to UP4 logical switch read requests.
     *
     * @param pdr the packet detection rule to be translated
     * @return the PDR translated to a PiTableEntry
     * @throws Up4TranslationException if the PDR cannot be translated
     */
    PiTableEntry pdrToUp4Entry(PacketDetectionRule pdr) throws Up4TranslationException;

    /**
     * Translate a ForwardingActionRule to a PiTableEntry for responding to UP4 logical switch read requests.
     *
     * @param far the forwarding action rule to be translated
     * @return the FAR translated to a PiTableEntry
     * @throws Up4TranslationException if the FAR cannot be translated
     */
    PiTableEntry farToUp4Entry(ForwardingActionRule far) throws Up4TranslationException;


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
