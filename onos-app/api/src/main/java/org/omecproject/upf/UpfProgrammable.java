package org.omecproject.upf;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;


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
     * @param appId Application ID of the caller of this API.
     * @param deviceId Device ID of the device that is to be the UpfProgrammable
     * @return True if initialized, false otherwise.
     */
    boolean init(ApplicationId appId, DeviceId deviceId);

    /**
     * Remove any state previously created by this API for the given application
     * ID.
     *
     * @param appId Application ID of the application using the
     *              UpfProgrammable.
     */
    void cleanUp(ApplicationId appId);


    /**
     * Return the Device ID of the UPF-programmable device.
     * @return the Device ID of the UPF-programmable device.
     */
    DeviceId deviceId();

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
    void addFar(ImmutableByteSequence sessionId, int farId, boolean drop, boolean notifyCp, GtpTunnel desc);

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

    /**
     * Read the the given cell (Counter index) of the PDR counters from the given device.
     * @param cellId The counter cell index from which to read
     * @return A structure containing ingress and egress packet and byte counts for the given cellId.
     */
    PdrStats readCounter(int cellId);
}
