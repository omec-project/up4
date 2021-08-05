/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;
import org.onosproject.net.behaviour.upf.QosEnforcementRule;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Stores state required for UP4.
 */
public interface Up4Store {

    /**
     * Clear all state.
     */
    void reset();

    /**
     * Returns true if the given FAR IDs is known to be a buffering one.
     *
     * @param farId FAR ID
     * @return boolean
     */
    boolean isFarIdBuffering(ImmutablePair<ImmutableByteSequence, Integer> farId);

    /**
     * Learns the given FAR ID as being a buffering one.
     *
     * @param farId FAR ID
     */
    void learnBufferingFarId(ImmutablePair<ImmutableByteSequence, Integer> farId);

    /**
     * Forgets the given FAR ID as being a buffering one. Returns true if the given
     * FAR IDs is known to be a buffering one.
     *
     * @param farId FAR ID
     * @return true if the given FAR IDs is known to be a buffering one
     */
    boolean forgetBufferingFarId(ImmutablePair<ImmutableByteSequence, Integer> farId);

    /**
     * Returns the set of known buffering FAR IDs.
     * @return set
     */
    Set<ImmutablePair<ImmutableByteSequence, Integer>> getBufferFarIds();

    /**
     * Stores the mapping between FAR ID and UE address as defined by the given PDR.
     *
     * @param pdr PDR
     */
    void learnFarIdToUeAddr(PacketDetectionRule pdr);

    /**
     * Returns the UE address associated with the given FAR ID.
     *
     * @param farId FAR ID
     * @return the Ip4Address assigned to UE
     */
    Ip4Address ueAddrOfFarId(ImmutablePair<ImmutableByteSequence, Integer> farId);

    /**
     * Removes the given UE address from the FAR ID to UE address map.
     * @param pdr PDR
     */
    void forgetUeAddr(PacketDetectionRule pdr);

    /**
     * Returns the FAR ID to UE address map.
     *
     * @return mapping far id to ue address
     */
    Map<ImmutablePair<ImmutableByteSequence, Integer>, Ip4Address> getFarIdsToUeAddrs();

    /**
     * Returns the UE to FAR ID address map.
     *
     * @return mapping ue to far id
     */
    Map<Ip4Address, ImmutablePair<ImmutableByteSequence, Integer>> getUeAddrsToFarIds();

    void storeQer(QosEnforcementRule qer);

    Collection<QosEnforcementRule> getQers();

    void forgetQer(int qerId);

}
