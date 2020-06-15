/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.up4;

import com.google.common.collect.Iterators;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.stub.StreamObserver;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.pi.model.*;
import org.onosproject.net.pi.runtime.*;
import org.onosproject.p4runtime.ctl.codec.CodecException;
import org.onosproject.p4runtime.ctl.utils.PipeconfHelper;
import org.osgi.service.component.annotations.*;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyServerBuilder;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import p4.config.v1.P4InfoOuterClass;
import p4.v1.P4RuntimeGrpc;
import p4.v1.P4RuntimeOuterClass;

import org.onosproject.p4runtime.model.P4InfoParser;
import org.onosproject.p4runtime.model.P4InfoParserException;

import static org.onosproject.net.pi.model.PiPipeconf.ExtensionType.P4_INFO_TEXT;

import java.net.URL;
import java.io.File;
import java.util.Iterator;
import java.util.Optional;

import org.onosproject.net.pi.model.PiPipeconf;

import org.onosproject.p4runtime.ctl.codec.Codecs;

import org.onosproject.codec.impl.PiTableModelCodec;

import org.onosproject.codec.CodecService;


import static org.onosproject.up4.AppConstants.PIPECONF_ID;

@Component(immediate = true)
public class Up4NorthComponent {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Server server;
    private ApplicationId appId;
    private P4InfoOuterClass.P4Info p4Info;
    private long pipeconfCookie = 0xbeefbeef;

