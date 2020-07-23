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
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;

/**
 * UP4 FAR deletion command.
 */
@Service
@Command(scope = "up4", name = "far-delete",
        description = "Delete a forwarding action rule from the UP4 dataplane")
public class FarDeleteCommand extends AbstractShellCommand {

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

    @Override
    protected void doExecute() {
        DeviceService deviceService = get(DeviceService.class);
        Up4Service app = get(Up4Service.class);

        Device device = deviceService.getDevice(DeviceId.deviceId(uri));
        if (device == null) {
            print("Device \"%s\" is not found", uri);
            return;
        }

        print("Deleting a FAR from device %s", uri);
        ForwardingActionRule far = ForwardingActionRule.builder()
                .withSessionId(sessionId)
                .withFarId(farId)
                .build();
        app.getUpfProgrammable().removeFar(far);
    }

}
