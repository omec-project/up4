/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.Server;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.PdrStats;
import org.omecproject.up4.Up4Event;
import org.omecproject.up4.Up4EventListener;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.Up4Translator;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammableException;
import org.omecproject.up4.behavior.Up4TranslatorImpl;
import org.onlab.packet.Ip4Address;
import org.onlab.util.HexString;
import org.onlab.util.ImmutableByteSequence;
import org.onlab.util.SharedExecutors;
import org.onosproject.net.pi.model.DefaultPiPipeconf;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.model.PiPipelineModel;
import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiCounterCellId;
import org.onosproject.net.pi.runtime.PiEntity;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.p4runtime.ctl.codec.CodecException;
import org.onosproject.p4runtime.ctl.codec.Codecs;
import org.onosproject.p4runtime.ctl.utils.P4InfoBrowser;
import org.onosproject.p4runtime.ctl.utils.PipeconfHelper;
import org.onosproject.p4runtime.model.P4InfoParser;
import org.onosproject.p4runtime.model.P4InfoParserException;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import p4.config.v1.P4InfoOuterClass;
import p4.v1.P4DataOuterClass;
import p4.v1.P4RuntimeGrpc;
import p4.v1.P4RuntimeOuterClass;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.grpc.Status.INVALID_ARGUMENT;
import static io.grpc.Status.PERMISSION_DENIED;
import static io.grpc.Status.UNIMPLEMENTED;
import static java.lang.String.format;
import static org.omecproject.up4.impl.AppConstants.PIPECONF_ID;
import static org.omecproject.up4.impl.NorthConstants.DDN_DIGEST_ID;
import static org.onosproject.net.pi.model.PiPipeconf.ExtensionType.P4_INFO_TEXT;


/* TODO: listen for netcfg changes. If the grpc port in the netcfg is different from the default,
         restart the grpc server on the new port.
 */

@Component(immediate = true)
public class Up4NorthComponent {
    private static final ImmutableByteSequence ZERO_SEQ = ImmutableByteSequence.ofZeros(4);
    private static final int DEFAULT_DEVICE_ID = 1;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected Up4Service up4Service;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    protected final Up4Translator up4Translator = new Up4TranslatorImpl();
    protected final Up4NorthService up4NorthService = new Up4NorthService();
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Up4EventListener up4EventListener = new InternalUp4EventListener();
    // Stores open P4Runtime StreamChannel(s)
    private final ConcurrentMap<P4RuntimeOuterClass.Uint128,
            StreamObserver<P4RuntimeOuterClass.StreamMessageResponse>> streams =
            Maps.newConcurrentMap();
    private final AtomicInteger ddnDigestListId = new AtomicInteger(0);

    protected P4InfoOuterClass.P4Info p4Info;
    protected PiPipeconf pipeconf;
    private Server server;
    // Maps UE address to F-SEID. Required for DDNs. Eventually consistent as DDNs are best effort,
    // if an entry cannot be found we will not be able to wake up the UE at this time.
    protected EventuallyConsistentMap<Ip4Address, ImmutableByteSequence> fseids;
    private long pipeconfCookie = 0xbeefbeef;

