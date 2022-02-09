/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2022-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onosproject.net.behaviour.upf.UpfMeter;
import org.onosproject.net.meter.Band;

/**
 * Utility class.
 */
public final class Up4Utils {

    // hide default constructor
    private Up4Utils() {
    }

    /**
     * Pretty print UPF meter entity.
     *
     * @param meter the UPF meter
     * @return the pretty print string representation.
     */
    public static String ppUpfMeter(UpfMeter meter) {
        StringBuilder sb = new StringBuilder("idx=" + meter.cellId());
        if (meter.peakBand().isPresent()) {
            Band peak = meter.peakBand().get();
            sb.append(", pir=").append(peak.rate()).append(", pburst=").append(peak.burst());
        }
        if (meter.committedBand().isPresent()) {
            Band committed = meter.committedBand().get();
            sb.append(", cir=").append(committed.rate()).append(", cburst=").append(committed.burst());
        }
        return sb.toString();
    }
}
