/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2021-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onosproject.net.behaviour.upf.UpfGtpTunnelPeer;
import org.onosproject.net.behaviour.upf.UpfMeter;
import org.onosproject.net.behaviour.upf.UpfSessionDownlink;
import org.onosproject.net.behaviour.upf.UpfCounter;
import org.onosproject.net.behaviour.upf.UpfTerminationDownlink;

import static com.google.common.base.Preconditions.checkArgument;
import static org.omecproject.up4.impl.Up4Utils.ppUpfMeter;

/**
 * Helper class primarily intended for printing downlink UPF flows.
 */
public final class DownlinkUpfFlow {
    private final UpfSessionDownlink sess;
    private final UpfTerminationDownlink term;
    private final UpfGtpTunnelPeer tunnelPeer;
    private final UpfCounter counter;
    private final UpfMeter sessMeter;
    private final UpfMeter appMeter;

    private DownlinkUpfFlow(UpfSessionDownlink sess, UpfTerminationDownlink term,
                            UpfGtpTunnelPeer tunnelPeer, UpfCounter counter,
                            UpfMeter sessMeter, UpfMeter appMeter) {
        this.sess = sess;
        this.term = term;
        this.tunnelPeer = tunnelPeer;
        this.counter = counter;
        this.sessMeter = sessMeter;
        this.appMeter = appMeter;
    }

    /**
     * Gets the downlink UE session of this UE flow.
     *
     * @return the downlink UE session
     */
    public UpfSessionDownlink getSession() {
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
    public UpfGtpTunnelPeer getTunnelPeer() {
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

    /**
     * Gets the session meter of this UE flow.
     *
     * @return the session meter if any, null otherwise
     */
    public UpfMeter getSessMeter() {
        return this.sessMeter;
    }

    /**
     * Gets the application meter of this UE flow.
     *
     * @return the application meter if any, null otherwise
     */
    public UpfMeter getAppMeter() {
        return appMeter;
    }

    @Override
    public String toString() {
        String strTermSess = "NO SESSION AND NO TERMINATION!";
        if (term != null && sess != null) {
            strTermSess = "ue_addr=" + term.ueSessionId() + ", app_id=" + term.applicationId() + ", ";
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
                        ", qfi=" + term.qfi() +
                        ", tc=" + term.trafficClass() +
                        ")";
            }
        } else if (term != null) {
            strTermSess = "NO_SESSION, ue_addr=" + term.ueSessionId() + ", app_id=" + term.applicationId() + ", ";
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

        String strSessMeter = "NO SESSION METER";
        if (sess != null) {
            strSessMeter += " (sess_meter_idx=" + sess.sessionMeterIdx() + ")";
        }
        if (sessMeter != null) {
            strSessMeter = "Session meter: " + ppUpfMeter(sessMeter);
        }

        String strAppMeter = "NO APPLICATION METER";
        if (term != null) {
            strAppMeter += " (app_meter_idx=" + term.appMeterIdx() + ")";
        }
        if (appMeter != null) {
            strAppMeter = "Application meter: " + ppUpfMeter(appMeter);
        }

        String statString = "NO STATISTICS!";
        if (counter != null) {
            statString = String.format(
                    "packets_ingress=%5d, packets_egress=%5d, packets_dropped=%5d",
                    counter.getIngressPkts(), counter.getEgressPkts(),
                    counter.getIngressPkts() - counter.getEgressPkts()
            );
        }
        return strTermSess + strTunn +
                "\n    " + strSessMeter +
                "\n    " + strAppMeter +
                "\n    " + statString;
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
        private UpfSessionDownlink sess = null;
        private UpfTerminationDownlink term = null;
        private UpfGtpTunnelPeer tunnelPeer = null;
        private UpfCounter counter = null;
        private UpfMeter sessMeter = null;
        private UpfMeter appMeter = null;

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
        public Builder withSessionDownlink(UpfSessionDownlink sess) {
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
        public Builder withTunnelPeer(UpfGtpTunnelPeer tunn) {
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

        /**
         * Adds the UPF session meter. The meter cell id should match the session
         * meter index in the downlink UPF session.
         *
         * @param meter the UPF session meter
         * @return this builder object
         */
        public Builder withSessionMeter(UpfMeter meter) {
            this.sessMeter = meter;
            return this;
        }

        /**
         * Adds the UPF app meter. The meter cell id should match the app
         * meter index in the downlink UPF termination.
         *
         * @param meter the UPF app meter
         * @return this builder object
         */
        public Builder withAppMeter(UpfMeter meter) {
            this.appMeter = meter;
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
            if (term != null && appMeter != null) {
                checkArgument(term.appMeterIdx() == appMeter.cellId(),
                              "UPF app meter must refer to the given UPF termination");
            }
            if (sess != null && sessMeter != null) {
                checkArgument(sess.sessionMeterIdx() == sessMeter.cellId(),
                              "UPF session meter must refer to the given UPF termination");
            }
            return new DownlinkUpfFlow(sess, term, tunnelPeer, counter, sessMeter, appMeter);
        }
    }
}