    public Up4NorthComponent() {
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

    @Activate
    protected void activate() {
        log.info("Starting...");
        // Load p4info.
        try {
            pipeconf = buildPipeconf();
        } catch (P4InfoParserException e) {
            log.error("Unable to parse UP4 p4info file.", e);
            throw new IllegalStateException("Unable to parse UP4 p4info file.", e);
        }
        p4Info = PipeconfHelper.getP4Info(pipeconf);
        // Init distributed maps.
        fseids = storageService
                .<Ip4Address, ImmutableByteSequence>eventuallyConsistentMapBuilder()
                .withName("up4-ue-to-fseid")
                .withSerializer(KryoNamespaces.API)
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .build();
        // Start server.
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
        // Listen for events.
        up4Service.addListener(up4EventListener);
        log.info("Started.");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Shutting down...");
        up4Service.removeListener(up4EventListener);
        if (server != null) {
            server.shutdown();
        }
        fseids.destroy();
        fseids = null;
        log.info("Stopped.");
    }

    /**
     * Translate the given logical pipeline table entry to a Up4Service entry deletion call.
     *
     * @param entry The logical table entry to be deleted
     * @throws StatusException if the table entry fails translation or cannot be deleted
     */
    private void translateEntryAndDelete(PiTableEntry entry) throws StatusException {
        log.debug("Translating UP4 deletion request to fabric entry deletion.");
        try {
            if (up4Translator.isUp4Interface(entry)) {
                UpfInterface upfInterface = up4Translator.up4EntryToInterface(entry);
                up4Service.getUpfProgrammable().removeInterface(upfInterface);

            } else if (up4Translator.isUp4Pdr(entry)) {
                PacketDetectionRule pdr = up4Translator.up4EntryToPdr(entry);
                log.debug("Translated UP4 PDR successfully. Deleting.");
                up4Service.getUpfProgrammable().removePdr(pdr);

            } else if (up4Translator.isUp4Far(entry)) {
                ForwardingActionRule far = up4Translator.up4EntryToFar(entry);
                log.debug("Translated UP4 FAR successfully. Deleting.");
                up4Service.getUpfProgrammable().removeFar(far);

            } else {
                log.warn("Received unknown table entry for table {} in UP4 delete request:", entry.table().id());
                throw INVALID_ARGUMENT
                        .withDescription("Deletion request was for an unknown table.")
                        .asException();
            }
        } catch (Up4Translator.Up4TranslationException e) {
            log.warn("Failed to translate UP4 entry in deletion request: {}", e.getMessage());
            throw INVALID_ARGUMENT
                    .withDescription("Failed to translate entry in deletion request: " + e.getMessage())
                    .asException();
        } catch (UpfProgrammableException e) {
            log.warn("Failed to complete deletion request: {}", e.getMessage());
            throw io.grpc.Status.UNAVAILABLE
                    .withDescription(e.getMessage())
                    .asException();
        }
    }

    /**
     * Translate the given logical pipeline table entry to a Up4Service entry insertion call.
     *
     * @param entry The logical table entry to be inserted
     * @throws StatusException if the entry fails translation or cannot be inserted
     */
    private void translateEntryAndInsert(PiTableEntry entry) throws StatusException {
        log.debug("Translating UP4 write request to fabric entry.");
        if (entry.action().type() != PiTableAction.Type.ACTION) {
            log.warn("Action profile entry insertion not supported. Ignoring.");
            throw UNIMPLEMENTED
                    .withDescription("Action profile entries not supported by UP4.")
                    .asException();
        }
        try {
            if (up4Translator.isUp4Interface(entry)) {
                UpfInterface iface = up4Translator.up4EntryToInterface(entry);
                up4Service.getUpfProgrammable().addInterface(iface);
            } else if (up4Translator.isUp4Pdr(entry)) {
                PacketDetectionRule pdr = up4Translator.up4EntryToPdr(entry);
                updateFseidMap(pdr);
                up4Service.getUpfProgrammable().addPdr(pdr);
            } else if (up4Translator.isUp4Far(entry)) {
                ForwardingActionRule far = up4Translator.up4EntryToFar(entry);
                up4Service.getUpfProgrammable().addFar(far);
            } else {
                log.warn("Received entry for unsupported table in UP4 write request: {}", entry.table().id());
                throw INVALID_ARGUMENT
                        .withDescription("Write request was for an unknown table.")
                        .asException();
            }
        } catch (Up4Translator.Up4TranslationException e) {
            log.warn("Failed to parse entry from a write request: {}", e.getMessage());
            throw INVALID_ARGUMENT
                    .withDescription("Translation error: " + e.getMessage())
                    .asException();
        } catch (UpfProgrammableException e) {
            log.warn("Failed to complete table entry insertion request: {}", e.getMessage());
            switch (e.getType()) {
                case TABLE_EXHAUSTED:
                    throw io.grpc.Status.RESOURCE_EXHAUSTED
                            .withDescription(e.getMessage())
                            .asException();
                case COUNTER_INDEX_OUT_OF_RANGE:
                    throw INVALID_ARGUMENT
                            .withDescription(e.getMessage())
                            .asException();
                case UNKNOWN:
                default:
                    throw io.grpc.Status.UNAVAILABLE
                            .withDescription(e.getMessage())
                            .asException();
            }
        }
    }

    private void updateFseidMap(PacketDetectionRule pdr) {
        // We know from the PFCP spec that when installing PDRs for the same UE, the F-SEID will be
        // the same for all PDRs, both downlink and uplink. The F-SEID for a given UE will only
        // change after a detach. For simplicity, we never remove values. The map provides the
        // last-seen F-SEID for a given UE. When scaling the number of UEs, if memory is an issues,
        // we should consider querying for F-SEID in UpfProgrammable, which can be more efficient
        // (e.g., by searching in the PDR flow rules).
        if (pdr.ueAddress() != null) {
            log.debug("Updating map with last seen F-SEID: {} -> {}", pdr.ueAddress(), pdr.sessionId());
            fseids.put(pdr.ueAddress(), pdr.sessionId());
        }
    }


    /**
     * Find all table entries that match the requested entry, and translate them to p4runtime
     * entities for responding to a read request.
     *
     * @param requestedEntry the entry from a p4runtime read request
     * @return all entries that match the request, translated to p4runtime entities
     * @throws StatusException if the requested entry fails translation
     */
    private List<P4RuntimeOuterClass.Entity> readEntriesAndTranslate(PiTableEntry requestedEntry)
            throws StatusException {
        List<P4RuntimeOuterClass.Entity> translatedEntries = new ArrayList<>();
        // Respond with all entries for the table of the requested entry, ignoring other requested properties
        // TODO: return more specific responses
        try {
            if (up4Translator.isUp4Interface(requestedEntry)) {
                for (UpfInterface iface : up4Service.getUpfProgrammable().getInterfaces()) {
                    if (iface.isDbufReceiver()) {
                        // Don't expose the dbuf interface to the logical switch
                        continue;
                    }
                    log.debug("Translating an interface for a read request: {}", iface);
                    P4RuntimeOuterClass.Entity responseEntity = Codecs.CODECS.entity().encode(
                            up4Translator.interfaceToUp4Entry(iface), null, pipeconf);
                    translatedEntries.add(responseEntity);
                }
            } else if (up4Translator.isUp4Far(requestedEntry)) {
                for (ForwardingActionRule far : up4Service.getUpfProgrammable().getFars()) {
                    log.debug("Translating a FAR for a read request: {}", far);
                    P4RuntimeOuterClass.Entity responseEntity = Codecs.CODECS.entity().encode(
                            up4Translator.farToUp4Entry(far), null, pipeconf);
                    translatedEntries.add(responseEntity);
                }
            } else if (up4Translator.isUp4Pdr(requestedEntry)) {
                for (PacketDetectionRule pdr : up4Service.getUpfProgrammable().getPdrs()) {
                    log.debug("Translating a PDR for a read request: {}", pdr);
                    P4RuntimeOuterClass.Entity responseEntity = Codecs.CODECS.entity().encode(
                            up4Translator.pdrToUp4Entry(pdr), null, pipeconf);
                    translatedEntries.add(responseEntity);
                }
            } else {
                log.warn("Unknown entry requested by UP4 read request: {}", requestedEntry);
                throw INVALID_ARGUMENT
                        .withDescription("Read request was for an unknown table.")
                        .asException();
            }
        } catch (Up4Translator.Up4TranslationException | UpfProgrammableException | CodecException e) {
            log.warn("Unable to encode/translate a read entry to a UP4 read response: {}",
                    e.getMessage());
            throw INVALID_ARGUMENT
                    .withDescription("Unable to translate a read table entry to a p4runtime entity.")
                    .asException();
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
    P4InfoOuterClass.P4Info setPhysicalSizes(P4InfoOuterClass.P4Info p4Info) {
        var newP4InfoBuilder = P4InfoOuterClass.P4Info.newBuilder(p4Info)
                .clearCounters()
                .clearTables();
        long physicalCounterSize = up4Service.getUpfProgrammable().pdrCounterSize();
        long physicalFarTableSize = up4Service.getUpfProgrammable().farTableSize();
        long physicalPdrTableSize = up4Service.getUpfProgrammable().pdrTableSize();
        int ingressPdrCounterId;
        int egressPdrCounterId;
        int pdrTableId;
        int farTableId;
        try {
            P4InfoBrowser browser = PipeconfHelper.getP4InfoBrowser(pipeconf);
            ingressPdrCounterId = browser.counters()
                    .getByName(NorthConstants.INGRESS_COUNTER_ID.id()).getPreamble().getId();
            egressPdrCounterId = browser.counters()
                    .getByName(NorthConstants.EGRESS_COUNTER_ID.id()).getPreamble().getId();
            pdrTableId = browser.tables()
                    .getByName(NorthConstants.PDR_TBL.id()).getPreamble().getId();
            farTableId = browser.tables()
                    .getByName(NorthConstants.FAR_TBL.id()).getPreamble().getId();
        } catch (P4InfoBrowser.NotFoundException e) {
            throw new NoSuchElementException("A UP4 counter that should always exist does not exist.");
        }
        p4Info.getCountersList().forEach(counter -> {
            if (counter.getPreamble().getId() == ingressPdrCounterId ||
                    counter.getPreamble().getId() == egressPdrCounterId) {
                // Change the sizes of the PDR counters
                newP4InfoBuilder.addCounters(
                        P4InfoOuterClass.Counter.newBuilder(counter)
                                .setSize((long) physicalCounterSize).build());
            } else {
                // Any other counters go unchanged (for now)
                newP4InfoBuilder.addCounters(counter);
            }
        });
        p4Info.getTablesList().forEach(table -> {
            if (table.getPreamble().getId() == pdrTableId) {
                newP4InfoBuilder.addTables(
                        P4InfoOuterClass.Table.newBuilder(table)
                                .setSize(physicalPdrTableSize).build());
            } else if (table.getPreamble().getId() == farTableId) {
                newP4InfoBuilder.addTables(
                        P4InfoOuterClass.Table.newBuilder(table)
                                .setSize(physicalFarTableSize).build());
            } else {
                // Any tables aside from the PDR and FAR tables go unchanged
                newP4InfoBuilder.addTables(table);
            }
        });
        return newP4InfoBuilder.build();
    }


    /**
     * Read the all p4 counter cell requested by the message, and translate them to p4runtime
     * entities for crafting a p4runtime read response.
     *
     * @param message a p4runtime CounterEntry message from a read request
     * @return the requested counter cells' contents, as a list of p4runtime entities
     * @throws StatusException if the counter index is out of range
     */
    private List<P4RuntimeOuterClass.Entity> readCountersAndTranslate(P4RuntimeOuterClass.CounterEntry message)
            throws StatusException {
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
                throw INVALID_ARGUMENT
                        .withDescription("Invalid UP4 counter identifier.")
                        .asException();
            }
        }
        // At this point, the counterName is null if all counters are requested, and non-null if a specific
        //  counter was requested. The index is null if all cells are requested, and non-null if a specific
        //  cell was requested.
        if (counterName != null && index != null) {
            // A single counter cell was requested
            PdrStats ctrValues;
            try {
                ctrValues = up4Service.getUpfProgrammable().readCounter(index);
            } catch (UpfProgrammableException e) {
                throw INVALID_ARGUMENT
                        .withDescription(e.getMessage())
                        .asException();
            }
            long pkts;
            long bytes;
            if (piCounterId.equals(NorthConstants.INGRESS_COUNTER_ID)) {
                pkts = ctrValues.getIngressPkts();
                bytes = ctrValues.getIngressBytes();
            } else if (piCounterId.equals(NorthConstants.EGRESS_COUNTER_ID)) {
                pkts = ctrValues.getEgressPkts();
                bytes = ctrValues.getEgressBytes();
            } else {
                log.warn("Received read request for unknown counter {}", piCounterId);
                throw INVALID_ARGUMENT
                        .withDescription("Invalid UP4 counter identifier.")
                        .asException();
            }
            responseCells.add(new PiCounterCell(PiCounterCellId.ofIndirect(piCounterId, index), pkts, bytes));
        } else {
            // All cells were requested, either for a specific counter or all counters
            // FIXME: only read the counter that was requested, instead of both ingress and egress unconditionally
            Collection<PdrStats> allStats;
            try {
                allStats = up4Service.getUpfProgrammable().readAllCounters();
            } catch (UpfProgrammableException e) {
                throw io.grpc.Status.UNKNOWN.withDescription(e.getMessage()).asException();
            }
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
                log.trace("Encoded response to counter read request for counter {} and index {}",
                        cell.cellId().counterId(), cell.cellId().index());
            } catch (CodecException e) {
                log.error("Unable to encode counter cell into a p4runtime entity: {}",
                        e.getMessage());
                throw io.grpc.Status.INTERNAL
                        .withDescription("Unable to encode counter cell into a p4runtime entity.")
                        .asException();
            }
        }
        log.debug("Encoded response to counter read request for {} cells", responseEntities.size());
        return responseEntities;
    }

