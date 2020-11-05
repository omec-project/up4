package org.omecproject.up4.behavior;

import org.apache.commons.lang3.tuple.Pair;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.Up4Exception;
import org.omecproject.up4.UpfFlow;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammable;
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
    public void addPdr(PacketDetectionRule pdr) throws Up4Exception {
        pdrs.add(pdr);

    }

    private <E> void removeEntry(List<E> entries, E entryToRemove) throws Up4Exception {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).equals(entryToRemove)) {
                entries.remove(i);
                return;
            }
        }
        throw new Up4Exception(Up4Exception.Type.ENTRY_NOT_FOUND, "Entry not found");
    }

    @Override
    public void removePdr(PacketDetectionRule pdr) throws Up4Exception {
        removeEntry(pdrs, pdr);
    }

    @Override
    public void addFar(ForwardingActionRule far) throws Up4Exception {
        fars.add(far);
    }

    @Override
    public void removeFar(ForwardingActionRule far) throws Up4Exception {
        removeEntry(fars, far);
    }

    @Override
    public void addInterface(UpfInterface upfInterface) throws Up4Exception {
        ifaces.add(upfInterface);
    }

    @Override
    public void removeInterface(UpfInterface upfInterface) throws Up4Exception {
        removeEntry(ifaces, upfInterface);
    }

    @Override
    public PdrStats readCounter(int cellId) throws Up4Exception {
        if (cellId >= TestConstants.PHYSICAL_COUNTER_SIZE || cellId < 0) {
            throw new Up4Exception(Up4Exception.Type.INVALID_COUNTER_INDEX,
                    "Counter index out of bounds");
        }
        return readPhonyCounter(cellId);
    }

    @Override
    public int pdrCounterSize() {
        return TestConstants.PHYSICAL_COUNTER_SIZE;
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
