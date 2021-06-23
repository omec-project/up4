/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.apache.commons.lang3.tuple.Pair;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.TestConsistentMap;
import org.onosproject.store.service.TestDistributedSet;

import java.util.Set;

import static org.omecproject.up4.impl.DistributedUp4Store.BUFFER_FAR_ID_SET_NAME;
import static org.omecproject.up4.impl.DistributedUp4Store.FAR_ID_UE_MAP_NAME;

public final class TestDistributedUp4Store {

    private TestDistributedUp4Store() {
    }

    public static DistributedUp4Store build() {
        var store = new DistributedUp4Store();

        TestDistributedSet.Builder<Pair<ImmutableByteSequence, Integer>> bufferFarIdsBuilder =
                TestDistributedSet.builder();
        bufferFarIdsBuilder
                .withName(BUFFER_FAR_ID_SET_NAME)
                .withRelaxedReadConsistency()
                .withSerializer(Serializer.using(KryoNamespaces.API));
        store.bufferFarIds = bufferFarIdsBuilder.build().asDistributedSet();

        TestConsistentMap.Builder<Pair<ImmutableByteSequence, Integer>, Set<Ip4Address>> farIdToUeAddrsBuilder =
                TestConsistentMap.builder();
        farIdToUeAddrsBuilder
                .withName(FAR_ID_UE_MAP_NAME)
                .withRelaxedReadConsistency()
                .withSerializer(Serializer.using(KryoNamespaces.API));
        store.farIdToUeAddrs = farIdToUeAddrsBuilder.build();

        store.activate();

        return store;
    }
}
