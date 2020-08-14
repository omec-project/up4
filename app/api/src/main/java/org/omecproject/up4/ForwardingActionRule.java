/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
    private final Type type;  // Is the FAR Uplink, Downlink, etc
    private Integer globalFarId;  // Globally unique identifier of this FAR

    private ForwardingActionRule(Integer globalFarId, ImmutableByteSequence sessionId, Integer farId,
                                 Boolean drop, Boolean notifyCp, GtpTunnel tunnelDesc, Type type) {
        this.globalFarId = globalFarId;
        this.sessionId = sessionId;
        this.farId = farId;
        this.drop = drop;
        this.notifyCp = notifyCp;
        this.tunnelDesc = tunnelDesc;
        this.type = type;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        String globalIdStr = globalFarId == null ? "None" : globalFarId.toString();
        String matchKeys = String.format("ID:%d,SEID:%s,GID:%s", farId, sessionId.toString(), globalIdStr);
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
     *
     * @return true if this instance has FAR action parameters, false otherwise.
     */
    public boolean hasActionParameters() {
        return type != Type.KEYS_ONLY;
    }

    /**
     * True if this FAR forwards packets in the uplink direction, and false otherwise.
     *
     * @return true if FAR is uplink
     */
    public boolean isUplink() {
        return type == Type.UPLINK;
    }

    /**
     * True if this FAR forwards packets in the downlink direction, and false otherwise.
     *
     * @return true is FAR is downlink
     */
    public boolean isDownlink() {
        return type == Type.DOWNLINK;
    }

    /**
     * Check whether a global FAR ID has been assigned, which is necessary for an entry to be written
     * to the fabric.p4 pipeline.
     *
     * @return true if a global FAR ID has been assigned
     */
    public boolean hasGlobalFarId() {
        return this.globalFarId != null;
    }

    /**
     * Set the globally unique identifier of this FAR.
     *
     * @return globally unique FAR ID
     */
    public int getGlobalFarId() {
        return this.globalFarId;
    }

    /**
     * Get the globally unique identifier of this FAR.
     *
     * @param globalFarId globally unique FAR ID
     */
    public void setGlobalFarId(int globalFarId) {
        this.globalFarId = globalFarId;
    }

    /**
     * Get the ID of the PFCP Session that produced this FAR.
     *
     * @return PFCP session ID
     */
    public ImmutableByteSequence sessionId() {
        return sessionId;
    }

    /**
     * Get the PFCP session-local ID of the FAR that should apply to packets that match this PDR.
     *
     * @return PFCP session-local FAR ID
     */
    public int localFarId() {
        return farId;
    }

    /**
     * Returns true if this FAR drops packets, and false otherwise.
     *
     * @return true if this FAR drops
     */
    public boolean dropFlag() {
        return drop;
    }

    /**
     * Returns true if this FAR notifies the control plane on receiving a packet, and false otherwise.
     *
     * @return true if this FAR notifies the cp
     */
    public boolean notifyCpFlag() {
        return notifyCp;
    }

    /**
     * A description of the tunnel that this FAR will encapsulate packets with, if it is a downlink FAR. If the FAR
     * is uplink, there will be no such tunnel and this method wil return null.
     *
     * @return A GtpTunnel instance containing a tunnel sourceIP, destIP, and GTPU TEID, or null if the FAR is uplink.
     */
    public GtpTunnel tunnelDesc() {
        return tunnelDesc;
    }

    /**
     * Get the source IP of the GTP tunnel that this FAR will encapsulate packets with.
     *
     * @return GTP tunnel source IP
     */
    public Ip4Address tunnelSrc() {
        if (tunnelDesc == null) {
            return null;
        }
        return tunnelDesc.src();
    }

    /**
     * Get the destination IP of the GTP tunnel that this FAR will encapsulate packets with.
     *
     * @return GTP tunnel destination IP
     */
    public Ip4Address tunnelDst() {
        if (tunnelDesc == null) {
            return null;
        }
        return tunnelDesc.dst();
    }

    /**
     * Get the identifier of the GTP tunnel that this FAR will encapsulate packets with.
     *
     * @return GTP tunnel ID
     */
    public ImmutableByteSequence teid() {
        if (tunnelDesc == null) {
            return null;
        }
        return tunnelDesc.teid();
    }

    public enum Type {
        /**
         * Uplink FARs apply to packets traveling in the uplink direction, and do not encapsulate.
         */
        UPLINK,
        /**
         * Downlink FARS apply to packets traveling in the downlink direction, and do encapsulate.
         */
        DOWNLINK,
        /**
         * FAR was not built with any action parameters, only match keys.
         */
        KEYS_ONLY
    }

    public static class Builder {
        private Integer globalFarId;
        private ImmutableByteSequence sessionId;
        private Integer farId;
        private Boolean drop;
        private Boolean notifyCp;
        private GtpTunnel tunnelDesc;

        public Builder() {
            globalFarId = null;
            sessionId = null;
            farId = null;
            drop = null;
            notifyCp = null;
            tunnelDesc = null;
        }

        /**
         * Set the ID of the PFCP session that created this FAR.
         *
         * @param sessionId PFC session ID
         * @return This builder object
         */
        public Builder withSessionId(ImmutableByteSequence sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Set the ID of the PFCP session that created this FAR.
         *
         * @param sessionId PFC session ID
         * @return This builder object
         */
        public Builder withSessionId(long sessionId) {
            try {
                this.sessionId = ImmutableByteSequence.copyFrom(sessionId).fit(96);
            } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
                // This error is literally impossible
            }
            return this;
        }

        /**
         * Set the PFCP Session-local ID of this FAR.
         *
         * @param farId PFCP session-local FAR ID
         * @return This builder object
         */
        public Builder withFarId(int farId) {
            this.farId = farId;
            return this;
        }

        /**
         * Set the globally unique ID of this FAR.
         *
         * @param globalFarId globally unique FAR ID
         * @return This builder object
         */
        public Builder withGlobalFarId(int globalFarId) {
            this.globalFarId = globalFarId;
            return this;
        }

        /**
         * Set flags specifying whether this FAR should drop packets and/or notify the control plane when
         * any packets arrive.
         *
         * @param drop     true if this FAR drops
         * @param notifyCp true if this FAR notifies the control plane
         * @return This builder object
         */
        public Builder withFlags(boolean drop, boolean notifyCp) {
            this.drop = drop;
            this.notifyCp = notifyCp;
            return this;
        }

        /**
         * Set a flag specifying if this FAR drops traffic or not.
         *
         * @param drop true if FAR drops
         * @return This builder object
         */
        public Builder withDropFlag(boolean drop) {
            this.drop = drop;
            return this;
        }

        /**
         * Set a flag specifying if the control plane should be notified when this FAR is hit.
         *
         * @param notifyCp true if FAR notifies control plane
         * @return This builder object
         */
        public Builder withNotifyFlag(boolean notifyCp) {
            this.notifyCp = notifyCp;
            return this;
        }

        /**
         * Set the GTP tunnel that this FAR should encapsulate packets with.
         *
         * @param tunnel GTP tunnel
         * @return This builder object
         */
        public Builder withTunnel(GtpTunnel tunnel) {
            this.tunnelDesc = tunnel;
            return this;
        }

        /**
         * Set the unidirectional GTP tunnel that this FAR should encapsulate packets with.
         *
         * @param src  GTP tunnel source IP
         * @param dst  GTP tunnel destination IP
         * @param teid GTP tunnel ID
         * @return This builder object
         */
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
            Type type;
            if (drop == null && notifyCp == null) {
                type = Type.KEYS_ONLY;
            } else if (tunnelDesc == null) {
                type = Type.UPLINK;
            } else {
                type = Type.DOWNLINK;
            }
            return new ForwardingActionRule(globalFarId, sessionId, farId, drop, notifyCp, tunnelDesc, type);
        }
    }
}
