/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;


import com.google.common.annotations.Beta;
import org.onosproject.event.ListenerService;


/**
 * The service provided by the UP4 Device Manager. Exposes UPF network level APIs.
 * This API is a work in progress.
 */
@Beta
public interface Up4Service extends ListenerService<Up4Event, Up4EventListener>, UpfProgrammable {

    /**
     * True if a UPF device is currently available in ONOS, and false otherwise.
     *
     * @return true if the device is available and false otherwise
     */
    boolean upfProgrammableAvailable();

    /**
     * True if a valid UP4 app configuration has been loaded, and false otherwise.
     *
     * @return true if a valid app config has been loaded
     */
    boolean configIsLoaded();

    /**
     * Install all UPF dataplane interfaces present in the app configuration.
     */
    void installInterfaces();

}
