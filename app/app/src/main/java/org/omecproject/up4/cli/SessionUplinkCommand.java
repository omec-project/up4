/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.SessionUplink;

/**
 * UP4 UE session command.
 */
@Service
@Command(scope = "up4", name = "session-ul",
        description = "Insert (or delete) a uplink UE Session into the UP4 dataplane," +
                " specifying TEID and tunnel peer means it's a DL UE session")
public class SessionUplinkCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "n3-addr",
            description = "Address of the N3/S1U interface for which this UE Session applies",
            required = true)
    String n3Addr = null;

    @Argument(index = 2, name = "teid",
            description = "Tunnel ID of the tunnel from the base station",
            required = true)
    int teid = -1;

    @Option(name = "--drop", aliases = "-b",
            description = "Drop traffic",
            required = false)
    boolean drop = false;

    @Option(name = "--delete", aliases = "-d",
            description = "Delete the given UE Session",
            required = false)
    boolean delete = false;

    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);
        SessionUplink session = SessionUplink.builder()
                .needsDropping(drop)
                .withTunDstAddr(Ip4Address.valueOf(n3Addr))
                .withTeid(teid)
                .build();
        if (delete) {
            app.delete(session);
        } else {
            app.apply(session);
        }
    }
}
