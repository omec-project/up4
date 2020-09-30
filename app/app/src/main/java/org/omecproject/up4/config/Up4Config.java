/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */

package org.omecproject.up4.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.InvalidFieldException;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that represents the config expected from a UPF network configuration JSON block.
 */
public class Up4Config extends Config<ApplicationId> {
    // JSON keys to look for in the network config
    public static final String KEY = "up4";  // base key that signals the presence of this config
    public static final String DEVICE_ID = "deviceId";
    public static final String UE_POOLS = "uePools";
    public static final String S1U_PREFIX = "s1uPrefix";
    // Eventually, we will have to support multiple dbuf instances, in which case it might make more
    // sense to have a dedicated config class with an array of dbuf instances.
    public static final String DBUF_SERVICE_ADDR = "dbufServiceAddr";
    public static final String DBUF_DATAPLANE_ADDR = "dbufDataplaneAddr";

    @Override
    public boolean isValid() {
        return hasOnlyFields(DEVICE_ID, UE_POOLS, S1U_PREFIX, DBUF_SERVICE_ADDR, DBUF_DATAPLANE_ADDR) &&
                // Mandatory fields.
                hasFields(DEVICE_ID, UE_POOLS, S1U_PREFIX) &&
                !uePools().isEmpty() &&
                isDbufConfigValid();
    }

    private boolean isDbufConfigValid() {
        // Both fields must null or present and valid.
        if (dbufServiceAddr() == null && dbufDataplaneAddr() == null) {
            return true;
        }
        return hasFields(DBUF_SERVICE_ADDR, DBUF_DATAPLANE_ADDR) &&
                isValidAddrString(dbufServiceAddr(), false) &&
                isValidAddrString(dbufDataplaneAddr(), true);
    }

    /**
     * Gets the UP4 ONOS device ID.
     *
     * @return UP4 device ID
     */
    public DeviceId up4DeviceId() {
        return DeviceId.deviceId(object.path(DEVICE_ID).asText());
    }

    /**
     * Set the UP4 ONOS device ID.
     *
     * @param deviceId device ID
     * @return an updated instance of this config
     */
    public Up4Config setUp4DeviceId(String deviceId) {
        return (Up4Config) setOrClear(DEVICE_ID, deviceId);
    }


    /**
     * Get the S1U IPv4 prefix assigned to the device. Or null if not configured.
     *
     * @return The S1U IPv4 prefix assigned to the device
     */
    public Ip4Prefix s1uPrefix() {
        String prefix = get(S1U_PREFIX, null);
        return prefix != null ? Ip4Prefix.valueOf(prefix) : null;
    }

    /**
     * Set the S1U IPv4 prefix of the device.
     *
     * @param prefix The S1U IPv4 prefix to assign
     * @return an updated instance of this config
     */
    public Up4Config setS1uPrefix(String prefix) {
        return (Up4Config) setOrClear(S1U_PREFIX, prefix);
    }

    /**
     * Gets the list of UE IPv4 address pools assigned to the device. Or null if not configured.
     *
     * @return UE IPv4 address pools assigned to the device
     */
    public List<Ip4Prefix> uePools() {
        if (!object.has(UE_POOLS)) {
            return null;
        }
        List<Ip4Prefix> uePools = new ArrayList<>();
        ArrayNode uePoolsNode = (ArrayNode) object.path(UE_POOLS);
        for (JsonNode uePoolNode : uePoolsNode) {
            String uePoolString = uePoolNode.asText("");
            if (uePoolString.equals("")) {
                return null;
            }
            uePools.add(Ip4Prefix.valueOf(uePoolString));
        }
        return ImmutableList.copyOf(uePools);
    }

    /**
     * Returns the address of the dbuf service (in the form of host:port). Or null if not
     * configured.
     *
     * @return the address of the dbuf service
     */
    public String dbufServiceAddr() {
        return get(DBUF_SERVICE_ADDR, null);
    }

    /**
     * Returns the address of the dbuf dataplane interface (in the form of host:port). Or null if
     * not configured. The host part is guaranteed to be a valid IPv4 address.
     *
     * @return the address of the dbuf dataplane interface
     */
    public String dbufDataplaneAddr() {
        return get(DBUF_DATAPLANE_ADDR, null);
    }

    static boolean isValidAddrString(String addr, boolean mustBeIp4Addr) {
        if (addr.isBlank()) {
            throw new InvalidFieldException(addr, "address string cannot be blank");
        }
        final var pieces = addr.split(":");
        if (pieces.length != 2) {
            throw new InvalidFieldException(addr, "invalid address, must be host:port");
        }
        if (mustBeIp4Addr) {
            try {
                Ip4Address.valueOf(pieces[0]);
            } catch (IllegalArgumentException e) {
                throw new InvalidFieldException(addr, "invalid IPv4 address");
            }
        }
        try {
            final int port = Integer.parseInt(pieces[1]);
            if (port <= 0) {
                throw new InvalidFieldException(addr, "invalid port number");
            }
        } catch (NumberFormatException e) {
            throw new InvalidFieldException(addr, "invalid port number");
        }
        return true;
    }
}
