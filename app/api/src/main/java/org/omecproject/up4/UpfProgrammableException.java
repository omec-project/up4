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
    /**
     * Creates a new exception for the given message.
     *
     * @param message message
     */
    public UpfProgrammableException(String message) {
        super(message);
    }
}
