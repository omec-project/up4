/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.collect.Maps;
import org.glassfish.jersey.internal.guava.Sets;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.Up4EventListener;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.UpfFlow;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammable;
import org.omecproject.up4.behavior.TestConstants;
import org.omecproject.up4.UpfRuleIdentifier;
import org.onlab.packet.Ip4Address;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MockUp4Service implements Up4Service {
    boolean upfProgrammableAvailable = true;
    boolean configAvailable = true;
    UpfProgrammable upfProgrammable = new UpfProgrammable() {
        final List<PacketDetectionRule> pdrs = new ArrayList<>();
        final List<ForwardingActionRule> fars = new ArrayList<>();
        final List<UpfInterface> ifaces = new ArrayList<>();

        @Override
        public boolean init(ApplicationId appId, DeviceId deviceId) {
            return true;
        }

        @Override
        public void setUeLimit(long ueLimit) {

        }

        @Override
        public void cleanUp(ApplicationId appId) {

        }

        @Override
        public long farTableSize() {
            return TestConstants.PHYSICAL_MAX_FARS;
        }

        @Override
        public long pdrTableSize() {
            return TestConstants.PHYSICAL_MAX_PDRS;
        }

        @Override
        public long pdrCounterSize() {
            return TestConstants.PHYSICAL_COUNTER_SIZE;
        }

        @Override
        public void setBufferDrainer(BufferDrainer drainer) {
        }

        @Override
        public void setDbufTunnel(Ip4Address switchAddr, Ip4Address dbufAddr) {
        }

        @Override
        public DeviceId deviceId() {
            return null;
        }

        @Override
        public Set<UpfRuleIdentifier> getBufferFarIds() {
            return Sets.newHashSet();
        }

        @Override
        public Map<UpfRuleIdentifier, Set<Ip4Address>> getFarIdToUeAddrs() {
            return Maps.newHashMap();
        }

        @Override
        public Collection<UpfFlow> getFlows() {
            return null;
        }

        @Override
        public void clearInterfaces() {
            ifaces.clear();

        }

        @Override
        public void clearFlows() {
            pdrs.clear();
            fars.clear();
        }

        @Override
        public Collection<ForwardingActionRule> getInstalledFars() {
            return List.copyOf(fars);
        }

        @Override
        public Collection<PacketDetectionRule> getInstalledPdrs() {
            return List.copyOf(pdrs);
        }

        @Override
        public Collection<UpfInterface> getInstalledInterfaces() {
            return List.copyOf(ifaces);
        }

        @Override
        public void addPdr(PacketDetectionRule pdr) {
            pdrs.add(pdr);

        }

        @Override
        public void removePdr(PacketDetectionRule pdr) {
            int index = pdrs.indexOf(pdr);
            if (index != -1) {
                pdrs.remove(index);
            }
        }

        @Override
        public void addFar(ForwardingActionRule far) {
            fars.add(far);
        }

        @Override
        public void removeFar(ForwardingActionRule far) {
            int index = fars.indexOf(far);
            if (index != -1) {
                fars.remove(index);
            }
        }

        @Override
        public void addInterface(UpfInterface upfInterface) {
            ifaces.add(upfInterface);

        }

        @Override
        public void removeInterface(UpfInterface upfInterface) {
            int index = ifaces.indexOf(upfInterface);
            if (index != -1) {
                ifaces.remove(index);
            }
        }

        @Override
        public PdrStats readCounter(int cellId) {
            return PdrStats.builder()
                    .withCellId(cellId)
                    .setEgress(NorthTestConstants.EGRESS_COUNTER_PKTS, NorthTestConstants.EGRESS_COUNTER_BYTES)
                    .setIngress(NorthTestConstants.INGRESS_COUNTER_PKTS, NorthTestConstants.INGRESS_COUNTER_BYTES)
                    .build();
        }

        @Override
        public Collection<PdrStats> readAllCounters() {
            List<PdrStats> stats = new ArrayList<>();
            for (int i = 0; i < TestConstants.PHYSICAL_COUNTER_SIZE; i++) {
                stats.add(PdrStats.builder()
                        .withCellId(i)
                        .setEgress(NorthTestConstants.EGRESS_COUNTER_PKTS,
                                NorthTestConstants.EGRESS_COUNTER_BYTES)
                        .setIngress(NorthTestConstants.INGRESS_COUNTER_PKTS,
                                NorthTestConstants.INGRESS_COUNTER_BYTES)
                        .build());
            }
            return stats;
        }
    };

    public void hideState(boolean hideUpfProgrammable, boolean hideConfig) {
        upfProgrammableAvailable = hideUpfProgrammable;
        configAvailable = hideConfig;
    }

    @Override
    public UpfProgrammable getUpfProgrammable() {
        if (upfProgrammableAvailable) {
            return upfProgrammable;
        } else {
            return null;
        }
    }

    @Override
    public boolean upfProgrammableAvailable() {
        return upfProgrammableAvailable;
    }

    @Override
    public void clearUpfProgrammable() {
    }

    @Override
    public void installInterfaces() {
    }

    @Override
    public boolean configIsLoaded() {
        return configAvailable;
    }

    @Override
    public boolean isUpfDevice(DeviceId deviceId) {
        return true;
    }

    @Override
    public void addListener(Up4EventListener listener) {

    }

    @Override
    public void removeListener(Up4EventListener listener) {

    }
}
