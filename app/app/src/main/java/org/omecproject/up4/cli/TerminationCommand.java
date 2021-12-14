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
 * UP4 UPF termination command.
 */
@Service
@Command(scope = "up4", name = "termination",
        description = "Insert (or delete) a UPF termination into the UP4 dataplane")
public class TerminationCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "ue-address",
            description = "UE address for this termination rule",
            required = true)
    String ueAddr = null;

    @Argument(index = 1, name = "traffic-class",
            description = "Traffic Class",
            required = true)
    byte trafficClass = 0;

    @Argument(index = 2, name = "counter-id",
            description = "Counter ID for this termination rule",
            required = true)
    int counterID = 0;

    @Argument(index = 3, name = "teid",
            description = "Tunnel ID of the tunnel to the base station, valid only for DL",
            required = false)
    int teid = -1;

    @Argument(index = 4, name = "qfi",
            description = "QoS Flow Identifier for this termination rule, valid only for DL",
            required = false)
    byte qfi = -1;

    @Option(name = "--delete", aliases = "-d",
            description = "Delete the given UPF Termination",
            required = false)
    boolean delete = false;

    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);
        //TODO
//        UpfTermination.Builder termBuilder = UpfTermination.builder()
//                .withUeSessionId(Ip4Address.valueOf(ueAddr))
//                .withTrafficClass(trafficClass);
//        if ((teid == -1 && qfi != -1) || (teid != -1 && qfi == -1)) {
//            print("TEID and QFI must be provided together or not at all");
//        }
//        if (teid != -1 && qfi != -1) {
//            termBuilder.withTeid(teid)
//                    .withQfi(qfi);
//        }
//        if (delete) {
//            app.delete(termBuilder.build());
//        } else {
//            app.apply(termBuilder.build());
//        }
    }
}
