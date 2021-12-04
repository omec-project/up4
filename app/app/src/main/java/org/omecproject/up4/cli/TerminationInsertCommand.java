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
 * UP4 Far insertion command.
 */
@Service
@Command(scope = "up4", name = "far-insert",
        description = "Insert a forwarding action rule into the UP4 dataplane")
public class TerminationInsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "session-id",
            description = "Session ID for this PDR",
            required = true)
    long sessionId = 0;

    @Argument(index = 1, name = "far-id",
            description = "ID of the FAR",
            required = true)
    int farId = 0;

    @Argument(index = 2, name = "teid",
            description = "Teid of the tunnel the packet should enter",
            required = false)
    int teid = -1;

    @Argument(index = 3, name = "tunnel-src",
            description = "Src IP of the tunnel the packet should enter",
            required = false)
    String tunnelSrc = null;

    @Argument(index = 4, name = "tunnel-dst",
            description = "Dst IP of the tunnel the packet should enter",
            required = false)
    String tunnelDst = null;

    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);
        //TOdo
    }
}
