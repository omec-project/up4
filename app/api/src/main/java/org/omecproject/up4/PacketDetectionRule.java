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
 * A single Packet Detection Rule (PDR), an entity described in the 3GPP specifications (although that does not mean
 * that this class is 3GPP compliant). An instance of this class will be generated by a logical switch write request
 * to the database-style PDR P4 table, and the resulting instance should contain all the information needed to
 * reproduce that logical switch PDR in the event of a client read request. The instance should also contain sufficient
 * information (or expose the means to retrieve such information) to generate the corresponding
 * fabric.p4 dataplane forwarding state that implements the PDR.
 */
public final class PacketDetectionRule {
    // Match keys
    private final Ip4Address ueAddr;  // The UE IP address that this PDR matches on
    private final ImmutableByteSequence teid;  // The Tunnel Endpoint ID that this PDR matches on (if PDR is uplink)
    private final Ip4Address tunnelDst;  // The tunnel destination address that this PDR matches on (if PDR is uplink)
    // Action parameters
    private final ImmutableByteSequence sessionId;  // The ID of the PFCP session that created this PDR
    private final Integer ctrId;  // Counter ID unique to this PDR
    private final Integer farId;  // The PFCP session-local ID of the FAR that should apply to packets if this PDR hits
    private final Type type; // Is the PDR Uplink, Downlink, etc.
    private int globalFarId; // The non-session-local ID of the FAR that should apply to packets if this PDR hits

    private PacketDetectionRule(ImmutableByteSequence sessionId, Integer ctrId, Integer farId, Ip4Address ueAddr,
                                ImmutableByteSequence teid, Ip4Address tunnelDst, Type type) {
        this.ueAddr = ueAddr;
        this.teid = teid;
        this.tunnelDst = tunnelDst;
        this.sessionId = sessionId;
        this.ctrId = ctrId;
        this.farId = farId;
        this.type = type;
    }

    public enum Type {
        /**
         * Uplink PDRs match on packets travelling in the uplink direction. These packets will have a GTP tunnel.
         */
        UPLINK,
        /**
         * Downlink PDRs match on packets travelling in the downlink direction.
         * These packets will not have a GTP tunnel.
         */
        DOWNLINK,
        /**
         * For uplink PDRs that were not build with any action parameters, only match keys.
         * These are usually built in the context of P4Runtime DELETE write requests.
         */
        UPLINK_KEYS_ONLY,
        /**
         * For downlink PDRs that were not build with any action parameters, only match keys.
         * These are usually built in the context of P4Runtime DELETE write requests.
         */
        DOWNLINK_KEYS_ONLY
    }

    @Override
    public String toString() {
        String matchKeys;
        String directionString;
        if (isUplink()) {
            directionString = "Uplink";
            matchKeys = String.format("UE:%s,TunnelDst:%s,TEID:%s",
                    ueAddr.toString(), tunnelDst.toString(), teid.toString());
        } else {
            directionString = "Downlink";
            matchKeys = String.format("UE:%s", ueAddr.toString());
        }
        String actionParams = "";
        if (hasActionParameters()) {
            actionParams = String.format("SEID:%s,FAR:%d,CtrIdx:%d", sessionId.toString(), farId, ctrId);
        }

        return String.format("%s-PDR{ Keys:(%s) -> Params (%s) }", directionString, matchKeys, actionParams);
    }

    /**
     * Instances created as a result of DELETE write requests will not have action parameters, only match keys.
     * This method should be used to avoid null pointer exceptions in those instances.
     *
     * @return true if this instance has PDR action parameters, false otherwise.
     */
    public boolean hasActionParameters() {
        return type == Type.UPLINK || type == Type.DOWNLINK;
    }

    /**
     * Is this a PDR matching on packets travelling in the uplink direction?
     *
     * @return true if the PDR matches only uplink packets
     */
    public boolean isUplink() {
        return type == Type.UPLINK || type == Type.UPLINK_KEYS_ONLY;
    }

    /**
     * Is this a PDR matching on packets travelling in the downlink direction?
     *
     * @return true if the PDR matches only downlink packets
     */
    public boolean isDownlink() {
        return type == Type.DOWNLINK || type == Type.DOWNLINK_KEYS_ONLY;
    }

    public ImmutableByteSequence sessionId() {
        return sessionId;
    }

