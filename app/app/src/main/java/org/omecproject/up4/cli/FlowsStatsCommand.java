/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2022-present Intel Corporation
 */
package org.omecproject.up4.cli;

import com.google.common.collect.Maps;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.impl.Up4AdminService;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfCounter;

import java.util.Map;

/**
 * UP4 UE traffic statistics.
 */
@Service
@Command(scope = "up4", name = "upf-stats",
        description = "Read all UE flows stats")
public class FlowsStatsCommand extends AbstractShellCommand {

    private static final String SEPARATOR = "-".repeat(40);
    private static final float DEFAULT_SLEEP_TIME = 2; // 2 seconds

    @Option(name = "-t", aliases = "--time",
            description = "Time between counter reads (seconds)",
            valueToShowInHelp = "2")
    float sleepTimeS = DEFAULT_SLEEP_TIME;

    @Option(name = "-s", aliases = "--session",
            description = "UE Session ID (IPv4 UE address)",
            valueToShowInHelp = "10.0.0.1")
    String ueSessionId = null;

    @Option(name = "-c", aliases = "--continue",
            description = "Continue to print stats, until ctrl+c")
    boolean cont = false;

    @Option(name = "-d", aliases = "--debug",
            description = "Print debug stats")
    boolean debug = false;

    @Override
    protected void doExecute() throws Exception {
        Up4AdminService adminService = get(Up4AdminService.class);
        Ip4Address ueAddr = ueSessionId != null ? Ip4Address.valueOf(ueSessionId) : null;
        do {
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
                print("No UE Sessions\n");
                if (cont) {
                    Thread.sleep((int) (sleepTimeS * 1000));
                }
                continue;
            }

            Thread.sleep((int) (sleepTimeS * 1000));

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
                print("Error while reading stats!\n");
                continue;
            }

            print(SEPARATOR);
            for (Ip4Address ueSessionId : beforeDownlink.keySet()) {
                if (ueAddr == null || ueAddr.equals(ueSessionId)) {
                    print("Session: " + ueSessionId.toString());
                    UpfCounter bfUl = beforeUplink.get(ueSessionId);
                    UpfCounter bfDl = beforeDownlink.get(ueSessionId);
                    UpfCounter afUl = afterUplink.get(ueSessionId);
                    UpfCounter afDl = afterDownlink.get(ueSessionId);

                    long rxPktsUl = afUl.getIngressPkts() - bfUl.getIngressPkts();
                    long rxPktsDl = afDl.getIngressPkts() - bfDl.getIngressPkts();
                    long txPktsUl = afUl.getEgressPkts() - bfUl.getEgressPkts();
                    long txPktsDl = afDl.getEgressPkts() - bfDl.getEgressPkts();

                    long rxBitsUl = (afUl.getIngressBytes() - bfUl.getIngressBytes()) * 8;
                    long rxBitsDl = (afDl.getIngressBytes() - bfDl.getIngressBytes()) * 8;
                    long txBitsUl = (afUl.getEgressBytes() - bfUl.getEgressBytes()) * 8;
                    long txBitsDl = (afDl.getEgressBytes() - bfDl.getEgressBytes()) * 8;

                    long droppedPktsUl = (afUl.getIngressPkts() - afUl.getEgressPkts()) -
                            (bfUl.getIngressPkts() - bfUl.getEgressPkts());
                    long droppedPktsDl = (afDl.getIngressPkts() - afDl.getEgressPkts()) -
                            (bfDl.getIngressPkts() - bfDl.getEgressPkts());
                    long droppedBitsUl = ((afUl.getIngressPkts() - afUl.getEgressPkts()) -
                            (bfUl.getIngressPkts() - bfUl.getEgressPkts())) * 8;
                    long droppedBitsDl = ((afDl.getIngressBytes() - afDl.getEgressBytes()) -
                            (bfDl.getIngressBytes() - bfDl.getEgressBytes())) * 8;

                    print("  Uplink: %s / %s (%.2f%% dropped)",
                          toReadable((double) txBitsUl / sleepTimeS, "bps"),
                          toReadable((double) txPktsUl / sleepTimeS, "pps"),
                          rxPktsUl == 0 ? 0.0 : ((double) droppedPktsUl / (double) rxPktsUl) * 100.0);
                    if (droppedPktsUl > rxPktsUl) {
                        print("    More dropped packets than received! (%d > %d)", droppedPktsUl, rxPktsUl);
                    }
                    if (debug) {
                        print("    RX: %s / %s",
                              toReadable(rxPktsUl, "pkts"),
                              toReadable(rxBitsUl, "bit"));
                        print("    TX: %s / %s",
                              toReadable(txPktsUl, "pkts"),
                              toReadable(txBitsUl, "bit"));
                        print("    Dropped: %s / %s",
                              toReadable(droppedPktsUl, "pkts"),
                              toReadable(droppedBitsUl, "bit"));
                    }

                    print("  Downlink: %s / %s (%.2f%% dropped)",
                          toReadable((double) txBitsDl / sleepTimeS, "bps"),
                          toReadable((double) txPktsDl / sleepTimeS, "pps"),
                          rxPktsDl == 0 ? 0.0 : ((double) droppedPktsDl / (double) rxPktsDl) * 100.0);
                    if (droppedPktsDl > rxPktsDl) {
                        print("    More dropped packets than received! (%d > %d)", droppedPktsDl, rxPktsDl);
                    }
                    if (debug) {
                        print("    RX: %s / %s",
                              toReadable(rxPktsDl, "pkts"),
                              toReadable(rxBitsDl, "bit"));
                        print("    TX: %s / %s",
                              toReadable(txPktsDl, "pkts"),
                              toReadable(txBitsDl, "bit"));
                        print("    Dropped: %s / %s",
                              toReadable(droppedPktsDl, "pkts"),
                              toReadable(droppedBitsDl, "bit"));
                    }
                    print(SEPARATOR);
                }
            }
            if (cont) {
                print("");
            }
        } while (cont);
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

    private String toReadable(long value, String unit) {
        if (value < 1000) {
            return String.format("%d %s", value, unit);
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
