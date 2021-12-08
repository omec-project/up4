/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.omecproject.up4.Up4EventListener;
import org.omecproject.up4.Up4Service;
import org.omecproject.up4.UpfFlow;
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
    final List<UpfEntity> sessions = new ArrayList<>();
    final List<UpfEntity> terminations = new ArrayList<>();
    final List<UpfEntity> tunnelPeers = new ArrayList<>();
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
            case TERMINATION:
                terminations.add(entity);
                break;
            case SESSION:
                sessions.add(entity);
                break;
            case TUNNEL_PEER:
                tunnelPeers.add(entity);
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
            case TERMINATION:
                return terminations;
            case SESSION:
                return sessions;
            case TUNNEL_PEER:
                return tunnelPeers;
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
            case TERMINATION:
                entities = terminations;
                break;
            case SESSION:
                entities = sessions;
                break;
            case TUNNEL_PEER:
                entities = tunnelPeers;
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
            case TERMINATION:
                terminations.clear();
                break;
            case SESSION:
                sessions.clear();
                break;
            case TUNNEL_PEER:
                tunnelPeers.clear();
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
            case TERMINATION:
                return TestImplConstants.PHYSICAL_MAX_TERMINATIONS;
            case SESSION:
                return TestImplConstants.PHYSICAL_MAX_SESSIONS;
            case TUNNEL_PEER:
                return TestImplConstants.PHYSICAL_MAX_TUNNEL_PEERS;
            case COUNTER:
                return TestImplConstants.PHYSICAL_COUNTER_SIZE;
            default:
                break;
        }
        return 0;
    }

    @Override
    public void enablePscEncap() throws UpfProgrammableException {

    }

    @Override
    public Collection<UpfFlow> getFlows() throws UpfProgrammableException {
        return null;
    }

    @Override
    public void installUpfEntities() {

    }

    @Override
    public void internalApply(UpfEntity entity) throws UpfProgrammableException {

    }

    @Override
    public Collection<? extends UpfEntity> internalReadAll(UpfEntityType entityType) throws UpfProgrammableException {
        return null;
    }

    @Override
    public void internalDelete(UpfEntity entity) throws UpfProgrammableException {

    }

    @Override
    public void internalDeleteAll(UpfEntityType entityType) throws UpfProgrammableException {

    }
}
