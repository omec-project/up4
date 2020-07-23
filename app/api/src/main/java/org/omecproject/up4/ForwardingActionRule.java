/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * A single Forwarding Action Rule (FAR), an entity described in the 3GPP specifications (although that does not mean
 * that this class is 3GPP compliant). An instance of this class will be generated by a logical switch write request
 * to the database-style FAR P4 table, and the resulting instance should contain all the information needed to
 * reproduce that logical switch FAR in the event of a client read request. The instance should also contain sufficient
 * information (or expose the means to retrieve such information) to generate the corresponding
 * fabric.p4 dataplane forwarding state that implements the FAR.
 */
public final class ForwardingActionRule {
    // Match Keys
    private final ImmutableByteSequence sessionId;  // The PFCP session identifier that created this FAR
    private final int farId;  // PFCP session-local identifier for this FAR
    // Action parameters
    private final Boolean drop;  // Should this FAR drop packets?
    private final Boolean notifyCp;  // Should this FAR notify the control plane when it sees a packet?
    private final GtpTunnel tunnelDesc;  // The GTP tunnel that this FAR should encapsulate packets with (if downlink)

    private ForwardingActionRule(ImmutableByteSequence sessionId, Integer farId, Boolean drop, Boolean notifyCp,
                                GtpTunnel tunnelDesc) {
        // All match keys are required
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

    /**
     * Instances created as a result of DELETE write requests will not have action parameters, only match keys.
     * This method should be used to avoid null pointer exceptions in those instances.
     * @return true if this instance has FAR action parameters, false otherwise.
     */
    public boolean hasActionParameters() {
        return drop != null && notifyCp != null;
    }

    /**
     * Is this an FAR which sends packets in the uplink direction?
     * @return true if FAR is uplink
     */
    public boolean isUplink() {
        if (!hasActionParameters()) {
            return false;
        }
        return tunnelDesc == null;
    }

    /**
     * Is this a FAR which sends packets in the downlink direction?
     * @return true is FAR is downlink
     */
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

    /**
     * A description of the tunnel that this FAR will encapsulate packets with, if it is a downlink FAR. If the FAR
     * is uplink, there will be no such tunnel and this method wil return null.
     * @return A GtpTunnel instance containing a tunnel sourceIP, destIP, and GTPU TEID, or null if the FAR is uplink.
     */
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
        private Integer farId;
        private Boolean drop;
        private Boolean notifyCp;
        private GtpTunnel tunnelDesc;

        public Builder() {
            sessionId = null;
            farId = null;
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
            return this.withTunnel(GtpTunnel.builder()
                                            .setSrc(src)
                                            .setDst(dst)
                                            .setTeid(teid)
                                            .build());
        }

        public ForwardingActionRule build() {
            // All match keys are required
            checkNotNull(sessionId, "Session ID is required");
            checkNotNull(farId, "FAR ID is required");
            // Action parameters are optional. If the tunnel desc is provided, the flags must also be provided.
            checkArgument((drop != null && notifyCp != null) ||
                            (drop == null && notifyCp == null && tunnelDesc == null),
                    "FAR Arguments must be provided together or not at all.");
            return new ForwardingActionRule(sessionId, farId, drop, notifyCp, tunnelDesc);
        }
    }
}
