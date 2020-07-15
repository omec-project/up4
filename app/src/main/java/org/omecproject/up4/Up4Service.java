/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;


import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.Device;

import java.util.List;

/**
 * Presents a high-level way to install dataplane table entries into the UPF extension of the fabric pipeline.
 */
public interface Up4Service {
    /**
     * A structure representing a GTP tunnel.
     */
    public class TunnelDesc {
        Ip4Address src;
        Ip4Address dst;
        ImmutableByteSequence teid;

        public TunnelDesc(Ip4Address src, Ip4Address dst, ImmutableByteSequence teid) {
            this.src = src;
            this.dst = dst;
            this.teid = teid;
        }
    }

    /**
     * A structure for compactly passing PDR counter values for a given counter ID.
     * Contains four counts: Ingress Packets, Ingress Bytes, Egress Packets, Egress Bytes
     */
    public class PdrStats {
        public int cellId;
        public long ingressPkts;
        public long ingressBytes;
        public long egressPkts;
        public long egressBytes;

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

        public static Builder builder(int cellId) {
            return new Builder(cellId);
        }


        public static class Builder {
            public int cellId;
            public long ingressPkts;
            public long ingressBytes;
            public long egressPkts;
            public long egressBytes;
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

    /**
     * Get a list of devices available to ONOS that are configured with UP4-supporting pipeconfs.
     * @return A list of Up4-supporting devices.
     */
    List<Device> getAvailableDevices();

    /**
     * Read the the given cell (Counter index) of the PDR counters from the given device.
     * @param cellId The counter cell index from which to read
     * @return A structure containing ingress and egress packet and byte counts for the given cellId.
     */
    PdrStats readCounter(int cellId);

    /**
     * Delete all entries installed by this app. Intended for debugging
     */
    void clearAllEntries();

    /**
     * Add a downlink PDR to the given device.
     * @param sessionId The PFCP Session ID that the PDR is from
     * @param ctrId The counter index that any packets that hit this PDR should use.
     * @param farId The FAR that packets should hit after hitting this PDR. Must belong to the same PFCP session.
     * @param ueAddr The IPv4 address of the UE for which this PDR should apply.
     */
    void addPdr(ImmutableByteSequence sessionId, int ctrId, int farId, Ip4Address ueAddr);

    /**
     * Add an uplink PDR to the given device.
     * @param sessionId The PFCP Session ID that the PDR is from
     * @param ctrId The counter index that any packets that hit this PDR should use.
     * @param farId The FAR that packets should hit after hitting this PDR. Must belong to the same PFCP session.
     * @param ueAddr The IPv4 address of the UE for which this PDR should apply.
     * @param teid The GTP Tunnel ID for which this PDR should apply.
     * @param tunnelDst The GTP Tunnel endpoint for which this PDR should apply.
     */
    void addPdr(ImmutableByteSequence sessionId, int ctrId, int farId,
                Ip4Address ueAddr, ImmutableByteSequence teid, Ip4Address tunnelDst);

    /**
     * Add a downlink FAR to the given device.
     * @param sessionId The PFCP Session ID that the FAR is from
     * @param farId PFCP Session-local FAR Identifier
     * @param drop Should this FAR drop packets?
     * @param notifyCp Should this FAR notify the Control Plane when a packet hits?
     * @param desc A description of the tunnel hit packets should be encapsulated with.
     */
    void addFar(ImmutableByteSequence sessionId, int farId, boolean drop, boolean notifyCp, TunnelDesc desc);

    /**
     * Add a uplink FAR to the given device.
     * @param sessionId The PFCP Session ID that the FAR is from
     * @param farId PFCP Session-local FAR Identifier
     * @param drop Should this FAR drop packets?
     * @param notifyCp Should this FAR notify the Control Plane when a packet hits?
     */
    void addFar(ImmutableByteSequence sessionId, int farId, boolean drop, boolean notifyCp);

    /**
     * Register a UE IPv4 address prefix with the interface lookup tables AKA the filtering stage.
     * @param poolPrefix The UE IPv4 address prefix
     */
    void addUePool(Ip4Prefix poolPrefix);

    /**
     * Register a S1U IPv4 address with the interface lookup tables AKA the filtering stage.
     * @param s1uAddr The S1U IPv4 address
     */
    void addS1uInterface(Ip4Address s1uAddr);

    /**
     * Remove a previously installed uplink PDR from the target device.
     * @param ueAddr The UE IPv4 address that the PDR matches on
     * @param teid The GTP Tunnel ID that the PDR matches on
     * @param tunnelDst The GTP Tunnel destination that the PDR matches on
     */
    void removePdr(Ip4Address ueAddr, ImmutableByteSequence teid, Ip4Address tunnelDst);

    /**
     * Remove a previously installed downlink PDR from the target device.
     * @param ueAddr The UE IPv4 address that the PDR matches on
     */
    void removePdr(Ip4Address ueAddr);

    /**
     * Remove a previously installed FAR from the target device.
     * @param sessionId The PFCP Session ID that owns the FAR
     * @param farId PFCP Session-local FAR Identifier
     */
    void removeFar(ImmutableByteSequence sessionId, int farId);

    /**
     * Remove a previously installed UE IPv4 address prefix from the interface lookup tables AKA the filtering stage.
     * @param poolPrefix The UE IPv4 address prefix
     */
    void removeUePool(Ip4Prefix poolPrefix);

    /**
     * Remove a previously installed S1U IPv4 address from the interface lookup tables AKA the filtering stage.
     * @param s1uAddr The S1U IPv4 address
     */
    void removeS1uInterface(Ip4Address s1uAddr);

    /**
     * Remove a previously installed interface lookup table entry that can be either a UE pool or S1U address.
     * Useful if you only know the address of the interface and not what type of interface it is.
     * @param ifacePrefix The prefix or address of the interface entry.
     */
    void removeUnknownInterface(Ip4Prefix ifacePrefix);

}