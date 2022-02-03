/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.collect.ImmutableSet;
import org.onlab.packet.Ip4Address;
import org.onlab.util.KryoNamespace;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected static final String BUFFER_UE_MAP_NAME = "up4-buffer-ue";

    protected static final KryoNamespace.Builder SERIALIZER = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API);

    // NOTE If we can afford to lose the buffer state, we can make this map a simple concurrent map.
    // This can happen in case of instance failure or change in the DNS resolution.
    protected EventuallyConsistentMap<Ip4Address, Boolean> bufferUes;

    @Activate
    protected void activate() {
        // Allow unit test to inject farIdMap here.
        if (storageService != null) {
            this.bufferUes =
                    storageService.<Ip4Address, Boolean>eventuallyConsistentMapBuilder()
                            .withName(BUFFER_UE_MAP_NAME)
                            .withSerializer(SERIALIZER)
                            .withTimestampProvider((k, v) -> new WallClockTimestamp())
                            .build();
        }
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        this.bufferUes.destroy();
        this.bufferUes = null;

        log.info("Stopped");
    }

    @Override
    public void reset() {
        bufferUes.clear();
    }

    @Override
    public boolean isUeBuffering(Ip4Address ueAddr) {
        checkNotNull(ueAddr);
        return bufferUes.containsKey(ueAddr);
    }

    @Override
    public void learnBufferingUe(Ip4Address ueAddr) {
        checkNotNull(ueAddr);
        bufferUes.put(ueAddr, true);
    }

    @Override
    public boolean forgetBufferingUe(Ip4Address ueAddr) {
        checkNotNull(ueAddr);
        return bufferUes.remove(ueAddr) != null;
    }

    @Override
    public Set<Ip4Address> getBufferUe() {
        return ImmutableSet.copyOf(bufferUes.keySet());
    }
}
