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
import org.onosproject.net.behaviour.upf.UpfApplication;
import org.onosproject.net.behaviour.upf.SessionUplink;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;

import java.util.Collection;

/**
 * UP4 UE session read command.
 */
@Service
@Command(scope = "up4", name = "read-flows",
        description = "Read all UE data flows installed in the dataplane")
public class ReadFlowsCommand extends AbstractShellCommand {

    private static final String SEPARATOR = "-".repeat(40);

    @Override
    protected void doExecute() throws Exception {
        Up4AdminService adminService = get(Up4AdminService.class);

        Collection<DownlinkUpfFlow> dlUpfFlow = adminService.getDownlinkFlows();
        Collection<UplinkUpfFlow> ulUpfFlow = adminService.getUplinkFlows();
        Collection<? extends UpfEntity> ulSess = adminService.adminReadAll(UpfEntityType.SESSION_UPLINK);
        Collection<? extends UpfEntity> appFilters = adminService.adminReadAll(UpfEntityType.APPLICATION);

        print(SEPARATOR);
        print(appFilters.size() + " Application Filters");
        for (UpfEntity a : appFilters) {
            if (!a.type().equals(UpfEntityType.APPLICATION)) {
                print("ERROR: Wrong application filter: " + a);
                continue;
            }
            UpfApplication app = (UpfApplication) a;
            print("ipv4_prefix=" + app.ip4Prefix() +
                          ", l4_range=" + app.l4PortRange() +
                          ", ip_proto=" + app.ipProto()
            );
        }
        print(SEPARATOR);
        print(ulSess.size() + " Uplink Sessions");
        for (UpfEntity s : ulSess) {
            if (!s.type().equals(UpfEntityType.SESSION_UPLINK)) {
                print("ERROR: Wrong uplink session: " + s);
                continue;
            }
            SessionUplink sess = (SessionUplink) s;
            print("n3_addr=" + sess.tunDstAddr() +
                          ", teid=" + sess.teid() +
                          (sess.needsDropping() ? ", drop()" : ", fwd()")
            );
        }
        print(SEPARATOR);
        print(ulUpfFlow.size() + " Uplink Flows");
        for (UplinkUpfFlow f : ulUpfFlow) {
            print(f.toString());
        }
        print(SEPARATOR);
        print(dlUpfFlow.size() + " Downlink Flows");
        for (DownlinkUpfFlow f : dlUpfFlow) {
            print(f.toString());
        }
        print(SEPARATOR);
        print("App filters=%d, UL sess=%d, UL flows=%d, DL flows=%s",
              appFilters.size(), ulSess.size(), ulUpfFlow.size(), dlUpfFlow.size());
    }
}
