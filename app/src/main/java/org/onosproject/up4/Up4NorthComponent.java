/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.onosproject.up4;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.pi.model.DefaultPiPipeconf;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiMatchType;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.model.PiPipelineModel;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiEntity;
import org.onosproject.net.pi.runtime.PiEntityType;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiRangeFieldMatch;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;
import org.onosproject.p4runtime.ctl.codec.CodecException;
import org.onosproject.p4runtime.ctl.codec.Codecs;
import org.onosproject.p4runtime.ctl.utils.PipeconfHelper;
import org.onosproject.p4runtime.model.P4InfoParser;
import org.onosproject.p4runtime.model.P4InfoParserException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import p4.config.v1.P4InfoOuterClass;
import p4.v1.P4RuntimeGrpc;
import p4.v1.P4RuntimeOuterClass;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import static org.onosproject.net.pi.model.PiPipeconf.ExtensionType.P4_INFO_TEXT;
import static org.onosproject.up4.AppConstants.PIPECONF_ID;

@Component(immediate = true,
        property = {
                "grpcPort=51001",
        })
public class Up4NorthComponent {
    private final Logger log = LoggerFactory.getLogger(getClass());


    private static final ImmutableByteSequence ZERO_SEQ = ImmutableByteSequence.ofZeros(4);

    private Server server;
    private ApplicationId appId;
    private P4InfoOuterClass.P4Info p4Info;
    private long pipeconfCookie = 0xbeefbeef;

    private PiPipelineModel piModel;
    private PiPipeconf pipeconf;
    private DeviceId deviceId;

    /** Port on which the P4runtime server listens. */
    private int grpcPort;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected Up4Service up4Service;


    @Activate
    protected void activate() {
        appId = coreService.getAppId(AppConstants.APP_NAME);
        start();
        log.info("UP4 Northbound component activated.");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        stop();
        log.info("Stopped");
    }


    protected void start() {
        // Load the UP4 p4info.txt
        try {
            pipeconf = buildPipeconf();
        } catch (P4InfoParserException e) {
            log.error("Unable to parse UP4 p4info file.", e);
            throw new IllegalStateException("Unable to parse UP4 p4info file.", e);
        }
        p4Info = PipeconfHelper.getP4Info(pipeconf);
        // Start Server
        try {
            server = NettyServerBuilder.forPort(AppConstants.GRPC_SERVER_PORT)
                    .addService(new Up4NorthService())
                    .build()
                    .start();
            log.info("UP4 gRPC server started on port {}", grpcPort);
        } catch (IOException e) {
            log.error("Unable to start gRPC server", e);
            throw new IllegalStateException("Unable to start gRPC server", e);
        }

        // TODO: add an integer p4runtime deviceId to the netcfg, and use those to identify these devices
        List<Device> availableDevices = up4Service.getAvailableDevices();

        if (availableDevices.isEmpty()) {
            log.error("No available UP4-compliant devices found!");
            throw new IllegalStateException("No available UP4-compliant devices found!");
        }
        log.info("{} UP4-compliant device(s) found. Using the first one.", availableDevices.size());
        deviceId = availableDevices.get(0).id();

        log.info("Started");
    }

    protected void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private PiPipeconf buildPipeconf() throws P4InfoParserException {

        log.info("Looking for p4info file named {}", AppConstants.P4INFO_PATH);
        final URL p4InfoUrl = Up4NorthComponent.class.getResource(AppConstants.P4INFO_PATH);
        log.info("Reading p4info file from path {}", p4InfoUrl);
        final PiPipelineModel pipelineModel = P4InfoParser.parse(p4InfoUrl);

        return DefaultPiPipeconf.builder()
                .withId(PIPECONF_ID)
                .withPipelineModel(pipelineModel)
                .addExtension(P4_INFO_TEXT, p4InfoUrl)
                .build();
    }

    private ImmutableByteSequence getFieldValue(PiTableEntry entry, PiMatchFieldId fieldId) {
        Optional<PiFieldMatch> optField = entry.matchKey().fieldMatch(fieldId);
        if (optField.isEmpty()) {
            log.error("Field {} is not present where expected!", fieldId);
            return ZERO_SEQ;
        }
        PiFieldMatch field = optField.get();
        if (field.type() == PiMatchType.EXACT) {
            return ((PiExactFieldMatch) field).value();
        } else if (field.type() == PiMatchType.LPM) {
            return ((PiLpmFieldMatch) field).value();
        } else if (field.type() == PiMatchType.TERNARY) {
            return ((PiTernaryFieldMatch) field).value();
        } else if (field.type() == PiMatchType.RANGE) {
            return ((PiRangeFieldMatch) field).lowValue();
        } else {
            log.error("Field has unknown match type!");
            return ZERO_SEQ;
        }
    }

