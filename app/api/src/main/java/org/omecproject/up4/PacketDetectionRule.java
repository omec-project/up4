/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;

/**
 * A single Packet Detection Rule (FAR). An instance of this class will be generated by a logical switch write request
 * to the database-style PDR P4 table, and the resulting instance should contain all the information needed to
 * reproduce that logical switch PDR in the event of a client read request. The instance should also contain sufficient
 * information (or expose the means to retrieve such information) to generate the corresponding
 * fabric.p4 dataplane forwarding state that implements the PDR.
 */
public class PacketDetectionRule {
    private final ImmutableByteSequence sessionId;
    private final Integer ctrId;
    private final Integer farId;
    private final Ip4Address ueAddr;
    private final ImmutableByteSequence teid;
    private final Ip4Address tunnelDst;
    private int globalFarId;

    public PacketDetectionRule(ImmutableByteSequence sessionId, Integer ctrId, Integer farId, Ip4Address ueAddr,
                               ImmutableByteSequence teid, Ip4Address tunnelDst) {
        this.sessionId = sessionId;
        this.ctrId = ctrId;
        this.farId = farId;
        this.ueAddr = ueAddr;
        this.teid = teid;
        this.tunnelDst = tunnelDst;
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

    public boolean hasActionParameters() {
        return ctrId != null && farId != null;
    }

    public boolean isUplink() {
        return teid != null && tunnelDst != null;
    }

    public boolean isDownlink() {
        return teid == null && tunnelDst == null;
    }

    public ImmutableByteSequence sessionId() {
        return sessionId;
    }

    public Ip4Address ueAddress() {
        return ueAddr;
    }

    public ImmutableByteSequence teid() {
        return teid;
    }

    public Ip4Address tunnelDest() {
        return tunnelDst;
    }

    public int counterId() {
        return ctrId;
    }

    public int localFarId() {
        return farId;
    }

    public void setGlobalFarId(int globalFarId) {
        this.globalFarId = globalFarId;
    }

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

        public Builder withSessionId(ImmutableByteSequence sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder withSessionId(long sessionId) {
            this.sessionId = ImmutableByteSequence.copyFrom(sessionId);
            return this;
        }

        public Builder withUeAddr(Ip4Address ueAddr) {
            this.ueAddr = ueAddr;
            return this;
        }

        public Builder withCounterId(int ctrId) {
            this.ctrId = ctrId;
            return this;
        }

        public Builder withFarId(int farId) {
            this.farId = farId;
            return this;
        }

        public Builder withTeid(int teid) {
            this.teid = ImmutableByteSequence.copyFrom(teid);
            return this;
        }

        public Builder withTeid(ImmutableByteSequence teid) {
            this.teid = teid;
            return this;
        }

        public Builder withTunnelDst(Ip4Address tunnelDst) {
            this.tunnelDst = tunnelDst;
            return this;
        }

        public Builder withTunnel(ImmutableByteSequence teid, Ip4Address tunnelDst) {
            this.teid = teid;
            this.tunnelDst = tunnelDst;
            return this;
        }

        public PacketDetectionRule build() {
            return new PacketDetectionRule(sessionId, ctrId, farId, ueAddr, teid, tunnelDst);
        }
    }
}
