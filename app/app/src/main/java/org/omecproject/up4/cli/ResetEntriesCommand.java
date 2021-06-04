/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;

/**
 * UP4 clear table entries command.
 */
@Service
@Command(scope = "up4", name = "reset-entries",
        description = "Remove all dataplane entries and then reinstall the interfaces present in the UP4 config.")
public class ResetEntriesCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        print("Clearing all UP4 dataplane table entries.");
        app.clearFlows();
        print("Reinstalling UP4 interfaces from app configuration.");
        app.installInterfaces();
    }
}

