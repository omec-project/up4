/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onosproject.cli.AbstractShellCommand;

/**
 * UP4  Interface json-format read command.
 */
@Service
@Command(scope = "up4", name = "json-read-interfaces",
        description = "Print all interfaces installed in the dataplane, in JSON format")
public class JsonReadInterfacesCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "pretty",
            description = "Set to true to print the JSON prettily",
            required = false)
    boolean pretty = false;

    @Override
    protected void doExecute() {
        Up4Service app = get(Up4Service.class);

        final ObjectMapper mapper = new ObjectMapper();
        final ArrayNode arrayNode = mapper.createArrayNode();

        app.getUpfProgrammable().getInstalledInterfaces()
                .forEach(iface -> arrayNode.add(iface.toJson()));

        if (pretty) {
            print(arrayNode.toPrettyString());
        } else {
            print(arrayNode.toString());
        }
    }
}
