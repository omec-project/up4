/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onosproject.net.pi.model.PiPipeconfId;

public final class AppConstants {
    public static final String APP_NAME = "org.omecproject.up4";
    public static final PiPipeconfId PIPECONF_ID = new PiPipeconfId(APP_NAME);
    public static final int GRPC_SERVER_PORT = 51001;
    public static final String P4INFO_PATH = "/p4info.txt";
    public static final String SUPPORTED_PIPECONF_STRING = "fabric-spgw";
    public static final int LOGICAL_SWITCH_DEVICE_ID = 1;
    public static final String CONFIG_DEVICE_ID_JSON_KEY = "upfDeviceId";

    // hide default constructor
    private AppConstants() {
    }
}
