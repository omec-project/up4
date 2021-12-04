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

/**
 * UP4 FAR deletion command.
 */
@Service
@Command(scope = "up4", name = "term-delete",
        description = "Delete a UPF termination rule from the UP4 dataplane")
public class TerminationDeleteCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "session-id",
            description = "Session ID for this PDR",
            required = true)
    long sessionId = 0;

    @Argument(index = 1, name = "far-id",
            description = "ID of the FAR",
            required = true)
    int farId = 0;

    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);
        //TODO
    }
}