    /**
     * The P4Runtime server service.
     */
    public class Up4NorthService extends P4RuntimeGrpc.P4RuntimeImplBase {

        /**
         * A streamChannel represents a P4Runtime session. This session should persist for the
         * lifetime of a connected controller. The streamChannel is used for master/slave
         * arbitration. Currently this implementation does not track a master, and blindly tells
         * every controller that they are the master as soon as they send an arbitration request. We
         * also do not yet handle anything except arbitration requests.
         *
         * @param responseObserver The thing that is fed responses to arbitration requests.
         * @return A thing that will be fed arbitration requests.
         */
        @Override
        public StreamObserver<P4RuntimeOuterClass.StreamMessageRequest> streamChannel(
                StreamObserver<P4RuntimeOuterClass.StreamMessageResponse> responseObserver) {
            return new StreamObserver<>() {
                // On instance of this class is created for each stream.
                // A stream without electionId is invalid.
                private P4RuntimeOuterClass.Uint128 electionId;

                @Override
                public void onNext(P4RuntimeOuterClass.StreamMessageRequest request) {
                    log.info("Received {} on StreamChannel", request.getUpdateCase());
                    // Arbitration with valid election_id should be the first message
                    if (!request.hasArbitration() && electionId == null) {
                        handleErrorResponse(PERMISSION_DENIED
                                .withDescription("Election_id not received for this stream"));
                        return;
                    }
                    switch (request.getUpdateCase()) {
                        case ARBITRATION:
                            handleArbitration(request.getArbitration());
                            return;
                        case PACKET:
                            handlePacketOut(request.getPacket());
                            return;
                        case DIGEST_ACK:
                        case OTHER:
                        case UPDATE_NOT_SET:
                        default:
                            handleErrorResponse(UNIMPLEMENTED
                                    .withDescription(request.getUpdateCase() + " not supported"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    // No idea what to do here yet.
                    if (t instanceof StatusRuntimeException) {
                        final StatusRuntimeException sre = (StatusRuntimeException) t;
                        final String logMsg;
                        if (sre.getCause() == null) {
                            logMsg = sre.getMessage();
                        } else {
                            logMsg = format("%s (%s)", sre.getMessage(), sre.getCause().toString());
                        }
                        log.warn("StreamChannel error: {}", logMsg);
                        log.debug("", t);
                    } else {
                        log.error("StreamChannel error", t);
                    }
                    if (electionId != null) {
                        streams.remove(electionId);
                    }
                }

                @Override
                public void onCompleted() {
                    log.info("StreamChannel closed");
                    if (electionId != null) {
                        streams.remove(electionId);
                    }
                    responseObserver.onCompleted();
                }

                private void handleArbitration(P4RuntimeOuterClass.MasterArbitrationUpdate request) {
                    if (request.getDeviceId() != DEFAULT_DEVICE_ID) {
                        handleErrorResponse(INVALID_ARGUMENT
                                .withDescription("Invalid device_id"));
                        return;
                    }
                    if (!P4RuntimeOuterClass.Role.getDefaultInstance().equals(request.getRole())) {
                        handleErrorResponse(UNIMPLEMENTED
                                .withDescription("Role config not supported"));
                        return;
                    }
                    if (P4RuntimeOuterClass.Uint128.getDefaultInstance()
                            .equals(request.getElectionId())) {
                        handleErrorResponse(INVALID_ARGUMENT
                                .withDescription("Missing election_id"));
                        return;
                    }
                    streams.compute(request.getElectionId(), (requestedElectionId, storedResponseObserver) -> {
                        if (storedResponseObserver == null) {
                            // All good.
                            this.electionId = requestedElectionId;
                            log.info("Blindly telling requester with election_id {} they are the primary controller",
                                    TextFormat.shortDebugString(this.electionId));
                            // FIXME: implement election_id handling
                            responseObserver.onNext(P4RuntimeOuterClass.StreamMessageResponse.newBuilder()
                                    .setArbitration(P4RuntimeOuterClass.MasterArbitrationUpdate.newBuilder()
                                            .setDeviceId(request.getDeviceId())
                                            .setRole(request.getRole())
                                            .setElectionId(this.electionId)
                                            .setStatus(Status.newBuilder().setCode(Code.OK.getNumber()).build())
                                            .build()
                                    ).build());
                            // Store in map.
                            return responseObserver;
                        } else if (responseObserver != storedResponseObserver) {
                            handleErrorResponse(INVALID_ARGUMENT
                                    .withDescription("Election_id already in use by another client"));
                            // Map value unchanged.
                            return storedResponseObserver;
                        } else {
                            // Client is sending a second arbitration request for the same or a new
                            // election_id. Not supported.
                            handleErrorResponse(UNIMPLEMENTED
                                    .withDescription("Update of master arbitration not supported"));
                            // Remove from map.
                            return null;
                        }
                    });
                }

                private void handlePacketOut(P4RuntimeOuterClass.PacketOut request) {
                    try {
                        errorIfSwitchNotReady();
                        if (request.getPayload().isEmpty()) {
                            log.error("Received packet-out with empty payload");
                            return;
                        }
                        final byte[] frame = request.getPayload().toByteArray();
                        if (log.isDebugEnabled()) {
                            log.debug("Sending packet-out: {}", HexString.toHexString(frame, " "));
                        }
                        up4Service.getUpfProgrammable().sendPacketOut(ByteBuffer.wrap(frame));
                    } catch (StatusException e) {
                        // Drop exception to avoid closing the stream.
                        log.error("Unable to send packet-out: {}", e.getMessage());
                    }
                }

                private void handleErrorResponse(io.grpc.Status status) {
                    log.warn("Closing StreamChannel with client: {}", status.toString());
                    responseObserver.onError(status.asException());
                    // Remove stream from map.
                    if (electionId != null) {
                        streams.computeIfPresent(electionId, (storedElectionId, storedResponseObserver) -> {
                            if (responseObserver == storedResponseObserver) {
                                // Remove.
                                return null;
                            } else {
                                // This is another stream with same election_id. Do not remove.
                                return storedResponseObserver;
                            }
                        });
                    }
                }
            };
        }

        /**
         * Receives a pipeline config from a client. Discards all but the p4info file and cookie,
         * and compares the received p4info to the already present hardcoded p4info. If the two
         * match, the cookie is stored and a success response is sent. If they do not, the cookie is
         * disarded and an error is reported.
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
                                    .setP4Info(setPhysicalSizes(p4Info))
                                    .build())
                            .build());
            responseObserver.onCompleted();
        }


        private void errorIfSwitchNotReady() throws StatusException {
            if (!up4Service.configIsLoaded()) {
                log.warn("UP4 client attempted to read or write to logical switch before an app config was loaded.");
                throw io.grpc.Status.UNAVAILABLE
                        .withDescription("App config not loaded.")
                        .asException();
            }
            if (!up4Service.upfProgrammableAvailable()) {
                log.warn("UP4 client attempted to read or write to logical switch " +
                        "while the physical device was unavailable.");
                throw io.grpc.Status.UNAVAILABLE
                        .withDescription("Physical switch unavailable.")
                        .asException();
            }
            if (p4Info == null) {
                log.warn("Read or write request received before pipeline config set.");
                throw io.grpc.Status.FAILED_PRECONDITION
                        .withDescription("Switch pipeline not set.")
                        .asException();
            }
        }

        private void doWrite(P4RuntimeOuterClass.WriteRequest request,
                             StreamObserver<P4RuntimeOuterClass.WriteResponse> responseObserver)
                throws StatusException {
            for (P4RuntimeOuterClass.Update update : request.getUpdatesList()) {
                if (!update.hasEntity()) {
                    log.warn("Update message with no entities received. Ignoring");
                    continue;
                }
                P4RuntimeOuterClass.Entity requestEntity = update.getEntity();
                switch (requestEntity.getEntityCase()) {
                    case COUNTER_ENTRY:
                        // TODO: support counter cell writes, including wildcard writes
                        break;
                    case TABLE_ENTRY:
                        PiEntity piEntity;
                        try {
                            piEntity = Codecs.CODECS.entity().decode(requestEntity, null, pipeconf);
                        } catch (CodecException e) {
                            log.warn("Unable to decode p4runtime entity update message", e);
                            throw INVALID_ARGUMENT.withDescription(e.getMessage()).asException();
                        }
                        PiTableEntry entry = (PiTableEntry) piEntity;
                        switch (update.getType()) {
                            case INSERT:
                            case MODIFY:
                                translateEntryAndInsert(entry);
                                break;
                            case DELETE:
                                translateEntryAndDelete(entry);
                                break;
                            default:
                                log.warn("Unsupported update type for a table entry");
                                throw INVALID_ARGUMENT
                                        .withDescription("Unsupported update type")
                                        .asException();
                        }
                        break;
                    default:
                        log.warn("Received write request for unsupported entity type {}",
                                requestEntity.getEntityCase());
                        throw INVALID_ARGUMENT
                                .withDescription("Unsupported entity type")
                                .asException();
                }
            }
            // Response is currently defined to be empty per p4runtime.proto
            responseObserver.onNext(P4RuntimeOuterClass.WriteResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }


        /**
         * Writes entities to the logical UP4 switch.
         *
         * @param request          A request containing entities to be written
         * @param responseObserver The thing that is fed a response once writing has concluded.
         */
        @Override
        public void write(P4RuntimeOuterClass.WriteRequest request,
                          StreamObserver<P4RuntimeOuterClass.WriteResponse> responseObserver) {
            log.debug("Received write request.");
            try {
                errorIfSwitchNotReady();
                doWrite(request, responseObserver);
            } catch (StatusException e) {
                responseObserver.onError(e);
            }
            log.debug("Done with write request.");
        }

        private void doRead(P4RuntimeOuterClass.ReadRequest request,
                            StreamObserver<P4RuntimeOuterClass.ReadResponse> responseObserver)
                throws StatusException {
            for (P4RuntimeOuterClass.Entity requestEntity : request.getEntitiesList()) {
                switch (requestEntity.getEntityCase()) {
                    case COUNTER_ENTRY:
                        responseObserver.onNext(P4RuntimeOuterClass.ReadResponse.newBuilder()
                                .addAllEntities(readCountersAndTranslate(requestEntity.getCounterEntry()))
                                .build());
                        break;
                    case TABLE_ENTRY:
                        PiTableEntry requestEntry;
                        try {
                            requestEntry = (PiTableEntry) Codecs.CODECS.entity().decode(
                                    requestEntity, null, pipeconf);
                        } catch (CodecException e) {
                            log.warn("Unable to decode p4runtime read request entity", e);
                            throw INVALID_ARGUMENT.withDescription(e.getMessage()).asException();
                        }
                        responseObserver.onNext(P4RuntimeOuterClass.ReadResponse.newBuilder()
                                .addAllEntities(readEntriesAndTranslate(requestEntry))
                                .build());
                        break;
                    default:
                        log.warn("Received read request for an entity we don't yet support. Skipping");
                        break;
                }
            }
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
            try {
                errorIfSwitchNotReady();
                doRead(request, responseObserver);
            } catch (StatusException e) {
                responseObserver.onError(e);
            }
            log.debug("Done with read request.");
        }
    }

    private void handleDdn(Up4Event event) {
        if (event.subject().ueAddress() == null) {
            log.error("Received {} but UE address is missing, bug?", event.type());
            return;
        }
        var fseid = fseids.get(event.subject().ueAddress());
        if (fseid == null) {
            log.error("Unable to derive F-SEID for UE {}, dropping {}. " +
                            "Was a PDR ever installed for the UE?",
                    event.subject().ueAddress(), event.type());
            return;
        }
        var digestList = P4RuntimeOuterClass.DigestList.newBuilder()
                .setDigestId(DDN_DIGEST_ID)
                .setListId(ddnDigestListId.incrementAndGet())
                .addData(P4DataOuterClass.P4Data.newBuilder()
                        .setBitstring(ByteString.copyFrom(fseid.asArray()))
                        .build())
                .build();
        var msg = P4RuntimeOuterClass.StreamMessageResponse.newBuilder()
                .setDigest(digestList).build();
        if (streams.isEmpty()) {
            log.warn("There are no clients connected, dropping {} for UE address {}",
                    event.type(), event.subject().ueAddress());
        } else {
            streams.forEach((electionId, responseObserver) -> {
                log.debug("Sending DDN digest to client with election_id {}: {}",
                        TextFormat.shortDebugString(electionId), TextFormat.shortDebugString(msg));
                responseObserver.onNext(msg);
            });
        }
    }

    class InternalUp4EventListener implements Up4EventListener {

        @Override
        public void event(Up4Event event) {
            if (event.type() == Up4Event.Type.DOWNLINK_DATA_NOTIFICATION) {
                SharedExecutors.getPoolThreadExecutor()
                        .execute(() -> handleDdn(event));
            }
        }
    }
}
