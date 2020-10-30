package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.UpfInterface;
import org.onosproject.cli.AbstractShellCommand;

/**
 * UP4 PDR read command.
 */
@Service
@Command(scope = "up4", name = "read-interfaces",
        description = "Print all interfaces installed in the dataplane")
public class ReadInterfacesCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        try {
            for (UpfInterface iface : app.getUpfProgrammable().getInstalledInterfaces()) {
                print(iface.toString());
            }
        } catch (Up4Service.Up4ServiceException e) {
            print("Command failed due to UP4 exception: %s", e.getMessage());
        }
    }
}
