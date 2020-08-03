package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Helper class primarily intended for organizing and printing PDRs and FARs, grouped by UE.
 */
public final class UeSession {
    private final Ip4Address ueAddress;
    private final ImmutableByteSequence pfcpSessionId;
    private final PacketDetectionRule uplinkPdr;
    private final PacketDetectionRule downlinkPdr;
    private final ForwardingActionRule uplinkFar;
    private final ForwardingActionRule downlinkFar;
    private final PdrStats uplinkStats;
    private final PdrStats downlinkStats;
    private final Collection<PacketDetectionRule> otherPdrs;
    private final Collection<ForwardingActionRule> otherFars;

    private UeSession(Ip4Address ueAddress, ImmutableByteSequence pfcpSessionId,
                      PacketDetectionRule uplinkPdr, PacketDetectionRule downlinkPdr,
                      ForwardingActionRule uplinkFar, ForwardingActionRule downlinkFar,
                      PdrStats uplinkStats, PdrStats downlinkStats,
                      Collection<PacketDetectionRule> otherPdrs, Collection<ForwardingActionRule> otherFars) {
        this.ueAddress = ueAddress;
        this.pfcpSessionId = pfcpSessionId;
        this.uplinkPdr = uplinkPdr;
        this.downlinkPdr = downlinkPdr;
        this.uplinkFar = uplinkFar;
        this.downlinkFar = downlinkFar;
        this.uplinkStats = uplinkStats;
        this.downlinkStats = downlinkStats;
        this.otherPdrs = otherPdrs;
        this.otherFars = otherFars;
    }

    @Override
    public String toString() {
        String divider = "-------------------------------------------------------\n";
        String dividerf = "-- %s --\n";
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(divider)
                .append("UE Address: ").append(ueAddress)
                .append(", PFCP Session ID: ").append(pfcpSessionId)
                .append("\n")
                .append(String.format(dividerf, "Uplink"))
                .append(flowToString(uplinkPdr, uplinkFar, uplinkStats))
                .append("\n")
                .append(String.format(dividerf, "Downlink"))
                .append(flowToString(downlinkPdr, downlinkFar, downlinkStats))
                .append("\n");

        if (!otherPdrs.isEmpty() || !otherFars.isEmpty()) {
            stringBuilder.append(String.format(dividerf, "Unknown Type Rules"));
            for (var pdr : otherPdrs) {
                stringBuilder.append(pdr).append("\n");
            }
            for (var far : otherFars) {
                stringBuilder.append(far).append("\n");
            }
        }
        stringBuilder.append(divider);
        return stringBuilder.toString();
    }

    private String flowToString(PacketDetectionRule pdr, ForwardingActionRule far, PdrStats stats) {
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
                pdrString = String.format("Match(Src=UE && TunnelDst=%s && TEID=%s)", pdr.tunnelDest(), pdr.teid());
            } else if (pdr.isDownlink()) {
                pdrString = "Match(Dst=UE && !GTP)";
            } else {
                pdrString = pdr.toString();
            }
        }
        return String.format("- %s  -->  %s;\n- %5d Ingress pkts -> %5d Egress pkts",
                pdrString, farString, stats.getIngressPkts(), stats.getEgressPkts());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PacketDetectionRule uplinkPdr;
        private PacketDetectionRule downlinkPdr;
        private ForwardingActionRule uplinkFar;
        private ForwardingActionRule downlinkFar;
        private List<PacketDetectionRule> otherPdrs;
        private List<ForwardingActionRule> otherFars;
        private List<PdrStats> statList;

        public Builder() {
            otherPdrs = new ArrayList<>();
            otherFars = new ArrayList<>();
            statList = new ArrayList<>();
        }

        /**
         * Add a PDR to the session. All added PDRs should match on the same UE address. If this condition
         * is violated, the call to build() will fail.
         *
         * @param pdr the PacketDetectionRule to add
         * @return this builder object
         */
        public Builder addPdr(PacketDetectionRule pdr) {
            if (pdr.isUplink()) {
                uplinkPdr = pdr;
            } else if (pdr.isDownlink()) {
                downlinkPdr = pdr;
            } else {
                otherPdrs.add(pdr);
            }
            return this;
        }

        /**
         * Add a PDR to the session. Each FAR should match on the FAR-ID and PFCP Session ID that were set by some
         * PDR that is also added to this builder. If this condition is violated, the call to build() will fail.
         *
         * @param far the ForwardingActionRule to add
         * @return this builder object
         */
        public Builder addFar(ForwardingActionRule far) {
            if (far.isUplink()) {
                uplinkFar = far;
            } else if (far.isDownlink()) {
                downlinkFar = far;
            } else {
                otherFars.add(far);
            }
            return this;
        }

        /**
         * Add a PDR counter statistics instance to this session. The instance will automatically be paired with
         * the associated PDR, if it was added to this builder object. If the associated PDR is not added before
         * build() is called, the statistics instance will be silently deleted.
         *
         * @param stat the PDR counter statistics instance to add
         * @return this builder object
         */
        public Builder addStats(PdrStats stat) {
            statList.add(stat);
            return this;
        }

        public UeSession build() {
            Ip4Address ueAddress = uplinkPdr.ueAddress();
            ImmutableByteSequence sessionId = uplinkPdr.sessionId();
            checkArgument(uplinkPdr.ueAddress().equals(downlinkPdr.ueAddress()),
                    "UE addresses of all PDRs must match!");
            // Confirm the FAR IDs match
            checkArgument(uplinkPdr.localFarId() == uplinkFar.localFarId(),
                    "FAR ID of the uplink PDR and uplink FAR must match!");
            checkArgument(downlinkPdr.localFarId() == downlinkFar.localFarId(),
                    "FAR ID of the downlink PDR and downlink FAR must match!");
            // Confirm they're all the same session
            checkArgument(downlinkPdr.sessionId().equals(sessionId)
                            && uplinkFar.sessionId().equals(sessionId)
                            && downlinkFar.sessionId().equals(sessionId),
                    "All PDRs and FARs must belong to the same PFCP session!");

            PdrStats uplinkStats = null;
            PdrStats downlinkStats = null;
            for (PdrStats stats : statList) {
                if (stats.getCellId() == uplinkPdr.counterId()) {
                    uplinkStats = stats;
                } else if (stats.getCellId() == downlinkPdr.counterId()) {
                    downlinkStats = stats;
                }
            }
            checkNotNull(uplinkStats, "No PDR statistics provided for the uplink PDR!");
            checkNotNull(downlinkStats, "No PDR statistics provided for the downlink PDR!");


            // All the unknown PDRs should have the same UE address and belong to the same PFCP session
            for (PacketDetectionRule pdr : otherPdrs) {
                checkArgument(ueAddress.equals(pdr.ueAddress()));
                checkArgument(sessionId.equals(pdr.sessionId()));
            }
            // All the unknown FARs should belong to the same PFCP session
            for (ForwardingActionRule far : otherFars) {
                checkArgument(sessionId.equals(far.sessionId()));
            }

            return new UeSession(ueAddress, sessionId,
                    uplinkPdr, downlinkPdr,
                    uplinkFar, downlinkFar,
                    uplinkStats, downlinkStats,
                    otherPdrs, otherFars);
        }
    }
}
