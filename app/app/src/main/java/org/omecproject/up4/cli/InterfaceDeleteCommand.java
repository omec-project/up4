/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfInterface;

import static org.omecproject.up4.impl.AppConstants.SLICE_MOBILE;

/**
 * UP4 N3 interface deletion command.
 */
@Service
@Command(scope = "up4", name = "n3-delete",
        description = "Delete a N3 interface address from the UP4 dataplane")
public class InterfaceDeleteCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "n3-addr",
            description = "Address of the N3 interface",
            required = true)
    String n3Addr = null;

    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);

        Ip4Address n3Addr = Ip4Address.valueOf(this.n3Addr);

        print("Removing N3 interface address %s", n3Addr.toString());
        // TODO: Update UpfInterface to use N3 instead of S1U
        app.delete(UpfInterface.createS1uFrom(n3Addr, SLICE_MOBILE));
    }
}

