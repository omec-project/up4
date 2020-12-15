/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.behavior;

import org.apache.commons.lang3.tuple.Pair;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.UpfFlow;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammable;
import org.omecproject.up4.UpfProgrammableException;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockUpfProgrammable implements UpfProgrammable {
    List<PacketDetectionRule> pdrs;
    List<ForwardingActionRule> fars;
    List<UpfInterface> ifaces;
    DeviceId deviceId;
    long ueLimit = -1;

    public MockUpfProgrammable() {
        pdrs = new ArrayList<>();
        fars = new ArrayList<>();
        ifaces = new ArrayList<>();
    }

    @Override
    public boolean init(ApplicationId appId, DeviceId deviceId) {
        pdrs.clear();
        fars.clear();
        ifaces.clear();
        this.deviceId = deviceId;
        return true;
    }

    @Override
    public void cleanUp(ApplicationId appId) {
        pdrs.clear();
        fars.clear();
        ifaces.clear();
    }

    @Override
    public DeviceId deviceId() {
        return deviceId;
    }

    private PdrStats readPhonyCounter(int cellId) {
        return PdrStats.builder()
                .withCellId(cellId)
                .setIngress(TestConstants.COUNTER_PKTS, TestConstants.COUNTER_BYTES)
                .setEgress(TestConstants.COUNTER_PKTS, TestConstants.COUNTER_BYTES)
                .build();
    }

    @Override
    public Collection<UpfFlow> getFlows() {
        Map<Pair<ImmutableByteSequence, Integer>, List<PacketDetectionRule>> farIdToPdrs = new HashMap<>();
        pdrs.forEach(pdr -> {
            farIdToPdrs.compute(Pair.of(pdr.sessionId(), pdr.farId()), (k, existingVal) -> {
                if (existingVal == null) {
                    return List.of(pdr);
                } else {
                    existingVal.add(pdr);
                    return existingVal;
                }
            });
        });

        List<UpfFlow> results = new ArrayList<>();
        for (ForwardingActionRule far : fars) {
            Pair<ImmutableByteSequence, Integer> fullFarId = Pair.of(far.sessionId(), far.farId());
            for (PacketDetectionRule pdr : farIdToPdrs.getOrDefault(fullFarId, List.of())) {
                results.add(UpfFlow.builder()
                        .setPdr(pdr)
                        .setFar(far)
                        .addStats(readPhonyCounter(pdr.counterId()))
                        .build());
            }
        }
        return results;
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
    public void setUeLimit(long ueLimit) {
        this.ueLimit = ueLimit;
    }

    @Override
    public void addPdr(PacketDetectionRule pdr) throws UpfProgrammableException {
        pdrs.add(pdr);
    }

    private <E> void removeEntry(List<E> entries, E entryToRemove) throws UpfProgrammableException {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).equals(entryToRemove)) {
                entries.remove(i);
                return;
            }
        }
        throw new UpfProgrammableException("Entry " + entryToRemove.toString() + " not found");
    }

    @Override
    public void removePdr(PacketDetectionRule pdr) throws UpfProgrammableException {
        removeEntry(pdrs, pdr);
    }

    @Override
    public void addFar(ForwardingActionRule far) throws UpfProgrammableException {
        fars.add(far);
    }

    @Override
    public void removeFar(ForwardingActionRule far) throws UpfProgrammableException {
        removeEntry(fars, far);
    }

    @Override
    public void addInterface(UpfInterface upfInterface) throws UpfProgrammableException {
        ifaces.add(upfInterface);
    }

    @Override
    public void removeInterface(UpfInterface upfInterface) throws UpfProgrammableException {
        removeEntry(ifaces, upfInterface);
    }

    @Override
    public PdrStats readCounter(int cellId) throws UpfProgrammableException {
        if (cellId >= TestConstants.PHYSICAL_COUNTER_SIZE || cellId < 0) {
            throw new UpfProgrammableException("PDR counter index out of bounds");
        }
        return readPhonyCounter(cellId);
    }

    @Override
    public long pdrCounterSize() {
        return TestConstants.PHYSICAL_COUNTER_SIZE;
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
    public Collection<PdrStats> readAllCounters() {
        List<PdrStats> allStats = new ArrayList<>();
        for (int i = 0; i < TestConstants.PHYSICAL_COUNTER_SIZE; i++) {
            allStats.add(readPhonyCounter(i));
        }
        return allStats;
    }

    @Override
    public void setDbufTunnel(Ip4Address switchAddr, Ip4Address dbufAddr) {

    }

    @Override
    public void setBufferDrainer(BufferDrainer drainer) {

    }
}
