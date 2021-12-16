/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.impl.DownlinkUpfFlow;
import org.omecproject.up4.impl.Up4AdminService;
import org.omecproject.up4.impl.UplinkUpfFlow;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.SessionUplink;
import org.onosproject.net.behaviour.upf.UpfEntityType;

import java.util.Collection;

/**
 * UP4 UE session read command.
 */
@Service
@Command(scope = "up4", name = "read-flows",
        description = "Read all UE data flows installed in the dataplane")
public class ReadFlowsCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() throws Exception {
        Up4AdminService adminService = get(Up4AdminService.class);

        Collection<DownlinkUpfFlow> dlUpfFlow = adminService.getDownlinkFlows();
        Collection<UplinkUpfFlow> ulUpfFlow = adminService.getUplinkFlows();
        Collection<SessionUplink> ulSess = (Collection<SessionUplink>)
                adminService.adminReadAll(UpfEntityType.SESSION_UPLINK);

        print("-".repeat(40));
        print(ulSess.size() + " Uplink Sessions");
        for (SessionUplink s : ulSess) {
            print("N3 addr=" + s.tunDstAddr() + ", TEID=" + s.teid());
        }
        print("-".repeat(40));
        print(ulUpfFlow.size() + " Uplink Flows");
        for (UplinkUpfFlow f : ulUpfFlow) {
            print(f.toString());
        }
        print("-".repeat(40));
        print(dlUpfFlow.size() + " Downlink Flows");
        for (DownlinkUpfFlow f : dlUpfFlow) {
            print(f.toString());
        }
        print("-".repeat(40));        print("UL sess=%d, UL flows=%d, DL flows=%s",
              ulSess.size(), ulUpfFlow.size(), dlUpfFlow.size());
    }
}
