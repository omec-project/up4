/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Service;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;

/**
 * UP4 PDR insertion command.
 */
@Service
@Command(scope = "up4", name = "pdr-insert",
        description = "Insert a packet detection rule into the UP4 dataplane")
public class PdrInsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "uri", description = "Device ID",
            required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String uri = null;

    @Argument(index = 1, name = "session-id",
            description = "Session ID for this PDR",
            required = true, multiValued = false)
    long sessionId = 0;

    @Argument(index = 2, name = "ue-ipv4-addr",
            description = "Address of the UE for which this PDR applies",
            required = true, multiValued = false)
    String ueAddr = null;

    @Argument(index = 3, name = "far-id",
            description = "ID of the FAR that should apply if this PDR matches",
            required = true, multiValued = false)
    int farId = 0;

    @Argument(index = 4, name = "teid",
            description = "Tunnel ID of the tunnel to/from the base station",
            required = false, multiValued = false)
    int teid = -1;

    @Argument(index = 5, name = "s1u-ip",
            description = "IP address of the S1U interface (endpoint of the tunnel to the base station)",
            required = false, multiValued = false)
    String s1uAddr = null;

    @Override
    protected void doExecute() {
        DeviceService deviceService = get(DeviceService.class);
        Up4Service app = get(Up4Service.class);

        Device device = deviceService.getDevice(DeviceId.deviceId(uri));
        if (device == null) {
            print("Device \"%s\" is not found", uri);
            return;
        }

        Ip4Address ueAddr = Ip4Address.valueOf(this.ueAddr);

        var pdrBuilder = PacketDetectionRule.builder()
                .withSessionId(sessionId)
                .withUeAddr(ueAddr)
                .withFarId(farId)
                .withCounterId(1);

        String directionString = "Downlink";
        if (teid != -1 || s1uAddr != null) {
            directionString = "Uplink";
            if (teid == -1) {
                print("TEID must be provided with the S1U IP address");
                return;
            }
            if (s1uAddr == null) {
                print("S1U IP address must be provided with the TEID.");
                return;
            }

            pdrBuilder.withTeid(teid)
                    .withTunnelDst(Ip4Address.valueOf(this.s1uAddr));
        }
        print("Installing *%s* PDR on device %s", directionString, uri);
        app.getUpfProgrammable().addPdr(pdrBuilder.build());
    }
}
