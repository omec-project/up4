/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onlab.packet.Ip4Address;

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
     * Returns true if the given UE address is known to be a buffering one.
     *
     * @param ueAddr UE address
     * @return boolean
     */
    boolean isUeBuffering(Ip4Address ueAddr);

    /**
     * Learns the given UE address as being a buffering one.
     *
     * @param ueAddr UE address
     */
    void learnBufferingUe(Ip4Address ueAddr);

    /**
     * Forgets the given UE address as being a buffering one. Returns true if
     * the given UE address is known to be a buffering one.
     *
     * @param ueAddr UE address
     * @return true if the given UE address is known to be a buffering one
     */
    boolean forgetBufferingUe(Ip4Address ueAddr);

    /**
     * Returns the set of known buffering UE addresses.
     *
     * @return set
     */
    Set<Ip4Address> getBufferUe();
}