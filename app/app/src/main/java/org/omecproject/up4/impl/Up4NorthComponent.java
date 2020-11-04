/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.Up4Translator;
import org.omecproject.up4.UpfInterface;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.pi.model.DefaultPiPipeconf;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.model.PiPipelineModel;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiCounterCellId;
import org.onosproject.net.pi.runtime.PiEntity;
import org.onosproject.net.pi.runtime.PiEntityType;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.p4runtime.ctl.codec.CodecException;
import org.onosproject.p4runtime.ctl.codec.Codecs;
import org.onosproject.p4runtime.ctl.utils.P4InfoBrowser;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.omecproject.up4.impl.AppConstants.PIPECONF_ID;
import static org.onosproject.net.pi.model.PiPipeconf.ExtensionType.P4_INFO_TEXT;


/* TODO: listen for netcfg changes. If the grpc port in the netcfg is different from the default,
         restart the grpc server on the new port.
 */

@Component(immediate = true)
public class Up4NorthComponent {
    private static final ImmutableByteSequence ZERO_SEQ = ImmutableByteSequence.ofZeros(4);
    private final Logger log = LoggerFactory.getLogger(getClass());
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected Up4Service up4Service;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected Up4Translator up4Translator;
    private Server server;
    protected P4InfoOuterClass.P4Info p4Info;
    private long pipeconfCookie = 0xbeefbeef;
    protected PiPipeconf pipeconf;

    protected final Up4NorthService up4NorthService = new Up4NorthService();

