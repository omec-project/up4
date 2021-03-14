/*
 * SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 * SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */

package org.omecproject.up4.behavior;

import org.omecproject.up4.UpfRuleIdentifier;
import org.onlab.packet.Ip4Address;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.TestConsistentMap;
import org.onosproject.store.service.TestDistributedSet;

import java.util.Set;

import static org.omecproject.up4.behavior.DistributedFabricUpfStore.BUFFER_FAR_ID_SET_NAME;
import static org.omecproject.up4.behavior.DistributedFabricUpfStore.FAR_ID_MAP_NAME;
import static org.omecproject.up4.behavior.DistributedFabricUpfStore.FAR_ID_UE_MAP_NAME;
import static org.omecproject.up4.behavior.DistributedFabricUpfStore.SESSION_PRIORITY_MAP_NAME;
import static org.omecproject.up4.behavior.DistributedFabricUpfStore.SERIALIZER;

public final class TestDistributedFabricUpfStore {

    private TestDistributedFabricUpfStore() {
    }

    public static DistributedFabricUpfStore build() {
        var store = new DistributedFabricUpfStore();
        TestConsistentMap.Builder<UpfRuleIdentifier, Integer> farIdMapBuilder =
                TestConsistentMap.builder();
        farIdMapBuilder.withName(FAR_ID_MAP_NAME)
                .withRelaxedReadConsistency()
                .withSerializer(Serializer.using(SERIALIZER.build()));
        store.farIdMap = farIdMapBuilder.build();

        TestDistributedSet.Builder<UpfRuleIdentifier> bufferFarIdsBuilder =
                TestDistributedSet.builder();
        bufferFarIdsBuilder
                .withName(BUFFER_FAR_ID_SET_NAME)
                .withRelaxedReadConsistency()
                .withSerializer(Serializer.using(SERIALIZER.build()));
        store.bufferFarIds = bufferFarIdsBuilder.build().asDistributedSet();

        TestConsistentMap.Builder<UpfRuleIdentifier, Set<Ip4Address>> farIdToUeAddrsBuilder =
                TestConsistentMap.builder();
        farIdToUeAddrsBuilder
                .withName(FAR_ID_UE_MAP_NAME)
                .withRelaxedReadConsistency()
                .withSerializer(Serializer.using(SERIALIZER.build()));
        store.farIdToUeAddrs = farIdToUeAddrsBuilder.build();

        TestConsistentMap.Builder<ImmutableByteSequence, Integer> pfcpSessionSPriorityMapBuilder =
                TestConsistentMap.builder();
        pfcpSessionSPriorityMapBuilder
                .withName(SESSION_PRIORITY_MAP_NAME)
                .withRelaxedReadConsistency()
                .withSerializer(Serializer.using(SERIALIZER.build()));
        store.pfcpSessionSPriorityMap = pfcpSessionSPriorityMapBuilder.build();

        store.activate();

        // Init with some translation state.
        store.farIdMap.put(
                new UpfRuleIdentifier(TestConstants.SESSION_ID, TestConstants.UPLINK_FAR_ID),
                TestConstants.UPLINK_PHYSICAL_FAR_ID);
        store.farIdMap.put(
                new UpfRuleIdentifier(TestConstants.SESSION_ID, TestConstants.DOWNLINK_FAR_ID),
                TestConstants.DOWNLINK_PHYSICAL_FAR_ID);

        // Init with some translation state.
        store.pfcpSessionSPriorityMap.put(TestConstants.SESSION_ID, TestConstants.DOWNLINK_PRIORITY);

        return store;
    }
}
