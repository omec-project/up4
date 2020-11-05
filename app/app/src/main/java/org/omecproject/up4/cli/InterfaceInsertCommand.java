/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammableException;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.cli.AbstractShellCommand;

/**
 * UP4 S1U Interface insertion command.
 */
@Service
@Command(scope = "up4", name = "s1u-insert",
        description = "Insert a S1U interface address into the UP4 dataplane")
public class InterfaceInsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "s1u-prefix",
            description = "Prefix of the S1U interface",
            required = true)
    String s1uPrefix = null;

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        Ip4Prefix s1uPrefix = Ip4Prefix.valueOf(this.s1uPrefix);

        print("Adding S1U interface address: %s", s1uPrefix.toString());
        try {
            app.getUpfProgrammable().addInterface(UpfInterface.createS1uFrom(s1uPrefix));
        } catch (UpfProgrammableException e) {
            print("Command failed with error: " + e.getMessage());
        }
    }
}

