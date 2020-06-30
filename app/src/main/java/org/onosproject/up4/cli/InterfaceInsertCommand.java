/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.onosproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.up4.Up4Service;

/**
 * UPF S1U Interface insertion command.
 */
@Service
@Command(scope = "up4", name = "s1u-insert",
        description = "Insert a S1U interface address into the UPF dataplane")
public class InterfaceInsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "uri", description = "Device ID",
            required = true)
    @Completion(DeviceIdCompleter.class)
    String uri = null;

    @Argument(index = 1, name = "s1u-addr",
            description = "Address of the S1U interface",
            required = true)
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

        Ip4Address s1uAddr = Ip4Address.valueOf(this.s1uAddr);

        print("Adding S1U interface address to device %s", uri);
        app.addS1uInterface(device.id(), s1uAddr);


    }

}