    /**
     * @return The UE IP address that this PDR matches on
     */
    public Ip4Address ueAddress() {
        return ueAddr;
    }

    /**
     * @return The GTP Tunnel ID that this PDR matches on
     */
    public ImmutableByteSequence teid() {
        return teid;
    }

    /**
     * @return The GTP tunnel destination that this PDR matches on
     */
    public Ip4Address tunnelDest() {
        return tunnelDst;
    }

    /**
     * @return The Counter CellID unique to this PDR
     */
    public int counterId() {
        return ctrId;
    }

    /**
     * @return PFCP Session-Local FAR ID
     */
    public int localFarId() {
        return farId;
    }

    /**
     * @param globalFarId Globally unique FAR ID
     */
    public void setGlobalFarId(int globalFarId) {
        this.globalFarId = globalFarId;
    }

    /**
     * @return Globally unique FAR ID
     */
    public int getGlobalFarId() {
        return globalFarId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ImmutableByteSequence sessionId = null;
        private Integer ctrId = null;
        private Integer farId = null;
        private Ip4Address ueAddr = null;
        private ImmutableByteSequence teid = null;
        private Ip4Address tunnelDst = null;

        public Builder() {
        }

        /**
         * @param sessionId The ID of the PFCP session that produced this PDR
         * @return This builder object
         */
        public Builder withSessionId(ImmutableByteSequence sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * @param sessionId The ID of the PFCP session that produced this PDR
         * @return This builder object
         */
        public Builder withSessionId(long sessionId) {
            this.sessionId = ImmutableByteSequence.copyFrom(sessionId);
            return this;
        }

        /**
         * @param ueAddr The UE IP address that this PDR matches on
         * @return This builder object
         */
        public Builder withUeAddr(Ip4Address ueAddr) {
            this.ueAddr = ueAddr;
            return this;
        }

        /**
         * @param ctrId Index into the dataplane counter that this PDR should use to count packets and bytes
         * @return This builder object
         */
        public Builder withCounterId(int ctrId) {
            this.ctrId = ctrId;
            return this;
        }

        /**
         * @param farId The ID of the FAR that should apply to packets that this PDR matches
         * @return This builder object
         */
        public Builder withFarId(int farId) {
            this.farId = farId;
            return this;
        }

        /**
         * @param teid The GTP tunnel ID that this PDR matches on
         * @return This builder object
         */
        public Builder withTeid(int teid) {
            this.teid = ImmutableByteSequence.copyFrom(teid);
            return this;
        }

        /**
         * @param teid The GTP tunnel ID that this PDR matches on
         * @return This builder object
         */
        public Builder withTeid(ImmutableByteSequence teid) {
            this.teid = teid;
            return this;
        }

        /**
         * @param tunnelDst The GTP tunnel destination IP that this PDR matches on.
         * @return This builder object
         */
        public Builder withTunnelDst(Ip4Address tunnelDst) {
            this.tunnelDst = tunnelDst;
            return this;
        }

        /**
         * @param teid      The GTP tunnel ID that this PDR matches on
         * @param tunnelDst The GTP tunnel destination IP that this PDR matches on.
         * @return This builder object
         */
        public Builder withTunnel(ImmutableByteSequence teid, Ip4Address tunnelDst) {
            this.teid = teid;
            this.tunnelDst = tunnelDst;
            return this;
        }

        public PacketDetectionRule build() {
            // Some match keys are required.
            checkNotNull(ueAddr, "UE address is required");
            checkArgument((teid == null && tunnelDst == null) ||
                            (teid != null && tunnelDst != null),
                    "TEID and Tunnel destination must be provided together or not at all");
            // Action parameters are optional but must be all provided together if they are provided
            checkArgument((sessionId != null && ctrId != null && farId != null) ||
                            (sessionId == null && ctrId == null && farId == null),
                    "PDR action parameters must be provided together or not at all.");
            Type type;
            if (teid != null) {
                if (sessionId != null) {
                    type = Type.UPLINK;
                } else {
                    type = Type.UPLINK_KEYS_ONLY;
                }
            } else {
                if (sessionId != null) {
                    type = Type.DOWNLINK;
                } else {
                    type = Type.DOWNLINK_KEYS_ONLY;
                }
            }
            return new PacketDetectionRule(sessionId, ctrId, farId, ueAddr, teid, tunnelDst, type);
        }
    }
}
