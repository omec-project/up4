/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.IpAddress;
import org.onosproject.net.behaviour.upf.GtpTunnelPeer;
import org.onosproject.net.behaviour.upf.UeSession;
import org.onosproject.net.behaviour.upf.UpfCounter;
import org.onosproject.net.behaviour.upf.UpfTermination;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Helper class primarily intended for organizing and printing UPF information, grouped by UE.
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
         * If the flow is for uplink traffic.
         */
        UPLINK,
        /**
         * If the flow is for downlink traffic.
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
        //TODO
        return "";

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
         * Sets the UE session.
         *
         * @param session the UE session to set
         * @return this builder object
         */
        public Builder setUeSession(UeSession session) {
            this.session = session;
            return this;
        }

        /**
         * Sets the termination rule.
         *
         * @param termination the termination rule to set
         * @return this builder object
         */
        public Builder setTermination(UpfTermination termination) {
            this.termination = termination;
            return this;
        }

        /**
         * Sets the tunnel peer. This can be set only for uplink flows. The
         * tunnel peer of the given tunnel should match the tunnel peer id set
         * by the UE session.
         *
         * @param tunnelPeer the tunnel peer to set.
         * @return this builder object
         */
        public Builder setGtpTunnelPeer(GtpTunnelPeer tunnelPeer) {
            this.tunnelPeer = tunnelPeer;
            return this;
        }

        /**
         * Set a UPF counter statistics instance to this session. The cell id of
         * the provided counter statistics should match the cell id set by the
         * termination rule. If this condition is violated, the call to build()
         * will fail.
         *
         * @param counters the UPF counter statistics instance to set
         * @return this builder object
         */
        public Builder setUpfCounter(UpfCounter counters) {
            this.counters = counters;
            return this;
        }

        /**
         * Get the current set termination rule.
         *
         * @return the current termination rule.
         */
        public UpfTermination getTermination() {
            return this.termination;
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
