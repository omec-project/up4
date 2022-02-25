/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Class that represents the config expected from a UPF network configuration JSON block.
 */
public class Up4Config extends Config<ApplicationId> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    // JSON keys to look for in the network config
    public static final String KEY = "up4";  // base key that signals the presence of this config
    public static final String MAX_UES = "maxUes";
    public static final String DEVICE_ID = "deviceId"; // TODO: remove this field after all configs updated
    public static final String DEVICES = "devices";
    public static final String DBUF_DRAIN_ADDR = "dbufDrainAddr";
    public static final String PSC_ENCAP_ENABLED = "pscEncapEnabled";

    // Optional fields, if not provided pfcpiface is free to install the corresponding interface entry.
    // TODO: remove these fields?
    public static final String UE_POOLS = "uePools";
    public static final String S1U_ADDR = "s1uAddr";
    public static final String N3_ADDR = "n3Addr";
    // Must be provided if using uePool, s1uAddr or n3Addr to configure N3 and N6 interfaces
    public static final String SLICE_ID = "sliceId";

    @Override
    public boolean isValid() {
        return hasOnlyFields(DEVICE_ID, DEVICES, UE_POOLS, S1U_ADDR, N3_ADDR,
                             DBUF_DRAIN_ADDR, MAX_UES, PSC_ENCAP_ENABLED,
                             SLICE_ID) &&
                // Mandatory fields.
                (hasField(DEVICE_ID) || hasField(DEVICES)) &&
                isSliceIdFieldRequired() &&
                !upfDeviceIds().isEmpty() &&
                isDbufConfigValid();
    }

    private boolean isSliceIdFieldRequired() {
        if (hasField(UE_POOLS) || hasField(S1U_ADDR) || hasField(N3_ADDR)) {
            return hasField(SLICE_ID);
        }
        return true;
    }

    private boolean isDbufConfigValid() {
        if (dbufDrainAddr() != null) {
            try {
                // Force the drain address string to be parsed
                dbufDrainAddr();
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the UPF device IDs.
     *
     * @return UPF device IDs
     */
    public List<DeviceId> upfDeviceIds() {
        if (hasField(DEVICES)) {
            List<DeviceId> deviceIds = Lists.newArrayList();
            ArrayNode devices = (ArrayNode) object.path(DEVICES);
            for (JsonNode deviceId : devices) {
                String deviceIdString = deviceId.asText("");
                if (!deviceIdString.equals("") && !deviceIds.contains(DeviceId.deviceId(deviceIdString))) {
                    deviceIds.add(DeviceId.deviceId(deviceIdString));
                }
            }
            return ImmutableList.copyOf(deviceIds);
        } else {
            return ImmutableList.of(DeviceId.deviceId(object.path(DEVICE_ID).asText()));
        }
    }

    /**
     * Get the N3 IPv4 address assigned to the device.
     *
     * @return The N3 IPv4 address assigned to the device or emtpy if not configured.
     */
    public Optional<Ip4Address> n3Address() {
        if (hasField(S1U_ADDR)) {
            String addr = get(S1U_ADDR, null);
            return addr != null ? Optional.of(Ip4Address.valueOf(addr)) : Optional.empty();
        }
        if (hasField(N3_ADDR)) {
            String addr = get(N3_ADDR, null);
            return addr != null ? Optional.of(Ip4Address.valueOf(addr)) : Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Get the slice ID configured in the netcfg. This is used only if N3 address
     * or UE IPv4 address pool fields are provided in the netcfg.
     *
     * @return The slice ID or empty if not configured.
     */
    public Optional<Integer> sliceId() {
        if (hasField(SLICE_ID)) {
            String sliceId = get(SLICE_ID, null);
            return sliceId != null ? Optional.of(Integer.valueOf(sliceId)) : Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Gets the list of UE IPv4 address pools assigned to the device.
     *
     * @return UE IPv4 address pools assigned to the device or empty list if not configured
     */
    public List<Ip4Prefix> uePools() {
        if (!object.has(UE_POOLS)) {
            return ImmutableList.of();
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
     * Returns the address of the UPF interface that the dbuf device will drain packets towards, or null
     * if not configured.
     *
     * @return the address of the upf interface that receives packets drained from dbuf
     */
    public Ip4Address dbufDrainAddr() {
        String addr = get(DBUF_DRAIN_ADDR, null);
        return addr != null ? Ip4Address.valueOf(addr) : null;
    }

    /**
     * Returns the maximum number of UEs the UPF can support, or -1 if not configured.
     *
     * @return the maximum number of UEs the UPF can support
     */
    public long maxUes() {
        return get(MAX_UES, -1);
    }

    /**
     * Returns whether the UPF should use GTP-U extension PDU Session Container when doing encap of
     * downlink packets.
     *
     * @return whether PSC encap is enabled
     */
    public boolean pscEncapEnabled() {
        return get(PSC_ENCAP_ENABLED, false);
    }

    /**
     * Enable or disable the PDU Session Container field.
     *
     * @param enabled True if enable PDU Session Container, False otherwise
     */
    public void setPscEncap(boolean enabled) {
        setOrClear(PSC_ENCAP_ENABLED, enabled);
    }
}

