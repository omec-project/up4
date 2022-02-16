/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2022-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfMeter;

/**
 * UP4 session meter command.
 */
@Service
@Command(scope = "up4", name = "session-meter",
        description = "Insert (or delete) a UPF Session Meter into the UP4 dataplane")
public class UpfSessionMeterCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "cell-index",
            description = "Index of the meter cell",
            required = true)
    int cellId = -1;

    @Argument(index = 1, name = "pir",
            description = "Peak information rate",
            required = false)
    Long pir = null;

    @Argument(index = 2, name = "pburst",
            description = "Peak burst",
            required = false)
    Long pburst = null;

    @Option(name = "--delete", aliases = "-d",
            description = "Delete the given session meter",
            required = false)
    boolean delete = false;

    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);
        if (delete) {
            app.apply(UpfMeter.resetSession(cellId));
        } else {
            if (pir == null || pburst == null) {
                print("PIR and PBURST must be provided when creating a meter");
                return;
            }
            UpfMeter sessionMeter = UpfMeter.builder()
                    .setCellId(cellId)
                    .setPeakBand(pir, pburst)
                    .setSession()
                    .build();
            app.apply(sessionMeter);
        }
    }
}