    private PiPipelineModel piModel;
    private PiPipeconf pipeconf;
    private DeviceId deviceId;

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
        //cfgService.registerProperties(getClass());
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
            log.info("UP4 gRPC server started on port {}", AppConstants.GRPC_SERVER_PORT);
        } catch (IOException e) {
            log.error("Unable to start gRPC server", e);
            throw new IllegalStateException("Unable to start gRPC server", e);
        }

        // TODO: add an integer deviceId to the netcfg, and use that instead
        Device device = deviceService.getDevice(DeviceId.deviceId("device:leaf1"));

        if (device == null) {
            log.error("Device \"device:leaf1\" is not found!!");
            throw new IllegalStateException("Unable to find device leaf1!");
        }
        deviceId = device.id();

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

    private ImmutableByteSequence getFieldVal(PiTableEntry entry, String fieldName) {
        Optional<PiFieldMatch> optField = entry.matchKey().fieldMatch(PiMatchFieldId.of(fieldName));
        if (optField.isEmpty()) {
            log.error("Field {} is not present where expected!", fieldName);
            return ImmutableByteSequence.ofZeros(1);
        }
        PiFieldMatch field = optField.get();
        if (field.type() == PiMatchType.EXACT) {
            return ((PiExactFieldMatch)field).value();
        }
        else if (field.type() == PiMatchType.LPM) {
            return ((PiLpmFieldMatch)field).value();
        }
        else if (field.type() == PiMatchType.TERNARY) {
            return ((PiTernaryFieldMatch)field).value();
        }
        else if (field.type() == PiMatchType.RANGE) {
            return ((PiRangeFieldMatch)field).lowValue();
        }
        else {
            log.error("Field has unknown match type!");
            return ImmutableByteSequence.ofZeros(1);
        }
    }

    private ImmutableByteSequence getParamIdValue(PiTableEntry entry, int paramId) {
        PiAction action = (PiAction)entry.action();
        return ((ImmutableByteSequence[])action.parameters().toArray())[paramId-1];
    }

    private Ip4Prefix getFieldPrefix(PiTableEntry entry, String fieldName) {
        PiLpmFieldMatch field = (PiLpmFieldMatch)entry.matchKey().fieldMatch(PiMatchFieldId.of(fieldName)).get();
        Ip4Address address = Ip4Address.valueOf(field.value().asArray());
        return Ip4Prefix.valueOf(address, field.prefixLength());
    }

    private Ip4Address getFieldAddress(PiTableEntry entry, String fieldName) {
        return Ip4Address.valueOf(getFieldVal(entry, fieldName).asArray());
    }


    private boolean fieldMaskIsZero(PiTableEntry entry, String fieldName) {
        Optional<PiFieldMatch> optField = entry.matchKey().fieldMatch(PiMatchFieldId.of(fieldName));
        if (optField.isEmpty()) {
            return true;
        }
        PiFieldMatch field = optField.get();
        if (field.type() != PiMatchType.TERNARY) {
            log.warn("Attempting to check mask for non-ternary field! {}", fieldName);
            return false;
        }
        for (byte b : ((PiTernaryFieldMatch)field).mask().asArray()) {
            if (b != (byte)0) { return false; }
        }
        return true;
    }

    private int byteSeqToInt(ImmutableByteSequence sequence) {
        int result = 0;
        for (byte b : sequence.asArray()) {
            result = (result << 8) + (int)b;
        }
        return result;
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
        if (tableId.equals(PiTableId.of("PreQosPipe.source_iface_lookup"))) {
            if (actionId.equals(PiActionId.of("PreQosPipe.set_source_iface"))) {
                // key: dst_prefix, args: direction, src_iface
                Ip4Prefix prefix = getFieldPrefix(entry, "ipv4_dst_prefix");
                if (getFieldVal(entry, "direction").equals(ImmutableByteSequence.copyFrom(1))) {
                    // 1 is uplink
                    up4Service.addS1uInterface(deviceId, prefix.address());
                }
                else {
                    // 2 is downlink. Assume 2 if not 1 for now.
                    up4Service.addUePool(deviceId, prefix);
                }
            }
            else {actionUnknown = true;}
        }
        else if (tableId.equals(PiTableId.of("PreQosPipe.pdrs"))) {
            if (actionId.equals(PiActionId.of("PreQosPipe.set_pdr_attributes"))) {
                int sessionId = byteSeqToInt(getParamIdValue(entry, 2));
                int ctrId = byteSeqToInt(getParamIdValue(entry, 3));
                int farId = byteSeqToInt(getParamIdValue(entry, 4));
                Ip4Address ueAddr = getFieldAddress(entry, "ue_addr");
                if (!fieldMaskIsZero(entry, "teid")) {
                    // uplink will have a non-ignored teid
                    int teid = byteSeqToInt(getFieldVal(entry, "teid"));
                    Ip4Address tunnelDst = getFieldAddress(entry, "tunnel_ipv4_dst");
                    up4Service.addPdr(deviceId, sessionId, ctrId, farId, ueAddr, teid, tunnelDst);
                }
                else {
                    // downlink
                    up4Service.addPdr(deviceId, sessionId, ctrId, farId, ueAddr);
                }
            }
            else {actionUnknown = true;}
        }
        else if (tableId.equals(PiTableId.of("PreQosPipe.load_far_attributes"))) {
            int farId = byteSeqToInt(getFieldVal(entry, "far_id"));
            int sessionId = byteSeqToInt(getFieldVal(entry, "session_id"));
            boolean needsDropping = byteSeqToInt(getParamIdValue(entry, 1)) > 0;
            boolean notifyCp = byteSeqToInt(getParamIdValue(entry, 2)) > 0;
            if (actionId.equals(PiActionId.of("PreQosPipe.load_normal_far_attributes"))) {
                up4Service.addFar(deviceId, sessionId, farId, needsDropping, notifyCp);
            }
            else if (actionId.equals(PiActionId.of("PreQosPipe.load_tunnel_far_attributes"))) {
                Ip4Address tunnelSrc = Ip4Address.valueOf(getParamIdValue(entry, 4).asArray());
                Ip4Address tunnelDst = Ip4Address.valueOf(getParamIdValue(entry, 5).asArray());
                int teid = byteSeqToInt(getParamIdValue(entry, 6));
                Up4Service.TunnelDesc tunnel = new Up4Service.TunnelDesc(tunnelSrc, tunnelDst, teid);
                up4Service.addFar(deviceId, sessionId, farId, needsDropping, notifyCp, tunnel);
            }
            else {actionUnknown = true;}
        }
        else {
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
        public StreamObserver<P4RuntimeOuterClass.StreamMessageRequest> streamChannel(StreamObserver<P4RuntimeOuterClass.StreamMessageResponse> responseObserver) {
            // streamChannel handles packet I/O and master arbitration. It persists as long as the controller is active.
            return new StreamObserver<P4RuntimeOuterClass.StreamMessageRequest>() {
                @Override
                public void onNext(P4RuntimeOuterClass.StreamMessageRequest value) {
                    if (value.hasArbitration()) {
                        log.info("Received arbitration messaged.");
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
                    }
                    else {
                        log.warn("Received non-arbitration message from streamChannel.");
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
                                                StreamObserver<P4RuntimeOuterClass.SetForwardingPipelineConfigResponse> responseObserver) {
            // Currently ignoring device_id, role_id, election_id, action
            log.info("Received setForwardingPipelineConfig message.");
            P4InfoOuterClass.P4Info otherP4Info = request.getConfig().getP4Info();
            if (!otherP4Info.equals(p4Info)) {
                log.error("Someone attempted to write a p4info file that doesn't match our hardcoded one! What a jerk");
            }

            // Response is currently defined to be empty per p4runtime.proto
            responseObserver.onNext(P4RuntimeOuterClass.SetForwardingPipelineConfigResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void getForwardingPipelineConfig(P4RuntimeOuterClass.GetForwardingPipelineConfigRequest request, StreamObserver<P4RuntimeOuterClass.GetForwardingPipelineConfigResponse> responseObserver) {
            responseObserver.onNext(
                P4RuntimeOuterClass.GetForwardingPipelineConfigResponse.newBuilder()
                    .setConfig(P4RuntimeOuterClass.ForwardingPipelineConfig.newBuilder()
                        .setCookie(P4RuntimeOuterClass.ForwardingPipelineConfig.Cookie.newBuilder().setCookie(pipeconfCookie))
                        .setP4Info(p4Info)
                        .build())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void write(P4RuntimeOuterClass.WriteRequest request, StreamObserver<P4RuntimeOuterClass.WriteResponse> responseObserver) {
            log.info("Received write request.");
            if (p4Info == null) {
                log.error("Write request received before pipeline config set! Ignoring");
                responseObserver.onNext(P4RuntimeOuterClass.WriteResponse.getDefaultInstance());
                responseObserver.onCompleted();
                return;
            }
            //super.write(request, responseObserver);
            for(P4RuntimeOuterClass.Update update : request.getUpdatesList()) {
                if (!update.hasEntity()) {
                    log.error("Update message with no entity received. Ignoring");
                    continue;
                }
                else if (!update.getEntity().hasTableEntry()) {
                    log.error("Update message with no table entry received. Ignoring.");
                    continue;
                }
                PiTableEntry entry;
                try {
                    PiEntity entity = Codecs.CODECS.entity().decode(update.getEntity(), null, pipeconf);
                    if (entity.piEntityType() == PiEntityType.TABLE_ENTRY) {
                        entry = (PiTableEntry) entity;
                    }
                    else {
                        log.error("Update entity is not a table entry. Ignoring.");
                        continue;
                    }
                } catch (CodecException e) {
                    log.error("Unable to decode p4runtime entity update message", e);
                    continue;
                }
                if (update.getType() == P4RuntimeOuterClass.Update.Type.INSERT
                        || update.getType() == P4RuntimeOuterClass.Update.Type.MODIFY) {
                    translateAndInsert(entry);
                }
                else if (update.getType() == P4RuntimeOuterClass.Update.Type.DELETE){
                    log.error("Unsupported delete update received. Ignoring.");
                }
                else {
                    throw new UnsupportedOperationException("Unsupported update type.");
                }
            }
            // Response is currently defined to be empty per p4runtime.proto
            responseObserver.onNext(P4RuntimeOuterClass.WriteResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

    }

}
