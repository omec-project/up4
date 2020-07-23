/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.GtpTunnel;
import org.omecproject.up4.Up4Service;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;

/**
 * UP4 Far insertion command.
 */
@Service
@Command(scope = "up4", name = "far-insert",
        description = "Insert a forwarding action rule into the UP4 dataplane")
public class FarInsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "uri", description = "Device ID",
            required = true)
    @Completion(DeviceIdCompleter.class)
    String uri = null;

    @Argument(index = 1, name = "session-id",
            description = "Session ID for this PDR",
            required = true)
    long sessionId = 0;

    @Argument(index = 2, name = "far-id",
            description = "ID of the FAR",
            required = true)
    int farId = 0;

    @Argument(index = 3, name = "teid",
            description = "Teid of the tunnel the packet should enter",
            required = false)
    int teid = -1;

    @Argument(index = 4, name = "tunnel-src",
            description = "Src IP of the tunnel the packet should enter",
            required = false)
    String tunnelSrc = null;

    @Argument(index = 5, name = "tunnel-dst",
            description = "Dst IP of the tunnel the packet should enter",
            required = false)
    String tunnelDst = null;

    @Override
    protected void doExecute() {
        DeviceService deviceService = get(DeviceService.class);
        Up4Service app = get(Up4Service.class);

        Device device = deviceService.getDevice(DeviceId.deviceId(uri));
        if (device == null) {
            print("Device \"%s\" is not found", uri);
            return;
        }

        var farBuilder = ForwardingActionRule.builder()
                .withFarId(farId)
                .withSessionId(sessionId)
                .withFlags(false, false);

        String directionString = "Uplink";
        if (teid != -1) {
            directionString = "Downlink";
            if (tunnelSrc == null || tunnelDst == null) {
                print("Tunnel source and destination IPs must be provided with TEID");
                return;
            }

            Ip4Address tunnelDst = Ip4Address.valueOf(this.tunnelDst);
            Ip4Address tunnelSrc = Ip4Address.valueOf(this.tunnelSrc);
            GtpTunnel tunnel = new GtpTunnel(tunnelSrc, tunnelDst,
                    ImmutableByteSequence.copyFrom(teid));

            farBuilder.withTunnel(tunnel);
        }
        print("Installing *%s* FAR on device %s", directionString, uri);
        app.getUpfProgrammable().addFar(farBuilder.build());
    }
}
