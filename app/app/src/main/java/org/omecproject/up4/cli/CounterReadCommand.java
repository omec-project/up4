/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfCounter;

/**
 * Counter read command.
 */
@Service
@Command(scope = "up4", name = "ctr-read",
        description = "Read a UPF counter")
public class CounterReadCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "ctr-index",
            description = "Index of the counter cell to read.",
            required = true, multiValued = false)
    int ctrIndex = 0;

    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);

        UpfCounter stats = app.readCounter(ctrIndex);
        print(stats.toString());
    }
}
