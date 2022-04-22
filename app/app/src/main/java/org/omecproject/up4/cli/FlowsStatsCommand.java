/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2022-present Intel Corporation
 */
package org.omecproject.up4.cli;

import com.google.common.collect.Maps;
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
    private static final int SLEEP_TIME_MS = 200;

    @Override
    protected void doExecute() throws Exception {
        Up4AdminService adminService = get(Up4AdminService.class);

        Map<Ip4Address, UpfCounter> beforeDownlink = Maps.newHashMap();
        adminService.getDownlinkFlows().forEach(dlUpfFlow -> {
            beforeDownlink.put(dlUpfFlow.getSession().ueAddress(), dlUpfFlow.getCounter());
        });

        Map<Ip4Address, UpfCounter> beforeUplink = Maps.newHashMap();
        adminService.getUplinkFlows().forEach(ulUpfFlow -> {
            beforeUplink.put(ulUpfFlow.getTermination().ueSessionId(), ulUpfFlow.getCounter());
        });
        Thread.sleep(SLEEP_TIME_MS);

        Map<Ip4Address, UpfCounter> afterDownlink = Maps.newHashMap();
        adminService.getDownlinkFlows().forEach(dlUpfFlow -> {
            afterDownlink.put(dlUpfFlow.getSession().ueAddress(), dlUpfFlow.getCounter());
        });

        Map<Ip4Address, UpfCounter> afterUplink = Maps.newHashMap();
        adminService.getUplinkFlows().forEach(ulUpfFlow -> {
            afterUplink.put(ulUpfFlow.getTermination().ueSessionId(), ulUpfFlow.getCounter());
        });

        print(SEPARATOR);
        if (!(beforeDownlink.keySet().equals(beforeUplink.keySet()) &&
                beforeDownlink.keySet().equals(afterUplink) &&
                beforeDownlink.keySet().equals(afterDownlink))) {
            print("ERROR while reading stats!");
            return;
        }
        for (Ip4Address ueSessionId : beforeDownlink.keySet()) {
            if (!beforeUplink.containsKey(ueSessionId) || !afterDownlink.containsKey(ueSessionId) || !afterUplink.containsKey())
            print("UE session: " + ueSessionId.toString());
            UpfCounter bfUl = beforeUplink.get(ueSessionId);
            UpfCounter bfDl = beforeDownlink.get(ueSessionId);
            UpfCounter afUl = afterUplink.get(ueSessionId);
            UpfCounter afDl = afterDownlink.get(ueSessionId);
            long droppedPktsUl = (afUl.getIngressPkts() - afUl.getEgressPkts()) -
                    (bfUl.getIngressPkts() - bfUl.getEgressPkts());
            long droppedPktsDl = (afDl.getIngressPkts() - afDl.getEgressPkts()) -
                    (bfDl.getIngressPkts() - bfDl.getEgressPkts());

            long txPktsUl = (afUl.getEgressPkts() - bfUl.getEgressPkts()) / SLEEP_TIME_MS;
            long txBytesUl = (afUl.getEgressBytes() - bfUl.getEgressBytes()) / SLEEP_TIME_MS;
            long txPktsDl = (afDl.getEgressPkts() - bfDl.getEgressPkts()) / SLEEP_TIME_MS;
            long txBytesDl = (afDl.getEgressBytes() - bfDl.getEgressBytes()) / SLEEP_TIME_MS;

            long rxPktsUl = (afUl.getIngressPkts() - bfUl.getIngressPkts()) / SLEEP_TIME_MS;
            long rxBytesUl = (afUl.getIngressBytes() - bfUl.getIngressBytes()) / SLEEP_TIME_MS;
            long rxPktsDl = (afDl.getIngressPkts() - bfDl.getIngressPkts()) / SLEEP_TIME_MS;
            long rxBytesDl = (afDl.getIngressBytes() - bfDl.getIngressBytes()) / SLEEP_TIME_MS;

            print("UPLINK");
            print("    RX:  %s  %s", toReadable(rxBytesUl, "Bps"), toReadable(rxPktsUl, "pkt/s"));
            print("    TX:  %s  %s", toReadable(txBytesUl, "Bps"), toReadable(txPktsUl, "pkt/s"));
            print("    DROPPED:  %d", droppedPktsDl);
            print("DOWNLINK");
            print("    RX:  %s  %s", toReadable(rxBytesDl, "Bps"), toReadable(rxPktsDl, "pkt/s"));
            print("    TX:  %s  %s", toReadable(txBytesDl, "Bps"), toReadable(txPktsDl, "pkt/s"));
            print("    DROPPED:  %d", droppedPktsUl);
            print(SEPARATOR);
        }

    }

    String toReadable(long value, String unit) {
        if (value < 1000) {
            return String.format("%.1f %s", (float) value, unit);
        } else if (value < 1000000) {
            return String.format("%.1f K%s", value / 1000.0, unit);
        } else if (value < 1000000000) {
            return String.format("%.1f M%s", value / 1000000.0, unit);
        } else {
            return String.format("%.1f G%s", value / 1000000000.0, unit);
        }
    }
}
