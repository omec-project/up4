/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

import java.util.ArrayList;
import java.util.List;

/**
 * UP4 read UPF entities command.
 */
@Service
@Command(scope = "up4", name = "read-entities",
        description = "Print UPF entities installed in the UPF dataplane")
public class ReadUpfEntitiesCommand extends AbstractShellCommand {

    @Option(name = "--all", aliases = "-a",
            description = "Include all UPF entities",
            required = false)
    private boolean all = false;

    @Option(name = "--ue", aliases = "-u",
            description = "Include all UE related entities (session, termination, tunnel peer and counters)",
            required = false)
    private boolean ue = false;

    @Option(name = "--sess", aliases = "-s",
            description = "Include the UE sessions",
            required = false)
    private boolean sessions = false;

    @Option(name = "--term", aliases = "-t",
            description = "Include the UPF termination rules",
            required = false)
    private boolean termination = false;

    @Option(name = "--tunn", aliases = "-g",
            description = "Include the GTP tunnel peers",
            required = false)
    private boolean tunnels = false;

    @Option(name = "--count", aliases = "-c",
            description = "Include the UPF counters",
            required = false)
    private boolean counters = false;

    @Option(name = "--intf", aliases = "-i",
            description = "Include the UPF interfaces",
            required = false)
    private boolean interfaces = false;


    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        try {
            List<UpfEntityType> printedTypes = new ArrayList<>();
            if (all) {
                printedTypes.add(UpfEntityType.SESSION);
                printedTypes.add(UpfEntityType.TERMINATION);
                printedTypes.add(UpfEntityType.TUNNEL_PEER);
                printedTypes.add(UpfEntityType.COUNTER);
                printedTypes.add(UpfEntityType.INTERFACE);
            } else if (ue) {
                printedTypes.add(UpfEntityType.SESSION);
                printedTypes.add(UpfEntityType.TERMINATION);
                printedTypes.add(UpfEntityType.TUNNEL_PEER);
            } else {
                if (sessions) {
                    printedTypes.add(UpfEntityType.SESSION);
                }
                if (termination) {
                    printedTypes.add(UpfEntityType.TERMINATION);
                }
                if (tunnels) {
                    printedTypes.add(UpfEntityType.TUNNEL_PEER);
                }
                if (counters) {
                    printedTypes.add(UpfEntityType.COUNTER);
                }
                if (interfaces) {
                    printedTypes.add(UpfEntityType.INTERFACE);
                }
            }
            for (var type : printedTypes) {
                app.readAll(type).forEach(upfEntity -> print(upfEntity.toString()));
            }
        } catch (UpfProgrammableException e) {
            print("Error while reading UPF entity: " + e.getMessage());
        }
    }
}
