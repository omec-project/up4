/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.config;

import org.onlab.packet.Ip4Address;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.InvalidFieldException;

/**
 * Represents config required by UP4 to control a dbuf instance.
 */
public class Up4DbufConfig extends Config<ApplicationId> {
    public static final String KEY = "dbuf";
    public static final String DBUF_SERVICE_ADDR = "serviceAddr";
    public static final String DBUF_DATAPLANE_ADDR = "dataplaneAddr";

    static boolean isValidAddrString(String addr, boolean mustBeIp4Addr) {
        if (addr.isBlank()) {
            throw new InvalidFieldException(addr, "address string cannot be blank");
        }
        final var pieces = addr.split(":");
        if (pieces.length != 2) {
            throw new InvalidFieldException(addr, "invalid address, must be host:port");
        }
        if (mustBeIp4Addr) {
            try {
                Ip4Address.valueOf(pieces[0]);
            } catch (IllegalArgumentException e) {
                throw new InvalidFieldException(addr, "invalid IPv4 address");
            }
        }
        try {
            final int port = Integer.parseInt(pieces[1]);
            if (port <= 0) {
                throw new InvalidFieldException(addr, "invalid port number");
            }
        } catch (NumberFormatException e) {
            throw new InvalidFieldException(addr, "invalid port number");
        }
        return true;
    }

    @Override
    public boolean isValid() {
        return hasOnlyFields(DBUF_SERVICE_ADDR, DBUF_DATAPLANE_ADDR) &&
                hasFields(DBUF_SERVICE_ADDR, DBUF_DATAPLANE_ADDR) &&
                isValidAddrString(serviceAddr(), false) &&
                isValidAddrString(dataplaneAddr(), true);
    }


    /**
     * Returns the address of the dbuf service (in the form of host:port). Or null if not
     * configured.
     *
     * @return the address of the dbuf service
     */
    public String serviceAddr() {
        return get(DBUF_SERVICE_ADDR, null);
    }

    /**
     * Returns the address of the dbuf dataplane interface (in the form of host:port). Or null if
     * not configured. The host part is guaranteed to be a valid IPv4 address.
     *
     * @return the address of the dbuf dataplane interface
     */
    public String dataplaneAddr() {
        return get(DBUF_DATAPLANE_ADDR, null);
    }

}

