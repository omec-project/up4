/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;


import org.onosproject.net.DeviceId;


/**
 * The service provided by the UP4 Device Manager. This API is a work in progress. Currently the only important
 * service it provides is retrieving the available UpfProgrammable.
 */
public interface Up4Service {

    /**
     * Grab a reference to the current UpfProgrammable, for installing PDRs, FARs and Interfaces.
     *
     * @return a reference to the current UpfProgrammable
     */
    UpfProgrammable getUpfProgrammable();

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
     * Clear all table entries in the UpfProgrammable installed by the UP4 app.
     */
    void clearUpfProgrammable();


    /**
     * Install all UPF dataplane interfaces present in the app configuration.
     */
    void installInterfaces();

    /**
     * Check if the device is registered and is a valid UPF dataplane.
     *
     * @param deviceId ID of the device to check
     * @return True if the device is a valid UPF data plane, and False otherwise
     */
    boolean isUpfDevice(DeviceId deviceId);
}
