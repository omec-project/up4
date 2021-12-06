/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

/**
 * UP4 clear table entries command.
 */
@Service
@Command(scope = "up4", name = "reset-entries",
        description = "Remove all dataplane entries and then reinstall the " +
                "interfaces and DBUF GTP tunnel present in the app config.")
public class ResetEntriesCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() throws UpfProgrammableException {
        Up4Service app = get(Up4Service.class);

        print("Clearing all UP4 dataplane table entries.");
        app.internalDeleteUpfEntities(UpfEntityType.SESSION);
        app.internalDeleteUpfEntities(UpfEntityType.TERMINATION);
        app.internalDeleteUpfEntities(UpfEntityType.TUNNEL_PEER);
        app.internalDeleteUpfEntities(UpfEntityType.INTERFACE);
        print("Reinstalling UP4 interfaces and DBUF GTP Tunnel from app configuration.");
        app.installInternalUpfEntities();
    }
}

