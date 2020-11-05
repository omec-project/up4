/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Exception;
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

    @Argument(index = 0, name = "address",
            description = "Address of the UE for downlink PDRs, or the tunnel endpoint for uplink.",
            required = true, multiValued = false)
    String address = null;

    @Argument(index = 1, name = "teid",
            description = "Tunnel ID of the tunnel to/from the base station."
                    + " The presence of this argument implies an uplink PDR. An absence implies downlink.",
            required = false, multiValued = false)
    int teid = -1;

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        var pdrBuilder = PacketDetectionRule.builder();
        Ip4Address address = Ip4Address.valueOf(this.address);

        if (teid != -1) {
            pdrBuilder.withTunnelDst(address)
                    .withTeid(teid);
        } else {
            pdrBuilder.withUeAddr(address);
        }

        PacketDetectionRule pdr = pdrBuilder.build();
        print("Removing %s from UPF", pdr.toString());
        try {
            app.getUpfProgrammable().removePdr(pdrBuilder.build());
        } catch (Up4Exception e) {
            print("Command failed with error: " + e.getMessage());
        }
    }
}
