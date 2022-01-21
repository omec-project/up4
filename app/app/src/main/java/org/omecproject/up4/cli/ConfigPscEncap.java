/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2022-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.config.Up4Config;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.NetworkConfigService;

import static org.omecproject.up4.impl.AppConstants.APP_NAME;

@Service
@Command(scope = "up4", name = "psc-encap",
        description = "Enable or disable GTP-U extension PDU Session Container (PSC) in the data plane.")
public class ConfigPscEncap extends AbstractShellCommand {

    @Argument(index = 0, name = "enable",
            description = "Enable or disable GTP-U extension PDU Session Container (PSC) in the data plane.",
            required = true)
    Boolean enable = null;

    @Override
    protected void doExecute() throws Exception {
        if (enable == null) {
            return;
        }
        NetworkConfigService netCfgService = get(NetworkConfigService.class);
        CoreService coreService = get(CoreService.class);
        ApplicationId appId = coreService.getAppId(APP_NAME);

        Up4Config config = netCfgService.getConfig(appId, Up4Config.class);
        if (config == null) {
            print("No UP4 netcfg has been pushed yet");
            return;
        }
        config.setPscEncap(enable);
        netCfgService.applyConfig(appId, Up4Config.class, config.node());
    }
}
