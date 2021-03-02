/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.UpfProgrammableException;
import org.onosproject.cli.AbstractShellCommand;

/**
 * UP4 PDR read command.
 */
@Service
@Command(scope = "up4", name = "read-pdrs",
        description = "Print all PDRs installed in the dataplane")
public class ReadPdrsCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        try {
            for (PacketDetectionRule pdr : app.getUpfProgrammable().getPdrs()) {
                print(pdr.toString());
            }
        } catch (UpfProgrammableException e) {
            print("Error while reading PDRs: " + e.getMessage());
        }
    }
}
