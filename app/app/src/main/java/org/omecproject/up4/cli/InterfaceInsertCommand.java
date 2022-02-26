/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.impl.Up4AdminService;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfInterface;

/**
 * UP4 N3 Interface insertion command.
 */
@Service
@Command(scope = "up4", name = "n3-insert",
        description = "Insert a N3 interface address into the UP4 dataplane")
public class InterfaceInsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "n3-addr",
            description = "Address of the N3 interface",
            required = true)
    String n3Addr = null;

    @Argument(index = 1, name = "slice-id",
            description = "Slice ID",
            required = true)
    Integer sliceId = null;

    @Override
    protected void doExecute() throws Exception {
        Up4AdminService app = get(Up4AdminService.class);
        Ip4Address n3Addr = Ip4Address.valueOf(this.n3Addr);
        print("Adding N3 interface address: %s", n3Addr.toString());
        app.adminApply(UpfInterface.createN3From(n3Addr, sliceId));
    }
}

