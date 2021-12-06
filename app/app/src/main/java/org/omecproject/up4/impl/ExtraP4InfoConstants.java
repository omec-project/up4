package org.omecproject.up4.impl;

public final class ExtraP4InfoConstants {

    // hide default constructor
    private ExtraP4InfoConstants() {
    }

    // FIXME: parse enum from P4Info
    public static final byte DIRECTION_UPLINK = 1;
    public static final byte DIRECTION_DOWNLINK = 2;
    public static final byte IFACE_ACCESS = 1;
    public static final byte IFACE_CORE = 2;
    public static final byte TUNNEL_TYPE_GTPU = 3;

    // FIXME: replace with name after we add P4Info browser for digests in ONOS
    public static final int DDN_DIGEST_ID = 396224266;
}
