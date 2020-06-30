/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */

package org.onosproject.up4;

import org.onosproject.net.pi.model.PiPipeconfId;

public final class AppConstants {
    // hide default constructor
    private AppConstants() {
    }
    public static final String APP_NAME = "org.onosproject.up4";
    public static final PiPipeconfId PIPECONF_ID = new PiPipeconfId("org.onosproject.up4");
    public static final int GRPC_SERVER_PORT = 51001;
    public static final String P4INFO_PATH = "/p4info.txt";
    public static final String SUPPORTED_PIPECONF_NAME = "fabric-spgw";
}
