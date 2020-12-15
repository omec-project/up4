/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4;

/**
 * An exception indicating a an error happened in the UPF programmable behaviour. Possible errors include the
 * attempted insertion of a malformed flow rule, the reading or writing of an out-of-bounds counter cell, the deletion
 * of a non-existent flow rule, and the attempted insertion of a flow rule into a full table.
 */
public class UpfProgrammableException extends Exception {
    private final Type type;

    public enum Type {
        /**
         * The UpfProgrammable did not provide a specific exception type.
         */
        UNKNOWN,
        /**
         * The target table is at capacity.
         */
        TABLE_EXHAUSTED,
        /**
         * A provided counter cell index was out of range.
         */
        COUNTER_INDEX_OUT_OF_RANGE
    }

    /**
     * Creates a new exception for the given message.
     *
     * @param message message
     */
    public UpfProgrammableException(String message) {
        super(message);
        this.type = Type.UNKNOWN;
    }

    /**
     * Creates a new exception for the given message and type.
     *
     * @param message exception message
     * @param type    exception type
     */
    public UpfProgrammableException(String message, Type type) {
        super(message);
        this.type = type;
    }

    /**
     * Get the type of the exception.
     *
     * @return exception type
     */
    public Type getType() {
        return type;
    }
}
