package org.omecproject.up4;

import org.onlab.util.ImmutableByteSequence;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Helper class primarily intended for organizing and printing PDRs and FARs, grouped by UE.
 */
public final class UpfFlow {
    private final ImmutableByteSequence pfcpSessionId;
    private final PacketDetectionRule pdr;
    private final ForwardingActionRule far;
    private final PdrStats flowStats;
    private final Type type;

    public enum Type {
        /**
         * If the flow consists of an uplink PDR and FAR.
         */
        UPLINK,
        /**
         * If the flow consists of a downlink PDR and FAR.
         */
        DOWNLINK
    }

    private UpfFlow(Type type, ImmutableByteSequence pfcpSessionId,
                    PacketDetectionRule pdr, ForwardingActionRule far, PdrStats flowStats) {
        this.pfcpSessionId = pfcpSessionId;
        this.pdr = pdr;
        this.far = far;
        this.flowStats = flowStats;
        this.type = type;
    }

    /**
     * Get the Packet Detection Rule of this UE data flow.
     *
     * @return the PDR of this data flow
     */
    public PacketDetectionRule getPdr() {
        return pdr;
    }

    /**
     * Get the Forward Action Rule of this UE data flow.
     *
     * @return the FAR of this data flow
     */
    public ForwardingActionRule getFar() {
        return far;
    }

    /**
     * Returns true if this UE data flow is in the uplink direction.
     *
     * @return true if uplink
     */
    public boolean isUplink() {
        return type == Type.UPLINK;
    }

    /**
     * Returns true if this UE data flow is in the downlink direction.
     *
     * @return true if downlink
     */
    public boolean isDownlink() {
        return type == Type.DOWNLINK;
    }

    @Override
    public String toString() {
        String farString = "NO FAR!";
        if (far != null) {
            if (far.isUplink()) {
                farString = String.format("FarID %d  -->  Decap()", far.localFarId());
            } else if (far.isDownlink()) {
                farString = String.format("FarID %d  -->  Encap(Src=%s, TEID=%s, Dst=%s)",
                        far.localFarId(), far.tunnelSrc(), far.teid(), far.tunnelDst());
            }
        }
        String pdrString = "NO PDR!";
        if (pdr != null) {
            if (pdr.isUplink()) {
                pdrString = String.format("Match(TunnelDst=%s && TEID=%s)", pdr.tunnelDest(), pdr.teid());
            } else if (pdr.isDownlink()) {
                pdrString = String.format("Match(Dst=%s && !GTP)", pdr.ueAddress());
            } else {
                pdrString = pdr.toString();
            }
        }
        String statString = "NO STATISTICS!";
        if (flowStats != null) {
            statString = String.format("%5d Ingress pkts -> %5d Egress pkts",
                    flowStats.getIngressPkts(), flowStats.getEgressPkts());
        }
        return String.format("SEID:%s - %s  -->  %s;\n |--- %s",
                pfcpSessionId, pdrString, farString, statString);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PacketDetectionRule pdr;
        private ForwardingActionRule far;
        private PdrStats flowStats;

        public Builder() {
        }

        /**
         * Add a PDR to the session. The PFCP session ID and the FAR ID set by this PDR should match whatever FAR
         * is added. If this condition is violated, the call to build() will fail.
         *
         * @param pdr the PacketDetectionRule to add
         * @return this builder object
         */
        public Builder setPdr(PacketDetectionRule pdr) {
            this.pdr = pdr;
            return this;
        }

        /**
         * Add a FAR to the session. The PFCP session ID and the FAR ID read by this FAR should match whatever PDR
         * is added. If this condition is violated, the call to build() will fail.
         *
         * @param far the ForwardingActionRule to add
         * @return this builder object
         */
        public Builder addFar(ForwardingActionRule far) {
            this.far = far;
            return this;
        }

        /**
         * Add a PDR counter statistics instance to this session.
         *
         * @param flowStats the PDR counter statistics instance to add
         * @return this builder object
         */
        public Builder addStats(PdrStats flowStats) {
            this.flowStats = flowStats;
            return this;
        }

        public UpfFlow build() {
            checkNotNull(pdr, "A PDR must be provided.");
            checkNotNull(far, "A FAR must be provided.");
            checkArgument(pdr.sessionId().equals(far.sessionId()),
                    "PFCP session ID of PDR and FAR must match!");
            checkArgument(pdr.localFarId() == far.localFarId(),
                    "FAR ID set by PDR and read by FAR must match!");
            checkArgument(pdr.counterId() == flowStats.getCellId(),
                    "Counter statistics provided do not use counter index set by provided PDR!");
            checkArgument((pdr.isDownlink() && far.isDownlink()) ||
                    (pdr.isUplink() && far.isUplink()), "PDR and FAR should be the same direction!");

            Type type = pdr.isUplink() ? Type.UPLINK : Type.DOWNLINK;

            return new UpfFlow(type, pdr.sessionId(), pdr, far, flowStats);
        }
    }
}
