package org.omecproject.up4.impl;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;

import java.util.Map;
import java.util.Set;

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
    void learBufferingFarId(ImmutablePair<ImmutableByteSequence, Integer> farId);

    /**
     * Forgets the given FAR ID as being a buffering one.
     *
     * @param farId FAR ID
     */
    void forgetBufferingFarId(ImmutablePair<ImmutableByteSequence, Integer> farId);

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
    void learnFarIdToUeAddrs(PacketDetectionRule pdr);

    /**
     * Returns the set of UE addresses associated with the given FAR ID.
     *
     * @param farId FAR ID
     * @return Set of Ip4Address
     */
    Set<Ip4Address> ueAddrsOfFarId(ImmutablePair<ImmutableByteSequence, Integer> farId);

    /**
     * Removes the given UE address from the FAR ID to UE address map.
     * @param ueAddr UE address
     */
    void forgetUeAddr(Ip4Address ueAddr);

    /**
     * Returns the FAR ID to UE addresses map.
     *
     * @return map
     */
    Map<ImmutablePair<ImmutableByteSequence, Integer>, Set<Ip4Address>> getFarIdToUeAddrs();

}
