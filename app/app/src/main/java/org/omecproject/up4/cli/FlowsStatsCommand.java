/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2022-present Intel Corporation
 */
package org.omecproject.up4.cli;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.omecproject.up4.impl.DownlinkUpfFlow;
import org.omecproject.up4.impl.Up4AdminService;
import org.omecproject.up4.impl.UplinkUpfFlow;
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.behaviour.upf.UpfCounter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

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

    @Option(name = "-w", aliases = "--watch",
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
            Map<Ip4Address, UpfCounter> beforeDownlink =
                    sumDownlinkUpfCountersPerSession(adminService.getDownlinkFlows(), ueAddr);
            Map<Ip4Address, UpfCounter> beforeUplink =
                    sumUplinkUpfCountersPerSession(adminService.getUplinkFlows(), ueAddr);

            if (beforeDownlink.isEmpty() || beforeUplink.isEmpty()) {
                print("No UE Sessions\n");
                if (cont) {
                    Thread.sleep((int) (sleepTimeS * 1000));
                }
                continue;
            }

            Thread.sleep((int) (sleepTimeS * 1000));

            Map<Ip4Address, UpfCounter> afterDownlink =
                    sumDownlinkUpfCountersPerSession(adminService.getDownlinkFlows(), ueAddr);
            Map<Ip4Address, UpfCounter> afterUplink =
                    sumUplinkUpfCountersPerSession(adminService.getUplinkFlows(), ueAddr);

            if (beforeDownlink.keySet().containsAll(beforeUplink.keySet()) &&
                    beforeDownlink.keySet().equals(afterUplink.keySet()) &&
                    beforeDownlink.keySet().equals(afterDownlink.keySet())) {
                print(SEPARATOR);
                for (Ip4Address ueSessionId : beforeDownlink.keySet()) {
                    if (ueAddr == null || ueAddr.equals(ueSessionId)) {
                        print("Session: " + ueSessionId.toString());
                        UpfCounter bfUl = beforeUplink.get(ueSessionId);
                        UpfCounter bfDl = beforeDownlink.get(ueSessionId);
                        UpfCounter afUl = afterUplink.get(ueSessionId);
                        UpfCounter afDl = afterDownlink.get(ueSessionId);

                        long afUlIngressPkts = afUl.getIngressPkts().orElse(0L);
                        long afUlEgressPkts = afUl.getEgressPkts().orElse(0L);
                        long afDlIngressPkts = afDl.getIngressPkts().orElse(0L);
                        long afDlEgressPkts = afDl.getEgressPkts().orElse(0L);

                        long afUlIngressBytes = afUl.getIngressBytes().orElse(0L);
                        long afUlEgressBytes = afUl.getEgressBytes().orElse(0L);
                        long afDlIngressBytes = afDl.getIngressBytes().orElse(0L);
                        long afDlEgressBytes = afDl.getEgressBytes().orElse(0L);

                        long bfUlIngressPkts = bfUl.getIngressPkts().orElse(0L);
                        long bfUlEgressPkts = bfUl.getEgressPkts().orElse(0L);
                        long bfDlIngressPkts = bfDl.getIngressPkts().orElse(0L);
                        long bfDlEgressPkts = bfDl.getEgressPkts().orElse(0L);

                        long bfUlIngressBytes = bfUl.getIngressBytes().orElse(0L);
                        long bfUlEgressBytes = bfUl.getEgressBytes().orElse(0L);
                        long bfDlIngressBytes = bfDl.getIngressBytes().orElse(0L);
                        long bfDlEgressBytes = bfDl.getEgressBytes().orElse(0L);

                        long rxPktsUl = afUlIngressPkts - bfUlIngressPkts;
                        long rxPktsDl = afDlIngressPkts - bfDlIngressPkts;
                        long txPktsUl = afUlEgressPkts - bfUlEgressPkts;
                        long txPktsDl = afDlEgressPkts - bfDlEgressPkts;

                        long rxBitsUl = (afUlIngressBytes - bfUlIngressBytes) * 8;
                        long rxBitsDl = (afDlIngressBytes - bfDlIngressBytes) * 8;
                        long txBitsUl = (afUlEgressBytes - bfUlEgressBytes) * 8;
                        long txBitsDl = (afDlEgressBytes - bfDlEgressBytes) * 8;

                        long droppedPktsUl = (afUlIngressPkts - afUlEgressPkts) -
                                (bfUlIngressPkts - bfUlEgressPkts);
                        long droppedPktsDl = (afDlIngressPkts - afDlEgressPkts) -
                                (bfDlIngressPkts - bfDlEgressPkts);
                        long droppedBitsUl = ((afUlIngressPkts - afUlEgressPkts) -
                                (bfUlIngressPkts - bfUlEgressPkts)) * 8;
                        long droppedBitsDl = ((afDlIngressBytes - afDlEgressBytes) -
                                (bfDlIngressBytes - bfDlEgressBytes)) * 8;

                        if (droppedPktsUl > rxPktsUl) {
                            print("  Uplink: more dropped packets than received! (%d > %d)", droppedPktsUl, rxPktsUl);
                        }
                        print("  Uplink: %s / %s (%.2f%% dropped)",
                              toReadable((double) txBitsUl / sleepTimeS, "bps"),
                              toReadable((double) txPktsUl / sleepTimeS, "pps"),
                              rxPktsUl == 0 ? 0.0 : ((double) droppedPktsUl / (double) rxPktsUl) * 100.0);
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

                        if (droppedPktsDl > rxPktsDl) {
                            print("  Downlink: more dropped packets than received! (%d > %d)", droppedPktsDl, rxPktsDl);
                        }
                        print("  Downlink: %s / %s (%.2f%% dropped)",
                              toReadable((double) txBitsDl / sleepTimeS, "bps"),
                              toReadable((double) txPktsDl / sleepTimeS, "pps"),
                              rxPktsDl == 0 ? 0.0 : ((double) droppedPktsDl / (double) rxPktsDl) * 100.0);
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
            } else {
                print("Error while reading stats!\n");
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
                .setIngress(first.getIngressPkts().orElse(0L) + second.getIngressPkts().orElse(0L),
                            first.getIngressBytes().orElse(0L) + second.getIngressBytes().orElse(0L))
                .setEgress(first.getEgressPkts().orElse(0L) + second.getEgressPkts().orElse(0L),
                           first.getEgressBytes().orElse(0L) + second.getEgressBytes().orElse(0L))
                .withCellId(first.getCellId())
                .build();
    }

    private Map<Ip4Address, UpfCounter> sumDownlinkUpfCountersPerSession(
            Collection<DownlinkUpfFlow> downlinkUpfFlowList, Ip4Address ueAddr) {
        Map<Ip4Address, Set<UpfCounter>> dlUpfCountersPerSession = Maps.newHashMap();
        downlinkUpfFlowList.stream()
                .filter(upfFlow -> ueAddr == null || ueAddr.equals(upfFlow.getTermination().ueSessionId()))
                .forEach(dlUpfFlow -> dlUpfCountersPerSession.compute(
                        dlUpfFlow.getTermination().ueSessionId(),
                        (key, value) -> {
                            if (value == null) {
                                return Sets.newHashSet(dlUpfFlow.getCounter());
                            } else {
                                value.add(dlUpfFlow.getCounter());
                                return value;
                            }
                        })
                );
        return sumCountersPerSession(dlUpfCountersPerSession);
    }

    private Map<Ip4Address, UpfCounter> sumUplinkUpfCountersPerSession(
            Collection<UplinkUpfFlow> uplinkUpfFlowList, Ip4Address ueAddr) {
        Map<Ip4Address, Set<UpfCounter>> ulUpfCountersPerSession = Maps.newHashMap();
        uplinkUpfFlowList.stream()
                .filter(upfFlow -> ueAddr == null || ueAddr.equals(upfFlow.getTermination().ueSessionId()))
                .forEach(ulUpfFlow -> ulUpfCountersPerSession.compute(
                        ulUpfFlow.getTermination().ueSessionId(),
                        (key, value) -> {
                            if (value == null) {
                                return Sets.newHashSet(ulUpfFlow.getCounter());
                            } else {
                                value.add(ulUpfFlow.getCounter());
                                return value;
                            }
                        })
                );
        return sumCountersPerSession(ulUpfCountersPerSession);
    }

    private Map<Ip4Address, UpfCounter> sumCountersPerSession(
            Map<Ip4Address, Set<UpfCounter>> countersPerSession) {
        Map<Ip4Address, UpfCounter> sumCountersSession = Maps.newHashMap();
        countersPerSession.forEach((ueSession, upfCounters) -> {
            UpfCounter result = null;
            for (UpfCounter upfCounter : upfCounters) {
                if (result == null) {
                    result = upfCounter;
                } else {
                    result = sumUpfCounters(result, upfCounter);
                }
            }
            sumCountersSession.put(ueSession, result);
        });
        return sumCountersSession;
    }
}
