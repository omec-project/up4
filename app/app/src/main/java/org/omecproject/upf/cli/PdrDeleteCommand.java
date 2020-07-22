/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.upf.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.upf.PacketDetectionRule;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.omecproject.upf.UpfService;

/**
 * UPF PDR deletion command.
 */
@Service
@Command(scope = "up4", name = "pdr-delete",
         description = "Delete a packet detection rule from the UPF dataplane")
public class PdrDeleteCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "uri", description = "Device ID",
              required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String uri = null;

    @Argument(index = 1, name = "ue-ipv4-addr",
            description = "Address of the UE for which this PDR applies",
            required = true, multiValued = false)
    String ueAddr = null;

    @Argument(index = 2, name = "teid",
            description = "Tunnel ID of the tunnel to/from the base station",
            required = false, multiValued = false)
    int teid = -1;

    @Argument(index = 3, name = "s1u-ip",
            description = "IP address of the S1U interface (endpoint of the tunnel to the base station)",
            required = false, multiValued = false)
    String s1uAddr = null;

    @Override
    protected void doExecute() {
        DeviceService deviceService = get(DeviceService.class);
        UpfService app = get(UpfService.class);

        Device device = deviceService.getDevice(DeviceId.deviceId(uri));
        if (device == null) {
            print("Device \"%s\" is not found", uri);
            return;
        }

        var pdrBuilder = PacketDetectionRule.builder()
                .withUeAddr(Ip4Address.valueOf(this.ueAddr));

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
        print("Removing *%s* PDR from device %s", directionString, uri);
        app.getUpfProgrammable().removePdr(pdrBuilder.build());


    }

}
