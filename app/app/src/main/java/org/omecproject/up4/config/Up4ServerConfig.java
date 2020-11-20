/*
SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
*/
package org.omecproject.up4.config;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

/**
 * Class that represents the config expected from a UP4 logical switch network configuration JSON
 * block. Currently unused. Planned to be used soon
 */
public class Up4ServerConfig extends Config<ApplicationId> {
  // JSON keys to look for in the network config
  public static final String KEY = "up4server"; // base key that signals the presence of this config
  public static final String P4RUNTIME_DEVICE_ID = "p4RuntimeDeviceId";
  public static final String GRPC_PORT = "grpcPort";

  @Override
  public boolean isValid() {
    return hasOnlyFields(GRPC_PORT, P4RUNTIME_DEVICE_ID)
        && p4RuntimeDeviceId() != -1
        && grpcPort() != -1;
  }

  /**
   * Get the deviceID that the UP4 logical switch p4runtime server will expect clients to use.
   *
   * @return UP4 logical switch's P4runtime Device ID
   */
  public int p4RuntimeDeviceId() {
    return get(P4RUNTIME_DEVICE_ID, -1);
  }

  /**
   * Set the deviceID that the UP4 logical switch p4runtime server will expect clients to use.
   *
   * @param deviceId UP4 logical switch's P4runtime Device ID
   * @return an updated instance of this config
   */
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

  /**
   * Set the port on which the p4runtime gRPC server of the logical UP4 switch will listen for
   * clients.
   *
   * @param grpcPort p4runtime server port
   * @return an updated instance of this config
   */
  public Up4ServerConfig setGrpcPort(int grpcPort) {
    return (Up4ServerConfig) setOrClear(GRPC_PORT, grpcPort);
  }
}
