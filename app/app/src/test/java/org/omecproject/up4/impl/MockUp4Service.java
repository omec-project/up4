/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2022-present Intel Corporation
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.collect.Maps;
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
import java.util.Map;
import java.util.stream.LongStream;

import static org.omecproject.up4.impl.TestImplConstants.PHYSICAL_APPLICATIONS_SIZE;
import static org.omecproject.up4.impl.TestImplConstants.PHYSICAL_COUNTER_SIZE;
import static org.omecproject.up4.impl.TestImplConstants.PHYSICAL_MAX_INTERFACES;
import static org.omecproject.up4.impl.TestImplConstants.PHYSICAL_MAX_METERS;
import static org.omecproject.up4.impl.TestImplConstants.PHYSICAL_MAX_SESSIONS;
import static org.omecproject.up4.impl.TestImplConstants.PHYSICAL_MAX_TERMINATIONS;
import static org.omecproject.up4.impl.TestImplConstants.PHYSICAL_MAX_TUNNEL_PEERS;


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
    final List<UpfEntity> sessionMeters = new ArrayList<>();
    final List<UpfEntity> applicationMeters = new ArrayList<>();
    final List<UpfEntity> sliceMeters = new ArrayList<>();
    final Map<Integer, UpfCounter> counters = Maps.newHashMap();
    final List<ByteBuffer> sentPacketOuts = new ArrayList<>();

    public MockUp4Service() {
        LongStream.range(0, PHYSICAL_COUNTER_SIZE)
                .forEach(i -> counters.put((int) i, UpfCounter.builder()
                        .withCellId((int) i)
                        .setIngress(0, 0)
                        .setEgress(0, 0)
                        .build())
                );
    }

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
            case SESSION_METER:
                sessionMeters.add(entity);
                break;
            case APPLICATION_METER:
                applicationMeters.add(entity);
                break;
            case SLICE_METER:
                sliceMeters.add(entity);
                break;
            case COUNTER:
                UpfCounter counter = (UpfCounter) entity;
                counters.put(counter.getCellId(), counter);
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
            case SESSION_METER:
                return sessionMeters;
            case APPLICATION_METER:
                return applicationMeters;
            case SLICE_METER:
                return sliceMeters;
            case COUNTER:
                return counters.values();
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
        return counters.get(cellId);
    }

    @Override
    public Collection<UpfCounter> readCounters(long maxCounterId) {
        return counters.values();
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
            case SESSION_METER:
                entities = sessionMeters;
                break;
            case APPLICATION_METER:
                entities = applicationMeters;
                break;
            case SLICE_METER:
                entities = sliceMeters;
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
            case SESSION_METER:
                sessionMeters.clear();
                break;
            case APPLICATION_METER:
                applicationMeters.clear();
                break;
            case SLICE_METER:
                sliceMeters.clear();
                break;
            default:
                break;
        }
    }

    @Override
    public long tableSize(UpfEntityType entityType) throws UpfProgrammableException {
        switch (entityType) {
            case INTERFACE:
                return PHYSICAL_MAX_INTERFACES;
            case TERMINATION_DOWNLINK:
            case TERMINATION_UPLINK:
                return PHYSICAL_MAX_TERMINATIONS;
            case SESSION_UPLINK:
            case SESSION_DOWNLINK:
                return PHYSICAL_MAX_SESSIONS;
            case TUNNEL_PEER:
                return PHYSICAL_MAX_TUNNEL_PEERS;
            case COUNTER:
                return PHYSICAL_COUNTER_SIZE;
            case APPLICATION:
                return PHYSICAL_APPLICATIONS_SIZE;
            case SESSION_METER:
            case APPLICATION_METER:
                return PHYSICAL_MAX_METERS;
            default:
                break;
        }
        return 0;
    }

    @Override
    public void enablePscEncap() throws UpfProgrammableException {

    }
}
