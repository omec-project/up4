package org.omecproject.up4.behavior;

import org.onosproject.core.GroupId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowId;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.flow.FlowRuleStore;
import org.onosproject.net.flow.FlowRuleStoreDelegate;
import org.onosproject.net.flow.TableId;
import org.onosproject.net.flow.TableStatisticsEntry;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.oldbatch.FlowRuleBatchEvent;
import org.onosproject.net.flow.oldbatch.FlowRuleBatchOperation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MockFlowRuleStore implements FlowRuleStore {
    public class MockFlowEntry implements FlowEntry {

        @Override
        public FlowEntryState state() {
            return null;
        }

        @Override
        public long life() {
            return 0;
        }

        @Override
        public FlowLiveType liveType() {
            return null;
        }

        @Override
        public long life(TimeUnit unit) {
            return 0;
        }

        @Override
        public long packets() {
            return 0;
        }

        @Override
        public long bytes() {
            return 0;
        }

        @Override
        public long lastSeen() {
            return 0;
        }

        @Override
        public int errType() {
            return 0;
        }

        @Override
        public int errCode() {
            return 0;
        }

        @Override
        public FlowId id() {
            return null;
        }

        @Override
        public short appId() {
            return 0;
        }

        @Override
        public GroupId groupId() {
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public DeviceId deviceId() {
            return null;
        }

        @Override
        public TrafficSelector selector() {
            return null;
        }

        @Override
        public TrafficTreatment treatment() {
            return null;
        }

        @Override
        public int timeout() {
            return 0;
        }

        @Override
        public int hardTimeout() {
            return 0;
        }

        @Override
        public FlowRemoveReason reason() {
            return null;
        }

        @Override
        public boolean isPermanent() {
            return false;
        }

        @Override
        public int tableId() {
            return 0;
        }

        @Override
        public TableId table() {
            return null;
        }

        @Override
        public boolean exactMatch(FlowRule rule) {
            return false;
        }
    }

    Set<FlowId> installedIds;

    public MockFlowRuleStore() {
        installedIds = new HashSet<>();
    }

    @Override
    public int getFlowRuleCount() {
        return 0;
    }

    @Override
    public int getFlowRuleCount(DeviceId deviceId) {
        return 0;
    }

    @Override
    public int getFlowRuleCount(DeviceId deviceId, FlowEntry.FlowEntryState state) {
        return 0;
    }

    @Override
    public FlowEntry getFlowEntry(FlowRule rule) {
        if (installedIds.contains(rule.id())) {
            return new MockFlowEntry();
        }
        return null;
    }

    @Override
    public Iterable<FlowEntry> getFlowEntries(DeviceId deviceId) {
        return null;
    }

    @Override
    public void storeFlowRule(FlowRule rule) {
        installedIds.add(rule.id());
    }

    @Override
    public void storeBatch(FlowRuleBatchOperation batchOperation) {

    }

    @Override
    public void batchOperationComplete(FlowRuleBatchEvent event) {

    }

    @Override
    public void deleteFlowRule(FlowRule rule) {
        installedIds.remove(rule.id());

    }

    @Override
    public FlowRuleEvent addOrUpdateFlowRule(FlowEntry rule) {
        return null;
    }

    @Override
    public FlowRuleEvent removeFlowRule(FlowEntry rule) {
        return null;
    }

    @Override
    public FlowRuleEvent pendingFlowRule(FlowEntry rule) {
        return null;
    }

    @Override
    public void purgeFlowRule(DeviceId deviceId) {

    }

    @Override
    public void purgeFlowRules() {

    }

    @Override
    public FlowRuleEvent updateTableStatistics(DeviceId deviceId, List<TableStatisticsEntry> tableStats) {
        return null;
    }

    @Override
    public Iterable<TableStatisticsEntry> getTableStatistics(DeviceId deviceId) {
        return null;
    }

    @Override
    public long getActiveFlowRuleCount(DeviceId deviceId) {
        return 0;
    }

    @Override
    public void setDelegate(FlowRuleStoreDelegate delegate) {

    }

    @Override
    public void unsetDelegate(FlowRuleStoreDelegate delegate) {

    }

    @Override
    public boolean hasDelegate() {
        return false;
    }
}
