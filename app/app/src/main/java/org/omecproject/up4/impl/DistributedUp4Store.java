/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onlab.util.KryoNamespace;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.EventuallyConsistentMapEvent;
import org.onosproject.store.service.EventuallyConsistentMapListener;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Distributed implementation of Up4Store.
 */
@Component(immediate = true, service = Up4Store.class)
public class DistributedUp4Store implements Up4Store {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    protected static final String BUFFER_FAR_ID_MAP_NAME = "up4-buffer-far-id";
    protected static final String FAR_ID_UE_MAP_NAME = "up4-far-id-ue";
    private static final String BUFFER_TAG = "";

    protected static final KryoNamespace.Builder SERIALIZER = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(ImmutablePair.class);

    // NOTE If we can afford to lose the buffer state, we can make this map a simple concurrent map.
    // This can happen in case of instance failure or change in the DNS resolution.
    protected EventuallyConsistentMap<ImmutablePair<ImmutableByteSequence, Integer>, String> bufferFarIds;
    protected EventuallyConsistentMap<ImmutablePair<ImmutableByteSequence, Integer>, Ip4Address> farIdToUeAddr;
    private EventuallyConsistentMapListener<ImmutablePair<ImmutableByteSequence, Integer>, Ip4Address>
            farIdToUeAddrListener;
    // Local, reversed copy of farIdToUeAddrMapper for reverse lookup
    protected Map<Ip4Address, ImmutablePair<ImmutableByteSequence, Integer>> reverseFarIdToUeAddr;

    @Activate
    protected void activate() {
        // Allow unit test to inject farIdMap here.
        if (storageService != null) {
            this.bufferFarIds =
                storageService.<ImmutablePair<ImmutableByteSequence, Integer>, String>eventuallyConsistentMapBuilder()
                            .withName(BUFFER_FAR_ID_MAP_NAME)
                            .withSerializer(SERIALIZER)
                            .withTimestampProvider((k, v) -> new WallClockTimestamp())
                            .build();
            this.farIdToUeAddr = storageService.
                            <ImmutablePair<ImmutableByteSequence, Integer>, Ip4Address>eventuallyConsistentMapBuilder()
                            .withName(FAR_ID_UE_MAP_NAME)
                            .withSerializer(SERIALIZER)
                            .withTimestampProvider((k, v) -> new WallClockTimestamp())
                            .build();
        }
        farIdToUeAddrListener = new FarIdToUeAddrMapListener();
        farIdToUeAddr.addListener(farIdToUeAddrListener);

        reverseFarIdToUeAddr = Maps.newConcurrentMap();
        farIdToUeAddr.entrySet().forEach(entry -> reverseFarIdToUeAddr.put(entry.getValue(), entry.getKey()));

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        this.farIdToUeAddr.removeListener(farIdToUeAddrListener);
        this.bufferFarIds.destroy();
        this.farIdToUeAddr.destroy();
        this.reverseFarIdToUeAddr.clear();

        log.info("Stopped");
    }

    @Override
    public void reset() {
        bufferFarIds.clear();
        farIdToUeAddr.clear();
        reverseFarIdToUeAddr.clear();
    }

    @Override
    public boolean isFarIdBuffering(ImmutablePair<ImmutableByteSequence, Integer> farId) {
        checkNotNull(farId);
        return bufferFarIds.containsKey(farId);
    }

    @Override
    public void learnBufferingFarId(ImmutablePair<ImmutableByteSequence, Integer> farId) {
        checkNotNull(farId);
        bufferFarIds.put(farId, BUFFER_TAG);
    }

    @Override
    public boolean forgetBufferingFarId(ImmutablePair<ImmutableByteSequence, Integer> farId) {
        checkNotNull(farId);
        return bufferFarIds.remove(farId) != null;
    }

    @Override
    public Set<ImmutablePair<ImmutableByteSequence, Integer>> getBufferFarIds() {
        return ImmutableSet.copyOf(bufferFarIds.keySet());
    }

    @Override
    public void learnFarIdToUeAddr(PacketDetectionRule pdr) {
        farIdToUeAddr.put(ImmutablePair.of(pdr.sessionId(), pdr.farId()), pdr.ueAddress());
    }


    @Override
    public Ip4Address ueAddrOfFarId(ImmutablePair<ImmutableByteSequence, Integer> farId) {
        return farIdToUeAddr.get(farId);
    }

    @Override
    public void forgetUeAddr(PacketDetectionRule pdr) {
        ImmutablePair<ImmutableByteSequence, Integer> ruleId = reverseFarIdToUeAddr.get(pdr.ueAddress());
        if (ruleId == null) {
            log.warn("Unable to find the pfcp session id and the local" +
                    "far id associated to {}", pdr.ueAddress());
            return;
        }
        farIdToUeAddr.remove(ruleId);
    }

    @Override
    public Map<ImmutablePair<ImmutableByteSequence, Integer>, Ip4Address> getFarIdsToUeAddrs() {
        return farIdToUeAddr.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Map<Ip4Address, ImmutablePair<ImmutableByteSequence, Integer>> getReverseFarIdsToUeAddrs() {
        return ImmutableMap.copyOf(reverseFarIdToUeAddr);
    }

    private class FarIdToUeAddrMapListener
            implements EventuallyConsistentMapListener<ImmutablePair<ImmutableByteSequence, Integer>, Ip4Address> {
        @Override
        public void event(
                EventuallyConsistentMapEvent<ImmutablePair<ImmutableByteSequence, Integer>, Ip4Address> event) {
            switch (event.type()) {
                case PUT:
                    reverseFarIdToUeAddr.put(event.value(), event.key());
                    break;
                case REMOVE:
                    reverseFarIdToUeAddr.remove(event.value());
                    break;
                default:
                    break;
            }
        }
    }
}
