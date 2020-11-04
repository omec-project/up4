/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;

import java.util.Collection;


/**
 * UPF programmable behavior. Provides means to update device forwarding state
 * to implement a 3GPP User Plane Function (e.g., tunnel termination, accounting,
 * etc.). An implementation of this API should not write state directly to the
 * device, but instead, always rely on core ONOS subsystems (e.g.,
 * FlowRuleService, GroupService, etc).
 */
public interface UpfProgrammable {

    /**
     * Apps are expected to call this method as the first one when they are
     * ready to install PDRs and FARs.
     *
     * @param appId    Application ID of the caller of this API.
     * @param deviceId Device ID of the device that is to be the UpfProgrammable
     * @return True if initialized, false otherwise.
     */
    boolean init(ApplicationId appId, DeviceId deviceId);


    /**
     * Remove any state previously created by this API for the given application
     * ID.
     *
     * @param appId Application ID of the application using the UpfProgrammable.
     */
    void cleanUp(ApplicationId appId);

    /**
     * Return the Device ID of the UPF-programmable device.
     *
     * @return the Device ID of the UPF-programmable device.
     */
    DeviceId deviceId();

    /**
     * Get all UE data flows currently installed on the UPF-programmable device.
     *
     * @return a collection of installed flows
     */
    Collection<UpfFlow> getFlows();

    /**
     * Remove all interfaces currently installed on the UPF-programmable device.
     */
    void clearInterfaces();

    /**
     * Remove all UE data flows currently installed on the UPF-programmable device.
     */
    void clearFlows();

    /**
     * Get all ForwardingActionRules currently installed on the UPF-programmable device.
     *
     * @return a collection of installed FARs
     */
    Collection<ForwardingActionRule> getInstalledFars();

    /**
     * Get all PacketDetectionRules currently installed on the UPF-programmable device.
     *
     * @return a collection of installed PDRs
     */
    Collection<PacketDetectionRule> getInstalledPdrs();

    /**
     * Get all UPF interface lookup entries currently installed on the UPF-programmable device.
     *
     * @return a collection of installed interfaces
     */
    Collection<UpfInterface> getInstalledInterfaces();

    /**
     * Add a Packet Detection Rule (PDR) to the given device.
     *
     * @param pdr The PDR to be added
     * @throws IndexOutOfBoundsException if the PDR references an out-of-bounds counter cell index
     */
    void addPdr(PacketDetectionRule pdr) throws IndexOutOfBoundsException;

    /**
     * Remove a previously installed Packet Detection Rule (PDR) from the target device.
     *
     * @param pdr The PDR to be removed
     */
    void removePdr(PacketDetectionRule pdr);

    /**
     * Add a Forwarding Action Rule (FAR) to the given device.
     *
     * @param far The FAR to be added
     */
    void addFar(ForwardingActionRule far);

    /**
     * Remove a previously installed Forwarding Action Rule (FAR) from the target device.
     *
     * @param far The FAR to be removed
     */
    void removeFar(ForwardingActionRule far);

    /**
     * Install a new interface on the UPF device's interface lookup tables.
     *
     * @param upfInterface the interface to install
     */
    void addInterface(UpfInterface upfInterface);

    /**
     * Remove a previously installed UPF interface from the target device.
     *
     * @param upfInterface the interface to be removed
     */
    void removeInterface(UpfInterface upfInterface);

    /**
     * Read the the given cell (Counter index) of the PDR counters from the given device.
     *
     * @param cellId The counter cell index from which to read
     * @return A structure containing ingress and egress packet and byte counts for the given cellId.
     * @throws IndexOutOfBoundsException if the cell ID is out of bounds
     */
    PdrStats readCounter(int cellId) throws IndexOutOfBoundsException;

    /**
     * Return the number of PDR counter cells available. The number of cells in the ingress and egress PDR counters
     * are equivalent.
     *
     * @return PDR counter size
     */
    int pdrCounterSize();

    /**
     * Read the counter contents for all cell indices currently referenced by any installed PDRs.
     *
     * @return A collection of counter values for all currently used cell indices
     */
    Collection<PdrStats> readAllCounters();

    /**
     * Set the source and destination of the GTPU tunnel used to send packets to a dbuf buffering device.
     *
     * @param switchAddr the address on the switch that sends and receives packets to and from dbuf
     * @param dbufAddr   the dataplane address of dbuf
     */
    void setDbufTunnel(Ip4Address switchAddr, Ip4Address dbufAddr);

    /**
     * Install a BufferDrainer reference that can be used to trigger the draining of a specific dbuf buffer
     * back into the UPF device.
     *
     * @param drainer the BufferDrainer reference
     */
    void setBufferDrainer(BufferDrainer drainer);

    /**
     * Used by the UpfProgrammable to trigger buffer draining as needed.
     * Install an instance using {@link UpfProgrammable#setBufferDrainer(BufferDrainer)}
     */
    interface BufferDrainer {
        /**
         * Drain the buffer that contains packets for the UE with the given address.
         *
         * @param ueAddr the address of the UE for which we should drain a buffer
         */
        public void drain(Ip4Address ueAddr);
    }
}
