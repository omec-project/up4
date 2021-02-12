/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;

import java.util.Collection;


/**
 * UPF programmable behavior. Provides means to update device forwarding state to implement a 3GPP
 * User Plane Function (e.g., tunnel termination, accounting, etc.). An implementation of this API
 * should not write state directly to the device, but instead, always rely on core ONOS subsystems
 * (e.g., FlowRuleService, GroupService, etc).
 */
public interface UpfProgrammable {

    /**
     * Apps are expected to call this method as the first one when they are ready to install PDRs
     * and FARs.
     *
     * @param appId Application ID of the caller of this API.
     * @return True if initialized, false otherwise.
     */
    boolean init(ApplicationId appId);


    /**
     * Remove any state previously created by this API for the given application ID.
     *
     * @param appId Application ID of the application using the UpfProgrammable.
     */
    void cleanUp(ApplicationId appId);

    /**
     * Return the Device ID of the UPF-programmable device.
     *
     * @return the Device ID of the UPF-programmable device.
     */
    // FIXME: remove after we make interface a driver behavior
    // DeviceId will be implicit from behavior instance
    DeviceId deviceId();

    /**
     * Get all UE data flows currently installed on the UPF-programmable device.
     *
     * @return a collection of installed flows
     * @throws UpfProgrammableException if flows are unable to be read
     */
    Collection<UpfFlow> getFlows() throws UpfProgrammableException;

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
     * @throws UpfProgrammableException if FARs are unable to be read
     */
    Collection<ForwardingActionRule> getInstalledFars() throws UpfProgrammableException;

    /**
     * Get all PacketDetectionRules currently installed on the UPF-programmable device.
     *
     * @return a collection of installed PDRs
     * @throws UpfProgrammableException if PDRs are unable to be read
     */
    Collection<PacketDetectionRule> getInstalledPdrs() throws UpfProgrammableException;

    /**
     * Get all UPF interface lookup entries currently installed on the UPF-programmable device.
     *
     * @return a collection of installed interfaces
     * @throws UpfProgrammableException if interfaces are unable to be read
     */
    Collection<UpfInterface> getInstalledInterfaces() throws UpfProgrammableException;


    /**
     * Limit the number of UE flows that can be installed on the UPF. Intended for capacity testing.
     * If the limit given is larger than physical resources permit, the physical limit will be used.
     * A limit of 0 prevents any UE flow installations, and a limit of -1 removes limitations.
     *
     * @param ueLimit the maximum number of UEs that can have flows installed on the UPF
     */
    void setUeLimit(long ueLimit);

    /**
     * Add a Packet Detection Rule (PDR) to the given device.
     *
     * @param pdr The PDR to be added
     * @throws UpfProgrammableException if the PDR cannot be installed, or the counter index is out
     *                                  of bounds
     */
    void addPdr(PacketDetectionRule pdr) throws UpfProgrammableException;

    /**
     * Remove a previously installed Packet Detection Rule (PDR) from the target device.
     *
     * @param pdr The PDR to be removed
     * @throws UpfProgrammableException if the PDR cannot be found
     */
    void removePdr(PacketDetectionRule pdr) throws UpfProgrammableException;

    /**
     * Add a Forwarding Action Rule (FAR) to the given device.
     *
     * @param far The FAR to be added
     * @throws UpfProgrammableException if the FAR cannot be installed
     */
    void addFar(ForwardingActionRule far) throws UpfProgrammableException;

    /**
     * Remove a previously installed Forwarding Action Rule (FAR) from the target device.
     *
     * @param far The FAR to be removed
     * @throws UpfProgrammableException if the FAR cannot be found
     */
    void removeFar(ForwardingActionRule far) throws UpfProgrammableException;

    /**
     * Install a new interface on the UPF device's interface lookup tables.
     *
     * @param upfInterface the interface to install
     * @throws UpfProgrammableException if the interface cannot be installed
     */
    void addInterface(UpfInterface upfInterface) throws UpfProgrammableException;

    /**
     * Remove a previously installed UPF interface from the target device.
     *
     * @param upfInterface the interface to be removed
     * @throws UpfProgrammableException if the interface cannot be found
     */
    void removeInterface(UpfInterface upfInterface) throws UpfProgrammableException;

    /**
     * Read the the given cell (Counter index) of the PDR counters from the given device.
     *
     * @param cellId The counter cell index from which to read
     * @return A structure containing ingress and egress packet and byte counts for the given
     * cellId.
     * @throws UpfProgrammableException if the cell ID is out of bounds
     */
    PdrStats readCounter(int cellId) throws UpfProgrammableException;

    /**
     * Return the number of PDR counter cells available. The number of cells in the ingress and
     * egress PDR counters are equivalent.
     *
     * @return PDR counter size
     */
    long pdrCounterSize();

    /**
     * Return the number of maximum number of table entries the FAR table supports.
     *
     * @return the number of FARs that can be installed
     */
    long farTableSize();

    /**
     * Return the total number of table entries the downlink and uplink PDR tables support. Both
     * tables support an equal number of entries, so the total is twice the size of either.
     *
     * @return the total number of PDRs that can be installed
     */
    long pdrTableSize();

    /**
     * Read the counter contents for all cell indices that are valid on the hardware switch.
     *
     * @return A collection of counter values for all valid hardware counter cells
     * @throws UpfProgrammableException if the counters are unable to be read
     */
    Collection<PdrStats> readAllCounters() throws UpfProgrammableException;

    /**
     * Set the source and destination of the GTPU tunnel used to send packets to a dbuf buffering
     * device.
     *
     * @param switchAddr the address on the switch that sends and receives packets to and from dbuf
     * @param dbufAddr   the dataplane address of dbuf
     */
    void setDbufTunnel(Ip4Address switchAddr, Ip4Address dbufAddr);

    /**
     * Install a BufferDrainer reference that can be used to trigger the draining of a specific dbuf
     * buffer back into the UPF device.
     *
     * @param drainer the BufferDrainer reference
     */
    void setBufferDrainer(BufferDrainer drainer);

    /**
     * Used by the UpfProgrammable to trigger buffer draining as needed. Install an instance using
     * {@link UpfProgrammable#setBufferDrainer(BufferDrainer)}
     */
    interface BufferDrainer {
        /**
         * Drain the buffer that contains packets for the UE with the given address.
         *
         * @param ueAddr the address of the UE for which we should drain a buffer
         */
        void drain(Ip4Address ueAddr);
    }

}
