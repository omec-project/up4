/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.dbuf.client;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.omecproject.dbuf.grpc.Dbuf;
import org.omecproject.dbuf.grpc.DbufServiceGrpc;
import org.onlab.packet.Ip4Address;

import java.util.concurrent.ExecutionException;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for DefaultDbufClient.
 */
public class DefaultDbufClientTest {

    private static final Ip4Address UE_IP4_ADDR = Ip4Address.valueOf("1.1.1.1");
    private static final Ip4Address INVALID_UE_IP4_ADDR = Ip4Address.valueOf("0.0.0.0");
    private static final Ip4Address UPF_GTP_IP4_ADDR = Ip4Address.valueOf("2.2.2.2");
    private static final int UPF_GTP_UDP_PORT = 2152;
    private static final String UPF_GTP_ADDR_STRING = "2.2.2.2:2152";
    private static final Ip4Address DBUF_DATAPLANE_IP4_ADDR = Ip4Address.valueOf("3.3.3.3");
    private static final int DBUF_DATAPLANE_UDP_PORT = 2152;
    private static final String DBUF_DATAPLANE_ADDR_STRING = "3.3.3.3:2152";

    // This rule manages automatic graceful shutdown for the registered servers and channels at the
    // end of test.
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    // By default the client will receive Status.UNIMPLEMENTED for all RPCs, unless we provide an
    // @Override.
    private final DbufServiceGrpc.DbufServiceImplBase serviceImpl =
            mock(DbufServiceGrpc.DbufServiceImplBase.class, delegatesTo(
                    new DbufServiceGrpc.DbufServiceImplBase() {
                        @Override
                        public void modifyQueue(
                                Dbuf.ModifyQueueRequest request,
                                StreamObserver<Dbuf.ModifyQueueResponse> responseObserver) {
                            if (request.getQueueId() == UE_IP4_ADDR.toInt()) {
                                responseObserver.onNext(Dbuf.ModifyQueueResponse.getDefaultInstance());
                                responseObserver.onCompleted();
                            } else {
                                responseObserver.onError(new StatusRuntimeException(
                                        Status.INVALID_ARGUMENT));
                            }
                        }

                        @Override
                        public void subscribe(
                                Dbuf.SubscribeRequest request,
                                StreamObserver<Dbuf.Notification> responseObserver) {
                            responseObserver.onNext(Dbuf.Notification.newBuilder()
                                    .setReady(Dbuf.Notification.Ready.newBuilder().build())
                                    .build());
                            // Do not complete, so the Subscribe RPC stays on and the client stays
                            // in state ready.
                        }
                    }));

    private DbufClient client;

    @Before
    public void setUp() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        grpcCleanup.register(InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(serviceImpl).build().start());

        // Create a client channel and register for automatic graceful shutdown.
        ManagedChannel channel = grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build());

        // Create a client using the in-process channel;
        client = new DefaultDbufClient("dummy", channel, DBUF_DATAPLANE_ADDR_STRING);

        // Wait for channel to be ready.
        Thread.sleep(500);

        // Assert that subscribe RPC has been started.
        ArgumentCaptor<Dbuf.SubscribeRequest> requestCaptor =
                ArgumentCaptor.forClass(Dbuf.SubscribeRequest.class);
        verify(serviceImpl).subscribe(requestCaptor.capture(), ArgumentMatchers.any());
        // Server received SubscribeRequest.
        Assert.assertEquals(Dbuf.SubscribeRequest.getDefaultInstance(), requestCaptor.getValue());
        // Dbuf client is ready,
        Assert.assertTrue(client.isReady());
    }

    @Test
    public void testDrain() throws ExecutionException, InterruptedException {
        final var future = client.drain(UE_IP4_ADDR, UPF_GTP_IP4_ADDR, UPF_GTP_UDP_PORT);
        // Verify message delivered to server.
        ArgumentCaptor<Dbuf.ModifyQueueRequest> requestCaptor =
                ArgumentCaptor.forClass(Dbuf.ModifyQueueRequest.class);
        verify(serviceImpl).modifyQueue(requestCaptor.capture(), ArgumentMatchers.any());
        Assert.assertEquals(UPF_GTP_ADDR_STRING, requestCaptor.getValue().getDestinationAddress());
        Assert.assertEquals(UE_IP4_ADDR.toInt(), requestCaptor.getValue().getQueueId());
        // Return result should be true.
        Assert.assertTrue(future.get());
    }

    @Test
    public void testDrainInvalidUeAddr() throws ExecutionException, InterruptedException {
        final var future = client.drain(INVALID_UE_IP4_ADDR, UPF_GTP_IP4_ADDR, UPF_GTP_UDP_PORT);
        ArgumentCaptor<Dbuf.ModifyQueueRequest> requestCaptor =
                ArgumentCaptor.forClass(Dbuf.ModifyQueueRequest.class);
        verify(serviceImpl).modifyQueue(requestCaptor.capture(), ArgumentMatchers.any());
        Assert.assertEquals(UPF_GTP_ADDR_STRING, requestCaptor.getValue().getDestinationAddress());
        Assert.assertEquals(INVALID_UE_IP4_ADDR.toInt(), requestCaptor.getValue().getQueueId());
        Assert.assertFalse(future.get());
    }

    @Test
    public void testDataplaneIp4Addr() {
        Assert.assertEquals(DBUF_DATAPLANE_IP4_ADDR, client.dataplaneIp4Addr());
    }

    @Test
    public void testDataplaneUdpPort() {
        Assert.assertEquals(DBUF_DATAPLANE_UDP_PORT, client.dataplaneUdpPort());
    }

    @After
    public void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }
}
