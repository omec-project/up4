package org.omecproject.up4;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;

import static com.google.common.base.Preconditions.checkNotNull;

public final class UpfInterface {
    private final Ip4Prefix prefix;
    private final Type type;

    private UpfInterface(Ip4Prefix prefix, Type type) {
        this.prefix = prefix;
        this.type = type;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an uplink UPF Interface from the given address, which will be treated as a /32 prefix.
     *
     * @param address the address of the new uplink interface
     * @return a new UPF interface
     */
    public static UpfInterface createS1uFrom(Ip4Address address) {
        return builder().setUplink().setPrefix(Ip4Prefix.valueOf(address, 32)).build();
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
     * Get the IP prefix of this interface.
     *
     * @return the interface prefix
     */
    public Ip4Prefix prefix() {
        return prefix;
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
     * Get the IPv4 prefix of this UPF interface.
     *
     * @return the interface prefix
     */
    public Ip4Prefix getPrefix() {
        return this.prefix;
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
        CORE
    }

    public static class Builder {
        private Ip4Prefix prefix;
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
            this.prefix = prefix;
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

        public UpfInterface build() {
            checkNotNull(prefix);
            return new UpfInterface(prefix, type);
        }
    }
}
