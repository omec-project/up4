/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.cli;

import com.google.common.collect.Range;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.Up4Service;
import org.onlab.packet.Ip4Prefix;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.ApplicationFilter;

/**
 * UP4 UE session command.
 */
@Service
@Command(scope = "up4", name = "app-filter",
        description = "Insert (or delete) an application filtering into the UP4 dataplane")
public class ApplicationFilterCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "app-id",
            description = "Application ID for this application filter rule",
            required = true)
    Byte appId = null;

    @Argument(index = 0, name = "priority",
            description = "Priority this application filter rule",
            required = true)
    Integer priority = null;

    @Option(name = "--ip-prefix",
            description = "IPv4 Prefix",
            required = false)
    String ipPrefix = null;

    @Option(name = "--l4-port",
            description = "L4 Port",
            required = false)
    Short l4Port = null;

    @Option(name = "--ip-proto",
            description = "IP Proto value",
            required = false)
    Byte ipProto = null;

    @Option(name = "--delete", aliases = "-d",
            description = "Delete the given Application Filter",
            required = false)
    boolean delete = false;


    @Override
    protected void doExecute() throws Exception {
        Up4Service app = get(Up4Service.class);
        ApplicationFilter.Builder appFilterBuilder = ApplicationFilter.builder()
                .withAppId(appId)
                .withPriority(priority);
        boolean oneFilter = false;
        if (ipPrefix != null) {
            appFilterBuilder.withIp4Prefix(Ip4Prefix.valueOf(ipPrefix));
            oneFilter = true;
        }
        if (l4Port != null) {
            appFilterBuilder.withL4PortRange(Range.closed(l4Port, l4Port));
            oneFilter = true;
        }
        if (ipProto != null) {
            appFilterBuilder.withIpProto(ipProto);
            oneFilter = true;
        }
        if (!oneFilter) {
            print("At least one filter (IP Prefix, L4Port, IpProto) must be provided");
            return;
        }
        if (delete) {
            app.delete(appFilterBuilder.build());
        } else {
            app.apply(appFilterBuilder.build());
        }
    }
}
