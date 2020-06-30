/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.onosproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.up4.Up4Service;

/**
 * UPF clear table entries command.
 */
@Service
@Command(scope = "up4", name = "clear-entries",
        description = "Clear all dataplane table entries installed by this app")
public class ClearEntriesCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        print("Clearing all UP4 dataplane table entries.");
        app.clearAllEntries();
    }

}

