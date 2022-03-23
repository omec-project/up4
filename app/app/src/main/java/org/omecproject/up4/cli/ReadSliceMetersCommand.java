/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.impl.Up4AdminService;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

/**
 * Read slice meters.
 */
@Service
@Command(scope = "up4", name = "slice-meters",
        description = "Print all slice meters installed in the dataplane")
public class ReadSliceMetersCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        Up4AdminService app = get(Up4AdminService.class);

        try {
            for (UpfEntity meters : app.adminReadAll(UpfEntityType.SLICE_METER)) {
                print(meters.toString());
            }
        } catch (UpfProgrammableException e) {
            print("Error while reading slice meters: " + e.getMessage());
        }
    }
}
