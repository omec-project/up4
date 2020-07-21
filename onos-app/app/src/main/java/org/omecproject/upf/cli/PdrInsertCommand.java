/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.upf.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.upf.Up4Service;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;

/**
 * UPF PDR insertion command.
 */
@Service
@Command(scope = "up4", name = "pdr-insert",
         description = "Insert a packet detection rule into the UPF dataplane")
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

        if (teid != -1 || s1uAddr != null) {
            if (teid == -1) {
                print("TEID must be provided with the S1U IP address");
                return;
            }
            if (s1uAddr == null) {
                print("S1U IP address must be provided with the TEID.");
                return;
            }
            Ip4Address s1uAddr = Ip4Address.valueOf(this.s1uAddr);
            print("Installing *Uplink* PDR on device %s", uri);
            app.addPdr(ImmutableByteSequence.copyFrom(sessionId), 1, farId, ueAddr,
                    ImmutableByteSequence.copyFrom(teid), s1uAddr);
        } else {
            print("Installing *Downlink* PDR on device %s", uri);
            app.addPdr(ImmutableByteSequence.copyFrom(sessionId), 1, farId, ueAddr);
        }


    }

}
