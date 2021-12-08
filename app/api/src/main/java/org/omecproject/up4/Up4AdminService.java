/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;


import com.google.common.annotations.Beta;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

import java.util.Collection;


/**
 * Internal UP4 APIs, used for CLI commands.
 * The methods are public for debug reason.
 */
@Beta
public interface Up4AdminService {

    /**
     * Get all UE flows (PDRs, FARs) currently installed in the network.
     * Used for debug purposes only.
     *
     * @return a collection of installed flows
     * @throws UpfProgrammableException if flows are unable to be read
     */
    Collection<UpfFlow> getFlows() throws UpfProgrammableException;

    /**
     * Install all UPF entities internal to UP4.
     * In particular, interfaces present in the app configuration and the DBUF
     * tunnel peer.
     */
    void installUpfEntities();

    /**
     * Applies the given UPF entity to the UPF data plane, without filtering
     * out modifications to entries directly managed by UP4.
     *
     * @param entity the UPF entity
     * @throws UpfProgrammableException propagate the exception from the UPF data plane.
     */
    void internalApply(UpfEntity entity) throws UpfProgrammableException;

    /**
     * Reads the given type of UPF entity from the UPF data plane, without filtering
     * out reads to entries directly managed by UP4.
     *
     * @param entityType The UPF entity type to read
     * @return The UPF entities.
     * @throws UpfProgrammableException propagate the exception from the UPF data plane.
     */
    Collection<? extends UpfEntity> internalReadAll(UpfEntityType entityType)
            throws UpfProgrammableException;

    /**
     * Deletes the given UPF entity from the UPF data plane, without filtering out
     * deletes to entries directly managed by UP4.
     *
     * @param entity The UPF entity to delete.
     * @throws UpfProgrammableException propagate the exception from the UPF data plane.
     */
    void internalDelete(UpfEntity entity) throws UpfProgrammableException;

    /**
     * Deletes all the UPF entity of the given type from the UPF data plane, without
     * filtering out deletion to entries directly managed by UP4.
     *
     * @param entityType The UPF entity type to delete.
     * @throws UpfProgrammableException propagate the exception from the UPF data plane.
     */
    void internalDeleteAll(UpfEntityType entityType) throws UpfProgrammableException;
}
