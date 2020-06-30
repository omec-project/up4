/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.onosproject.up4;


import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip4Address;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;

import java.util.List;


public interface Up4Service {


    /**
     * A structure representing a GTP tunnel.
     */
    public class TunnelDesc {
        Ip4Address src;
        Ip4Address dst;
        int teid;

        public TunnelDesc(Ip4Address src, Ip4Address dst, int teid) {
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

        public PdrStats setIngress(long pkts, long bytes) {
            ingressPkts = pkts;
            ingressBytes = bytes;
            return this;
        }

        public PdrStats setEgress(long pkts, long bytes) {
            egressPkts = pkts;
            egressBytes = bytes;
            return this;
        }

        public PdrStats(int cellId) {
            this.cellId = cellId;
        }

        public static PdrStats of(int cellId) {
            return new PdrStats(cellId);
        }

    }

    /**
     * Get a list of devices available to ONOS that are configured with UP4-supporting pipeconfs.
     * @return A list of Up4-supporting devices.
     */
    List<Device> getAvailableDevices();

    /**
     * Read the the given cell (Counter index) of the PDR counters from the given device.
     * @param deviceId The device from which to read
     * @param cellId The counter cell index from which to read
     * @return A structure containing ingress and egress packet and byte counts for the given cellId.
     */
    PdrStats readCounter(DeviceId deviceId, int cellId);

    /**
     * Delete all entries installed by this app. Intended for debugging
     */
    void clearAllEntries();

    /**
     * Add a downlink PDR to the given device.
     * @param deviceId The target device on which to install the PDR
     * @param sessionId The PFCP Session ID that the PDR is from
     * @param ctrId The counter index that any packets that hit this PDR should use.
     * @param farId The FAR that packets should hit after hitting this PDR. Must belong to the same PFCP session.
     * @param ueAddr The IPv4 address of the UE for which this PDR should apply.
     */
    void addPdr(DeviceId deviceId, int sessionId, int ctrId, int farId, Ip4Address ueAddr);

    /**
     * Add an uplink PDR to the given device.
     * @param deviceId The target device on which to install the PDR
     * @param sessionId The PFCP Session ID that the PDR is from
     * @param ctrId The counter index that any packets that hit this PDR should use.
     * @param farId The FAR that packets should hit after hitting this PDR. Must belong to the same PFCP session.
     * @param ueAddr The IPv4 address of the UE for which this PDR should apply.
     * @param teid The GTP Tunnel ID for which this PDR should apply.
     * @param tunnelDst The GTP Tunnel endpoint for which this PDR should apply.
     */
    void addPdr(DeviceId deviceId, int sessionId, int ctrId, int farId,
                        Ip4Address ueAddr, int teid, Ip4Address tunnelDst);

    /**
     * Add a downlink FAR to the given device.
     * @param deviceId The target device on which to install the FAR
     * @param sessionId The PFCP Session ID that the FAR is from
     * @param farId PFCP Session-local FAR Identifier
     * @param drop Should this FAR drop packets?
     * @param notifyCp Should this FAR notify the Control Plane when a packet hits?
     * @param desc A description of the tunnel hit packets should be encapsulated with.
     */
    void addFar(DeviceId deviceId, int sessionId, int farId, boolean drop, boolean notifyCp, TunnelDesc desc);

    /**
     * Add a uplink FAR to the given device.
     * @param deviceId The target device on which to install the FAR
     * @param sessionId The PFCP Session ID that the FAR is from
     * @param farId PFCP Session-local FAR Identifier
     * @param drop Should this FAR drop packets?
     * @param notifyCp Should this FAR notify the Control Plane when a packet hits?
     */
    void addFar(DeviceId deviceId, int sessionId, int farId, boolean drop, boolean notifyCp);

    /**
     * Register a UE IPv4 address prefix with the interface lookup tables AKA the filtering stage.
     * @param deviceId The target device on which to install the interface lookup entry
     * @param poolPrefix The UE IPv4 address prefix
     */
    void addUePool(DeviceId deviceId, Ip4Prefix poolPrefix);

    /**
     * Register a S1U IPv4 address with the interface lookup tables AKA the filtering stage.
     * @param deviceId The target device on which to install the interface lookup entry
     * @param s1uAddr The S1U IPv4 address
     */
    void addS1uInterface(DeviceId deviceId, Ip4Address s1uAddr);

    /**
     * Remove a previously installed uplink PDR from the target device.
     * @param deviceId The target device.
     * @param ueAddr The UE IPv4 address that the PDR matches on
     * @param teid The GTP Tunnel ID that the PDR matches on
     * @param tunnelDst The GTP Tunnel destination that the PDR matches on
     */
    void removePdr(DeviceId deviceId, Ip4Address ueAddr, int teid, Ip4Address tunnelDst);

    /**
     * Remove a previously installed downlink PDR from the target device.
     * @param deviceId The target device.
     * @param ueAddr The UE IPv4 address that the PDR matches on
     */
    void removePdr(DeviceId deviceId, Ip4Address ueAddr);

    /**
     * Remove a previously installed FAR from the target device.
     * @param deviceId The target device.
     * @param sessionId The PFCP Session ID that owns the FAR
     * @param farId PFCP Session-local FAR Identifier
     */
    void removeFar(DeviceId deviceId, int sessionId, int farId);

    /**
     * Remove a previously installed UE IPv4 address prefix from the interface lookup tables AKA the filtering stage.
     * @param deviceId The target device from which the interface lookup entry should be removed
     * @param poolPrefix The UE IPv4 address prefix
     */
    void removeUePool(DeviceId deviceId, Ip4Prefix poolPrefix);

    /**
     * Remove a previously installed S1U IPv4 address from the interface lookup tables AKA the filtering stage.
     * @param deviceId The target device from which the interface lookup entry should be removed
     * @param s1uAddr The S1U IPv4 address
     */
    void removeS1uInterface(DeviceId deviceId, Ip4Address s1uAddr);

    /**
     * Remove a previously installed interface lookup table entry that can be either a UE pool or S1U address.
     * Useful if you only know the address of the interface and not what type of interface it is.
     * @param deviceId The target device from which the interface lookup entry should be removed
     * @param ifacePrefix The prefix or address of the interface entry.
     */
    void removeUnknownInterface(DeviceId deviceId, Ip4Prefix ifacePrefix);

}