package org.omecproject.upf.config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.Config;

import java.util.ArrayList;
import java.util.List;

public class UpfDeviceConfig extends Config<DeviceId> {
    public static final String KEY = "upf";

    public static final String P4RUNTIME_DEVICE_ID = "p4RuntimeDeviceId";

    public static final String UE_POOLS = "uePools";

    public static final String S1U_PREFIX = "s1uPrefix";

    @Override
    public boolean isValid() {
        return hasOnlyFields(P4RUNTIME_DEVICE_ID, UE_POOLS, S1U_PREFIX) &&
                s1uPrefix() != null &&
                uePools() != null && !uePools().isEmpty();

    }

    public int p4RuntimeDeviceId() {
        return get(P4RUNTIME_DEVICE_ID, -1);
    }

    public UpfDeviceConfig setP4RuntimeDeviceId(int deviceId) {
        return (UpfDeviceConfig) setOrClear(P4RUNTIME_DEVICE_ID, deviceId);
    }

    /**
     * Get the S1U IPv4 prefix assigned to the device. Or null if not configured.
     * @return The S1U IPv4 prefix assigned to the device
     */
    public Ip4Prefix s1uPrefix() {
        String prefix = get(S1U_PREFIX, null);
        return prefix != null ? Ip4Prefix.valueOf(prefix) : null;
    }

    /**
     * Set the S1U IPv4 prefix of the device
     * @param prefix The S1U IPv4 prefix to assign
     * @return the config of the device
     */
    public UpfDeviceConfig setS1uPrefix(String prefix) {
        return (UpfDeviceConfig) setOrClear(S1U_PREFIX, prefix);
    }

    /**
     * Gets the list of UE IPv4 address pools assigned to the device. Or null if not configured.
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
            if (uePoolString == "") {
                return null;
            }
            uePools.add(Ip4Prefix.valueOf(uePoolString));
        }
        return ImmutableList.copyOf(uePools);
    }
}
