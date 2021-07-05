/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.omecproject.up4.Up4EventListener;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.UpfFlow;
import org.onosproject.net.behaviour.upf.ForwardingActionRule;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;
import org.onosproject.net.behaviour.upf.PdrStats;
import org.onosproject.net.behaviour.upf.UpfInterface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MockUp4Service implements Up4Service {
    boolean upfProgrammableAvailable = true;
    boolean configAvailable = true;
    final List<PacketDetectionRule> pdrs = new ArrayList<>();
    final List<ForwardingActionRule> fars = new ArrayList<>();
    final List<UpfInterface> ifaces = new ArrayList<>();
    final List<ByteBuffer> sentPacketOuts = new ArrayList<>();

    public void hideState(boolean hideUpfProgrammable, boolean hideConfig) {
        upfProgrammableAvailable = !hideUpfProgrammable;
        configAvailable = !hideConfig;
    }

    @Override
    public boolean upfAvailable() {
        return upfProgrammableAvailable;
    }

    @Override
    public void installInterfaces() {
    }

    @Override
    public boolean configIsLoaded() {
        return configAvailable;
    }

    @Override
    public void addListener(Up4EventListener listener) {

    }

    @Override
    public void removeListener(Up4EventListener listener) {

    }

    @Override
    public void cleanUp() {

    }

    @Override
    public long farTableSize() {
        return TestImplConstants.PHYSICAL_MAX_FARS;
    }

    @Override
    public long pdrTableSize() {
        return TestImplConstants.PHYSICAL_MAX_PDRS;
    }

    @Override
    public long pdrCounterSize() {
        return TestImplConstants.PHYSICAL_COUNTER_SIZE;
    }

    @Override
    public void enablePscEncap(int defaultQfi) {

    }

    @Override
    public void disablePscEncap() {

    }

    @Override
    public void sendPacketOut(ByteBuffer data) {
        sentPacketOuts.add(data);
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
    public Collection<ForwardingActionRule> getFars() {
        return List.copyOf(fars);
    }

    @Override
    public Collection<PacketDetectionRule> getPdrs() {
        return List.copyOf(pdrs);
    }

    @Override
    public Collection<UpfInterface> getInterfaces() {
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
    public Collection<PdrStats> readAllCounters(long maxCounterId) {
        List<PdrStats> stats = new ArrayList<>();
        for (int i = 0; i < TestImplConstants.PHYSICAL_COUNTER_SIZE; i++) {
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
}
