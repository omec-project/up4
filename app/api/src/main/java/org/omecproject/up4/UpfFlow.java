/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.behaviour.TunnelEndPoint;
import org.onosproject.net.behaviour.upf.GtpTunnelPeer;
import org.onosproject.net.behaviour.upf.UeSession;
import org.onosproject.net.behaviour.upf.UpfCounter;
import org.onosproject.net.behaviour.upf.UpfDevice;
import org.onosproject.net.behaviour.upf.UpfTermination;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Helper class primarily intended for organizing and printing PDRs and FARs, grouped by UE.
 */
public final class UpfFlow {
    private final IpAddress ueAddress;
    private final UeSession session;
    private final UpfTermination termination;
    private final GtpTunnelPeer tunnelEndPoint;
    private final UpfCounter counters;
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

    private UpfFlow(Type type, IpAddress ueAddress, UeSession session,
                    UpfTermination termination, GtpTunnelPeer tunnelEndPoint,
                    UpfCounter counters) {
        this.ueAddress = ueAddress;
        this.session = session;
        this.termination = termination;
        this.tunnelEndPoint = tunnelEndPoint;
        this.counters = counters;
        this.type = type;
    }

    /**
     * Get the UE session of this UE data flow.
     *
     * @return the UE session of this data flow
     */
    public UeSession getUeSession() {
        return session;
    }

    /**
     * Get the termination rule of this UE data flow.
     *
     * @return the termination rule of this data flow
     */
    public UpfTermination getTermination() {
        return termination;
    }

    /**
     * Get the GTP Tunnel peer endpoint of this UE data flow if the flow is Downlink.
     *
     * @return the GTP tunnel peer, or null if {@link #isUplink()} returns true.
     */
    public GtpTunnelPeer getTunnelEndPoint() {
        return tunnelEndPoint;
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

//        String farString = "NO FAR!";
//        if (far != null) {
//            farString = String.format("FarID %d  -->  %s", far.farId(), far.actionString());
//        }
//        String pdrString = "NO PDR!";
//        if (pdr != null) {
//            pdrString = pdr.matchString();
//            if (pdr.hasQfi() && pdr.pushQfi()) {
//                // Push QFI
//                pdrString = String.format("%s, Push_qfi(%s)", pdrString, pdr.qfi());
//            }
//        }
//        String statString = "NO STATISTICS!";
//        if (flowStats != null) {
//            statString = String.format("%5d Ingress pkts -> %5d Egress pkts",
//                    flowStats.getIngressPkts(), flowStats.getEgressPkts());
//        }
//        return String.format("SEID:%s - %s  -->  %s;\n    >> %s",
//                pfcpSessionId, pdrString, farString, statString);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UeSession session;
        private UpfTermination termination;
        private UpfCounter counters;
        private GtpTunnelPeer tunnelPeer;

        public Builder() {
        }

        /**
         * Add a PDR to the session. The PFCP session ID and the FAR ID set by this PDR should match whatever FAR
         * is added (if a FAR is added). If this condition is violated, the call to build() will fail.
         *
         * @param pdr the PacketDetectionRule to add
         * @return this builder object
         */
        public Builder setUeSession(UeSession session) {
            this.session = session;
            return this;
        }

        /**
         * Add a FAR to the session. The PFCP session ID and the FAR ID read by this FAR should match whatever PDR
         * is added (if a PDR is added). If this condition is violated, the call to build() will fail.
         *
         * @param far the ForwardingActionRule to add
         * @return this builder object
         */
        public Builder setTermination(UpfTermination termination) {
            this.termination = termination;
            return this;
        }

        public UpfTermination getTermination() {
            return this.termination;
        }

        public Builder setGtpTunnelPeer(GtpTunnelPeer tunnelPeer) {
            this.tunnelPeer = tunnelPeer;
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
        public Builder addUpfCounter(UpfCounter counters) {
            this.counters = counters;
            return this;
        }

        public UpfFlow build() {
            checkArgument(this.session != null,
                          "UE Session cannot be null");
            checkArgument(this.termination != null,
                          "UPF Termination rule cannot be null");
            checkArgument(session.isUplink() == termination.isUplink(),
                          "Session and termination with different direction");
            checkArgument(session.isUplink() || tunnelPeer != null,
                          "Downlink flows must have a GTP tunnel peer");
            Type type;
            IpAddress ueAddress = termination.ueSessionId();
            if (session.isUplink()) {
                type = Type.UPLINK;
            } else {
                type = Type.DOWNLINK;
                checkArgument(session.tunPeerId() == tunnelPeer.tunPeerId(),
                              "UE Session pointing to different GTP tunnel peer");
            }
            if (counters != null) {
                checkArgument(termination.counterId() == counters.getCellId(),
                              "Counter statistics provided do not use counter index set by the termination rule");
            }
            return new UpfFlow(type, ueAddress, session, termination, tunnelPeer, counters);
        }
    }
}
