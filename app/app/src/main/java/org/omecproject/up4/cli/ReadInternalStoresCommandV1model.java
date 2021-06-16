/*
 * SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 * SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.pipelines.fabric.behaviour.upf.UpfRuleIdentifier;
import org.onosproject.pipelines.fabric.behaviour.upf.UpfStore;

import java.util.Map;
import java.util.Set;

/**
 * UP4 FarIdMap read command.
 */
@Service
@Command(scope = "up4", name = "read-internal-stores-v1model",
        description = "Print internal stores in UpfProgrammable for fabric-v1model")
public class ReadInternalStoresCommandV1model extends AbstractShellCommand {
    @Option(name = "-v", aliases = "--verbose",
            description = "Print more detail of each entry",
            required = false, multiValued = false)
    private boolean verbose = false;

    @Override
    protected void doExecute() {
        log.info("This command works only when using the fabric-v1model UpfProgrammable implementation");
        Up4Service up4Service = get(Up4Service.class);
        UpfStore upfStore = get(UpfStore.class);

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

        Set<UpfRuleIdentifier> bufferFarIds = upfStore.getBufferFarIds();
        print("bufferFarIds size: " + bufferFarIds.size());
        if (verbose) {
            bufferFarIds.forEach(upfRuleIdentifier -> print(upfRuleIdentifier.toString()));
        }

        Map<UpfRuleIdentifier, Set<Ip4Address>> farIdToUeAddrs = upfStore.getFarIdToUeAddrs();
        print("farIdToUeAddrs size: " + farIdToUeAddrs.size());
        if (verbose) {
            farIdToUeAddrs.entrySet().forEach(entry -> print(entry.toString()));
        }
    }
}
