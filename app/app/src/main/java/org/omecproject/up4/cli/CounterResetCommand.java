/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.impl.Up4AdminService;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.DeviceId;

/**
 * Counter reset command.
 */
@Service
@Command(scope = "up4", name = "ctr-reset",
        description = "Reset a UPF counter")
public class CounterResetCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "ctr-index",
            description = "Index of the counter cell to reset.",
            required = true)
    int ctrIndex = 0;

    @Argument(index = 1, name = "device-id",
            description = "Device ID to reset counters.")
    @Completion(DeviceIdCompleter.class)
    String deviceId = null;

    @Override
    protected void doExecute() throws Exception {
        Up4AdminService app = get(Up4AdminService.class);
        if (deviceId != null) {
            app.resetCounter(ctrIndex, DeviceId.deviceId(deviceId));
        } else {
            app.resetCounter(ctrIndex);
        }
        print("Done!");
    }
}