    private ImmutableByteSequence getParamValue(PiTableEntry entry, PiActionParamId paramId) {
        PiAction action = (PiAction) entry.action();

        for (PiActionParam param : action.parameters()) {
            if (param.id().equals(paramId)) {
                return param.value();
            }
        }
        log.error("Unable to find ParamId {} in table entry!", paramId);
        return ZERO_SEQ;
    }

    private int getFieldInt(PiTableEntry entry, PiMatchFieldId fieldId) {
        return byteSeqToInt(getFieldValue(entry, fieldId));
    }

    private int getParamInt(PiTableEntry entry, PiActionParamId paramId) {
        return byteSeqToInt(getParamValue(entry, paramId));
    }

    private Ip4Address getParamAddress(PiTableEntry entry, PiActionParamId paramId) {
        return Ip4Address.valueOf(getParamValue(entry, paramId).asArray());
    }

    private Ip4Prefix getFieldPrefix(PiTableEntry entry, PiMatchFieldId fieldId) {
        Optional<PiFieldMatch> optField = entry.matchKey().fieldMatch(fieldId);
        if (optField.isEmpty()) {
            log.error("Field {} is not present where expected!", fieldId);
            return null;
        }
        PiLpmFieldMatch field = (PiLpmFieldMatch) optField.get();
        Ip4Address address = Ip4Address.valueOf(field.value().asArray());
        return Ip4Prefix.valueOf(address, field.prefixLength());
    }

    private Ip4Address getFieldAddress(PiTableEntry entry, PiMatchFieldId fieldId) {
        return Ip4Address.valueOf(getFieldValue(entry, fieldId).asArray());
    }


    private boolean fieldMaskIsZero(PiTableEntry entry, PiMatchFieldId fieldId) {
        Optional<PiFieldMatch> optField = entry.matchKey().fieldMatch(fieldId);
        if (optField.isEmpty()) {
            return true;
        }
        PiFieldMatch field = optField.get();
        if (field.type() != PiMatchType.TERNARY) {
            log.warn("Attempting to check mask for non-ternary field! {}", fieldId);
            return false;
        }
        for (byte b : ((PiTernaryFieldMatch) field).mask().asArray()) {
            if (b != (byte) 0) {
                return false;
            }
        }
        return true;
    }

    private int byteSeqToInt(ImmutableByteSequence sequence) {
        int result = 0;
        for (byte b : sequence.asArray()) {
            result = (result << 8) + ((int) b & 0xff);
        }
        return result;
    }

    private void translateAndDelete(PiTableEntry entry) {
        log.info("Translating UP4 deletion request to fabric entry deletion.");
        PiTableId tableId = entry.table();
        if (tableId.equals(NorthConstants.IFACE_TBL)) {
            Ip4Prefix prefix = getFieldPrefix(entry, NorthConstants.IFACE_DST_PREFIX_KEY);
            up4Service.removeUnknownInterface(deviceId, prefix);
        } else if (tableId.equals(NorthConstants.PDR_TBL)) {
            Ip4Address ueAddr = getFieldAddress(entry, NorthConstants.UE_ADDR_KEY);
            if (!fieldMaskIsZero(entry, NorthConstants.TEID_KEY)) {
                // uplink will have a non-ignored teid
                int teid = getFieldInt(entry, NorthConstants.TEID_KEY);
                Ip4Address tunnelDst = getFieldAddress(entry, NorthConstants.TUNNEL_DST_KEY);
                up4Service.removePdr(deviceId, ueAddr, teid, tunnelDst);
            } else {
                // downlink
                up4Service.removePdr(deviceId,  ueAddr);
            }
        } else if (tableId.equals(NorthConstants.FAR_TBL)) {
            int farId = byteSeqToInt(getFieldValue(entry, NorthConstants.FAR_ID_KEY));
            int sessionId = byteSeqToInt(getFieldValue(entry, NorthConstants.SESSION_ID_KEY));
            up4Service.removeFar(deviceId, sessionId, farId);
        } else {
            log.error("Attempting to translate table entry of unknown table! {}", tableId.toString());
            return;
        }
        log.info("Translation of UP4 deletion request successful.");
    }

