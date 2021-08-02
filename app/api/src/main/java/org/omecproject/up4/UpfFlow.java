/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.behaviour.upf.ForwardingActionRule;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;
import org.onosproject.net.behaviour.upf.PdrStats;

import static com.google.common.base.Preconditions.checkArgument;

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
         * If the flow is not complete enough to have a known direction.
         */
        UNKNOWN,
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
            farString = String.format("FarID %d  -->  %s", far.farId(), far.actionString());
        }
        String pdrString = "NO PDR!";
        if (pdr != null) {
            pdrString = pdr.matchString();
            if (pdr.hasQfi() && pdr.matchesUnencapped()) {
                // Push QFI
                pdrString = String.format("%s, Push_qfi(%s)", pdrString, pdr.qfi());
            }
        }
        String statString = "NO STATISTICS!";
        if (flowStats != null) {
            statString = String.format("%5d Ingress pkts -> %5d Egress pkts",
                    flowStats.getIngressPkts(), flowStats.getEgressPkts());
        }
        return String.format("SEID:%s - %s  -->  %s;\n    >> %s",
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
         * is added (if a FAR is added). If this condition is violated, the call to build() will fail.
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
         * is added (if a PDR is added). If this condition is violated, the call to build() will fail.
         *
         * @param far the ForwardingActionRule to add
         * @return this builder object
         */
        public Builder setFar(ForwardingActionRule far) {
            this.far = far;
            return this;
        }

        /**
         * Add a PDR counter statistics instance to this session. The cell id of the provided counter statistics
         * should match the cell id set by the PDR added by setPdr(). If this condition is violated,
         * the call to build() will fail.
         *
         * @param flowStats the PDR counter statistics instance to add
         * @return this builder object
         */
        public Builder addStats(PdrStats flowStats) {
            this.flowStats = flowStats;
            return this;
        }

        public UpfFlow build() {
            Type type = Type.UNKNOWN;
            ImmutableByteSequence sessionId = null;
            if (pdr != null && far != null) {
                checkArgument(pdr.sessionId().equals(far.sessionId()),
                        "PFCP session ID of PDR and FAR must match!");
                checkArgument(pdr.farId() == far.farId(),
                        "FAR ID set by PDR and read by FAR must match!");
            }
            if (pdr != null) {
                if (flowStats != null) {
                    checkArgument(pdr.counterId() == flowStats.getCellId(),
                            "Counter statistics provided do not use counter index set by provided PDR!");
                }
                sessionId = pdr.sessionId();
                type = pdr.matchesEncapped() ? Type.UPLINK : Type.DOWNLINK;
            } else if (far != null) {
                sessionId = far.sessionId();
                if (far.forwards()) {
                    type = Type.UPLINK;
                } else if (far.encaps()) {
                    type = Type.DOWNLINK;
                }
            }

            return new UpfFlow(type, sessionId, pdr, far, flowStats);
        }
    }
}
