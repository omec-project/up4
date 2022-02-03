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
    public static final String SUPPORTED_PIPECONF_STRING = "fabric-spgw";

    // TODO: SLICE_MOBILE should be configurable via netcfg or from the north.
    //  See: https://jira.opennetworking.org/browse/SDFAB-985
    //  We should use a slice ID different than the default, slice ID should be
    //  one of the slices configured in the slicing manager via netcfg.
    public static final int SLICE_MOBILE = 0;
    // Default slice id is required for traffic coming from DBUF.
    public static final int DEFAULT_SLICE_ID = 0;

    // hide default constructor
    private AppConstants() {
    }
}
