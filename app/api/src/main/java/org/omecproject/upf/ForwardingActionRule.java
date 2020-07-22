package org.omecproject.upf;

import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;

public class ForwardingActionRule {
    private final ImmutableByteSequence sessionId;
    private final int farId;
    private final boolean drop;
    private final boolean notifyCp;
    private final GtpTunnel tunnelDesc;

    public ForwardingActionRule(ImmutableByteSequence sessionId, int farId, boolean drop, boolean notifyCp,
                                GtpTunnel tunnelDesc) {
        this.sessionId = sessionId;
        this.farId = farId;
        this.drop = drop;
        this.notifyCp = notifyCp;
        this.tunnelDesc = tunnelDesc;
    }

    public boolean isUplink() {
        return tunnelDesc == null;
    }

    public boolean isDownlink() {
        return !isUplink();
    }

    public ImmutableByteSequence sessionId() {
        return sessionId;
    }

    public int localId() {
        return farId;
    }

    public boolean dropFlag() {
        return drop;
    }

    public boolean notifyCpFlag() {
        return notifyCp;
    }

    public GtpTunnel tunnelDesc() {
        return tunnelDesc;
    }

    public Ip4Address tunnelSrc() {
        if (tunnelDesc == null) {
            return null;
        }
        return tunnelDesc.src();
    }

    public Ip4Address tunnelDst() {
        if (tunnelDesc == null) {
            return null;
        }
        return tunnelDesc.dst();
    }

    public ImmutableByteSequence teid() {
        if (tunnelDesc == null) {
            return null;
        }
        return tunnelDesc.teid();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ImmutableByteSequence sessionId;
        private int farId;
        private boolean drop;
        private boolean notifyCp;
        private GtpTunnel tunnelDesc;

        public Builder() {
            sessionId = null;
            farId = -1;
            drop = false;
            notifyCp = false;
            tunnelDesc = null;
        }

        public Builder withSessionId(ImmutableByteSequence sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder withSessionId(long sessionId) {
            this.sessionId = ImmutableByteSequence.copyFrom(sessionId);
            return this;
        }

        public Builder withFarId(int farId) {
            this.farId = farId;
            return this;
        }

        public Builder withFlags(boolean drop, boolean notifyCp) {
            this.drop = drop;
            this.notifyCp = notifyCp;
            return this;
        }

        public Builder withDropFlag(boolean drop) {
            this.drop = drop;
            return this;
        }

        public Builder withNotifyFlag(boolean notifyCp) {
            this.notifyCp = notifyCp;
            return this;
        }

        public Builder withTunnel(GtpTunnel tunnel) {
            this.tunnelDesc = tunnel;
            return this;
        }

        public Builder withTunnel(Ip4Address src, Ip4Address dst, ImmutableByteSequence teid) {
            return this.withTunnel(new GtpTunnel(src, dst, teid));
        }

        public ForwardingActionRule build() {
            return new ForwardingActionRule(sessionId, farId, drop, notifyCp, tunnelDesc);
        }
    }
}
