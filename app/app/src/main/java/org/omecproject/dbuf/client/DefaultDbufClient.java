/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.dbuf.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import io.grpc.Channel;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.omecproject.dbuf.grpc.Dbuf;
import org.omecproject.dbuf.grpc.DbufServiceGrpc;
import org.onlab.packet.Ip4Address;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.omecproject.dbuf.grpc.Dbuf.ModifyQueueRequest.QueueAction.QUEUE_ACTION_RELEASE_AND_PASSTHROUGH;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Default implementation of DbufClient.
 */
public final class DefaultDbufClient implements DbufClient {

    private static final String LOAD_BALANCING_POLICY =
            new PickFirstLoadBalancerProvider().getPolicyName();
    private static final DnsNameResolverProvider DNS_NAME_RESOLVER_PROVIDER =
            new DnsNameResolverProvider();
    private static final int DEFAULT_RPC_TIMEOUT_SEC = 5;
    private static final Dbuf.ModifyQueueResponse DEFAULT_MODIFY_QUEUE_RESPONSE =
            Dbuf.ModifyQueueResponse.getDefaultInstance();

    private final Logger log = getLogger(getClass());
    private final String serviceAddr;
    private final ManagedChannel channel;
    private final DbufSubscribeManager subscribeManager;
    private final Ip4Address dataplaneIp4Addr;
    private final int dataplaneUdpPort;

    /**
     * Creates a new client for the given gRPC service address and dataplane address (both in the
     * form of host:port), immediately triggering a connection to the gRPC service and Subscribe
     * RPC.
     * <p>
     * The host part of dataplaneAddr must be a valid IPv4 address.
     *
     * @param serviceAddr   service address
     * @param dataplaneAddr dataplane address
     */
    public DefaultDbufClient(String serviceAddr, String dataplaneAddr) {
        this(serviceAddr, buildChannel(serviceAddr), dataplaneAddr);
    }

    @VisibleForTesting
    static ManagedChannel buildChannel(String serviceAddr) {
        // Address string is validated at netcfg-level (see Up4Config)
        final var pieces = serviceAddr.split(":");
        return NettyChannelBuilder
                .forAddress(pieces[0], Integer.parseInt(pieces[1]))
                .nameResolverFactory(DNS_NAME_RESOLVER_PROVIDER)
                .defaultLoadBalancingPolicy(LOAD_BALANCING_POLICY)
                .usePlaintext()
                .build();
    }

    @VisibleForTesting
    DefaultDbufClient(String serviceAddr, ManagedChannel channel, String dataplaneAddr) {
        this.serviceAddr = serviceAddr;
        this.channel = channel;
        this.subscribeManager = new DbufSubscribeManager(this);
        final var pieces = dataplaneAddr.split(":");
        this.dataplaneIp4Addr = Ip4Address.valueOf(pieces[0]);
        this.dataplaneUdpPort = Integer.parseInt(pieces[1]);

        // Force channel to establish connection to server now, and follow channel state to:
        // - start Subscribe RPC once channel is READY
        // - avoid going IDLE
        channel.getState(true);
        channel.notifyWhenStateChanged(
                ConnectivityState.IDLE, this::channelStateCallback);
        log.info("Created new dbuf client for {}", serviceAddr);
    }

    @Override
    public String serviceAddr() {
        return this.serviceAddr;
    }

    @Override
    public boolean isReady() {
        return isServerReachable() && subscribeManager.isReady();
    }

    @Override
    public Ip4Address dataplaneIp4Addr() {
        return this.dataplaneIp4Addr;
    }

    @Override
    public int dataplaneUdpPort() {
        return this.dataplaneUdpPort;
    }

