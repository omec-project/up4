/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.impl.Up4AdminService;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

import java.util.Collection;

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
        Up4AdminService app = get(Up4AdminService.class);

        print("Clearing all UP4 dataplane table entries.");
        app.adminDeleteAll(UpfEntityType.SESSION_UPLINK);
        app.adminDeleteAll(UpfEntityType.SESSION_DOWNLINK);
        app.adminDeleteAll(UpfEntityType.TERMINATION_UPLINK);
        app.adminDeleteAll(UpfEntityType.TERMINATION_DOWNLINK);
        app.adminDeleteAll(UpfEntityType.TUNNEL_PEER);
        app.adminReadAll(UpfEntityType.INTERFACE);
        Collection<UpfInterface> configInterfaces = app.configInterfaces();
        // Remove only the interfaces from UP4 config
        for (UpfInterface upfInterface : configInterfaces) {
            app.adminDelete(upfInterface);
        }
        app.resetAllSessionMeters();
        app.resetAllApplicationMeters();
        app.resetAllSliceMeters();
        print("Reinstalling UP4 interfaces and DBUF GTP Tunnel from app configuration.");
        app.installUpfEntities();
    }
}

