/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.util.ImmutableByteSequence;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.pi.runtime.PiTableEntry;

/**
 * This service provides stateful translation between UP4 p4 table entries, fabric.p4 table entries,
 * and the intermediate java structures PacketDetectionRule, ForwardingActionRule, and UpfInterface.
 */
public interface Up4Translator {

    /**
     * Clear all state associated with translation.
     */
    void reset();

    /**
     * Returns true if the given table entry is a Packet Detection Rule from the UP4 logical pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a logical PDR
     * @return true if the entry is a logical PDR
     */
    boolean isUp4Pdr(PiTableEntry entry);

    /**
     * Returns true if the given table entry is a Packet Detection Rule from the physical fabric pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a fabric.p4 PDR
     * @return true if the entry is a fabric.p4 PDR
     */
    boolean isFabricPdr(FlowRule entry);

    /**
     * Returns true if the given table entry is a Forwarding Action Rule from the UP4 logical pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a logical FAR
     * @return true if the entry is a UP4 FAR
     */
    boolean isUp4Far(PiTableEntry entry);

    /**
     * Returns true if the given table entry is a Forwarding Action Rule from the physical fabric pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a fabric.p4 FAR
     * @return true if the entry is a fabric.p4 FAR
     */
    boolean isFabricFar(FlowRule entry);

    /**
     * Returns true if the given table entry is an interface table entry from the UP4 logical pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a UP4 interface
     * @return true if the entry is a UP4 interface
     */
    boolean isUp4Interface(PiTableEntry entry);

    /**
     * Returns true if the given table entry is an interface table entry from the fabric.p4 physical pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a fabric.p4 UPF interface
     * @return true if the entry is a fabric.p4 UPF interface
     */
    boolean isFabricInterface(FlowRule entry);

    /**
     * Get a globally unique integer identifier for the FAR identified by the given (Session ID, Far ID) pair.
     *
     * @param farIdPair a RuleIdentifier instance uniquely identifying the FAR
     * @return A globally unique integer identifier
     */
    int globalFarIdOf(UpfRuleIdentifier farIdPair);

    /**
     * Get a globally unique integer identifier for the FAR identified by the given (Session ID, Far ID) pair.
     *
     * @param pfcpSessionId     The ID of the PFCP session that produced the FAR ID.
     * @param sessionLocalFarId The FAR ID.
     * @return A globally unique integer identifier
     */
    int globalFarIdOf(ImmutableByteSequence pfcpSessionId, int sessionLocalFarId);


    /**
     * Translate a fabric.p4 PDR table entry to a PacketDetectionRule instance for easier handling.
     *
     * @param entry the fabric.p4 entry to translate
     * @return the corresponding PacketDetectionRule
     * @throws Up4TranslationException if the entry cannot be translated
     */
    PacketDetectionRule fabricEntryToPdr(FlowRule entry)
            throws Up4TranslationException;

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
     * Translate a fabric.p4 FAR table entry to a ForwardActionRule instance for easier handling.
     *
     * @param entry the fabric.p4 entry to translate
     * @return the corresponding ForwardingActionRule
     * @throws Up4TranslationException if the entry cannot be translated
     */
    ForwardingActionRule fabricEntryToFar(FlowRule entry)
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
     * Translate a fabric.p4 interface table entry to a UpfInterface instance for easier handling.
     *
     * @param entry the fabric.p4 entry to translate
     * @return the corresponding UpfInterface
     * @throws Up4TranslationException if the entry cannot be translated
     */
    UpfInterface fabricEntryToInterface(FlowRule entry)
            throws Up4TranslationException;

    /**
     * Translate a ForwardingActionRule to a FlowRule to be inserted into the fabric.p4 pipeline.
     * A side effect of calling this method is the FAR object's globalFarId is assigned if it was not already.
     *
     * @param far      The FAR to be translated
     * @param deviceId the ID of the device the FlowRule should be installed on
     * @param appId    the ID of the application that will insert the FlowRule
     * @param priority the FlowRule's priority
     * @return the FAR translated to a FlowRule
     * @throws Up4TranslationException if the FAR to be translated is malformed
     */
    FlowRule farToFabricEntry(ForwardingActionRule far, DeviceId deviceId, ApplicationId appId, int priority)
            throws Up4TranslationException;

    /**
     * Translate a PacketDetectionRule to a FlowRule to be inserted into the fabric.p4 pipeline.
     * A side effect of calling this method is the PDR object's globalFarId is assigned if it was not already.
     *
     * @param pdr      The PDR to be translated
     * @param deviceId the ID of the device the FlowRule should be installed on
     * @param appId    the ID of the application that will insert the FlowRule
     * @param priority the FlowRule's priority
     * @return the FAR translated to a FlowRule
     * @throws Up4TranslationException if the PDR to be translated is malformed
     */
    FlowRule pdrToFabricEntry(PacketDetectionRule pdr, DeviceId deviceId, ApplicationId appId, int priority)
            throws Up4TranslationException;

    /**
     * Translate a UpfInterface to a FlowRule to be inserted into the fabric.p4 pipeline.
     *
     * @param upfInterface The interface to be translated
     * @param deviceId     the ID of the device the FlowRule should be installed on
     * @param appId        the ID of the application that will insert the FlowRule
     * @param priority     the FlowRule's priority
     * @return the UPF interface translated to a FlowRule
     * @throws Up4TranslationException if the interface cannot be translated
     */
    FlowRule interfaceToFabricEntry(UpfInterface upfInterface, DeviceId deviceId, ApplicationId appId, int priority)
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
