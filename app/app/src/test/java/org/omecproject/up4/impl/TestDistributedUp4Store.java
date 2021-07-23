/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.store.service.TestEventuallyConsistentMap;
import org.onosproject.store.service.WallClockTimestamp;

import static org.omecproject.up4.impl.DistributedUp4Store.BUFFER_FAR_ID_MAP_NAME;
import static org.omecproject.up4.impl.DistributedUp4Store.FAR_ID_UE_MAP_NAME;
import static org.omecproject.up4.impl.DistributedUp4Store.SERIALIZER;

public final class TestDistributedUp4Store {

    private TestDistributedUp4Store() {
    }

    public static DistributedUp4Store build() {
        var store = new DistributedUp4Store();

        TestEventuallyConsistentMap.Builder<ImmutablePair<ImmutableByteSequence, Integer>, String>
                bufferFarIdsBuilder = TestEventuallyConsistentMap.builder();
        bufferFarIdsBuilder.withName(BUFFER_FAR_ID_MAP_NAME)
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .withSerializer(SERIALIZER.build());

        TestEventuallyConsistentMap.Builder<ImmutablePair<ImmutableByteSequence, Integer>, Ip4Address>
                farIdToUeAddrBuilder = TestEventuallyConsistentMap.builder();
        farIdToUeAddrBuilder.withName(FAR_ID_UE_MAP_NAME)
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .withSerializer(SERIALIZER.build());

        store.farIdToUeAddr = farIdToUeAddrBuilder.build();
        store.bufferFarIds = bufferFarIdsBuilder.build();

        store.activate();

        return store;
    }
}
