/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

/**
 * A structure for compactly passing PDR counter values for a given counter ID.
 * Contains four counts: Ingress Packets, Ingress Bytes, Egress Packets, Egress Bytes
 */
public final class PdrStats {
    private final int cellId;
    private final long ingressPkts;
    private final long ingressBytes;
    private final long egressPkts;
    private final long egressBytes;

    public String toString() {
        return String.format("PDR-Stats:{ Ctr-ID: %d, Ingress:(%dpkts,%dbytes), Egress:(%dpkts,%dbytes) }",
                cellId, ingressPkts, ingressBytes, egressPkts, egressBytes);
    }

    public PdrStats(int cellId, long ingressPkts, long ingressBytes,
                    long egressPkts, long egressBytes) {
        this.cellId = cellId;
        this.ingressPkts = ingressPkts;
        this.ingressBytes = ingressBytes;
        this.egressPkts = egressPkts;
        this.egressBytes = egressBytes;
    }

    public int getCellId() {
        return cellId;
    }
    public long getIngressPkts() {
        return ingressPkts;
    }
    public long getEgressPkts() {
        return egressPkts;
    }
    public long getIngressBytes() {
        return ingressBytes;
    }
    public long getEgressBytes() {
        return egressBytes;
    }

    public static Builder builder(int cellId) {
        return new Builder(cellId);
    }


    public static class Builder {
        private final int cellId;
        private long ingressPkts;
        private long ingressBytes;
        private long egressPkts;
        private long egressBytes;
        public Builder(int cellId) {
            this.cellId = cellId;
            this.ingressPkts = 0;
            this.ingressBytes = 0;
            this.egressPkts = 0;
            this.egressBytes = 0;
        }

        public Builder setIngress(long ingressPkts, long ingressBytes) {
            this.ingressPkts = ingressPkts;
            this.ingressBytes = ingressBytes;
            return this;
        }

        public Builder setEgress(long egressPkts, long egressBytes) {
            this.egressPkts = egressPkts;
            this.egressBytes = egressBytes;
            return this;
        }

        public PdrStats build() {
            return new PdrStats(cellId, ingressPkts, ingressBytes, egressPkts, egressBytes);
        }
    }
}
