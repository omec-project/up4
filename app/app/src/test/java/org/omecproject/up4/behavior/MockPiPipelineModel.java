package org.omecproject.up4.behavior;

import org.omecproject.up4.impl.SouthConstants;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionModel;
import org.onosproject.net.pi.model.PiActionProfileId;
import org.onosproject.net.pi.model.PiActionProfileModel;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiCounterModel;
import org.onosproject.net.pi.model.PiCounterType;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiMatchFieldModel;
import org.onosproject.net.pi.model.PiMeterId;
import org.onosproject.net.pi.model.PiMeterModel;
import org.onosproject.net.pi.model.PiPacketOperationModel;
import org.onosproject.net.pi.model.PiPacketOperationType;
import org.onosproject.net.pi.model.PiPipelineModel;
import org.onosproject.net.pi.model.PiRegisterId;
import org.onosproject.net.pi.model.PiRegisterModel;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.model.PiTableModel;
import org.onosproject.net.pi.model.PiTableType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class MockPiPipelineModel implements PiPipelineModel {
    class MockTableModel implements PiTableModel {
        PiTableId id;
        int size;

        public MockTableModel(PiTableId id, int size) {
            this.id = id;
            this.size = size;
        }

        @Override
        public PiTableId id() {
            return this.id;
        }

        @Override
        public PiTableType tableType() {
            return null;
        }

        @Override
        public PiActionProfileModel actionProfile() {
            return null;
        }

        @Override
        public long maxSize() {
            return size;
        }

        @Override
        public Collection<PiCounterModel> counters() {
            return null;
        }

        @Override
        public Collection<PiMeterModel> meters() {
            return null;
        }

        @Override
        public boolean supportsAging() {
            return false;
        }

        @Override
        public Collection<PiMatchFieldModel> matchFields() {
            return null;
        }

        @Override
        public Collection<PiActionModel> actions() {
            return null;
        }

        @Override
        public Optional<PiActionModel> constDefaultAction() {
            return Optional.empty();
        }

        @Override
        public boolean isConstantTable() {
            return false;
        }

        @Override
        public Optional<PiActionModel> action(PiActionId actionId) {
            return Optional.empty();
        }

        @Override
        public Optional<PiMatchFieldModel> matchField(PiMatchFieldId matchFieldId) {
            return Optional.empty();
        }
    }

    class MockCounterModel implements PiCounterModel {
        PiCounterId id;
        int size;

        public MockCounterModel(PiCounterId id, int size) {
            this.id = id;
            this.size = size;
        }

        @Override
        public PiCounterId id() {
            return this.id;
        }

        @Override
        public PiCounterType counterType() {
            return null;
        }

        @Override
        public Unit unit() {
            return null;
        }

        @Override
        public PiTableId table() {
            return null;
        }

        @Override
        public long size() {
            return this.size;
        }
    }


    @Override
    public Optional<PiTableModel> table(PiTableId tableId) {
        return Optional.empty();
    }

    @Override
    public Collection<PiTableModel> tables() {
        return List.of(
                new MockTableModel(SouthConstants.FABRIC_INGRESS_SPGW_UPLINK_PDRS,
                        TestConstants.PHYSICAL_MAX_PDRS / 2),
                new MockTableModel(SouthConstants.FABRIC_INGRESS_SPGW_DOWNLINK_PDRS,
                        TestConstants.PHYSICAL_MAX_PDRS / 2),
                new MockTableModel(SouthConstants.FABRIC_INGRESS_SPGW_FARS, TestConstants.PHYSICAL_MAX_FARS)
        );
    }

    @Override
    public Optional<PiCounterModel> counter(PiCounterId counterId) {
        return Optional.empty();
    }

    @Override
    public Collection<PiCounterModel> counters() {
        return List.of(
                new MockCounterModel(SouthConstants.FABRIC_INGRESS_SPGW_PDR_COUNTER,
                        TestConstants.PHYSICAL_COUNTER_SIZE),
                new MockCounterModel(SouthConstants.FABRIC_EGRESS_SPGW_PDR_COUNTER,
                        TestConstants.PHYSICAL_COUNTER_SIZE)
        );
    }

    @Override
    public Optional<PiMeterModel> meter(PiMeterId meterId) {
        return Optional.empty();
    }

    @Override
    public Collection<PiMeterModel> meters() {
        return null;
    }

    @Override
    public Optional<PiRegisterModel> register(PiRegisterId registerId) {
        return Optional.empty();
    }

    @Override
    public Collection<PiRegisterModel> registers() {
        return null;
    }

    @Override
    public Optional<PiActionProfileModel> actionProfiles(PiActionProfileId actionProfileId) {
        return Optional.empty();
    }

    @Override
    public Collection<PiActionProfileModel> actionProfiles() {
        return null;
    }

    @Override
    public Optional<PiPacketOperationModel> packetOperationModel(PiPacketOperationType type) {
        return Optional.empty();
    }
}
