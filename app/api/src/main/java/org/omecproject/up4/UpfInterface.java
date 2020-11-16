/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A UPF device interface, such as a S1U or UE IP address pool.
 */
public final class UpfInterface {
    private final Ip4Address address;
    private final int prefixLen;
    private final Type type;

    private UpfInterface(Ip4Address address, int prefixLen, Type type) {
        this.address = address;
        this.prefixLen = prefixLen;
        this.type = type;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        String typeStr;
        if (type.equals(Type.ACCESS)) {
            typeStr = "Uplink";
        } else if (type.equals(Type.CORE)) {
            typeStr = "Downlink";
        } else if (type.equals(Type.DBUF)) {
            typeStr = "Dbuf-Receiver";
        } else {
            typeStr = "UNKNOWN";
        }
        return String.format("Interface{%s, %s/%d}", typeStr, address, prefixLen);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        UpfInterface that = (UpfInterface) obj;
        return (this.type.equals(that.type) &&
                this.address.equals(that.address) &&
                this.prefixLen == that.prefixLen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, prefixLen, type);
    }

    /**
     * Create an uplink UPF Interface from the given address, which will be treated as a /32 prefix.
     *
     * @param address the address of the new uplink interface
     * @return a new UPF interface
     */
    public static UpfInterface createS1uFrom(Ip4Address address) {
        return builder().setUplink().setAddress(address).setPrefixLen(32).build();
    }

    /**
     * Create an uplink UPF Interface from the given IP prefix.
     *
     * @param prefix the prefix of the new uplink interface
     * @return a new UPF interface
     */
    public static UpfInterface createS1uFrom(Ip4Prefix prefix) {
        return builder().setUplink().setPrefix(prefix).build();
    }

    /**
     * Create an downlink UPF Interface from the given IP prefix.
     *
     * @param prefix the prefix of the new downlink interface
     * @return a new UPF interface
     */
    public static UpfInterface createUePoolFrom(Ip4Prefix prefix) {
        return builder().setDownlink().setPrefix(prefix).build();
    }

    /**
     * Create a dbuf-receiving UPF interface from the given IP address.
     *
     * @param address the address of the dbuf-receiving interface
     * @return a new UPF interface
     */
    public static UpfInterface createDbufReceiverFrom(Ip4Address address) {
        return UpfInterface.builder().setDbufReceiver().setAddress(address).setPrefixLen(32).build();
    }

    /**
     * Get the IP prefix of this interface. Host bits will not be included.
     *
     * @return the interface prefix
     */
    public Ip4Prefix getPrefix() {
        return Ip4Prefix.valueOf(address, prefixLen);
    }


    /**
     * Get the IP address of this interface, which is the prefix address plus host bits.
     *
     * @return the interface address
     */
    public Ip4Address getAddress() {
        return address;
    }

    /**
     * Get the length of the IP prefix of this interface.
     *
     * @return the interface prefix length
     */
    public int getPrefixLen() {
        return prefixLen;
    }

    /**
     * Check if this UPF interface is for uplink packets from UEs.
     * This will be true for S1U interface table entries.
     *
     * @return true if uplink
     */
    public boolean isUplink() {
        return type == Type.ACCESS;
    }

    /**
     * Check if this UPF interface is for downlink packets towards UEs.
     * This will be true for UE IP address pool table entries.
     *
     * @return true if downlink
     */
    public boolean isDownlink() {
        return type == Type.CORE;
    }


    /**
     * Check if this UPF interface is for receiving buffered packets as they are released from the dbuf
     * buffering device.
     *
     * @return true if interface receives from dbuf
     */
    public boolean isDbufReceiver() {
        return type == Type.DBUF;
    }

    public enum Type {
        /**
         * Unknown UPF interface type.
         */
        UNKNOWN,

        /**
         * Uplink interface that receives GTP encapsulated packets.
         * This is the type of the S1U interface.
         */
        ACCESS,

        /**
         * Downlink interface that receives unencapsulated packets from the core of the network.
         * This is the type of UE IP address pool interfaces.
         */
        CORE,

        /**
         * Interface that receives buffered packets as they are drained from a dbuf device.
         */
        DBUF
    }

    public static class Builder {
        private Ip4Address address;
        private Integer prefixLen;
        private Type type;

        public Builder() {
            type = Type.UNKNOWN;
        }

        /**
         * Set the IPv4 prefix of this interface.
         *
         * @param prefix the interface prefix
         * @return this builder object
         */
        public Builder setPrefix(Ip4Prefix prefix) {
            this.address = prefix.address();
            this.prefixLen = prefix.prefixLength();
            return this;
        }

        /**
         * Set address of the IPv4 prefix of this interface. Host bits will not be discarded.
         *
         * @param address the base address of the interface's prefix
         * @return this builder object
         */
        public Builder setAddress(Ip4Address address) {
            this.address = address;
            return this;
        }

        /**
         * Set the length of the IPv4 prefix of this interface.
         * @param length the length of the prefix of this interface
         * @return this builder object
         */
        public Builder setPrefixLen(int length) {
            this.prefixLen = length;
            return this;
        }

        /**
         * Make this an uplink interface.
         *
         * @return this builder object
         */
        public Builder setUplink() {
            this.type = Type.ACCESS;
            return this;
        }

        /**
         * Make this a downlink interface.
         *
         * @return this builder object
         */
        public Builder setDownlink() {
            this.type = Type.CORE;
            return this;
        }

        /**
         * Make this a dbuf-facing interface.
         *
         * @return this builder object
         */
        public Builder setDbufReceiver() {
            this.type = Type.DBUF;
            return this;
        }

        public UpfInterface build() {
            checkNotNull(address);
            checkNotNull(prefixLen);
            checkArgument(prefixLen >= 0 && prefixLen <= 32);
            return new UpfInterface(address, prefixLen, type);
        }
    }
}
