/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.impl.Up4AdminService;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

/**
 * UP4 PDR read command.
 */
@Service
@Command(scope = "up4", name = "read-interfaces",
        description = "Print all interfaces installed in the dataplane")
public class ReadInterfacesCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        Up4AdminService app = get(Up4AdminService.class);

        try {
            for (UpfEntity iface : app.adminReadAll(UpfEntityType.INTERFACE)) {
                print(iface.toString());
            }
        } catch (UpfProgrammableException e) {
            print("Error while reading interfaces: " + e.getMessage());
        }
    }
}
