/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2021-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.onosproject.net.behaviour.upf.UpfCounter;
import org.onosproject.net.behaviour.upf.UpfMeter;
import org.onosproject.net.behaviour.upf.UpfTerminationUplink;

import static com.google.common.base.Preconditions.checkArgument;
import static org.omecproject.up4.impl.Up4Utils.ppUpfMeter;

/**
 * Helper class primarily intended for printing uplink UPF flows.
 */
public final class UplinkUpfFlow {
    private final UpfTerminationUplink term;
    private final UpfCounter counter;
    private final UpfMeter appMeter;

    private UplinkUpfFlow(UpfTerminationUplink term, UpfCounter counter, UpfMeter appMeter) {
        this.term = term;
        this.counter = counter;
        this.appMeter = appMeter;
    }

    /**
     * Gets the uplink UPF termination of this UE flow.
     *
     * @return the uplink UPF termination
     */
    public UpfTerminationUplink getTermination() {
        return this.term;
    }

    /**
     * Gets the UPF counter value of this UE flow.
     *
     * @return the UPF counter value
     */
    public UpfCounter getCounter() {
        return this.counter;
    }

    @Override
    public String toString() {
        String termString = "NO TERMINATION!";
        if (term != null) {
            termString = "ue_addr=" + term.ueSessionId() + ", app_id=" + term.applicationId() + ", ";
            if (term.needsDropping()) {
                termString += "drop()";
            } else {
                termString += "fwd(tc=" + term.trafficClass() + ")";
            }
        }

        String strAppMeter = "NO APPLICATION METER";
        if (term != null) {
            strAppMeter += " (app_meter_idx=" + term.appMeterIdx() + ")";
        }
        if (appMeter != null) {
            strAppMeter = "Application meter: " + ppUpfMeter(appMeter);
        }

        String statString = "NO STATISTICS!";
        if (counter != null) {
            statString = String.format(
                    "packets_ingress=%5d, packets_egress=%5d",
                    counter.getIngressPkts(), counter.getEgressPkts()
            );
        }
        return termString +
                "\n    " + strAppMeter +
                "\n    " + statString;
    }

    /**
     * Returns a new uplink UPF flow builder.
     *
     * @return uplink UPF flow builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder of a uplink UPF flow.
     */
    public static class Builder {
        private UpfTerminationUplink term = null;
        private UpfCounter counter = null;
        private UpfMeter appMeter = null;

        public Builder() {
        }

        /**
         * Adds the uplink UPF termination. The counter id should match the UPF
         * counter ID value.
         *
         * @param term the uplink UPF termination
         * @return this builder object
         */
        public Builder withTerminationUplink(UpfTerminationUplink term) {
            this.term = term;
            return this;
        }

        /**
         * Adds the UPF counter. The counter ID should match the counter id in the
         * uplink UPF termination.
         *
         * @param counter the UPF counter
         * @return this builder object
         */
        public Builder withCounter(UpfCounter counter) {
            this.counter = counter;
            return this;
        }

        /**
         * Adds the UPF app meter. The meter cell id should match the app
         * meter index in the uplink UPF termination.
         *
         * @param meter the UPF app meter
         * @return this builder object
         */
        public Builder withAppMeter(UpfMeter meter) {
            this.appMeter = meter;
            return this;
        }

        public UplinkUpfFlow build() {
            if (term != null && counter != null) {
                checkArgument(term.counterId() == counter.getCellId(),
                              "UPF Counter must refer to the given UPF Termination");
            }
            if (term != null && appMeter != null) {
                checkArgument(term.appMeterIdx() == appMeter.cellId(),
                              "UPF app meter must refer to the given UPF termination");
            }
            return new UplinkUpfFlow(term, counter, appMeter);
        }
    }
}
