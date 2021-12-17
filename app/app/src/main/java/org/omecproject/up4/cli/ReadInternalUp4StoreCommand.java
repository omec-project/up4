/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2021-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.impl.Up4Store;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;

import java.util.Set;

/**
 * Read internal UP4 stores.
 */
@Service
@Command(scope = "up4", name = "read-internal-stores",
        description = "Print internal UP4 stores")
public class ReadInternalUp4StoreCommand extends AbstractShellCommand {
    @Option(name = "-v", aliases = "--verbose",
            description = "Print more detail of each entry",
            required = false, multiValued = false)
    boolean verbose = false;

    @Override
    protected void doExecute() {
        Up4Service up4Service = get(Up4Service.class);
        Up4Store upfStore = get(Up4Store.class);

        if (up4Service == null) {
            print("Error: Up4Service is null");
            return;
        }

        if (upfStore == null) {
            print("Error: FabricUpfStore is null");
            return;
        }

        Set<Ip4Address> bufferUes = upfStore.getBufferUe();
        print("bufferFarIds size: " + bufferUes.size());
        if (verbose) {
            bufferUes.forEach(ue -> print("UEAddress{" + ue.toString() + "}"));
        }
    }
}
