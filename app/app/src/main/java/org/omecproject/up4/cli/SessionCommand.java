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
import org.onosproject.cli.AbstractShellCommand;

/**
 * UP4 UE session command.
 */
@Service
@Command(scope = "up4", name = "session",
        description = "Insert (or delete) a UE Session into the UP4 dataplane," +
                " specifying TEID and tunnel peer means it's a DL UE session")
public class SessionCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "ipv4-addr",
            description = "Address of the UE or s1u interface for which this UE Session applies",
            required = true)
    String dstAddr = null;

    @Argument(index = 2, name = "teid",
            description = "Tunnel ID of the tunnel from the base station",
            required = false)
    int teid = -1;

    @Argument(index = 3, name = "tunnel-peer",
            description = "ID of the GTP Tunnel Peer",
            required = false)
    byte tunnelPeer = -1;

    @Option(name = "--buffer", aliases = "-b",
            description = "Buffering UE",
            required = false)
    boolean buff = false;

    @Option(name = "--delete", aliases = "-d",
            description = "Delete the given UE Session",
            required = false)
    boolean delete = false;


    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);
        //TODO
//        UeSession.Builder sessBuilder = UeSession.builder()
//                .needsBuffering(buff)
//                .withIpv4Address(Ip4Address.valueOf(dstAddr));
//        if (teid != -1 && tunnelPeer != -1) {
//            sessBuilder.withGtpTunnelPeerId(tunnelPeer)
//                    .withTeid(teid);
//        }
//        if (delete) {
//            app.delete(sessBuilder.build());
//        } else {
//            app.apply(sessBuilder.build());
//        }
    }
}
