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
import org.onosproject.net.behaviour.upf.UpfTerminationDownlink;

/**
 * UP4 UPF termination command.
 */
@Service
@Command(scope = "up4", name = "termination-dl",
        description = "Insert (or delete) a downlink UPF termination into the UP4 dataplane")
public class TerminationDownlinkCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "ue-address",
            description = "UE address for this termination rule",
            required = true)
    String ueAddr = null;

    @Argument(index = 1, name = "app-id",
            description = "Application ID for this termination rule",
            required = true)
    Byte appId = null;

    @Argument(index = 2, name = "counter-id",
            description = "Counter ID for this termination rule",
            required = true)
    int counterID = 0;

    @Argument(index = 3, name = "traffic-class",
            description = "Traffic Class",
            required = false)
    byte trafficClass = -1;

    @Argument(index = 4, name = "teid",
            description = "Tunnel ID of the tunnel to the base station, valid only for DL",
            required = false)
    int teid = -1;

    @Argument(index = 5, name = "qfi",
            description = "QoS Flow Identifier for this termination rule, valid only for DL",
            required = false)
    byte qfi = -1;

    @Argument(index = 6, name = "app-meter-idx",
            description = "Index of the application meter",
            required = false)
    int appMeterIdx = -1;

    @Option(name = "--drop", aliases = "-dd",
            description = "Drop traffic",
            required = false)
    boolean drop = false;

    @Option(name = "--delete", aliases = "-d",
            description = "Delete the given UPF Termination",
            required = false)
    boolean delete = false;

    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);
        UpfTerminationDownlink.Builder termBuilder = UpfTerminationDownlink.builder()
                .needsDropping(drop)
                .withUeSessionId(Ip4Address.valueOf(ueAddr))
                .withApplicationId(appId)
                .withCounterId(counterID);
        if (trafficClass != -1) {
            termBuilder.withTrafficClass(trafficClass);
        }
        if (teid != -1) {
            termBuilder.withTeid(teid);
        }
        if (qfi != -1) {
            termBuilder.withQfi(qfi);
        }
        if (appMeterIdx != -1) {
            termBuilder.withAppMeterIdx(appMeterIdx);
        }
        if (delete) {
            app.delete(termBuilder.build());
        } else {
            app.apply(termBuilder.build());
        }
    }
}