    private void translateAndInsert(PiTableEntry entry) {
        log.info("Translating UP4 write request to fabric entry.");
        PiTableId tableId = entry.table();
        if (entry.action().type() != PiTableAction.Type.ACTION) {
            log.error("Action profile entry insertion not supported. Ignoring.");
            return;
        }
        boolean actionUnknown = false;
        PiAction action = (PiAction) entry.action();
        PiActionId actionId = action.id();
        if (tableId.equals(NorthConstants.IFACE_TBL)) {
            if (actionId.equals(NorthConstants.LOAD_IFACE)) {
                Ip4Prefix prefix = getFieldPrefix(entry, NorthConstants.IFACE_DST_PREFIX_KEY);
                int direction = byteSeqToInt(getParamValue(entry, NorthConstants.DIRECTION));
                if (direction == NorthConstants.DIRECTION_UPLINK) {
                    log.info("Interpreted write req as Uplink Interface with S1U address {}", prefix.address());
                    up4Service.addS1uInterface(deviceId, prefix.address());
                } else if (direction == NorthConstants.DIRECTION_DOWNLINK) {
                    log.info("Interpreted write req as Downlink Interface with UE Pool prefix {}", prefix);
                    up4Service.addUePool(deviceId, prefix);
                } else {
                    log.error("Received an interface lookup entry with unknown direction {}!", direction);
                }
            } else {
                actionUnknown = true;
            }
        } else if (tableId.equals(NorthConstants.PDR_TBL)) {
            if (actionId.equals(NorthConstants.LOAD_PDR)) {
                // 1:pdr-id, 2:session-id, 3:ctr-id, 4:far-id, 5:needs-gtpu-decap
                int sessionId = getParamInt(entry, NorthConstants.SESSION_ID_PARAM);
                int ctrId = getParamInt(entry, NorthConstants.CTR_ID);
                int farId = getParamInt(entry, NorthConstants.FAR_ID_PARAM);
                Ip4Address ueAddr = getFieldAddress(entry, NorthConstants.UE_ADDR_KEY);
                int srcInterface = getFieldInt(entry, NorthConstants.SRC_IFACE_KEY);
                if (srcInterface == NorthConstants.IFACE_ACCESS) {
                    int teid = getFieldInt(entry, NorthConstants.TEID_KEY);
                    Ip4Address tunnelDst = getFieldAddress(entry, NorthConstants.TUNNEL_DST_KEY);
                    log.info("Interpreted write req as Uplink PDR with UE-ADDR:{}, TEID:{}, " +
                                    "S1UAddr:{}, SessionID:{}, CTR-ID:{}, FAR-ID:{}",
                            ueAddr, teid, tunnelDst, sessionId, ctrId, farId);
                    up4Service.addPdr(deviceId, sessionId, ctrId, farId, ueAddr, teid, tunnelDst);
                } else if (srcInterface == NorthConstants.IFACE_CORE) {
                    // downlink
                    log.info("Interpreted write req as Downlink PDR with UE-ADDR:{}, " +
                                    "SessionID:{}, CTR-ID:{}, FAR-ID:{}",
                            ueAddr, sessionId, ctrId, farId);
                    up4Service.addPdr(deviceId, sessionId, ctrId, farId, ueAddr);
                } else {
                    log.error("PDR that does not match on an access or core src_iface " +
                            "is currently unsupported. Ignoring");
                }
            } else {
                actionUnknown = true;
            }
        } else if (tableId.equals(NorthConstants.FAR_TBL)) {
            int farId = getFieldInt(entry, NorthConstants.FAR_ID_KEY);
            int sessionId = getFieldInt(entry, NorthConstants.SESSION_ID_KEY);
            boolean needsDropping = getParamInt(entry, NorthConstants.DROP_FLAG) > 0;
            boolean notifyCp = getParamInt(entry, NorthConstants.NOTIFY_FLAG) > 0;
            if (actionId.equals(NorthConstants.LOAD_FAR_NORMAL)) {
                log.info("Interpreted write req as Uplink FAR with FAR-ID:{}, SessionID:{}, Drop:{}, Notify:{}",
                        farId, sessionId, needsDropping, notifyCp);
                up4Service.addFar(deviceId, sessionId, farId, needsDropping, notifyCp);
            } else if (actionId.equals(NorthConstants.LOAD_FAR_TUNNEL)) {
                Ip4Address tunnelSrc = getParamAddress(entry, NorthConstants.TUNNEL_SRC_PARAM);
                Ip4Address tunnelDst = getParamAddress(entry, NorthConstants.TUNNEL_DST_PARAM);
                int teid = getParamInt(entry, NorthConstants.TEID_PARAM);
                Up4Service.TunnelDesc tunnel = new Up4Service.TunnelDesc(tunnelSrc, tunnelDst, teid);
                log.info("Interpreted write req as Downlink FAR with FAR-ID:{}, SessionID:{}, Drop:{}," +
                                " Notify:{}, TunnelSrc:{}, TunnelDst:{}, TEID:{}",
                        farId, sessionId, needsDropping, notifyCp, tunnelSrc, tunnelDst, teid);
                up4Service.addFar(deviceId, sessionId, farId, needsDropping, notifyCp, tunnel);
            } else {
                actionUnknown = true;
            }
        } else {
            log.error("Attempting to translate table entry of unknown table! {}", tableId.toString());
            return;
        }
        if (actionUnknown) {
            log.error("Attempting to translate table entry with unknown action! {}", actionId.toString());
            return;
        }
        log.info("Translation of UP4 write request successful.");

    }


