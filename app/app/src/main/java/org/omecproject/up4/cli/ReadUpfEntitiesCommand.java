/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.impl.Up4AdminService;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;
import org.onosproject.net.behaviour.upf.UpfTermination;

import java.util.ArrayList;
import java.util.Collection;
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
            description = "Include all UE related entities (session, termination, tunnel peer, counters)",
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
            description = "Include the all the UPF counters",
            required = false)
    private boolean counters = false;

    @Option(name = "--intf", aliases = "-i",
            description = "Include the UPF interfaces",
            required = false)
    private boolean interfaces = false;


    @Override
    protected void doExecute() {
        Up4AdminService up4Admin = get(Up4AdminService.class);
        Up4Service up4Service = get(Up4Service.class);
        boolean filterCounters = false;
        try {
            List<UpfEntityType> printedTypes = new ArrayList<>();
            if (all) {
                printedTypes.add(UpfEntityType.SESSION);
                printedTypes.add(UpfEntityType.TERMINATION);
                printedTypes.add(UpfEntityType.TUNNEL_PEER);
                printedTypes.add(UpfEntityType.INTERFACE);
                filterCounters = true;
            } else if (ue) {
                printedTypes.add(UpfEntityType.SESSION);
                printedTypes.add(UpfEntityType.TERMINATION);
                printedTypes.add(UpfEntityType.TUNNEL_PEER);
                filterCounters = true;
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
                    filterCounters = false;
                }
                if (interfaces) {
                    printedTypes.add(UpfEntityType.INTERFACE);
                }
            }
            for (var type : printedTypes) {
                if (!type.equals(UpfEntityType.TERMINATION)) {
                    up4Admin.adminReadAll(type).forEach(upfEntity -> print(upfEntity.toString()));
                } else {
                    Collection<? extends UpfEntity> terminations = up4Admin.adminReadAll(type);
                    for (var t : terminations) {
                        UpfTermination term = (UpfTermination) t;
                        print(term.toString());
                        if (filterCounters) {
                            print(up4Service.readCounter(term.counterId()).toString());
                        }
                    }
                }
            }
        } catch (UpfProgrammableException e) {
            print("Error while reading UPF entity: " + e.getMessage());
        }
    }
}
