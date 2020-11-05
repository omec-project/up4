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
 * UP4 UE IPv4 address pool deletion command.
 */
@Service
@Command(scope = "up4", name = "ue-pool-delete",
        description = "Delete a UE IPv4 pool prefix from the UP4 dataplane")
public class UePoolDeleteCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "ue-pool-prefix",
            description = "IPv4 Prefix of the UE pool",
            required = true)
    String poolPrefix = null;

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        Ip4Prefix poolPrefix = Ip4Prefix.valueOf(this.poolPrefix);

        print("Deleting UE IPv4 address pool prefix: %s", poolPrefix);
        try {
            app.getUpfProgrammable().removeInterface(UpfInterface.createUePoolFrom(poolPrefix));
        } catch (UpfProgrammableException e) {
            print("Command failed with error: " + e.getMessage());
        }
    }
}

