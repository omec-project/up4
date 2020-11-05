/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.behavior;

import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.runtime.PiPacketOperation;
import org.onosproject.p4runtime.api.P4RuntimeClient;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class MockP4RuntimeClient implements P4RuntimeClient {
    @Override
    public ReadRequest read(long p4DeviceId, PiPipeconf pipeconf) {
        return new MockReadRequest();
    }

    @Override
    public CompletableFuture<Boolean> setPipelineConfig(long p4DeviceId, PiPipeconf pipeconf, ByteBuffer deviceData) {
        return null;
    }

    @Override
    public boolean setPipelineConfigSync(long p4DeviceId, PiPipeconf pipeconf, ByteBuffer deviceData) {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> isPipelineConfigSet(long p4DeviceId, PiPipeconf pipeconf) {
        return null;
    }

    @Override
    public boolean isPipelineConfigSetSync(long p4DeviceId, PiPipeconf pipeconf) {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> isAnyPipelineConfigSet(long p4DeviceId) {
        return null;
    }

    @Override
    public boolean isAnyPipelineConfigSetSync(long p4DeviceId) {
        return false;
    }

    @Override
    public void shutdown() {

    }

    @Override
    public boolean isServerReachable() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> probeService() {
        return null;
    }

    @Override
    public void setMastership(long p4DeviceId, boolean master, BigInteger electionId) {

    }

    @Override
    public boolean isSessionOpen(long p4DeviceId) {
        return false;
    }

    @Override
    public void closeSession(long p4DeviceId) {

    }

    @Override
    public boolean isMaster(long p4DeviceId) {
        return false;
    }

    @Override
    public void packetOut(long p4DeviceId, PiPacketOperation packet, PiPipeconf pipeconf) {

    }

    @Override
    public WriteRequest write(long p4DeviceId, PiPipeconf pipeconf) {
        return null;
    }
}
