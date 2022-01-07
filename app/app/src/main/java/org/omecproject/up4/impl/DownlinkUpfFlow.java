/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2021-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onosproject.net.behaviour.upf.GtpTunnelPeer;
import org.onosproject.net.behaviour.upf.SessionDownlink;
import org.onosproject.net.behaviour.upf.UpfCounter;
import org.onosproject.net.behaviour.upf.UpfTerminationDownlink;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Helper class primarily intended for printing downlink UPF flows.
 */
public final class DownlinkUpfFlow {
    private final SessionDownlink sess;
    private final UpfTerminationDownlink term;
    private final GtpTunnelPeer tunnelPeer;
    private final UpfCounter counter;

    private DownlinkUpfFlow(SessionDownlink sess, UpfTerminationDownlink term,
                            GtpTunnelPeer tunnelPeer, UpfCounter counter) {
        this.sess = sess;
        this.term = term;
        this.tunnelPeer = tunnelPeer;
        this.counter = counter;
    }

    /**
     * Gets the downlink UE session of this UE flow.
     *
     * @return the downlink UE session
     */
    public SessionDownlink getSession() {
        return sess;
    }

    /**
     * Gets the downlink UPF termination of this UE flow.
     *
     * @return the downlink UPF termination
     */
    public UpfTerminationDownlink getTermination() {
        return this.term;
    }

    /**
     * Gets the GTP tunnel peers of this downlink UE flow.
     *
     * @return the GTP tunnel peer
     */
    public GtpTunnelPeer getTunnelPeer() {
        return this.tunnelPeer;
    }

    /**
     * Gets the UPF counter value of this UE flow.
     *
     * @return the UPF counter value
     */
    public UpfCounter getCounter() {
        return this.counter;
    }

    @Override
    public String toString() {
        String strTermSess = "NO SESSION AND NO TERMINATION!";
        if (term != null && sess != null) {
            strTermSess = "ue_addr=" + term.ueSessionId() + ", ";
            if ((sess.needsBuffering() && sess.needsDropping()) ||
                    (sess.needsBuffering() && term.needsDropping())) {
                strTermSess += "drop_buff()";
            } else if (sess.needsBuffering()) {
                strTermSess += "buff()";
            } else if (sess.needsDropping() || term.needsDropping()) {
                strTermSess += "drop()";
            } else {
                strTermSess += "fwd";
                strTermSess += "(" +
                        "teid=" + term.teid() +
                        " qfi=" + term.qfi() +
                        " tc=" + term.trafficClass() +
                        ")";
            }
        } else if (term != null) {
            strTermSess = "NO_SESSION, ue_addr=" + term.ueSessionId() + ", ";
            if (term.needsDropping()) {
                strTermSess += "drop()";
            } else {
                strTermSess += "fwd(" +
                        "teid=" + term.teid() +
                        " qfi=" + term.qfi() +
                        " tc=" + term.trafficClass() +
                        ")";
            }
        } else if (sess != null) {
            strTermSess = "NO_TERM, ue_addr=" + sess.ueAddress() + ", ";
            if (sess.needsBuffering() && sess.needsDropping()) {
                strTermSess += "drop_buff()";
            } else {
                strTermSess += "fwd()";
            }
        }

        String strTunn = "NO GTP TUNNEL!";
        if (tunnelPeer != null) {
            strTunn = ", tunnel(peer_id=" + tunnelPeer.tunPeerId() +
                    ", dst_addr=" + tunnelPeer.dst() +
                    ", src_addr=" + tunnelPeer.src() +
                    ", src_port=" + tunnelPeer.srcPort() +
                    ")";
        }

        String statString = "NO STATISTICS!";
        if (counter != null) {
            statString = String.format(
                    "packets_ingress=%5d, packets_egress=%5d",
                    counter.getIngressPkts(), counter.getEgressPkts()
            );
        }
        return strTermSess + strTunn + ", " + statString;
    }

    /**
     * Returns a new downlink UPF flow builder.
     *
     * @return downlink UPF flow builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of a downlink UPF flow.
     */
    public static class Builder {
        private SessionDownlink sess = null;
        private UpfTerminationDownlink term = null;
        private GtpTunnelPeer tunnelPeer = null;
        private UpfCounter counter = null;

        public Builder() {
        }

        /**
         * Adds the downlink UE session. The UE address of this session should match
         * the UE address of the downlink UPF termination. The tunnel peer ID, if
         * set by this UE session, should match the ID in the GTP tunnel peer.
         *
         * @param sess the downlink UE session
         * @return this builder object
         */
        public Builder withSessionDownlink(SessionDownlink sess) {
            this.sess = sess;
            return this;
        }

        /**
         * Adds the downlink UPF termination. The UE address of this termination
         * should match the UE address of the downlink UE session. The counter id
         * should match the UPF counter ID value.
         *
         * @param term the downlink UPF termination
         * @return this builder object
         */
        public Builder withTerminationDownlink(UpfTerminationDownlink term) {
            this.term = term;
            return this;
        }

        /**
         * Adds the GTP tunnel peer. The counter ID should match the tunnel peer id
         * in the downlink UE session.
         *
         * @param tunn the GTP tunnel peer
         * @return this builder object
         */
        public Builder withTunnelPeer(GtpTunnelPeer tunn) {
            this.tunnelPeer = tunn;
            return this;
        }

        /**
         * Adds the UPF counter. The counter ID should match the counter id in the
         * downlink UPF termination.
         *
         * @param counter the UPF counter
         * @return this builder object
         */
        public Builder withCounter(UpfCounter counter) {
            this.counter = counter;
            return this;
        }

        public DownlinkUpfFlow build() {
            if (sess != null && term != null) {
                checkArgument(sess.ueAddress().equals(term.ueSessionId()),
                              "Session and termination must refer to the same UE address");
            }
            if (sess != null && tunnelPeer != null) {
                checkArgument(sess.tunPeerId().equals(tunnelPeer.tunPeerId()),
                              "Session and tunnel peer must refer to the same tunnel ID");
            }
            if (term != null && counter != null) {
                checkArgument(term.counterId() == counter.getCellId(),
                              "UPF Counter must refer to the given UPF Termination");
            }
            return new DownlinkUpfFlow(sess, term, tunnelPeer, counter);
        }
    }
}
