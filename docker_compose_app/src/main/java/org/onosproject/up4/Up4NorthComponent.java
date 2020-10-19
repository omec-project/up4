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

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.stub.StreamObserver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import io.grpc.Server;
import io.grpc.ServerBuilder;
//import io.grpc.Status;

import java.io.IOException;

import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import p4.config.v1.P4InfoOuterClass;
import p4.v1.P4RuntimeGrpc;
import p4.v1.P4RuntimeOuterClass;

import org.onosproject.p4runtime.model.P4InfoParser;
import org.onosproject.p4runtime.model.P4InfoParserException;
import org.onosproject.net.pi.model.PiPipelineModel;

import java.net.URL;
import java.io.File;

@Component(immediate = true,
        property = {
                "grpcPort=8089",
                "up4Info=path/to/p4info.txt"
        })
public class Up4NorthComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** gRPC port on which this component will listen for UP4 messages. */
    private int grpcPort;
    private Server server;
    /** Path to the UP4 p4info.txt file */
    private String up4Info;

    private PiPipelineModel piModel;

    @Activate
    protected void activate() {
        URL up4InfoUrl;
        try {
            up4InfoUrl = new File(up4Info).toURI().toURL();
        } catch (java.net.MalformedURLException e) {
            log.error("Unable to convert up4Info path to URL", e);
            throw new IllegalStateException("Bad up4Info path", e);
        }
        try {
            piModel = P4InfoParser.parse(up4InfoUrl);
        } catch (P4InfoParserException e) {
            log.error("Unable to parse up4Info file", e);
            throw new IllegalStateException("Unable to parse up4Info file", e);
        }
        try {
            server = ServerBuilder.forPort(grpcPort)
                    .addService(new Up4NorthService())
                    .build()
                    .start();
        } catch (IOException e) {
            log.error("Unable to start gRPC server", e);
            throw new IllegalStateException("Unable to start gRPC server", e);
        }

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        if (server != null) {
            server.shutdown();
        }
        log.info("Stopped");
    }


    public class Up4NorthService extends P4RuntimeGrpc.P4RuntimeImplBase {
        @Override
        public StreamObserver<P4RuntimeOuterClass.StreamMessageRequest> streamChannel(StreamObserver<P4RuntimeOuterClass.StreamMessageResponse> responseObserver) {
            // streamChannel handles packet I/O and master arbitration. It persists as long as the controller is active.
            return new StreamObserver<P4RuntimeOuterClass.StreamMessageRequest>() {
                @Override
                public void onNext(P4RuntimeOuterClass.StreamMessageRequest value) {
                    if (value.hasArbitration()) {
                        // This response should tell the requester that it is now the master controller,
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
                        // We currently only respond to arbitration requests. Anything else gets a default response.
                        responseObserver.onNext(P4RuntimeOuterClass.StreamMessageResponse.getDefaultInstance());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    // No idea what to do here yet.
                    log.error("P4runtime streamChannel error");
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public void write(P4RuntimeOuterClass.WriteRequest request, StreamObserver<P4RuntimeOuterClass.WriteResponse> responseObserver) {
            //super.write(request, responseObserver);
            for(P4RuntimeOuterClass.Update update : request.getUpdatesList()) {
                if(update.getType() == P4RuntimeOuterClass.Update.Type.INSERT || update.getType() == P4RuntimeOuterClass.Update.Type.MODIFY) {
                    // TODO: translate it to a Up4Component call
                }
                else {
                    throw new UnsupportedOperationException("Deletions not yet implemented.");
                }
            }
            responseObserver.onNext(P4RuntimeOuterClass.WriteResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

    }

}
