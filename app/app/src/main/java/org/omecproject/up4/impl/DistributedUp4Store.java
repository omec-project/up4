/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onlab.util.KryoNamespace;
import org.onosproject.net.behaviour.upf.PacketDetectionRule;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.DistributedSet;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Distributed implementation of Up4Store.
 */
@Component(immediate = true, service = Up4Store.class)
public class DistributedUp4Store implements Up4Store {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;


    protected static final String BUFFER_FAR_ID_SET_NAME = "fabric-upf-buffer-far-id";
    protected static final String FAR_ID_UE_MAP_NAME = "fabric-upf-far-id-ue";

    protected static final KryoNamespace.Builder SERIALIZER = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(ImmutablePair.class);

    protected DistributedSet<ImmutablePair<ImmutableByteSequence, Integer>> bufferFarIds;
    protected ConsistentMap<ImmutablePair<ImmutableByteSequence, Integer>, Set<Ip4Address>> farIdToUeAddrs;

    @Activate
    protected void activate() {
        // Allow unit test to inject farIdMap here.
        if (storageService != null) {
            this.bufferFarIds =
                    storageService.<ImmutablePair<ImmutableByteSequence, Integer>>setBuilder()
                            .withName(BUFFER_FAR_ID_SET_NAME)
                            .withRelaxedReadConsistency()
                            .withSerializer(Serializer.using(SERIALIZER.build()))
                            .build().asDistributedSet();
            this.farIdToUeAddrs =
                    storageService.
                            <ImmutablePair<ImmutableByteSequence, Integer>, Set<Ip4Address>>consistentMapBuilder()
                            .withName(FAR_ID_UE_MAP_NAME)
                            .withRelaxedReadConsistency()
                            .withSerializer(Serializer.using(SERIALIZER.build()))
                            .build();
        }
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }

    @Override
    public void reset() {
        bufferFarIds.clear();
        farIdToUeAddrs.clear();
    }

    @Override
    public boolean isFarIdBuffering(ImmutablePair<ImmutableByteSequence, Integer> farId) {
        checkNotNull(farId);
        return bufferFarIds.contains(farId);
    }

    @Override
    public void learBufferingFarId(ImmutablePair<ImmutableByteSequence, Integer> farId) {
        checkNotNull(farId);
        bufferFarIds.add(farId);
    }

    @Override
    public void forgetBufferingFarId(ImmutablePair<ImmutableByteSequence, Integer> farId) {
        checkNotNull(farId);
        bufferFarIds.remove(farId);
    }

    @Override
    public Set<ImmutablePair<ImmutableByteSequence, Integer>> getBufferFarIds() {
        return Set.copyOf(bufferFarIds);
    }

    @Override
    public void learnFarIdToUeAddrs(PacketDetectionRule pdr) {
        var ruleId = ImmutablePair.of(pdr.sessionId(), pdr.farId());
        farIdToUeAddrs.compute(ruleId, (k, set) -> {
            if (set == null) {
                set = new HashSet<>();
            }
            set.add(pdr.ueAddress());
            return set;
        });
    }

    @Override
    public Set<Ip4Address> ueAddrsOfFarId(ImmutablePair<ImmutableByteSequence, Integer> farId) {
        return farIdToUeAddrs.getOrDefault(farId, Set.of()).value();
    }

    @Override
    public void forgetUeAddr(Ip4Address ueAddr) {
        farIdToUeAddrs.keySet().forEach(
                farId -> farIdToUeAddrs.computeIfPresent(farId, (farIdz, ueAddrs) -> {
                    ueAddrs.remove(ueAddr);
                    return ueAddrs;
                }));
    }

    @Override
    public Map<ImmutablePair<ImmutableByteSequence, Integer>, Set<Ip4Address>> getFarIdToUeAddrs() {
        return Map.copyOf(farIdToUeAddrs.asJavaMap());
    }
}
