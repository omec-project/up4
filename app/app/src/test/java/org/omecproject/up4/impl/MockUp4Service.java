/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.omecproject.up4.Up4EventListener;
import org.omecproject.up4.Up4Service;
import org.onosproject.net.behaviour.upf.UpfCounter;
import org.onosproject.net.behaviour.upf.UpfEntity;
import org.onosproject.net.behaviour.upf.UpfEntityType;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MockUp4Service implements Up4Service {
    boolean upfProgrammableAvailable = true;
    boolean configAvailable = true;
    final List<UpfEntity> sessionsUl = new ArrayList<>();
    final List<UpfEntity> sessionsDl = new ArrayList<>();
    final List<UpfEntity> terminationsUl = new ArrayList<>();
    final List<UpfEntity> terminationsDl = new ArrayList<>();
    final List<UpfEntity> tunnelPeers = new ArrayList<>();
    final List<UpfEntity> applications = new ArrayList<>();
    final List<UpfEntity> ifaces = new ArrayList<>();
    final List<ByteBuffer> sentPacketOuts = new ArrayList<>();

    public void hideState(boolean hideUpfProgrammable, boolean hideConfig) {
        upfProgrammableAvailable = !hideUpfProgrammable;
        configAvailable = !hideConfig;
    }

    @Override
    public boolean isReady() {
        return upfProgrammableAvailable;
    }

    @Override
    public boolean configIsLoaded() {
        return configAvailable;
    }

    @Override
    public void addListener(Up4EventListener listener) {

    }

    @Override
    public void removeListener(Up4EventListener listener) {

    }

    @Override
    public void cleanUp() {

    }

    @Override
    public void apply(UpfEntity entity) throws UpfProgrammableException {
        switch (entity.type()) {
            case INTERFACE:
                ifaces.add(entity);
                break;
            case TERMINATION_UPLINK:
                terminationsUl.add(entity);
                break;
            case TERMINATION_DOWNLINK:
                terminationsDl.add(entity);
                break;
            case SESSION_UPLINK:
                sessionsUl.add(entity);
                break;
            case SESSION_DOWNLINK:
                sessionsDl.add(entity);
                break;
            case TUNNEL_PEER:
                tunnelPeers.add(entity);
                break;
            case APPLICATION:
                applications.add(entity);
                break;
            default:
                break;
        }
    }

    @Override
    public Collection<? extends UpfEntity> readAll(UpfEntityType entityType)
            throws UpfProgrammableException {
        switch (entityType) {
            case INTERFACE:
                return ifaces;
            case TERMINATION_UPLINK:
                return terminationsUl;
            case TERMINATION_DOWNLINK:
                return terminationsDl;
            case SESSION_UPLINK:
                return sessionsUl;
            case SESSION_DOWNLINK:
                return sessionsDl;
            case TUNNEL_PEER:
                return tunnelPeers;
            case APPLICATION:
                return applications;
            default:
                break;
        }
        return null;
    }

    @Override
    public void disablePscEncap() {

    }

    @Override
    public void sendPacketOut(ByteBuffer data) {
        sentPacketOuts.add(data);
    }

    @Override
    public UpfCounter readCounter(int cellId) {
        return UpfCounter.builder()
                .withCellId(cellId)
                .setEgress(NorthTestConstants.EGRESS_COUNTER_PKTS, NorthTestConstants.EGRESS_COUNTER_BYTES)
                .setIngress(NorthTestConstants.INGRESS_COUNTER_PKTS, NorthTestConstants.INGRESS_COUNTER_BYTES)
                .build();
    }

    @Override
    public Collection<UpfCounter> readCounters(long maxCounterId) {
        List<UpfCounter> stats = new ArrayList<>();
        for (int i = 0; i < TestImplConstants.PHYSICAL_COUNTER_SIZE; i++) {
            stats.add(UpfCounter.builder()
                              .withCellId(i)
                              .setEgress(NorthTestConstants.EGRESS_COUNTER_PKTS,
                                         NorthTestConstants.EGRESS_COUNTER_BYTES)
                              .setIngress(NorthTestConstants.INGRESS_COUNTER_PKTS,
                                          NorthTestConstants.INGRESS_COUNTER_BYTES)
                              .build());
        }
        return stats;
    }

    @Override
    public void delete(UpfEntity entity) throws UpfProgrammableException {
        List<UpfEntity> entities;
        switch (entity.type()) {
            case INTERFACE:
                entities = ifaces;
                break;
            case TERMINATION_UPLINK:
                entities = terminationsUl;
                break;
            case TERMINATION_DOWNLINK:
                entities = terminationsDl;
                break;
            case SESSION_UPLINK:
                entities = sessionsUl;
                break;
            case SESSION_DOWNLINK:
                entities = sessionsDl;
                break;
            case TUNNEL_PEER:
                entities = tunnelPeers;
                break;
            case APPLICATION:
                entities = applications;
                break;
            default:
                return;
        }
        int index = entities.indexOf(entity);
        if (index != -1) {
            entities.remove(index);
        }
    }

    @Override
    public void deleteAll(UpfEntityType entityType) throws UpfProgrammableException {
        switch (entityType) {
            case INTERFACE:
                ifaces.clear();
                break;
            case TERMINATION_UPLINK:
                terminationsUl.clear();
                break;
            case TERMINATION_DOWNLINK:
                terminationsDl.clear();
                break;
            case SESSION_UPLINK:
                sessionsUl.clear();
                break;
            case SESSION_DOWNLINK:
                sessionsDl.clear();
                break;
            case TUNNEL_PEER:
                tunnelPeers.clear();
                break;
            case APPLICATION:
                applications.clear();
                break;
            default:
                break;
        }
    }

    @Override
    public long tableSize(UpfEntityType entityType) throws UpfProgrammableException {
        switch (entityType) {
            case INTERFACE:
                return TestImplConstants.PHYSICAL_MAX_INTERFACES;
            case TERMINATION_DOWNLINK:
            case TERMINATION_UPLINK:
                return TestImplConstants.PHYSICAL_MAX_TERMINATIONS;
            case SESSION_UPLINK:
            case SESSION_DOWNLINK:
                return TestImplConstants.PHYSICAL_MAX_SESSIONS;
            case TUNNEL_PEER:
                return TestImplConstants.PHYSICAL_MAX_TUNNEL_PEERS;
            case COUNTER:
                return TestImplConstants.PHYSICAL_COUNTER_SIZE;
            case APPLICATION:
                return TestImplConstants.PHYSICAL_APPLICATIONS_SIZE;
            default:
                break;
        }
        return 0;
    }

    @Override
    public void enablePscEncap() throws UpfProgrammableException {

    }
}