    public class Up4NorthService extends P4RuntimeGrpc.P4RuntimeImplBase {
        @Override
        public StreamObserver<P4RuntimeOuterClass.StreamMessageRequest>
        streamChannel(StreamObserver<P4RuntimeOuterClass.StreamMessageResponse> responseObserver) {
            // streamChannel handles packet I/O and master arbitration. It persists as long as the controller is active.
            log.info("streamChannel opened.");
            return new StreamObserver<P4RuntimeOuterClass.StreamMessageRequest>() {
                @Override
                public void onNext(P4RuntimeOuterClass.StreamMessageRequest value) {
                    log.info("Received streamChannel message.");
                    if (value.hasArbitration()) {
                        log.info("Stream message was arbitration request. " +
                                "Blindly telling requester they are the new master.");
                        // This response should tell every requester that it is now the master controller,
                        // due to the OK status.
                        responseObserver.onNext(P4RuntimeOuterClass.StreamMessageResponse.newBuilder()
                                .setArbitration(P4RuntimeOuterClass.MasterArbitrationUpdate.newBuilder()
                                        .setDeviceId(value.getArbitration().getDeviceId())
                                        .setRole(value.getArbitration().getRole())
                                        .setElectionId(value.getArbitration().getElectionId())
                                        .setStatus(Status.newBuilder().setCode(Code.OK.getNumber()).build())
                                        .build()
                                ).build()
                        );
                    } else {
                        log.warn("streamChannel message was not an  arbitration message.");
                        // We currently only respond to arbitration requests. Anything else gets a default response.
                        responseObserver.onNext(P4RuntimeOuterClass.StreamMessageResponse.getDefaultInstance());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    // No idea what to do here yet.
                    log.error("P4runtime streamChannel error", t);
                }

                @Override
                public void onCompleted() {
                    // Session termination. Is anything needed here?
                    log.info("streamChannel closed.");
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public void setForwardingPipelineConfig(P4RuntimeOuterClass.SetForwardingPipelineConfigRequest request,
                                                StreamObserver<P4RuntimeOuterClass.SetForwardingPipelineConfigResponse>
                                                        responseObserver) {
            // Currently ignoring device_id, role_id, election_id, action
            log.info("Received setForwardingPipelineConfig message.");
            P4InfoOuterClass.P4Info otherP4Info = request.getConfig().getP4Info();
            if (!otherP4Info.equals(p4Info)) {
                log.error("Someone attempted to write a p4info file that doesn't match our hardcoded one! What a jerk");
            } else {
                log.info("Received p4info correctly matches hardcoded p4info. Saving cookie.");
                pipeconfCookie = request.getConfig().getCookie().getCookie();
            }

            // Response is currently defined to be empty per p4runtime.proto
            responseObserver.onNext(P4RuntimeOuterClass.SetForwardingPipelineConfigResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void getForwardingPipelineConfig(P4RuntimeOuterClass.GetForwardingPipelineConfigRequest request,
                                                StreamObserver<P4RuntimeOuterClass.GetForwardingPipelineConfigResponse>
                                                        responseObserver) {
            responseObserver.onNext(
                P4RuntimeOuterClass.GetForwardingPipelineConfigResponse.newBuilder()
                    .setConfig(P4RuntimeOuterClass.ForwardingPipelineConfig.newBuilder()
                        .setCookie(P4RuntimeOuterClass.ForwardingPipelineConfig.Cookie.newBuilder()
                                .setCookie(pipeconfCookie))
                        .setP4Info(p4Info)
                        .build())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void write(P4RuntimeOuterClass.WriteRequest request,
                          StreamObserver<P4RuntimeOuterClass.WriteResponse> responseObserver) {
            log.info("Received write request.");
            if (p4Info == null) {
                log.error("Write request received before pipeline config set! Ignoring");
                responseObserver.onNext(P4RuntimeOuterClass.WriteResponse.getDefaultInstance());
                responseObserver.onCompleted();
                return;
            }
            for (P4RuntimeOuterClass.Update update : request.getUpdatesList()) {
                if (!update.hasEntity()) {
                    log.error("Update message with no entity received. Ignoring");
                    continue;
                } else if (!update.getEntity().hasTableEntry()) {
                    log.error("Update message with no table entry received. Ignoring.");
                    continue;
                }
                PiTableEntry entry;

                try {
                    PiEntity entity = Codecs.CODECS.entity().decode(update.getEntity(), null, pipeconf);
                    if (entity.piEntityType() == PiEntityType.TABLE_ENTRY) {
                        entry = (PiTableEntry) entity;
                    } else {
                        log.error("Update entity is not a table entry. Ignoring.");
                        continue;
                    }
                } catch (CodecException e) {
                    log.error("Unable to decode p4runtime entity update message", e);
                    continue;
                }
                if (update.getType() == P4RuntimeOuterClass.Update.Type.INSERT
                        || update.getType() == P4RuntimeOuterClass.Update.Type.MODIFY) {
                    log.info("Update type is insert or modify.");
                    translateAndInsert(entry);
                } else if (update.getType() == P4RuntimeOuterClass.Update.Type.DELETE) {
                    log.info("Update type is delete");
                    translateAndDelete(entry);
                } else {
                    throw new UnsupportedOperationException("Unsupported update type.");
                }
            }
            // Response is currently defined to be empty per p4runtime.proto
            responseObserver.onNext(P4RuntimeOuterClass.WriteResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void read(P4RuntimeOuterClass.ReadRequest request,
                         StreamObserver<P4RuntimeOuterClass.ReadResponse> responseObserver) {
            log.info("Received read request.");
            for (P4RuntimeOuterClass.Entity entity : request.getEntitiesList()) {
                PiEntity piEntity;
                try {
                    piEntity = Codecs.CODECS.entity().decode(entity, null, pipeconf);
                } catch (CodecException e) {
                    log.error("Unable to decode p4runtime read request entity", e);
                    continue;
                }
                if (piEntity.piEntityType() != PiEntityType.COUNTER_CELL) {
                    log.error("Received read request for an entity we don't yet support. Skipping");
                    continue;
                }
                PiCounterCell cellEntity = (PiCounterCell) piEntity;

                int counterIndex = (int) cellEntity.cellId().index();

                Up4Service.PdrStats ctrValues = up4Service.readCounter(deviceId, counterIndex);

                PiCounterId piCounterId = cellEntity.cellId().counterId();

                String gress;
                long pkts;
                long bytes;
                if (piCounterId.equals(NorthConstants.INGRESS_COUNTER_ID)) {
                    gress = "ingress";
                    pkts = ctrValues.ingressPkts;
                    bytes = ctrValues.ingressBytes;
                } else if (piCounterId.equals(NorthConstants.EGRESS_COUNTER_ID)) {
                    gress = "egress";
                    pkts = ctrValues.egressPkts;
                    bytes = ctrValues.egressBytes;
                } else {
                    log.error("Received read request for unknown counter {}. Skipping.", piCounterId);
                    continue;
                }

                responseObserver.onNext(P4RuntimeOuterClass.ReadResponse.newBuilder()
                    .addEntities(P4RuntimeOuterClass.Entity.newBuilder()
                        .setCounterEntry(P4RuntimeOuterClass.CounterEntry.newBuilder()
                            .setCounterId(entity.getCounterEntry().getCounterId())
                            .setData(P4RuntimeOuterClass.CounterData.newBuilder()
                                .setByteCount(bytes)
                                .setPacketCount(pkts)
                                .build())
                            .setIndex(P4RuntimeOuterClass.Index.newBuilder().setIndex(counterIndex))
                            .build())
                        .build())
                    .build());
                log.info("Responded to {} counter read request for counter ID {}.", gress, counterIndex);
            }

            responseObserver.onCompleted();
        }
    }

}
