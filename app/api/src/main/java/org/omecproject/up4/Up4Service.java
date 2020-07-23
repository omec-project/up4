/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;


import org.onosproject.net.DeviceId;


/**
 * The service provided by the UP4 Device Manager. This API is a work in progress. Currently the only important
 * service it provides is retrieving the available UpfProgrammable.
 */
public interface Up4Service {

    UpfProgrammable getUpfProgrammable();

    boolean upfProgrammableAvailable();

    void clearDevice();

    /**
     * Check if the device is registered and is a valid UPF dataplane.
     *
     * @param deviceId ID of the device to check
     * @return True if the device is a valid UPF data plane, and False otherwise
     */
    boolean isUpfDevice(DeviceId deviceId);
}