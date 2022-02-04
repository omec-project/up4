/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2021-present Open Networking Foundation <info@opennetworking.org>
 */

package org.omecproject.up4.impl;

/**
 * UP4 component properties.
 */
public final class OsgiPropertyConstants {

    public static final String UPF_RECONCILE_INTERVAL = "upfReconcileInterval";
    public static final long UPF_RECONCILE_INTERVAL_DEFAULT = 30; // Seconds

    private OsgiPropertyConstants() {
    }
}
