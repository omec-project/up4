/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.dbuf.client;

import org.onlab.packet.Ip4Address;

import java.util.concurrent.CompletableFuture;

/**
 * Representation of a client controlling a Dbuf instance.
 */
public interface DbufClient {

    /**
     * Returns the address of the gRPC service associated with this client.
     *
     * @return gRPC server address in the form host:port
     */
    String serviceAddr();

    /**
     * Returns true if the dbuf instance is deemed reachable and ready to buffer packets.
     *
     * @return true if ready, false otherwise
     */
    boolean isReady();

    /**
     * Returns the IPv4 address of the data plane host interface associated with this dbuf client.
     * Data plane packets sent to this address and {@link #dataplaneUdpPort()} will be buffered by
     * dbuf.
     *
     * @return IPv4 address
     */
    Ip4Address dataplaneIp4Addr();

    /**
     * Returns the UDP port used by dbuf to listen for packets to be buffered. Data plane packets
     * sent to {@link #dataplaneIp4Addr()} and this UDP port will be buffered by dbuf.
     *
     * @return UDP port
     */
    int dataplaneUdpPort();

    /**
     * Trigger buffer drain for the given UE IPv4 address, requesting dbuf to send buffered packets
     * in a GTP tunnel with the given destination IPv4 address and UDP port.
     *
     * @param ueAddr  UE IPv4 address.
     * @param dstAddr destination IPv4 address of the GTP tunnel
     * @param udpPort destination UDP port of the GTP tunnel
     * @return a completable future of a boolean indicating whether the drain was initiated
     * successfully (true), or not.
     */
    CompletableFuture<Boolean> drain(Ip4Address ueAddr, Ip4Address dstAddr, int udpPort);

    /**
     * Triggers shutdown of this client, destroying any resources associated to it.
     */
    void shutdown();

}
