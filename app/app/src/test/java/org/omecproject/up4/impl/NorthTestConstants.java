/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onlab.util.HexString;
import p4.v1.P4RuntimeOuterClass;

public final class NorthTestConstants {
    public static final long P4RUNTIME_DEVICE_ID = 1;
    public static final P4RuntimeOuterClass.Role P4RUNTIME_ROLE =
            P4RuntimeOuterClass.Role.getDefaultInstance();
    public static final P4RuntimeOuterClass.Uint128 P4RUNTIME_ELECTION_ID =
            P4RuntimeOuterClass.Uint128.newBuilder().setLow(1).build();
    public static final long PIPECONF_COOKIE = 0xbeefcafe;
    public static final int UPLINK_COUNTER_INDEX = 1;
    public static final int DOWNLINK_COUNTER_INDEX = 2;
    public static final long INGRESS_COUNTER_PKTS = 1;
    public static final long INGRESS_COUNTER_BYTES = 2;
    public static final long EGRESS_COUNTER_PKTS = 3;
    public static final long EGRESS_COUNTER_BYTES = 4;
    // Bytes of a random but valid Ethernet frame.
    public static final byte[] PKT_OUT_PAYLOAD = HexString.fromHexString(
            "00060708090a0001020304058100000a08004500006a000100004011f92ec0a80001c0a8000204d2005" +
                    "00056a8d5000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20" +
                    "2122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f4041424344454" +
                    "64748494a4b4c4d", "");
    public static final byte[] PKT_OUT_METADATA_1 = new byte[]{0x00};

    private NorthTestConstants() {
    }
}
