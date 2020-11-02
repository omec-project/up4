package org.omecproject.up4.impl;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;
import org.omecproject.up4.UpfProgrammable;
import org.omecproject.up4.behavior.TestConstants;
import org.omecproject.up4.behavior.Up4TranslatorImpl;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiCounterCellId;
import org.onosproject.net.pi.runtime.PiEntity;
import org.onosproject.net.pi.runtime.PiEntityType;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.p4runtime.ctl.codec.CodecException;
import org.onosproject.p4runtime.ctl.codec.Codecs;
import org.onosproject.p4runtime.ctl.utils.PipeconfHelper;
import p4.config.v1.P4InfoOuterClass;
import p4.v1.P4RuntimeOuterClass;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class Up4NorthComponentTest {

    private final Up4NorthComponent up4NorthComponent = new Up4NorthComponent();
    private final Up4NorthComponent.Up4NorthService up4NorthService = up4NorthComponent.up4NorthService;
    PiPipeconf pipeconf;
    private P4InfoOuterClass.P4Info p4Info;
    UpfProgrammable upfProgrammable;

    static class MockStreamObserver<T> implements StreamObserver<T> {
        public List<T> responsesObserved = new ArrayList<>();

        public T lastResponse() {
            return responsesObserved.get(responsesObserved.size() - 1);
        }

        @Override
        public void onNext(T value) {
            responsesObserved.add(value);
        }

        @Override
        public void onError(Throwable t) {
            fail("Stream observer shouldn't see any errors");
        }

        @Override
        public void onCompleted() {

        }
    }

    @Before
    public void setUp() throws Exception {
        pipeconf = Up4NorthComponent.buildPipeconf();
        p4Info = PipeconfHelper.getP4Info(pipeconf);
        up4NorthComponent.pipeconf = pipeconf;
        up4NorthComponent.p4Info = p4Info;
        up4NorthComponent.up4Translator = new Up4TranslatorImpl();
        up4NorthComponent.up4Service = new MockUp4Service();
        upfProgrammable = up4NorthComponent.up4Service.getUpfProgrammable();
    }

    private void insertionTest(PiTableEntry entryToInsert) {
        MockStreamObserver<P4RuntimeOuterClass.WriteResponse> responseObserver = new MockStreamObserver<>();
        P4RuntimeOuterClass.Entity entity;
        try {
            entity = Codecs.CODECS.entity().encode(entryToInsert, null, pipeconf);
        } catch (CodecException e) {
            fail("Unable to encode UP4 entry into a p4runtime entity.");
            return;
        }

        P4RuntimeOuterClass.WriteRequest request = P4RuntimeOuterClass.WriteRequest.newBuilder()
                .setDeviceId(NorthTestConstants.P4RUNTIME_DEVICE_ID)
                .addUpdates(P4RuntimeOuterClass.Update.newBuilder()
                        .setEntity(entity)
                        .setType(P4RuntimeOuterClass.Update.Type.INSERT)
                        .build())
                .build();

        up4NorthService.write(request, responseObserver);

        var response = responseObserver.lastResponse();
        assertThat(response, equalTo(P4RuntimeOuterClass.WriteResponse.getDefaultInstance()));
    }

    private void deletionTest(PiTableEntry entryToDelete) {
        MockStreamObserver<P4RuntimeOuterClass.WriteResponse> responseObserver = new MockStreamObserver<>();
        P4RuntimeOuterClass.Entity entity;
        try {
            entity = Codecs.CODECS.entity().encode(entryToDelete, null, pipeconf);
        } catch (CodecException e) {
            fail("Unable to encode UP4 entry into a p4runtime entity.");
            return;
        }

        P4RuntimeOuterClass.WriteRequest request = P4RuntimeOuterClass.WriteRequest.newBuilder()
                .setDeviceId(NorthTestConstants.P4RUNTIME_DEVICE_ID)
                .addUpdates(P4RuntimeOuterClass.Update.newBuilder()
                        .setEntity(entity)
                        .setType(P4RuntimeOuterClass.Update.Type.DELETE)
                        .build())
                .build();

        up4NorthService.write(request, responseObserver);

        var response = responseObserver.lastResponse();
        assertThat(response, equalTo(P4RuntimeOuterClass.WriteResponse.getDefaultInstance()));
    }

    private void readTest(PiTableEntry entryToRead) {
        MockStreamObserver<P4RuntimeOuterClass.ReadResponse> responseObserver = new MockStreamObserver<>();
        P4RuntimeOuterClass.Entity entity;
        try {
            entity = Codecs.CODECS.entity().encode(entryToRead, null, pipeconf);
        } catch (CodecException e) {
            fail("Unable to encode UP4 entry into a p4runtime entity.");
            return;
        }

        P4RuntimeOuterClass.ReadRequest request = P4RuntimeOuterClass.ReadRequest.newBuilder()
                .addEntities(entity)
                .setDeviceId(NorthTestConstants.P4RUNTIME_DEVICE_ID)
                .build();

        up4NorthService.read(request, responseObserver);
        var response = responseObserver.lastResponse();

        assertThat(response.getEntitiesCount(), equalTo(1));
        assertThat(response.getEntitiesList().get(0), equalTo(entity));
    }

    @Test
    public void readWildcardCounterTest() {
        // A counter read request with no counterID or cellID should return ALL active counter cells
        MockStreamObserver<P4RuntimeOuterClass.ReadResponse> responseObserver = new MockStreamObserver<>();
        P4RuntimeOuterClass.ReadRequest request = P4RuntimeOuterClass.ReadRequest.newBuilder()
                .addEntities(P4RuntimeOuterClass.Entity.newBuilder()
                        .setCounterEntry(P4RuntimeOuterClass.CounterEntry.newBuilder().build())
                        .build())
                .build();
        up4NorthService.read(request, responseObserver);
        var response = responseObserver.lastResponse();
        assertThat(response.getEntitiesList().size(), equalTo(4));
    }

    private void readPartialWildcardCounterTest(PiCounterId counterId) {
        // A counter read request with a counterID but no cellID
        // Encode a dummy cell just so we can get the p4runtime counter integer ID from the encoder
        PiCounterCell dummyCell = new PiCounterCell(
                PiCounterCellId.ofIndirect(counterId, 1), 0, 0);
        P4RuntimeOuterClass.Entity dummyEntity;
        try {
            dummyEntity = Codecs.CODECS.entity().encode(dummyCell, null, pipeconf);
        } catch (CodecException e) {
            fail("Unable to encode counter cell to p4runtime entity!");
            return;
        }
        int intCounterId = dummyEntity.getCounterEntry().getCounterId();
        // Now build the actual request
        MockStreamObserver<P4RuntimeOuterClass.ReadResponse> responseObserver = new MockStreamObserver<>();
        P4RuntimeOuterClass.ReadRequest request = P4RuntimeOuterClass.ReadRequest.newBuilder()
                .addEntities(P4RuntimeOuterClass.Entity.newBuilder()
                        .setCounterEntry(P4RuntimeOuterClass.CounterEntry.newBuilder()
                                .setCounterId(intCounterId).build())
                        .build())
                .build();
        up4NorthService.read(request, responseObserver);
        var response = responseObserver.lastResponse();
        assertThat(response.getEntitiesList().size(), equalTo(2));
        for (P4RuntimeOuterClass.Entity entity : response.getEntitiesList()) {
            PiCounterCell responseCell = entityToCounterCell(entity);
            if (!responseCell.cellId().counterId().equals(counterId)) {
                fail("Counter read returned a cell from the wrong counter!");
            }
        }
    }

    private PiCounterCell entityToCounterCell(P4RuntimeOuterClass.Entity entity) {
        PiEntity responsePiEntity;
        try {
            responsePiEntity = Codecs.CODECS.entity().decode(entity, null, pipeconf);
        } catch (CodecException e) {
            fail("Unable to decode p4runtime entity from read response!");
            return null;
        }
        if (responsePiEntity.piEntityType() != PiEntityType.COUNTER_CELL) {
            fail("Counter read returned something other than a counter cell!");
            return null;
        }
        return (PiCounterCell) responsePiEntity;
    }

    @Test
    public void readAllIngressCountersTest() {
        upfProgrammable.addPdr(TestConstants.DOWNLINK_PDR);
        upfProgrammable.addPdr(TestConstants.UPLINK_PDR);
        readPartialWildcardCounterTest(NorthConstants.INGRESS_COUNTER_ID);
    }

    @Test
    public void readAllEgressCountersTest() {
        upfProgrammable.addPdr(TestConstants.DOWNLINK_PDR);
        upfProgrammable.addPdr(TestConstants.UPLINK_PDR);
        readPartialWildcardCounterTest(NorthConstants.EGRESS_COUNTER_ID);
    }

    private void readCounterTest(PiCounterId counterId, long expectedPackets, long expectedBytes) {
        MockStreamObserver<P4RuntimeOuterClass.ReadResponse> responseObserver = new MockStreamObserver<>();
        int cellIndex = 1;
        PiCounterCell requestedCell = new PiCounterCell(
                PiCounterCellId.ofIndirect(counterId, cellIndex), 0, 0);
        P4RuntimeOuterClass.Entity entity;
        try {
            entity = Codecs.CODECS.entity().encode(requestedCell, null, pipeconf);
        } catch (CodecException e) {
            fail("Unable to encode counter cell to p4runtime entity!");
            return;
        }

        P4RuntimeOuterClass.ReadRequest request = P4RuntimeOuterClass.ReadRequest.newBuilder()
                .addEntities(entity)
                .setDeviceId(NorthTestConstants.P4RUNTIME_DEVICE_ID)
                .build();

        up4NorthService.read(request, responseObserver);
        var response = responseObserver.lastResponse();

        PiCounterCell expectedCell = new PiCounterCell(
                PiCounterCellId.ofIndirect(counterId, cellIndex), expectedPackets, expectedBytes);
        P4RuntimeOuterClass.Entity expectedEntity;
        try {
            expectedEntity = Codecs.CODECS.entity().encode(expectedCell, null, pipeconf);
        } catch (CodecException e) {
            fail("Unable to encode counter cell to p4runtime entity!");
            return;
        }
        assertThat(response.getEntitiesCount(), equalTo(1));
        assertThat(response.getEntitiesList().get(0), equalTo(expectedEntity));
    }

    @Test
    public void readIngressCounterTest() {
        readCounterTest(NorthConstants.INGRESS_COUNTER_ID,
                NorthTestConstants.INGRESS_COUNTER_PKTS, NorthTestConstants.INGRESS_COUNTER_BYTES);
    }

    @Test
    public void readEgressCounterTest() {
        readCounterTest(NorthConstants.EGRESS_COUNTER_ID,
                NorthTestConstants.EGRESS_COUNTER_PKTS, NorthTestConstants.EGRESS_COUNTER_BYTES);
    }

    @Test
    public void downlinkFarReadTest() {
        upfProgrammable.addFar(TestConstants.DOWNLINK_FAR);
        readTest(TestConstants.UP4_DOWNLINK_FAR);
    }

    @Test
    public void uplinkFarReadTest() {
        upfProgrammable.addFar(TestConstants.UPLINK_FAR);
        readTest(TestConstants.UP4_UPLINK_FAR);
    }

    @Test
    public void downlinkPdrReadTest() {
        upfProgrammable.addPdr(TestConstants.DOWNLINK_PDR);
        readTest(TestConstants.UP4_DOWNLINK_PDR);
    }

    @Test
    public void uplinkPdrReadTest() {
        upfProgrammable.addPdr(TestConstants.UPLINK_PDR);
        readTest(TestConstants.UP4_UPLINK_PDR);
    }

    @Test
    public void downlinkInterfaceReadTest() {
        upfProgrammable.addInterface(TestConstants.DOWNLINK_INTERFACE);
        readTest(TestConstants.UP4_DOWNLINK_INTERFACE);
    }

    @Test
    public void uplinkInterfaceReadTest() {
        upfProgrammable.addInterface(TestConstants.UPLINK_INTERFACE);
        readTest(TestConstants.UP4_UPLINK_INTERFACE);
    }

    @Test
    public void downlinkFarInsertionTest() {
        PiTableEntry entry = TestConstants.UP4_DOWNLINK_FAR;
        insertionTest(entry);
        assertThat(upfProgrammable.getInstalledFars().size(), equalTo(1));
    }

    @Test
    public void downlinkFarDeletionTest() {
        upfProgrammable.addFar(TestConstants.DOWNLINK_FAR);
        deletionTest(TestConstants.UP4_DOWNLINK_FAR);
        assertTrue(upfProgrammable.getInstalledFars().isEmpty());
    }

    @Test
    public void uplinkFarInsertionTest() {
        insertionTest(TestConstants.UP4_UPLINK_FAR);
        assertThat(upfProgrammable.getInstalledFars().size(), equalTo(1));
    }

    @Test
    public void uplinkFarDeletionTest() {
        upfProgrammable.addFar(TestConstants.UPLINK_FAR);
        deletionTest(TestConstants.UP4_UPLINK_FAR);
        assertTrue(upfProgrammable.getInstalledFars().isEmpty());
    }

    @Test
    public void downlinkPdrInsertionTest() {
        insertionTest(TestConstants.UP4_DOWNLINK_PDR);
        assertThat(upfProgrammable.getInstalledPdrs().size(), equalTo(1));
    }

    @Test
    public void downlinkPdrDeletionTest() {
        upfProgrammable.addPdr(TestConstants.DOWNLINK_PDR);
        deletionTest(TestConstants.UP4_DOWNLINK_PDR);
        assertTrue(upfProgrammable.getInstalledPdrs().isEmpty());
    }

    @Test
    public void uplinkPdrInsertionTest() {
        insertionTest(TestConstants.UP4_UPLINK_PDR);
        assertThat(upfProgrammable.getInstalledPdrs().size(), equalTo(1));
    }

    @Test
    public void uplinkPdrDeletionTest() {
        upfProgrammable.addPdr(TestConstants.UPLINK_PDR);
        deletionTest(TestConstants.UP4_UPLINK_PDR);
        assertTrue(upfProgrammable.getInstalledPdrs().isEmpty());
    }

    @Test
    public void downlinkInterfaceInsertionTest() {
        insertionTest(TestConstants.UP4_DOWNLINK_INTERFACE);
        assertThat(upfProgrammable.getInstalledInterfaces().size(), equalTo(1));
    }

    @Test
    public void downlinkInterfaceDeletionTest() {
        upfProgrammable.addInterface(TestConstants.DOWNLINK_INTERFACE);
        deletionTest(TestConstants.UP4_DOWNLINK_INTERFACE);
        assertTrue(upfProgrammable.getInstalledInterfaces().isEmpty());
    }

    @Test
    public void uplinkInterfaceInsertionTest() {
        PiTableEntry entry = TestConstants.UP4_UPLINK_INTERFACE;
        insertionTest(entry);
        assertThat(upfProgrammable.getInstalledInterfaces().size(), equalTo(1));
    }

    @Test
    public void uplinkInterfaceDeletionTest() {
        upfProgrammable.addInterface(TestConstants.UPLINK_INTERFACE);
        deletionTest(TestConstants.UP4_UPLINK_INTERFACE);
        assertTrue(upfProgrammable.getInstalledInterfaces().isEmpty());
    }


    @Test
    public void arbitrationTest() {
        MockStreamObserver<P4RuntimeOuterClass.StreamMessageResponse> responseObserver
                = new MockStreamObserver<>();

        StreamObserver<P4RuntimeOuterClass.StreamMessageRequest> requestObserver
                = up4NorthService.streamChannel(responseObserver);

        var deviceId = NorthTestConstants.P4RUNTIME_DEVICE_ID;
        var role = P4RuntimeOuterClass.Role.newBuilder().setId(0).build();
        var electionId = P4RuntimeOuterClass.Uint128.newBuilder().setLow(1).build();

        P4RuntimeOuterClass.StreamMessageRequest request = P4RuntimeOuterClass.StreamMessageRequest.newBuilder()
                .setArbitration(P4RuntimeOuterClass.MasterArbitrationUpdate.newBuilder()
                        .setDeviceId(deviceId)
                        .setRole(role)
                        .setElectionId(electionId)
                        .build())
                .build();

        requestObserver.onNext(request);
        var response = responseObserver.lastResponse();
        assertThat(response.getArbitration().getDeviceId(), equalTo(deviceId));
        assertThat(response.getArbitration().getRole(), equalTo(role));
        assertThat(response.getArbitration().getElectionId(), equalTo(electionId));
        assertThat(response.getArbitration().getStatus(),
                equalTo(Status.newBuilder().setCode(Code.OK.getNumber()).build()));
    }


    @Test
    public void setPipelineConfigTest() {
        MockStreamObserver<P4RuntimeOuterClass.SetForwardingPipelineConfigResponse> responseObserver
                = new MockStreamObserver<>();
        var setPipeRequest = P4RuntimeOuterClass.SetForwardingPipelineConfigRequest.newBuilder()
                .setDeviceId(NorthTestConstants.P4RUNTIME_DEVICE_ID)
                .setConfig(P4RuntimeOuterClass.ForwardingPipelineConfig.newBuilder()
                        .setCookie(P4RuntimeOuterClass.ForwardingPipelineConfig.Cookie.newBuilder()
                                .setCookie(NorthTestConstants.PIPECONF_COOKIE))
                        .setP4Info(p4Info)
                        .build())
                .build();
        up4NorthService.setForwardingPipelineConfig(setPipeRequest, responseObserver);
        var response = responseObserver.lastResponse();
        assertThat(response,
                equalTo(P4RuntimeOuterClass.SetForwardingPipelineConfigResponse.getDefaultInstance()));
    }

    @Test
    public void getPipelineConfigTest() {

        MockStreamObserver<P4RuntimeOuterClass.GetForwardingPipelineConfigResponse> responseObserver
                = new MockStreamObserver<>();

        var getPipeRequest = P4RuntimeOuterClass.GetForwardingPipelineConfigRequest.newBuilder()
                .setDeviceId(NorthTestConstants.P4RUNTIME_DEVICE_ID)
                .build();
        up4NorthService.getForwardingPipelineConfig(getPipeRequest, responseObserver);
        var response = responseObserver.lastResponse();
        assertThat(p4Info, equalTo(response.getConfig().getP4Info()));
    }
}