    @Activate
    protected void activate() {
        log.info("Starting...");
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
                    .addService(up4NorthService)
                    .build()
                    .start();
            log.info("UP4 gRPC server started on port {}", AppConstants.GRPC_SERVER_PORT);
        } catch (IOException e) {
            log.error("Unable to start gRPC server", e);
            throw new IllegalStateException("Unable to start gRPC server", e);
        }
        log.info("Started.");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Shutting down...");
        if (server != null) {
            server.shutdown();
        }
        log.info("Stopped.");
    }

    protected static PiPipeconf buildPipeconf() throws P4InfoParserException {
        final URL p4InfoUrl = Up4NorthComponent.class.getResource(AppConstants.P4INFO_PATH);
        final PiPipelineModel pipelineModel = P4InfoParser.parse(p4InfoUrl);
        return DefaultPiPipeconf.builder()
                .withId(PIPECONF_ID)
                .withPipelineModel(pipelineModel)
                .addExtension(P4_INFO_TEXT, p4InfoUrl)
                .build();
    }


    /**
     * Translate the given logical pipeline table entry to a Up4Service entry deletion call.
     *
     * @param entry The logical table entry to be deleted
     */
    private void translateEntryAndDelete(PiTableEntry entry) {
        log.debug("Translating UP4 deletion request to fabric entry deletion.");
        if (up4Translator.isUp4Interface(entry)) {
            try {
                UpfInterface upfInterface = up4Translator.up4EntryToInterface(entry);
                up4Service.getUpfProgrammable().removeInterface(upfInterface);
            } catch (Up4Translator.Up4TranslationException e) {
                log.warn("Failed to parse UP4 interface in delete write! Error was: {}", e.getMessage());
            }
        } else if (up4Translator.isUp4Pdr(entry)) {
            try {
                PacketDetectionRule pdr = up4Translator.up4EntryToPdr(entry);
                log.debug("Translated UP4 PDR successfully. Deleting.");
                up4Service.getUpfProgrammable().removePdr(pdr);
            } catch (Up4Translator.Up4TranslationException e) {
                log.warn("Failed to parse UP4 PDR in delete write! Error was: {}", e.getMessage());
            }
        } else if (up4Translator.isUp4Far(entry)) {
            try {
                ForwardingActionRule far = up4Translator.up4EntryToFar(entry);
                log.debug("Translated UP4 FAR successfully. Deleting.");
                up4Service.getUpfProgrammable().removeFar(far);
            } catch (Up4Translator.Up4TranslationException e) {
                log.warn("Failed to parse UP4 FAR in delete write! Error was {}", e.getMessage());
            }
        } else {
            log.warn("Received unknown table entry for table {} in UP4 delete request:", entry.table().id());
        }
    }

    /**
     * Translate the given logical pipeline table entry to a Up4Service entry insertion call.
     *
     * @param entry The logical table entry to be inserted
     */
    private Throwable translateEntryAndInsert(PiTableEntry entry) {
        log.debug("Translating UP4 write request to fabric entry.");
        PiTableId tableId = entry.table();
        if (entry.action().type() != PiTableAction.Type.ACTION) {
            log.warn("Action profile entry insertion not supported. Ignoring.");
            return null;
        }

        if (up4Translator.isUp4Interface(entry)) {
            try {
                UpfInterface iface = up4Translator.up4EntryToInterface(entry);
                up4Service.getUpfProgrammable().addInterface(iface);
            } catch (Up4Translator.Up4TranslationException e) {
                log.warn("Unable to translate UP4 interface table entry! Error was: {}", e.getMessage());
            }
        } else if (up4Translator.isUp4Pdr(entry)) {
            try {
                PacketDetectionRule pdr = up4Translator.up4EntryToPdr(entry);
                up4Service.getUpfProgrammable().addPdr(pdr);
            } catch (Up4Translator.Up4TranslationException e) {
                log.warn("Failed to parse UP4 PDR! Error was: {}", e.getMessage());
            } catch (IndexOutOfBoundsException e) {
                log.warn("Failed to add PDR, error was: {}", e.getMessage());
                return io.grpc.Status.OUT_OF_RANGE
                        .withDescription("PDR counter index out of range!")
                        .asException();
            }
        } else if (up4Translator.isUp4Far(entry)) {
            try {
                ForwardingActionRule far = up4Translator.up4EntryToFar(entry);
                up4Service.getUpfProgrammable().addFar(far);
            } catch (Up4Translator.Up4TranslationException e) {
                log.warn("Failed to parse UP4 FAR! Error was {}", e.getMessage());
            }
        } else {
            log.warn("Received unsupported table entry for table {} in UP4 write request:", entry.table().id());
        }
        return null;
    }


    /**
     * Find all table entries that match the requested entry, and translate them to p4runtime entities for
     * responding to a read request.
     *
     * @param requestedEntry the entry from a p4runtime read request
     * @return all entries that match the request, translated to p4runtime entities
     */
    private List<P4RuntimeOuterClass.Entity> readEntriesAndTranslate(PiTableEntry requestedEntry) {
        List<P4RuntimeOuterClass.Entity> translatedEntries = new ArrayList<>();
        // Respond with all entries for the table of the requested entry, ignoring other requested properties
        // TODO: return more specific responses
        if (up4Translator.isUp4Interface(requestedEntry)) {
            for (UpfInterface iface : up4Service.getUpfProgrammable().getInstalledInterfaces()) {
                if (iface.isDbufReceiver()) {
                    // Don't expose the dbuf interface to the logical switch
                    continue;
                }
                log.debug("Translating an interface for a read request: {}", iface);
                try {
                    P4RuntimeOuterClass.Entity responseEntity = Codecs.CODECS.entity().encode(
                            up4Translator.interfaceToUp4Entry(iface), null, pipeconf);
                    translatedEntries.add(responseEntity);

                } catch (Up4Translator.Up4TranslationException | CodecException e) {
                    log.error("Unable to encode interface to a UP4 read response. Error was: {}",
                            e.getMessage());
                }
            }
        } else if (up4Translator.isUp4Far(requestedEntry)) {
            for (ForwardingActionRule far : up4Service.getUpfProgrammable().getInstalledFars()) {
                log.debug("Translating a FAR for a read request: {}", far);
                try {
                    P4RuntimeOuterClass.Entity responseEntity = Codecs.CODECS.entity().encode(
                            up4Translator.farToUp4Entry(far), null, pipeconf);
                    translatedEntries.add(responseEntity);
                } catch (Up4Translator.Up4TranslationException | CodecException e) {
                    log.error("Unable to encode FAR to a UP4 read response. Error was: {}",
                            e.getMessage());
                }
            }
        } else if (up4Translator.isUp4Pdr(requestedEntry)) {
            for (PacketDetectionRule pdr : up4Service.getUpfProgrammable().getInstalledPdrs()) {
                log.debug("Translating a PDR for a read request: {}", pdr);
                try {
                    P4RuntimeOuterClass.Entity responseEntity = Codecs.CODECS.entity().encode(
                            up4Translator.pdrToUp4Entry(pdr), null, pipeconf);
                    translatedEntries.add(responseEntity);
                } catch (Up4Translator.Up4TranslationException | CodecException e) {
                    log.error("Unable to encode PDR to a UP4 read response. Error was: {}",
                            e.getMessage());
                }
            }
        } else {
            log.warn("Unknown entry requested by UP4 read request! Entry was {}", requestedEntry);
        }
        return translatedEntries;
    }

    /**
     * Update the logical p4info with physical resource sizes. TODO: set table sizes as well
     *
     * @param p4Info a logical UP4 switch's p4info
     * @return the same p4info, but with resource sizes set to the sizes from the physical switch
     */
    @VisibleForTesting
    P4InfoOuterClass.P4Info addPhysicalSizesToP4Info(P4InfoOuterClass.P4Info p4Info) {
        var newP4InfoBuilder = P4InfoOuterClass.P4Info.newBuilder(p4Info).clearCounters();
        int physicalCounterSize = up4Service.getUpfProgrammable().pdrCounterSize();
        Collection<P4InfoOuterClass.Counter> correctedCounters = new ArrayList<>();
        p4Info.getCountersList().forEach(counter -> {
            newP4InfoBuilder.addCounters(
                    P4InfoOuterClass.Counter.newBuilder(counter)
                            .setSize((long) physicalCounterSize).build());
        });
        return newP4InfoBuilder.build();
    }


    /**
     * Read the all p4 counter cell requested by the message, and translate them to p4runtime entities for crafting
     * a p4runtime read response.
     *
     * @param message a p4runtime CounterEntry message from a read request
     * @return the requested counter cells' contents, as a list of p4runtime entities
     */
    private List<P4RuntimeOuterClass.Entity> readCountersAndTranslate(P4RuntimeOuterClass.CounterEntry message) {
        ArrayList<PiCounterCell> responseCells = new ArrayList<>();
        Integer index = null;
        // FYI a counter read message with no index corresponds to a wildcard read of all indices
        if (message.hasIndex()) {
            index = (int) message.getIndex().getIndex();
        }
        String counterName = null;
        PiCounterId piCounterId = null;
        int counterId = message.getCounterId();
        // FYI a counterId of 0 corresponds to a wildcard read of all counters
        if (counterId != 0) {
            try {
                counterName = PipeconfHelper.getP4InfoBrowser(pipeconf).counters()
                        .getById(message.getCounterId())
                        .getPreamble()
                        .getName();
                piCounterId = PiCounterId.of(counterName);
            } catch (P4InfoBrowser.NotFoundException e) {
                log.warn("Unable to find UP4 counter with ID {}", counterId);
                return List.of();
            }
        }
        // At this point, the counterName is null if all counters are requested, and non-null if a specific
        //  counter was requested. The index is null if all cells are requested, and non-null if a specific
        //  cell was requested.
        if (counterName != null && index != null) {
            // A single counter cell was requested
            PdrStats ctrValues = up4Service.getUpfProgrammable().readCounter(index);
            long pkts;
            long bytes;
            if (piCounterId.equals(NorthConstants.INGRESS_COUNTER_ID)) {
                pkts = ctrValues.getIngressPkts();
                bytes = ctrValues.getIngressBytes();
            } else if (piCounterId.equals(NorthConstants.EGRESS_COUNTER_ID)) {
                pkts = ctrValues.getEgressPkts();
                bytes = ctrValues.getEgressBytes();
            } else {
                log.warn("Received read request for unknown counter {}. Skipping.", piCounterId);
                return List.of();
            }
            responseCells.add(new PiCounterCell(PiCounterCellId.ofIndirect(piCounterId, index), pkts, bytes));
        } else {
            // All cells were requested, either for a specific counter or all counters
            Collection<PdrStats> allStats = up4Service.getUpfProgrammable().readAllCounters();
            for (PdrStats stat : allStats) {
                if (piCounterId == null || piCounterId.equals(NorthConstants.INGRESS_COUNTER_ID)) {
                    // If all counters were requested, or just the ingress one
                    responseCells.add(new PiCounterCell(
                            PiCounterCellId.ofIndirect(NorthConstants.INGRESS_COUNTER_ID, stat.getCellId()),
                            stat.getIngressPkts(), stat.getIngressBytes()));
                }
                if (piCounterId == null || piCounterId.equals(NorthConstants.EGRESS_COUNTER_ID)) {
                    // If all counters were requested, or just the egress one
                    responseCells.add(new PiCounterCell(
                            PiCounterCellId.ofIndirect(NorthConstants.EGRESS_COUNTER_ID, stat.getCellId()),
                            stat.getEgressPkts(), stat.getEgressBytes()));
                }
            }
        }
        List<P4RuntimeOuterClass.Entity> responseEntities = new ArrayList<>();
        for (PiCounterCell cell : responseCells) {
            try {
                responseEntities.add(Codecs.CODECS.entity().encode(cell, null, pipeconf));
                log.debug("Encoded response to counter read request for counter {} and index {}",
                        cell.cellId().counterId(), cell.cellId().index());
            } catch (CodecException e) {
                log.error("Unable to encode counter cell into a p4runtime entity. Exception was: {}",
                        e.getMessage());
            }
        }
        return responseEntities;
    }

    /**
     * The P4Runtime server service.
     */
    public class Up4NorthService extends P4RuntimeGrpc.P4RuntimeImplBase {

        /**
         * A streamChannel represents a P4Runtime session. This session should persist for the lifetime of a connected
         * controller. The streamChannel is used for master/slave arbitration. Currently this implementation does not
         * track a master, and blindly tells every controller that they are the master as soon as they send an
         * arbitration request. We also do not yet handle anything except arbitration requests.
         *
         * @param responseObserver The thing that is fed responses to arbitration requests.
         * @return A thing that will be fed arbitration requests.
         */
        @Override
        public StreamObserver<P4RuntimeOuterClass.StreamMessageRequest>
        streamChannel(StreamObserver<P4RuntimeOuterClass.StreamMessageResponse> responseObserver) {
            // streamChannel handles packet I/O and master arbitration. It persists as long as the controller is active.
            log.info("streamChannel opened.");
            return new StreamObserver<>() {
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

        /**
         * Receives a pipeline config from a client. Discards all but the p4info file and cookie, and compares the
         * received p4info to the already present hardcoded p4info. If the two match, the cookie is stored and a
         * success response is sent. If they do not, the cookie is disarded and an error is reported.
         *
         * @param request          A request containing a p4info and cookie
         * @param responseObserver The thing that is fed a response to the config request.
         */
        @Override
        public void setForwardingPipelineConfig(P4RuntimeOuterClass.SetForwardingPipelineConfigRequest request,
                                                StreamObserver<P4RuntimeOuterClass.SetForwardingPipelineConfigResponse>
                                                        responseObserver) {
            // Currently ignoring device_id, role_id, election_id, action
            log.info("Received setForwardingPipelineConfig message.");
            P4InfoOuterClass.P4Info otherP4Info = request.getConfig().getP4Info();
            if (!otherP4Info.equals(p4Info)) {
                log.warn("Someone attempted to write a p4info file that doesn't match our hardcoded one! What a jerk");
            } else {
                log.info("Received p4info correctly matches hardcoded p4info. Saving cookie.");
                pipeconfCookie = request.getConfig().getCookie().getCookie();
            }

            // Response is currently defined to be empty per p4runtime.proto
            responseObserver.onNext(P4RuntimeOuterClass.SetForwardingPipelineConfigResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        /**
         * Returns the UP4 logical switch p4info (but with physical resource sizes) and cookie.
         *
         * @param request          A request for a forwarding pipeline config
         * @param responseObserver The thing that is fed the pipeline config response.
         */
        @Override
        public void getForwardingPipelineConfig(P4RuntimeOuterClass.GetForwardingPipelineConfigRequest request,
                                                StreamObserver<P4RuntimeOuterClass.GetForwardingPipelineConfigResponse>
                                                        responseObserver) {

            responseObserver.onNext(
                    P4RuntimeOuterClass.GetForwardingPipelineConfigResponse.newBuilder()
                            .setConfig(P4RuntimeOuterClass.ForwardingPipelineConfig.newBuilder()
                                    .setCookie(P4RuntimeOuterClass.ForwardingPipelineConfig.Cookie.newBuilder()
                                            .setCookie(pipeconfCookie))
                                    .setP4Info(addPhysicalSizesToP4Info(p4Info))
                                    .build())
                            .build());
            responseObserver.onCompleted();
        }

        /**
         * Writes entities to the logical UP4 switch. Currently only supports direct table entries.
         *
         * @param request          A request containing entities to be written
         * @param responseObserver The thing that is fed a response once writing has concluded.
         */
        @Override
        public void write(P4RuntimeOuterClass.WriteRequest request,
                          StreamObserver<P4RuntimeOuterClass.WriteResponse> responseObserver) {
            log.debug("Received write request.");
            if (p4Info == null) {
                log.warn("Write request received before pipeline config set! Ignoring");
                responseObserver.onNext(P4RuntimeOuterClass.WriteResponse.getDefaultInstance());
                responseObserver.onCompleted();
                return;
            }
            for (P4RuntimeOuterClass.Update update : request.getUpdatesList()) {
                if (!update.hasEntity()) {
                    log.warn("Update message with no entity received. Ignoring");
                    continue;
                } else if (!update.getEntity().hasTableEntry()) {
                    log.warn("Update message with no table entry received. Ignoring.");
                    continue;
                }
                PiTableEntry entry;

                try {
                    PiEntity entity = Codecs.CODECS.entity().decode(update.getEntity(), null, pipeconf);
                    if (entity.piEntityType() == PiEntityType.TABLE_ENTRY) {
                        entry = (PiTableEntry) entity;
                    } else {
                        log.warn("Update entity is not a table entry. Ignoring.");
                        continue;
                    }
                } catch (CodecException e) {
                    log.warn("Unable to decode p4runtime entity update message", e);
                    continue;
                }
                if (update.getType() == P4RuntimeOuterClass.Update.Type.INSERT
                        || update.getType() == P4RuntimeOuterClass.Update.Type.MODIFY) {
                    log.debug("Update type is insert or modify.");
                    Throwable insertionError = translateEntryAndInsert(entry);
                    if (insertionError != null) {
                        responseObserver.onError(insertionError);
                        return;
                    }
                } else if (update.getType() == P4RuntimeOuterClass.Update.Type.DELETE) {
                    log.debug("Update type is delete");
                    translateEntryAndDelete(entry);
                } else {
                    throw new UnsupportedOperationException("Unsupported update type.");
                }
            }
            // Response is currently defined to be empty per p4runtime.proto
            responseObserver.onNext(P4RuntimeOuterClass.WriteResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        /**
         * Reads entities from the logical UP4 switch. Currently only supports counter reads.
         *
         * @param request          A request containing one or more entities to be read.
         * @param responseObserver Thing that will be fed descriptions of the requested entities.
         */
        @Override
        public void read(P4RuntimeOuterClass.ReadRequest request,
                         StreamObserver<P4RuntimeOuterClass.ReadResponse> responseObserver) {
            log.debug("Received read request.");
            for (P4RuntimeOuterClass.Entity requestEntity : request.getEntitiesList()) {
                if (requestEntity.getEntityCase() == P4RuntimeOuterClass.Entity.EntityCase.COUNTER_ENTRY) {
                    log.debug("Received read request for logical counter cell");
                    responseObserver.onNext(P4RuntimeOuterClass.ReadResponse.newBuilder()
                            .addAllEntities(readCountersAndTranslate(requestEntity.getCounterEntry()))
                            .build());
                    log.debug("Finished responding to counter read request.");
                } else if (requestEntity.getEntityCase() == P4RuntimeOuterClass.Entity.EntityCase.TABLE_ENTRY) {
                    log.info("Received read request for logical table entry");
                    PiTableEntry requestEntry;
                    try {
                        requestEntry = (PiTableEntry) Codecs.CODECS.entity().decode(
                                requestEntity, null, pipeconf);
                    } catch (CodecException e) {
                        log.warn("Unable to decode p4runtime read request entity", e);
                        continue;
                    }
                    responseObserver.onNext(P4RuntimeOuterClass.ReadResponse.newBuilder()
                            .addAllEntities(readEntriesAndTranslate(requestEntry))
                            .build());
                    log.debug("Responded to table entry read request.");
                } else {
                    log.warn("Received read request for an entity we don't yet support. Skipping");
                }
            }
            log.debug("Done read response.");
            responseObserver.onCompleted();
        }
    }
}
