/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.ForwardingActionRule;

/**
 * UP4 FAR deletion command.
 */
@Service
@Command(scope = "up4", name = "far-delete",
        description = "Delete a forwarding action rule from the UP4 dataplane")
public class FarDeleteCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "far-id",
            description = "Global ID of the FAR",
            required = true)
    int farId = 0;

    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);

        ForwardingActionRule far = ForwardingActionRule.builder()
                .setFarId(farId)
                .build();
        print("Deleting %s", far.toString());
        app.removeFar(far);
    }
}
