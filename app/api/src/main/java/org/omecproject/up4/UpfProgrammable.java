/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
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
     * Get all UE sessions currently installed on the UPF-programmable device.
     *
     * @return a collection of installed Sessions
     */
    Collection<UeSession> getSessions();

    /**
     * Remove all UE sessions currently installed on the UPF-programmable device.
     */
    void clearSessions();

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
     */
    void addPdr(PacketDetectionRule pdr);

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
     * Register a UE IPv4 address prefix with the interface lookup tables AKA the filtering stage.
     *
     * @param poolPrefix The UE IPv4 address prefix
     */
    void addUePool(Ip4Prefix poolPrefix);

    /**
     * Register a S1U IPv4 address with the interface lookup tables AKA the filtering stage.
     *
     * @param s1uAddr The S1U IPv4 address
     */
    void addS1uInterface(Ip4Address s1uAddr);

    /**
     * Remove a previously installed UE IPv4 address prefix from the interface lookup tables AKA the filtering stage.
     *
     * @param poolPrefix The UE IPv4 address prefix
     */
    void removeUePool(Ip4Prefix poolPrefix);

    /**
     * Remove a previously installed S1U IPv4 address from the interface lookup tables AKA the filtering stage.
     *
     * @param s1uAddr The S1U IPv4 address
     */
    void removeS1uInterface(Ip4Address s1uAddr);

    /**
     * Remove a previously installed interface lookup table entry that can be either a UE pool or S1U address.
     * Useful if you only know the address of the interface and not what type of interface it is.
     *
     * @param ifacePrefix The prefix or address of the interface entry.
     */
    void removeUnknownInterface(Ip4Prefix ifacePrefix);

    /**
     * Read the the given cell (Counter index) of the PDR counters from the given device.
     *
     * @param cellId The counter cell index from which to read
     * @return A structure containing ingress and egress packet and byte counts for the given cellId.
     */
    PdrStats readCounter(int cellId);
}
