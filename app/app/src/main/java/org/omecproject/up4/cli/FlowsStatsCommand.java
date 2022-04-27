/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2022-present Intel Corporation
 */
package org.omecproject.up4.cli;

import com.google.common.collect.Maps;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.impl.Up4AdminService;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfCounter;

import java.util.Map;

/**
 * UP4 UE flow stats.
 */
@Service
@Command(scope = "up4", name = "flow-stats",
        description = "Read all UE flows stats")
public class FlowsStatsCommand extends AbstractShellCommand {

    private static final String SEPARATOR = "-".repeat(40);
    private static final int DEFAULT_SLEEP_TIME = 2000; // 2 seconds

    @Argument(index = 0, name = "sleep-time",
            description = "Time between counter reads",
            required = false)
    int sleepTimeMs = DEFAULT_SLEEP_TIME;

    @Argument(index = 1, name = "ue-session-id",
            description = "UE Session ID (IPv4 UE address)",
            required = false)
    String ueSessionId = null;

    @Override
    protected void doExecute() throws Exception {
        Up4AdminService adminService = get(Up4AdminService.class);
        Ip4Address ueAddr = ueSessionId != null ? Ip4Address.valueOf(ueSessionId) : null;

        Map<Ip4Address, UpfCounter> beforeDownlink = Maps.newHashMap();
        adminService.getDownlinkFlows().stream()
                .filter(upfFlow -> ueAddr == null || ueAddr.equals(upfFlow.getTermination().ueSessionId()))
                .forEach(
                        dlUpfFlow -> beforeDownlink.compute(
                                dlUpfFlow.getTermination().ueSessionId(),
                                (key, value) -> (value == null) ?
                                        dlUpfFlow.getCounter() :
                                        sumUpfCounters(value, dlUpfFlow.getCounter())
                        )
                );

        Map<Ip4Address, UpfCounter> beforeUplink = Maps.newHashMap();
        adminService.getUplinkFlows().stream()
                .filter(upfFlow -> ueAddr == null || ueAddr.equals(upfFlow.getTermination().ueSessionId()))
                .forEach(
                        ulUpfFlow -> beforeUplink.compute(
                                ulUpfFlow.getTermination().ueSessionId(),
                                (key, value) -> (value == null) ?
                                        ulUpfFlow.getCounter() :
                                        sumUpfCounters(value, ulUpfFlow.getCounter())
                        )
                );

        if (beforeDownlink.isEmpty() || beforeUplink.isEmpty()) {
            print("No UE Sessions");
            return;
        }
        Thread.sleep(sleepTimeMs);

        Map<Ip4Address, UpfCounter> afterDownlink = Maps.newHashMap();
        adminService.getDownlinkFlows().stream()
                .filter(upfFlow -> ueAddr == null || ueAddr.equals(upfFlow.getTermination().ueSessionId()))
                .forEach(
                        dlUpfFlow -> afterDownlink.compute(
                                dlUpfFlow.getTermination().ueSessionId(),
                                (key, value) -> (value == null) ?
                                        dlUpfFlow.getCounter() :
                                        sumUpfCounters(value, dlUpfFlow.getCounter())
                        )
                );

        Map<Ip4Address, UpfCounter> afterUplink = Maps.newHashMap();
        adminService.getUplinkFlows().stream()
                .filter(upfFlow -> ueAddr == null || ueAddr.equals(upfFlow.getTermination().ueSessionId()))
                .forEach(
                        ulUpfFlow -> afterUplink.compute(
                                ulUpfFlow.getTermination().ueSessionId(),
                                (key, value) -> (value == null) ?
                                        ulUpfFlow.getCounter() :
                                        sumUpfCounters(value, ulUpfFlow.getCounter())
                        )
                );

        if (!(beforeDownlink.keySet().containsAll(beforeUplink.keySet()) &&
                beforeDownlink.keySet().equals(afterUplink.keySet()) &&
                beforeDownlink.keySet().equals(afterDownlink.keySet()))) {
            print("Error while reading stats!");
            return;
        }

        print(SEPARATOR);
        for (Ip4Address ueSessionId : beforeDownlink.keySet()) {
            if (ueAddr == null || ueAddr.equals(ueSessionId)) {
                print("UE session: " + ueSessionId.toString());
                UpfCounter bfUl = beforeUplink.get(ueSessionId);
                UpfCounter bfDl = beforeDownlink.get(ueSessionId);
                UpfCounter afUl = afterUplink.get(ueSessionId);
                UpfCounter afDl = afterDownlink.get(ueSessionId);
                long droppedPktsUl = (afUl.getIngressPkts() - afUl.getEgressPkts()) -
                        (bfUl.getIngressPkts() - bfUl.getEgressPkts());
                long droppedPktsDl = (afDl.getIngressPkts() - afDl.getEgressPkts()) -
                        (bfDl.getIngressPkts() - bfDl.getEgressPkts());

                double txPktsUl = (afUl.getEgressPkts() - bfUl.getEgressPkts()) / (sleepTimeMs / 1000.0);
                double txBytesUl = ((afUl.getEgressBytes() - bfUl.getEgressBytes()) * 8) / (sleepTimeMs / 1000.0);
                double txPktsDl = (afDl.getEgressPkts() - bfDl.getEgressPkts()) / (sleepTimeMs / 1000.0);
                double txBytesDl = ((afDl.getEgressBytes() - bfDl.getEgressBytes()) * 8) / (sleepTimeMs / 1000.0);

                double rxPktsUl = (afUl.getIngressPkts() - bfUl.getIngressPkts()) / (sleepTimeMs / 1000.0);
                double rxPktsDl = (afDl.getIngressPkts() - bfDl.getIngressPkts()) / (sleepTimeMs / 1000.0);

                print("Uplink");
                print("  tput: %s / %s (%.1f%% dropped)",
                      toReadable(txBytesUl, "bps"),
                      toReadable(txPktsUl, "pps"),
                      rxPktsUl == 0 ? 0.0 : (droppedPktsUl / rxPktsUl) * 100.0);
                print("Downlink");
                print("  tput: %s / %s (%.1f%% dropped)",
                      toReadable(txBytesDl, "bps"),
                      toReadable(txPktsDl, "pps"),
                      rxPktsDl == 0 ? 0.0 : (droppedPktsDl / rxPktsDl) * 100.0);
                print(SEPARATOR);
            }
        }
    }

    private String toReadable(double value, String unit) {
        if (value < 1000) {
            return String.format("%.3f %s", value, unit);
        } else if (value < 1000000) {
            return String.format("%.3f K%s", value / 1000.0, unit);
        } else if (value < 1000000000) {
            return String.format("%.3f M%s", value / 1000000.0, unit);
        } else {
            return String.format("%.3f G%s", value / 1000000000.0, unit);
        }
    }

    private UpfCounter sumUpfCounters(UpfCounter first, UpfCounter second) {
        return UpfCounter.builder()
                .setIngress(first.getIngressPkts() + second.getIngressPkts(),
                            first.getIngressBytes() + second.getIngressBytes())
                .setEgress(first.getEgressPkts() + second.getEgressPkts(),
                           first.getEgressBytes() + second.getEgressBytes())
                .withCellId(first.getCellId())
                .build();
    }
}
