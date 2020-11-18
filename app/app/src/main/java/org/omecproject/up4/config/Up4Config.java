/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that represents the config expected from a UPF network configuration JSON block.
 */
public class Up4Config extends Config<ApplicationId> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    // JSON keys to look for in the network config
    public static final String KEY = "up4";  // base key that signals the presence of this config
    public static final String DEVICE_ID = "deviceId";
    public static final String UE_POOLS = "uePools";
    public static final String S1U_PREFIX = "s1uPrefix";  // TODO: remove this field after all configs updated
    public static final String S1U_ADDR = "s1uAddr";
    // Eventually, we will have to support multiple dbuf instances, in which case it might make more
    // sense to have a dedicated config class with an array of dbuf instances.
    public static final String DBUF_SERVICE_ADDR = "dbufServiceAddr";
    public static final String DBUF_DATAPLANE_ADDR = "dbufDataplaneAddr";
    public static final String DBUF_DRAIN_ADDR = "dbufDrainAddr";

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

    @Override
    public boolean isValid() {
        return hasOnlyFields(DEVICE_ID, UE_POOLS, S1U_ADDR, S1U_PREFIX, DBUF_SERVICE_ADDR,
                DBUF_DATAPLANE_ADDR, DBUF_DRAIN_ADDR) &&
                // Mandatory fields.
                hasFields(DEVICE_ID, UE_POOLS) &&
                (hasField(S1U_ADDR) || hasField(S1U_PREFIX)) &&
                !uePools().isEmpty() &&
                isDbufConfigValid();
    }

    private boolean isDbufConfigValid() {
        // Both fields must null or present and valid.
        if (dbufServiceAddr() == null && dbufDataplaneAddr() == null && dbufDrainAddr() == null) {
            return true;
        }
        try {
            // Force the drain address string to be parsed
            dbufDrainAddr();
        } catch (IllegalArgumentException e) {
            return false;
        }

        return hasFields(DBUF_SERVICE_ADDR, DBUF_DATAPLANE_ADDR, DBUF_DRAIN_ADDR) &&
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
     * Get the S1U IPv4 address assigned to the device. Or null if not properly configured.
     *
     * @return The S1U IPv4 address assigned to the device
     */
    public Ip4Address s1uAddress() {
        if (hasField(S1U_ADDR)) {
            String addr = get(S1U_ADDR, null);
            return addr != null ? Ip4Address.valueOf(addr) : null;
        } else if (hasField(S1U_PREFIX)) {
            // TODO: remove this whole block after all network configs have been updated
            log.warn("UP4 config field {} has been replaced by {}, please update your config!",
                    S1U_PREFIX, S1U_ADDR);
            String prefix = get(S1U_PREFIX, null);
            if (prefix == null) {
                return null;
            }
            try {
                // Try converting to a prefix just to check that the format is correct
                Ip4Prefix.valueOf(prefix);
            } catch (Exception e) {
                return null;
            }
            // We can't do Ip4Prefix.address() because it masks off the host bits
            String[] pieces = prefix.split("/");
            return Ip4Address.valueOf(pieces[0]);
        } else {
            return null;
        }
    }

    /**
     * Set the S1U IPv4 address of the device.
     *
     * @param addr The S1U IPv4 address to assign
     * @return an updated instance of this config
     */
    public Up4Config setS1uAddr(String addr) {
        return (Up4Config) setOrClear(S1U_ADDR, addr);
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

    /**
     * Returns the address of the UPF interface that the dbuf device will drain packets towards, or null
     * if not configured.
     *
     * @return the address of the upf interface that receives packets drained from dbuf
     */
    public Ip4Address dbufDrainAddr() {
        String addr = get(DBUF_DRAIN_ADDR, null);
        return addr != null ? Ip4Address.valueOf(addr) : null;
    }
}

