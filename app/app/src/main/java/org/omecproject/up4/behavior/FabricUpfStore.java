/*
 * SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 * SPDX-FileCopyrightText: {year}-present Open Networking Foundation <info@opennetworking.org>
 */

package org.omecproject.up4.behavior;

import org.omecproject.up4.UpfRuleIdentifier;
import org.onlab.util.ImmutableByteSequence;

import java.util.Map;

/**
 * Stores state required for translation of UPF entries to pipeline-specific entities.
 */
public interface FabricUpfStore {
    /**
     * Clear all state associated with translation.
     */
    void reset();

    /**
     * Returns the farIdMap.
     *
     * @return the farIdMap.
     */
    Map<UpfRuleIdentifier, Integer> getFarIdMap();

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
     * Get the corresponding PFCP session ID and session-local FAR ID from a globally unique FAR ID,
     * or return null if no such mapping is found.
     *
     * @param globalFarId globally unique FAR ID
     * @return the corresponding PFCP session ID and session-local FAR ID, as a RuleIdentifier
     */
    UpfRuleIdentifier localFarIdOf(int globalFarId);
}
