/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;


import com.google.common.annotations.Beta;
import org.onosproject.event.ListenerService;
import org.onosproject.net.behaviour.upf.UpfDevice;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

import java.util.Collection;


/**
 * The service provided by the UP4 Device Manager. Exposes UPF network level APIs.
 * This API is a work in progress.
 */
@Beta
public interface Up4Service extends ListenerService<Up4Event, Up4EventListener>, UpfDevice {

    /**
     * True if the UPF network-level device is currently available, and false otherwise.
     *
     * @return true if the device is available and false otherwise
     */
    boolean upfAvailable();

    /**
     * True if a valid UP4 app configuration has been loaded, and false otherwise.
     *
     * @return true if a valid app config has been loaded
     */
    boolean configIsLoaded();

    /**
     * Install all UPF data plane interfaces present in the app configuration.
     * Public only for debug purposes. Users of the Up4Service shouldn't invoke
     * this method.
     */
    void installInterfaces();

    /**
     * Get all UE flows (PDRs, FARs) currently installed in the network.
     * Used for debug purposes only.
     *
     * @return a collection of installed flows
     * @throws UpfProgrammableException if flows are unable to be read
     */
    Collection<UpfFlow> getFlows() throws UpfProgrammableException;

}