    @Override
    public CompletableFuture<Boolean> drain(Ip4Address ueAddr, Ip4Address dstAddr, int udpPort) {
        if (!isReady()) {
            log.warn("Client to {} is not ready, cannot drain buffer", serviceAddr);
            return CompletableFuture.completedFuture(false);
        }

        final var request = Dbuf.ModifyQueueRequest.newBuilder()
                .setAction(QUEUE_ACTION_RELEASE_AND_PASSTHROUGH)
                // FIXME: check with Max whether queue_id can be the UE address.
                .setQueueId(ueAddr.toInt())
                .setDestinationAddress(String.format("%s:%s", dstAddr.toString(), udpPort))
                .build();

        final var future = new CompletableFuture<Boolean>();
        final var responseObserver = new StreamObserver<Dbuf.ModifyQueueResponse>() {
            @Override
            public void onNext(Dbuf.ModifyQueueResponse value) {
                if (!DEFAULT_MODIFY_QUEUE_RESPONSE.equals(value)) {
                    log.warn("Received invalid ModifyQueueResponse from {} [{}]",
                            serviceAddr,
                            TextFormat.shortDebugString(value));
                    future.complete(false);
                }
                future.complete(true);
            }

            @Override
            public void onError(Throwable t) {
                handleRpcError(t, "ModifyQueue");
                future.complete(false);
            }

            @Override
            public void onCompleted() {
                // Ignore, unary call.
            }
        };

        DbufServiceGrpc.newStub(channel)
                .withDeadlineAfter(DEFAULT_RPC_TIMEOUT_SEC, TimeUnit.SECONDS)
                .modifyQueue(request, responseObserver);

        return future;
    }

    @Override
    public void shutdown() {
        if (channel.isShutdown()) {
            log.warn("Client to {} is already shutdown", serviceAddr);
            return;
        }
        log.info("Shutting down client for {}", serviceAddr);
        channel.shutdown();
    }

    void handleNotification(Dbuf.Notification notification) {
        switch (notification.getMessageTypeCase()) {
            case READY:
                // TODO: Store dataplane IPv4 and UDP addr
                log.info("Dbuf service at {} is READY={} [{}]", serviceAddr,
                        isReady(), TextFormat.shortDebugString(notification.getReady()));
                break;
            case FIRST_BUFFER:
                // TODO: notify PFCP agent (DDN)
                log.info("Received FIRST_BUFFER: {}", TextFormat.shortDebugString(notification.getFirstBuffer()));
                break;
            case DROPPED_PACKET:
                // Not sure what to do with this. Drop stats should already be reported to Aether
                // monitoring.
                log.info("Received DROPPED_PACKET: {}", TextFormat.shortDebugString(notification.getDroppedPacket()));
                break;
            case MESSAGETYPE_NOT_SET:
                break;
            default:
                log.warn("Notification type unhandled: {}", notification.getMessageTypeCase());
        }
    }

    boolean isServerReachable() {
        final ConnectivityState state = channel.getState(false);
        switch (state) {
            case READY:
            case IDLE:
                return true;
            case CONNECTING:
            case TRANSIENT_FAILURE:
            case SHUTDOWN:
                return false;
            default:
                log.error("Unrecognized channel connectivity state {}", state);
                return false;
        }
    }

    // Invoked at each change of the channel connectivity state. New callbacks are created as long
    // as the channel is not shut down.
    private void channelStateCallback() {
        final ConnectivityState newState = channel.getState(false);
        log.info("Channel to {} is in state {}", serviceAddr, newState);
        switch (newState) {
            case READY:
                subscribeManager.subscribe();
                break;
            case IDLE:
                log.info("Forcing channel to {} to exit state IDLE...", serviceAddr);
                channel.getState(true);
                break;
            case SHUTDOWN:
                subscribeManager.shutdown();
                break;
            case CONNECTING:
            case TRANSIENT_FAILURE:
                // Nothing to do, eventually state will be READY.
                break;
            default:
                log.warn("Unhandled channel state {}", newState);
        }

        if (newState != ConnectivityState.SHUTDOWN) {
            // Channels never leave SHUTDOWN state, no need for a new callback.
            channel.notifyWhenStateChanged(
                    newState, this::channelStateCallback);
        }
    }

    private void handleRpcError(Throwable throwable, String opDescription) {
        if (throwable instanceof StatusRuntimeException) {
            final StatusRuntimeException sre = (StatusRuntimeException) throwable;
            final String logMsg;
            if (sre.getCause() == null) {
                logMsg = sre.getMessage();
            } else {
                logMsg = format("%s (%s)", sre.getMessage(), sre.getCause().toString());
            }
            log.warn("Error while performing {} on {}: {}",
                    opDescription, serviceAddr, logMsg);
            log.debug("", throwable);
            return;
        }
        log.error(format("Exception while performing %s on %s",
                opDescription, serviceAddr), throwable);
    }

    Channel channel() {
        return this.channel;
    }
}
