/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.dbuf.client;

import com.google.protobuf.TextFormat;
import io.grpc.Context;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.omecproject.dbuf.grpc.Dbuf;
import org.omecproject.dbuf.grpc.DbufServiceGrpc;
import org.slf4j.Logger;

import java.net.ConnectException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A manager for the dbuf Subscribe RPC that opportunistically starts new RPC (e.g. when one fails
 * because of errors) and relays notification to the client.
 */
// TODO: create ONOS abstract class to manage gRPC stream RPCS.
//  There's enough commonality between this one and ONOS's GnmiSubscriptionManager to justify such
//  common abstract class.
final class DbufSubscribeManager {

    private static final long DEFAULT_RECONNECT_DELAY = 5; // Seconds

    private static final Logger log = getLogger(DbufSubscribeManager.class);

    private final DefaultDbufClient client;
    private final StreamObserver<Dbuf.Notification> responseObserver;
    private final ScheduledExecutorService streamCheckerExecutor =
            newSingleThreadScheduledExecutor(groupedThreads("up4/dbuf-subscribe-check", "%d", log));
    private final AtomicBoolean subscribeRequested = new AtomicBoolean(false);
    private final AtomicBoolean readyReceived = new AtomicBoolean(false);

    private Future<?> checkTask;
    private Context.CancellableContext rpcContext;

    public DbufSubscribeManager(DefaultDbufClient client) {
        this.client = client;
        this.responseObserver = new InternalStreamResponseObserver();
    }

    public void subscribe() {
        synchronized (this) {
            subscribeRequested.set(true);
            if (!isCancelled()) {
                log.debug("Cancelling existing subscription for {} before " +
                        "starting a new one", client.serviceAddr());
                cancel();
            }
            // Async spawn periodic task start Subscribe RPC, and to make sure
            // it is restarted in case of failures.
            if (checkTask == null) {
                checkTask = streamCheckerExecutor.scheduleAtFixedRate(
                        this::checkSubscription, 0,
                        DEFAULT_RECONNECT_DELAY, TimeUnit.SECONDS);
            }
        }
    }

    private void checkSubscription() {
        synchronized (this) {
            if (subscribeRequested.get() && isCancelled()) {
                if (client.isServerReachable()) {
                    log.info("Starting Subscribe RPC for {}...", client.serviceAddr());
                    rpcContext = Context.current().withCancellation();
                    rpcContext.run(() -> DbufServiceGrpc.newStub(client.channel())
                            .subscribe(Dbuf.SubscribeRequest.getDefaultInstance(),
                                    responseObserver));
                } else {
                    log.debug("Not starting Subscribe RPC for {}, server is NOT reachable",
                            client.serviceAddr());
                }
            }
        }
    }

    private void cancel() {
        synchronized (this) {
            if (rpcContext != null) {
                rpcContext.cancel(null);
                rpcContext = null;
            }
            readyReceived.set(false);
        }
    }

    public boolean isCancelled() {
        return rpcContext == null || rpcContext.isCancelled();
    }

    public boolean isReady() {
        return subscribeRequested.get() && !isCancelled() && readyReceived.get();
    }

    public void shutdown() {
        synchronized (this) {
            log.debug("Shutting down subscription manager for {}", client.serviceAddr());
            subscribeRequested.set(false);
            if (checkTask != null) {
                checkTask.cancel(false);
                checkTask = null;
            }
            streamCheckerExecutor.shutdown();
            cancel();
        }
    }

    /**
     * Handles messages sent by the server over the Subscribe RPC.
     */
    private final class InternalStreamResponseObserver implements StreamObserver<Dbuf.Notification> {

        @Override
        public void onNext(Dbuf.Notification notification) {
            try {
                if (log.isTraceEnabled()) {
                    log.trace("Received Notification from {}: {}",
                            client.serviceAddr(), TextFormat.shortDebugString(notification));
                }
                if (notification.hasReady()) {
                    readyReceived.set(true);
                }
                client.handleNotification(notification);
            } catch (Throwable ex) {
                log.error("Exception processing Notification from " + client.serviceAddr(),
                        ex);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (throwable instanceof StatusRuntimeException) {
                StatusRuntimeException sre = (StatusRuntimeException) throwable;
                if (sre.getStatus().getCause() instanceof ConnectException) {
                    log.warn("{} is unreachable ({})",
                            client.serviceAddr(), sre.getCause().getMessage());
                } else {
                    log.warn("Error on Subscribe RPC for {}: {}",
                            client.serviceAddr(), throwable.getMessage());
                }
            } else {
                log.error(format("Exception on Subscribe RPC for %s",
                        client.serviceAddr()), throwable);
            }
            cancel();
        }

        @Override
        public void onCompleted() {
            log.warn("Subscribe RPC for {} has completed", client.serviceAddr());
            cancel();
        }
    }
}


