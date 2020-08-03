package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.Up4Service;
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

        for (PacketDetectionRule pdr : app.getUpfProgrammable().getInstalledPdrs()) {
            print(pdr.toString());
        }
    }
}
