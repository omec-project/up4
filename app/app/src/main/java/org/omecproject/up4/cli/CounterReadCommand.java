/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;


/**
 * Counter read command.
 */
@Service
@Command(scope = "up4", name = "ctr-read",
        description = "Read a PDR counter")
public class CounterReadCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "ctr-index",
            description = "Index of the counter cell to read.",
            required = true, multiValued = false)
    int ctrIndex = 0;

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        try {
            PdrStats stats = app.getUpfProgrammable().readCounter(ctrIndex);
            print(stats.toString());
        } catch (Exception e) {
            print("Command failed with error: " + e.getMessage());
        }
    }
}
