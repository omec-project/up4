/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;


import static com.google.common.base.Preconditions.checkNotNull;

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
        return String.format("PDR-Stats:{ CellID: %d, Ingress:(%dpkts,%dbytes), Egress:(%dpkts,%dbytes) }",
                cellId, ingressPkts, ingressBytes, egressPkts, egressBytes);
    }

    private PdrStats(int cellId, long ingressPkts, long ingressBytes,
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer cellId;
        private long ingressPkts;
        private long ingressBytes;
        private long egressPkts;
        private long egressBytes;
        public Builder() {
            this.ingressPkts = 0;
            this.ingressBytes = 0;
            this.egressPkts = 0;
            this.egressBytes = 0;
        }

        public Builder withCellId(int cellId) {
            this.cellId = cellId;
            return this;
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
            checkNotNull(cellId, "CellID must be provided");
            return new PdrStats(cellId, ingressPkts, ingressBytes, egressPkts, egressBytes);
        }
    }
}
