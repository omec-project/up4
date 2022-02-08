/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;


import com.google.common.annotations.Beta;
import org.onosproject.net.DeviceId;
import org.onosproject.net.behaviour.upf.UpfCounter;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

import java.util.Collection;


/**
 * Internal UP4 APIs, used for CLI commands.
 * The methods are public for debug reason.
 */
@Beta
public interface Up4AdminService {

    /**
     * Gets all the uplink UPF flow installed. Used for debug purposes only.
     *
     * @return a collection of installed uplink UPF flow
     * @throws UpfProgrammableException if flows are unable to read
     */
    Collection<UplinkUpfFlow> getUplinkFlows() throws UpfProgrammableException;

    /**
     * Gets all the downlink UPF flow installed. Used for debug purposes only.
     *
     * @return a collection of installed downlink UPF flow
     * @throws UpfProgrammableException if flows are unable to read
     */
    Collection<DownlinkUpfFlow> getDownlinkFlows() throws UpfProgrammableException;

    /**
     * Install all UPF entities internal to UP4.
     * In particular, interfaces present in the app configuration and the DBUF
     * tunnel peer.
     */
    void installUpfEntities();

    /**
     * Gets the UPF interfaces present in the UP4 netcfg.
     * This method doesn't query the UPF data plane.
     *
     * @return a collection of UPF interfaces configured via UP4 netcfg.
     */
    Collection<UpfInterface> configInterfaces();

    /**
     * Applies the given UPF entity to the UPF data plane, without filtering
     * out modifications to entries directly managed by UP4.
     *
     * @param entity the UPF entity
     * @throws UpfProgrammableException propagate the exception from the UPF data plane.
     */
    void adminApply(UpfEntity entity) throws UpfProgrammableException;

    /**
     * Reads the given type of UPF entity from the UPF data plane, without filtering
     * out reads to entries directly managed by UP4.
     *
     * @param entityType The UPF entity type to read
     * @return The UPF entities.
     * @throws UpfProgrammableException propagate the exception from the UPF data plane.
     */
    Collection<? extends UpfEntity> adminReadAll(UpfEntityType entityType)
            throws UpfProgrammableException;

    /**
     * Deletes the given UPF entity from the UPF data plane, without filtering out
     * deletes to entries directly managed by UP4.
     *
     * @param entity The UPF entity to delete.
     * @throws UpfProgrammableException propagate the exception from the UPF data plane.
     */
    void adminDelete(UpfEntity entity) throws UpfProgrammableException;

    /**
     * Deletes all the UPF entity of the given type from the UPF data plane, without
     * filtering out deletion to entries directly managed by UP4.
     *
     * @param entityType The UPF entity type to delete.
     * @throws UpfProgrammableException propagate the exception from the UPF data plane.
     */
    void adminDeleteAll(UpfEntityType entityType) throws UpfProgrammableException;

    /**
     * Reads a counter at the given ID from the given UPF data plane device.
     *
     * @param counterIdx Counter ID
     * @param device UPF data plane device
     * @return The UPF counter
     * @throws UpfProgrammableException propagate the exception from the UPF data plane
     * and if the given device is not a UPF programmable.
     */
    UpfCounter readCounter(int counterIdx, DeviceId device) throws UpfProgrammableException;
}
