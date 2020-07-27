/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Service;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;

/**
 * UP4 PDR deletion command.
 */
@Service
@Command(scope = "up4", name = "pdr-delete",
        description = "Delete a packet detection rule from the UP4 dataplane")
public class PdrDeleteCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "ue-ipv4-addr",
            description = "Address of the UE for which this PDR applies",
            required = true, multiValued = false)
    String ueAddr = null;

    @Argument(index = 1, name = "teid",
            description = "Tunnel ID of the tunnel to/from the base station",
            required = false, multiValued = false)
    int teid = -1;

    @Argument(index = 2, name = "s1u-ip",
            description = "IP address of the S1U interface (endpoint of the tunnel to the base station)",
            required = false, multiValued = false)
    String s1uAddr = null;

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        var pdrBuilder = PacketDetectionRule.builder()
                .withUeAddr(Ip4Address.valueOf(this.ueAddr));

        if (teid != -1) {
            pdrBuilder.withTeid(teid);
        }
        if (s1uAddr != null) {
            pdrBuilder.withTunnelDst(Ip4Address.valueOf(s1uAddr));
        }

        PacketDetectionRule pdr = pdrBuilder.build();
        print("Removing %s from UPF", pdr.toString());
        app.getUpfProgrammable().removePdr(pdrBuilder.build());
    }
}
