/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onosproject.net.pi.model.PiPipeconfId;

public final class AppConstants {
    public static final String APP_NAME = "org.omecproject.up4";
    public static final PiPipeconfId PIPECONF_ID = new PiPipeconfId(APP_NAME);
    public static final int GRPC_SERVER_PORT = 51001;
    public static final String P4INFO_PATH = "/p4info.txt";
    public static final String SUPPORTED_PIPECONF_STRING = "fabric-upf";

    // TODO: change when we support multi-slice
    //  https://jira.opennetworking.org/browse/SDFAB-986
    // Default slice id is required for traffic coming from DBUF.
    public static final int DEFAULT_SLICE_ID = 0;

    // Values used when you don't want to specify a band. Meaning rate = 0 for
    // that band
    public static final long ZERO_BAND_RATE = 0;
    public static final long ZERO_BAND_BURST = 0;

    // hide default constructor
    private AppConstants() {
    }
}
