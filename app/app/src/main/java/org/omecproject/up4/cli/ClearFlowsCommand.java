/*
SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
*/
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;

/** UP4 clear sessions command. */
@Service
@Command(
    scope = "up4",
    name = "clear-flows",
    description = "Clear all dataplane uplink and downlink UE flow rules installed by this app")
public class ClearFlowsCommand extends AbstractShellCommand {

  @Override
  protected void doExecute() {
    Up4Service app = get(Up4Service.class);

    print("Clearing all currently installed UE sessions.");
    app.getUpfProgrammable().clearFlows();
  }
}
