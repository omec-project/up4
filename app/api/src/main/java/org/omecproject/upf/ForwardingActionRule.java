package org.omecproject.upf;

import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;

public class ForwardingActionRule {
    private final ImmutableByteSequence sessionId;
    private final int farId;
    private final Boolean drop;
    private final Boolean notifyCp;
    private final GtpTunnel tunnelDesc;

    public ForwardingActionRule(ImmutableByteSequence sessionId, int farId, Boolean drop, Boolean notifyCp,
                                GtpTunnel tunnelDesc) {
        this.sessionId = sessionId;
        this.farId = farId;
        this.drop = drop;
        this.notifyCp = notifyCp;
        this.tunnelDesc = tunnelDesc;
    }

    @Override
    public String toString() {
        String matchKeys = String.format("ID:%d,SEID:%s", farId, sessionId.toString());
        String directionString;
        String actionParams;
        if (isUplink()) {
            directionString = "Uplink";
            actionParams = String.format("Drop:%b,Notify:%b", drop, notifyCp);
        } else if (isDownlink()) {
            directionString = "Downlink";
            actionParams = String.format("Drop:%b,Notify:%b,Tunnel:%s", drop, notifyCp, tunnelDesc.toString());
        } else {
            directionString = "Blank";
            actionParams = "";
        }

        return String.format("%s-FAR{ Keys:(%s) -> Params (%s) }", directionString, matchKeys, actionParams);
    }

    public boolean hasActionParameters() {
        return drop != null && notifyCp != null;
    }

    public boolean isUplink() {
        if (!hasActionParameters()) {
            return false;
        }
        return tunnelDesc == null;
    }

    public boolean isDownlink() {
        if (!hasActionParameters()) {
            return false;
        }
        return tunnelDesc != null;
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
        private Boolean drop;
        private Boolean notifyCp;
        private GtpTunnel tunnelDesc;

        public Builder() {
            sessionId = null;
            farId = -1;
            drop = null;
            notifyCp = null;
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
