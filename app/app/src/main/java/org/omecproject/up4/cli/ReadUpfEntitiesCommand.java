/*
 SPDX-License-Identifier: Apache-2.0
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
import org.onosproject.net.behaviour.upf.UpfTerminationDownlink;
import org.onosproject.net.behaviour.upf.UpfTerminationUplink;

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
    boolean all = false;

    @Option(name = "--ue", aliases = "-u",
            description = "Include all UE related entities (session, termination, tunnel peer, counters)",
            required = false)
    boolean ue = false;

    @Option(name = "--sess", aliases = "-s",
            description = "Include the UE sessions",
            required = false)
    boolean sessions = false;

    @Option(name = "--term", aliases = "-t",
            description = "Include the UPF termination rules",
            required = false)
    boolean termination = false;

    @Option(name = "--tunn", aliases = "-g",
            description = "Include the GTP tunnel peers",
            required = false)
    boolean tunnels = false;

    @Option(name = "--count", aliases = "-c",
            description = "Include the all the UPF counters",
            required = false)
    boolean counters = false;

    @Option(name = "--intf", aliases = "-i",
            description = "Include the UPF interfaces",
            required = false)
    boolean interfaces = false;

    @Option(name = "--apps", aliases = "-f",
            description = "Include the UPF Applications",
            required = false)
    boolean application = false;

    @Option(name = "--app-meter", aliases = "-am",
            description = "Include the UPF App Meters",
            required = false)
    boolean appMeters = false;

    @Option(name = "--sess-meter", aliases = "-sm",
            description = "Include the UPF Session Meters",
            required = false)
    boolean sessMeters = false;


    @Override
    protected void doExecute() {
        Up4AdminService up4Admin = get(Up4AdminService.class);
        Up4Service up4Service = get(Up4Service.class);
        boolean filterCounters = false;
        try {
            List<UpfEntityType> printedTypes = new ArrayList<>();
            if (all) {
                printedTypes.add(UpfEntityType.APPLICATION);
                printedTypes.add(UpfEntityType.SESSION_UPLINK);
                printedTypes.add(UpfEntityType.SESSION_DOWNLINK);
                printedTypes.add(UpfEntityType.TERMINATION_UPLINK);
                printedTypes.add(UpfEntityType.TERMINATION_DOWNLINK);
                printedTypes.add(UpfEntityType.TUNNEL_PEER);
                printedTypes.add(UpfEntityType.INTERFACE);
                printedTypes.add(UpfEntityType.SESSION_METER);
                printedTypes.add(UpfEntityType.APPLICATION_METER);
                filterCounters = true;
            } else if (ue) {
                printedTypes.add(UpfEntityType.APPLICATION);
                printedTypes.add(UpfEntityType.SESSION_UPLINK);
                printedTypes.add(UpfEntityType.SESSION_DOWNLINK);
                printedTypes.add(UpfEntityType.TERMINATION_UPLINK);
                printedTypes.add(UpfEntityType.TERMINATION_DOWNLINK);
                printedTypes.add(UpfEntityType.TUNNEL_PEER);
                printedTypes.add(UpfEntityType.SESSION_METER);
                printedTypes.add(UpfEntityType.APPLICATION_METER);
                filterCounters = true;
            } else {
                if (application) {
                    printedTypes.add(UpfEntityType.APPLICATION);
                }
                if (sessions) {
                    printedTypes.add(UpfEntityType.SESSION_UPLINK);
                    printedTypes.add(UpfEntityType.SESSION_DOWNLINK);
                }
                if (termination) {
                    printedTypes.add(UpfEntityType.TERMINATION_UPLINK);
                    printedTypes.add(UpfEntityType.TERMINATION_DOWNLINK);
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
                if (appMeters) {
                    printedTypes.add(UpfEntityType.APPLICATION_METER);
                }
                if (sessMeters) {
                    printedTypes.add(UpfEntityType.SESSION_METER);
                }
            }
            for (var type : printedTypes) {
                if (type.equals(UpfEntityType.TERMINATION_UPLINK)) {
                    Collection<? extends UpfEntity> terminations = up4Admin.adminReadAll(type);
                    for (var t : terminations) {
                        UpfTerminationUplink term = (UpfTerminationUplink) t;
                        print(term.toString());
                        if (filterCounters) {
                            print(up4Service.readCounter(term.counterId()).toString());
                        }
                    }
                } else if (type.equals(UpfEntityType.TERMINATION_DOWNLINK)) {
                    Collection<? extends UpfEntity> terminations = up4Admin.adminReadAll(type);
                    for (var t : terminations) {
                        UpfTerminationDownlink term = (UpfTerminationDownlink) t;
                        print(term.toString());
                        if (filterCounters) {
                            print(up4Service.readCounter(term.counterId()).toString());
                        }
                    }
                } else {
                    up4Admin.adminReadAll(type).forEach(upfEntity -> print(upfEntity.toString()));
                }
            }
        } catch (UpfProgrammableException e) {
            print("Error while reading UPF entity: " + e.getMessage());
        }
    }
}
