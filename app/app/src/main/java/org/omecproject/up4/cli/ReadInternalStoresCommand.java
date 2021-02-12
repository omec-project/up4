/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.UpfRuleIdentifier;
import org.omecproject.up4.behavior.FabricUpfStore;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;

import java.util.Map;
import java.util.Set;

/**
 * UP4 FarIdMap read command.
 */
@Service
@Command(scope = "up4", name = "read-internal-stores",
        description = "Print internal stores")
public class ReadInternalStoresCommand extends AbstractShellCommand {
    @Option(name = "-v", aliases = "--verbose",
            description = "Print more detail of each entry",
            required = false, multiValued = false)
    private boolean verbose = false;

    @Override
    protected void doExecute() {
        Up4Service up4Service = get(Up4Service.class);
        FabricUpfStore upfStore = get(FabricUpfStore.class);

        if (up4Service == null) {
            print("Error: Up4Service is null");
            return;
        }

        if (upfStore == null) {
            print("Error: FabricUpfStore is null");
            return;
        }

        Map<UpfRuleIdentifier, Integer> farIdMap = upfStore.getFarIdMap();
        print("farIdMap size: " + farIdMap.size());
        if (verbose) {
            farIdMap.entrySet().forEach(entry -> print(entry.toString()));
        }

        Set<UpfRuleIdentifier> bufferFarIds = up4Service.getUpfProgrammable().getBufferFarIds();
        print("bufferFarIds size: " + bufferFarIds.size());
        if (verbose) {
            bufferFarIds.forEach(upfRuleIdentifier -> print(upfRuleIdentifier.toString()));
        }

        Map<UpfRuleIdentifier, Set<Ip4Address>> farIdToUeAddrs = up4Service.getUpfProgrammable().getFarIdToUeAddrs();
        print("farIdToUeAddrs size: " + farIdToUeAddrs.size());
        if (verbose) {
            farIdToUeAddrs.entrySet().forEach(entry -> print(entry.toString()));
        }
    }
}
