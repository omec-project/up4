/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.impl.Up4AdminService;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

/**
 * UP4 clear sessions command.
 */
@Service
@Command(scope = "up4", name = "clear-flows",
        description = "Clear all dataplane uplink and downlink UE flow rules installed by this app")
public class ClearFlowsCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() throws UpfProgrammableException {
        Up4Service app = get(Up4Service.class);
        Up4AdminService adminApp = get(Up4AdminService.class);

        print("Clearing all currently installed UE sessions.");
        app.deleteAll(UpfEntityType.APPLICATION);
        app.deleteAll(UpfEntityType.SESSION_DOWNLINK);
        app.deleteAll(UpfEntityType.SESSION_UPLINK);
        app.deleteAll(UpfEntityType.TERMINATION_DOWNLINK);
        app.deleteAll(UpfEntityType.TERMINATION_UPLINK);
        app.deleteAll(UpfEntityType.TUNNEL_PEER);
        adminApp.resetAllSessionMeters();
        adminApp.resetAllApplicationMeters();
        adminApp.resetAllSliceMeters();
    }
}

