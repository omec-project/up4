package org.omecproject.upf.config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.Config;

import java.util.ArrayList;
import java.util.List;

public class UpfConfig extends Config<ApplicationId> {
    public static final String KEY = "upf";

    public static final String DEVICE_ID = "deviceId";

    public static final String UE_POOLS = "uePools";

    public static final String S1U_PREFIX = "s1uPrefix";

    @Override
    public boolean isValid() {
        return hasOnlyFields(DEVICE_ID, UE_POOLS, S1U_PREFIX) &&
                upfDeviceId() != null &&
                s1uPrefix() != null &&
                uePools() != null && !uePools().isEmpty();

    }

    /**
     * Gets the UPF device ID.
     *
     * @return UPF device ID
     */
    public DeviceId upfDeviceId() {
        return DeviceId.deviceId(object.path(DEVICE_ID).asText());
    }

    public UpfConfig setUpfDeviceId(String deviceId) {
        return (UpfConfig) setOrClear(DEVICE_ID, deviceId);
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
     * Set the S1U IPv4 prefix of the device.
     * @param prefix The S1U IPv4 prefix to assign
     * @return the config of the device
     */
    public UpfConfig setS1uPrefix(String prefix) {
        return (UpfConfig) setOrClear(S1U_PREFIX, prefix);
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
