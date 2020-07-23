/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A structure representing a unidirectional GTP tunnel.
 */
public final class GtpTunnel {
    private final Ip4Address src;  // The source address of the unidirectional tunnel
    private final Ip4Address dst;  // The destination address of the unidirectional tunnel
    private final ImmutableByteSequence teid;  // Tunnel Endpoint Identifier

    private GtpTunnel(Ip4Address src, Ip4Address dst, ImmutableByteSequence teid) {
        this.src = src;
        this.dst = dst;
        this.teid = teid;
    }

    @Override
    public String toString() {
        return String.format("GTP-Tunnel(%s -> %s, TEID:%s)", src.toString(), dst.toString(), teid.toString());
    }

    public Ip4Address src() {
        return this.src;
    }

    public Ip4Address dst() {
        return this.dst;
    }

    public ImmutableByteSequence teid() {
        return this.teid;
    }

    public static GtpTunnelBuilder builder() {
        return new GtpTunnelBuilder();
    }

    public static class GtpTunnelBuilder {
        private Ip4Address src;
        private Ip4Address dst;
        private ImmutableByteSequence teid;

        public GtpTunnelBuilder() {
            this.src = null;
            this.dst = null;
            this.teid = null;
        }
        public GtpTunnelBuilder setSrc(Ip4Address src) {
            this.src = src;
            return this;
        }
        public GtpTunnelBuilder setDst(Ip4Address dst) {
            this.dst = dst;
            return this;
        }
        public GtpTunnelBuilder setTeid(ImmutableByteSequence teid) {
            this.teid = teid;
            return this;
        }
        public GtpTunnelBuilder setTeid(long teid) {
            this.teid = ImmutableByteSequence.copyFrom(teid);
            return this;
        }
        public GtpTunnel build() {
            checkNotNull(src, "Tunnel source address cannot be null");
            checkNotNull(dst, "Tunnel destination address cannot be null");
            checkNotNull(teid, "Tunnel TEID cannot be null");
            return new GtpTunnel(this.src, this.dst, this.teid);
        }
    }

}
