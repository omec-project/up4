/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onlab.packet.Ip4Address;
import org.onosproject.store.service.TestEventuallyConsistentMap;
import org.onosproject.store.service.WallClockTimestamp;

import static org.omecproject.up4.impl.DistributedUp4Store.BUFFER_UE_MAP_NAME;
import static org.omecproject.up4.impl.DistributedUp4Store.SERIALIZER;

public final class TestDistributedUp4Store {

    private TestDistributedUp4Store() {
    }

    public static DistributedUp4Store build() {
        var store = new DistributedUp4Store();

        TestEventuallyConsistentMap.Builder<Ip4Address, Boolean>
                bufferUeBuilder = TestEventuallyConsistentMap.builder();
        bufferUeBuilder.withName(BUFFER_UE_MAP_NAME)
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .withSerializer(SERIALIZER.build());

        store.bufferUes = bufferUeBuilder.build();
        store.activate();
        return store;
    }
}