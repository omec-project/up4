/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

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
    private final ImmutableByteSequence teid;  // The Tunnel Endpoint ID that this PDR matches on
    private final Ip4Address tunnelDst;  // The tunnel destination address that this PDR matches on
    // Action parameters
    private final ImmutableByteSequence sessionId;  // The ID of the PFCP session that created this PDR
    private final Integer ctrId;  // Counter ID unique to this PDR
    private final Integer farId;  // The PFCP session-local ID of the FAR that should apply after this PDR hits
    private final Type type;

    private static final int SESSION_ID_BITWIDTH = 96;

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

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return a string representing the match conditions of this PDR.
     *
     * @return a string representing the PDR match conditions
     */
    public String matchString() {
        if (matchesEncapped()) {
            return String.format("Match(Dst=%s, TEID=%s)", tunnelDest(), teid());
        } else {
            return String.format("Match(Dst=%s, !GTP)", ueAddress());
        }
    }

    @Override
    public String toString() {
        String actionParams = "";
        if (hasActionParameters()) {
            actionParams = String.format("SEID=%s, FAR=%d, CtrIdx=%d",
                    sessionId.toString(), farId, ctrId);
        }

        return String.format("PDR{%s -> LoadParams(%s)}", matchString(), actionParams);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PacketDetectionRule that = (PacketDetectionRule) obj;

        // Safe comparisons between potentially null objects
        return (this.type.equals(that.type) &&
                Objects.equals(this.teid, that.teid) &&
                Objects.equals(this.tunnelDst, that.tunnelDst) &&
                Objects.equals(this.ueAddr, that.ueAddr) &&
                Objects.equals(this.ctrId, that.ctrId) &&
                Objects.equals(this.sessionId, that.sessionId) &&
                Objects.equals(this.farId, that.farId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(ueAddr, teid, tunnelDst, sessionId, ctrId, farId, type);
    }

    /**
     * Instances created as a result of DELETE write requests will not have action parameters, only match keys.
     * This method should be used to avoid null pointer exceptions in those instances.
     *
     * @return true if this instance has PDR action parameters, false otherwise.
     */
    public boolean hasActionParameters() {
        return type == Type.MATCH_ENCAPPED || type == Type.MATCH_UNENCAPPED;
    }

    /**
     * Return a new instance of this PDR with the action parameters stripped, leaving only the match keys.
     *
     * @return a new PDR with only match keys
     */
    public PacketDetectionRule withoutActionParams() {
        if (matchesEncapped()) {
            return PacketDetectionRule.builder()
                    .withTeid(teid)
                    .withTunnelDst(tunnelDst)
                    .build();
        } else {
            return PacketDetectionRule.builder()
                    .withUeAddr(ueAddr).build();
        }
    }

    /**
     * True if this PDR matches on packets received with a GTP header, and false otherwise.
     *
     * @return true if the PDR matches only encapsulated packets
     */
    public boolean matchesEncapped() {
        return type == Type.MATCH_ENCAPPED || type == Type.MATCH_ENCAPPED_NO_ACTION;
    }

    /**
     * True if this PDR matches on packets received without a GTP header, and false otherwise.
     *
     * @return true if the PDR matches only unencapsulated packets
     */
    public boolean matchesUnencapped() {
        return type == Type.MATCH_UNENCAPPED || type == Type.MATCH_UNENCAPPED_NO_ACTION;
    }

    /**
     * Get the ID of the PFCP session that produced this PDR.
     *
     * @return PFCP session ID
     */
    public ImmutableByteSequence sessionId() {
        return sessionId;
    }

    /**
     * Get the UE IP address that this PDR matches on.
     *
     * @return UE IP address
     */
    public Ip4Address ueAddress() {
        return ueAddr;
    }

    /**
     * Get the identifier of the GTP tunnel that this PDR matches on.
     *
     * @return GTP tunnel ID
     */
    public ImmutableByteSequence teid() {
        return teid;
    }

    /**
     * Get the destination IP of the GTP tunnel that this PDR matches on.
     *
     * @return GTP tunnel destination IP
     */
    public Ip4Address tunnelDest() {
        return tunnelDst;
    }

    /**
     * Get the dataplane PDR counter cell ID that this PDR is assigned.
     *
     * @return PDR counter cell ID
     */
    public int counterId() {
        return ctrId;
    }

    /**
     * Get the PFCP session-local ID of the far that should apply to packets that this PDR matches.
     *
     * @return PFCP session-local FAR ID
     */
    public int farId() {
        return farId;
    }

    public enum Type {
        /**
         * Match on packets that are encapsulated in a GTP tunnel.
         */
        MATCH_ENCAPPED,
        /**
         * Match on packets that are not encapsulated in a GTP tunnel.
         */
        MATCH_UNENCAPPED,
        /**
         * For PDRs that match on encapsulated packets but do not yet have any action parameters set.
         * These are usually built in the context of P4Runtime DELETE write requests.
         */
        MATCH_ENCAPPED_NO_ACTION,
        /**
         * For PDRs that match on unencapsulated packets but do not yet have any action parameters set.
         * These are usually built in the context of P4Runtime DELETE write requests.
         */
        MATCH_UNENCAPPED_NO_ACTION
    }

    public static class Builder {
        private ImmutableByteSequence sessionId = null;
        private Integer ctrId = null;
        private Integer localFarId = null;
        private Ip4Address ueAddr = null;
        private ImmutableByteSequence teid = null;
        private Ip4Address tunnelDst = null;

        public Builder() {
        }

        /**
         * Set the ID of the PFCP session that produced this PDR.
         *
         * @param sessionId PFCP session ID
         * @return This builder object
         */
        public Builder withSessionId(ImmutableByteSequence sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Set the ID of the PFCP session that produced this PDR.
         *
         * @param sessionId PFCP session ID
         * @return This builder object
         */
        public Builder withSessionId(long sessionId) {
            try {
                this.sessionId = ImmutableByteSequence.copyFrom(sessionId).fit(SESSION_ID_BITWIDTH);
            } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
                // This error is literally impossible
            }
            return this;
        }

        /**
         * Set the UE IP address that this PDR matches on.
         *
         * @param ueAddr UE IP address
         * @return This builder object
         */
        public Builder withUeAddr(Ip4Address ueAddr) {
            this.ueAddr = ueAddr;
            return this;
        }

        /**
         * Set the dataplane PDR counter cell ID that this PDR is assigned.
         *
         * @param ctrId PDR counter cell ID
         * @return This builder object
         */
        public Builder withCounterId(int ctrId) {
            this.ctrId = ctrId;
            return this;
        }


        /**
         * Set the PFCP session-local ID of the far that should apply to packets that this PDR matches.
         *
         * @param localFarId PFCP session-local FAR ID
         * @return This builder object
         */
        public Builder withLocalFarId(int localFarId) {
            this.localFarId = localFarId;
            return this;
        }

        /**
         * Set the identifier of the GTP tunnel that this PDR matches on.
         *
         * @param teid GTP tunnel ID
         * @return This builder object
         */
        public Builder withTeid(int teid) {
            this.teid = ImmutableByteSequence.copyFrom(teid);
            return this;
        }

        /**
         * Set the identifier of the GTP tunnel that this PDR matches on.
         *
         * @param teid GTP tunnel ID
         * @return This builder object
         */
        public Builder withTeid(ImmutableByteSequence teid) {
            this.teid = teid;
            return this;
        }

        /**
         * Set the destination IP of the GTP tunnel that this PDR matches on.
         *
         * @param tunnelDst GTP tunnel destination IP
         * @return This builder object
         */
        public Builder withTunnelDst(Ip4Address tunnelDst) {
            this.tunnelDst = tunnelDst;
            return this;
        }

        /**
         * Set the tunnel ID and destination IP of the GTP tunnel that this PDR matches on.
         *
         * @param teid      GTP tunnel ID
         * @param tunnelDst GTP tunnel destination IP
         * @return This builder object
         */
        public Builder withTunnel(ImmutableByteSequence teid, Ip4Address tunnelDst) {
            this.teid = teid;
            this.tunnelDst = tunnelDst;
            return this;
        }

        public PacketDetectionRule build() {
            // Some match keys are required.
            checkArgument((ueAddr != null && teid == null && tunnelDst == null) ||
                            (ueAddr == null && teid != null && tunnelDst != null),
                    "Either a UE address or a TEID and Tunnel destination must be provided, but not both.");
            // Action parameters are optional but must be all provided together if they are provided
            checkArgument((sessionId != null && ctrId != null && localFarId != null) ||
                            (sessionId == null && ctrId == null && localFarId == null),
                    "PDR action parameters must be provided together or not at all.");
            Type type;
            if (teid != null) {
                if (sessionId != null) {
                    type = Type.MATCH_ENCAPPED;
                } else {
                    type = Type.MATCH_ENCAPPED_NO_ACTION;
                }
            } else {
                if (sessionId != null) {
                    type = Type.MATCH_UNENCAPPED;
                } else {
                    type = Type.MATCH_UNENCAPPED_NO_ACTION;
                }
            }
            return new PacketDetectionRule(sessionId, ctrId, localFarId, ueAddr, teid, tunnelDst, type);
        }
    }
}
