/*
 SPDX-License-Identifier: Apache-2.0
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
import org.onosproject.net.behaviour.upf.UpfSessionDownlink;

/**
 * UP4 UE session command.
 */
@Service
@Command(scope = "up4", name = "session-dl",
        description = "Insert (or delete) a downlink UE Session into the UP4 dataplane")
public class SessionDownlinkCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "ue-addr",
            description = "Address of the UE for which this UE Session applies",
            required = true)
    String ueAddr = null;

    @Argument(index = 1, name = "tunnel-peer",
            description = "ID of the GTP Tunnel Peer",
            required = false)
    byte tunnelPeer = -1;

    @Argument(index = 2, name = "sess-meter-idx",
            description = "Index of the session meter",
            required = false)
    int sessMeterIdx = -1;

    @Option(name = "--buffer", aliases = "-b",
            description = "Buffering UE",
            required = false)
    boolean buff = false;

    @Option(name = "--drop", aliases = "-dd",
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
        UpfSessionDownlink.Builder sessBuilder = UpfSessionDownlink.builder()
                .needsBuffering(buff)
                .needsDropping(drop)
                .withUeAddress(Ip4Address.valueOf(ueAddr));
        if (tunnelPeer != -1) {
            sessBuilder.withGtpTunnelPeerId(tunnelPeer);
        }
        if (sessMeterIdx != -1) {
            sessBuilder.withSessionMeterIdx(sessMeterIdx);
        }
        if (delete) {
            app.delete(sessBuilder.build());
        } else {
            app.apply(sessBuilder.build());
        }
    }
}
