/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.ForwardingActionRule;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

/**
 * UP4 FAR read command.
 */
@Service
@Command(scope = "up4", name = "read-fars",
        description = "Print all FARS installed in the dataplane")
public class ReadFarsCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        try {
            for (ForwardingActionRule far : app.getFars()) {
                print(far.toString());
            }
        } catch (UpfProgrammableException e) {
            print("Error while reading FARs: " + e.getMessage());
        }
    }
}
