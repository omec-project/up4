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
 * UP4 PDR insertion command.
 */
@Service
@Command(scope = "up4", name = "pdr-insert",
        description = "Insert a packet detection rule into the UP4 dataplane")
public class PdrInsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "session-id",
            description = "Session ID for this PDR",
            required = true, multiValued = false)
    long sessionId = 0;

    @Argument(index = 1, name = "ue-ipv4-addr",
            description = "Address of the UE for which this PDR applies",
            required = true, multiValued = false)
    String ueAddr = null;

    @Argument(index = 2, name = "far-id",
            description = "ID of the FAR that should apply if this PDR matches",
            required = true, multiValued = false)
    int farId = 0;

    @Argument(index = 3, name = "teid",
            description = "Tunnel ID of the tunnel to/from the base station",
            required = false, multiValued = false)
    int teid = -1;

    @Argument(index = 4, name = "s1u-ip",
            description = "IP address of the S1U interface (endpoint of the tunnel to the base station)",
            required = false, multiValued = false)
    String s1uAddr = null;

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        Ip4Address ueAddr = Ip4Address.valueOf(this.ueAddr);

        var pdrBuilder = PacketDetectionRule.builder()
                .withSessionId(sessionId)
                .withUeAddr(ueAddr)
                .withLocalFarId(farId)
                .withCounterId(1);

        if (teid != -1) {
            pdrBuilder.withTeid(teid);
        }
        if (s1uAddr != null) {
            pdrBuilder.withTunnelDst(Ip4Address.valueOf(s1uAddr));
        }

        PacketDetectionRule pdr = pdrBuilder.build();
        print("Installing %s on UPF", pdr.toString());
        try {
            app.getUpfProgrammable().addPdr(pdrBuilder.build());
        } catch (Up4Service.Up4ServiceException e) {
            print("Command failed due to UP4 exception:", e.getMessage());
        }
    }
}
