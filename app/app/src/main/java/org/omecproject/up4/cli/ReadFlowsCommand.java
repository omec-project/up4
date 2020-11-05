/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.UpfFlow;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;

import java.util.Collection;

/**
 * UP4 UE session read command.
 */
@Service
@Command(scope = "up4", name = "read-flows",
        description = "Read all UE data flows installed in the dataplane")
public class ReadFlowsCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        Collection<UpfFlow> flows = app.getUpfProgrammable().getFlows();
        for (UpfFlow flow : flows) {
            print(flow.toString());
        }
        print("%d flows found", flows.size());
    }
}
