package org.omecproject.upf;

import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;

/**
 * A structure representing a unidirectional GTP tunnel.
 */
public final class GtpTunnel {
    private final Ip4Address src;
    private final Ip4Address dst;
    private final ImmutableByteSequence teid;

    public GtpTunnel(Ip4Address src, Ip4Address dst, ImmutableByteSequence teid) {
        this.src = src;
        this.dst = dst;
        this.teid = teid;
    }

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
        public GtpTunnel build() {
            return new GtpTunnel(this.src, this.dst, this.teid);
        }
    }

}
