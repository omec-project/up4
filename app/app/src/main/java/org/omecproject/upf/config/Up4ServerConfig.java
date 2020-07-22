package org.omecproject.upf.config;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;


public class Up4ServerConfig extends Config<ApplicationId> {
    public static final String KEY = "up4server";

    public static final String P4RUNTIME_DEVICE_ID = "p4RuntimeDeviceId";

    public static final String GRPC_PORT = "grpcPort";

    @Override
    public boolean isValid() {
        return hasOnlyFields(GRPC_PORT, P4RUNTIME_DEVICE_ID) &&
                p4RuntimeDeviceId() != -1 &&
                grpcPort() != -1;

    }

    /**
     * Get the deviceID that the UP4 logical switch p4runtime server will expect clients to use.
     *
     * @return UP4 logical switch's P4runtime Device ID
     */
    public int p4RuntimeDeviceId() {
        return get(P4RUNTIME_DEVICE_ID, -1);
    }

    public Up4ServerConfig setP4RuntimeDeviceId(int deviceId) {
        return (Up4ServerConfig) setOrClear(P4RUNTIME_DEVICE_ID, deviceId);
    }

    /**
     * Get the deviceID that the UP4 logical switch p4runtime server will expect clients to use.
     *
     * @return UP4 logical switch's P4runtime Device ID
     */
    public int grpcPort() {
        return get(GRPC_PORT, -1);
    }

    public Up4ServerConfig setGrpcPort(int deviceId) {
        return (Up4ServerConfig) setOrClear(GRPC_PORT, deviceId);
    }
}
