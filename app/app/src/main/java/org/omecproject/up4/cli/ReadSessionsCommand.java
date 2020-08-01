package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.UeSession;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;

import java.util.Collection;

/**
 * UP4 UE session read command.
 */
@Service
@Command(scope = "up4", name = "read-sessions",
        description = "Read all sessions installed in the dataplane")
public class ReadSessionsCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        Collection<UeSession> sessions = app.getUpfProgrammable().getSessions();
        for (UeSession session : sessions) {
            print(session.toString());
        }
    }
}
