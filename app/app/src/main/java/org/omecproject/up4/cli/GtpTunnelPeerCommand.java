/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.impl.Up4AdminService;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfGtpTunnelPeer;

/**
 * UP4 GTP Tunnel Peer command.
 */
@Service
@Command(scope = "up4", name = "tunnel-peer",
        description = "Insert (or delete) a GTP Tunnel Peer into the UP4 dataplane")
public class GtpTunnelPeerCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "tunnel-peer",
            description = "ID of the GTP Tunnel Peer",
            required = true)
    byte tunnelPeer = -1;

    @Argument(index = 1, name = "src-addr",
            description = "Tunnel source IPv4 Address",
            required = true)
    String srcAddr = "";

    @Argument(index = 2, name = "dst-addr",
            description = "Tunnel destination IPv4 address",
            required = true)
    String dstAddr = "";

    @Argument(index = 3, name = "src-port",
            description = "Tunnel source UDP port",
            required = false)
    short srcPort = -1;

    @Option(name = "--delete", aliases = "-d",
            description = "Delete the given GTP Tunnel Peer ",
            required = false)
    boolean delete = false;

    @Override
    protected void doExecute() throws Exception {
        Up4AdminService app = get(Up4AdminService.class);
        UpfGtpTunnelPeer.Builder tunnelPeerBuilder = UpfGtpTunnelPeer.builder()
                .withTunnelPeerId(tunnelPeer)
                .withSrcAddr(Ip4Address.valueOf(srcAddr))
                .withDstAddr(Ip4Address.valueOf(dstAddr));
        if (srcPort != -1) {
            tunnelPeerBuilder.withSrcPort(srcPort);
        }
        if (delete) {
            app.adminDelete(tunnelPeerBuilder.build());
        } else {
            app.adminApply(tunnelPeerBuilder.build());
        }
    }
}
