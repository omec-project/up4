package org.omecproject.up4.impl;

import org.onosproject.net.behaviour.upf.UpfCounter;
import org.onosproject.net.behaviour.upf.UpfTerminationUplink;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Helper class primarily intended for printing uplink UPF flows.
 */
public final class UplinkUpfFlow {
    private final UpfTerminationUplink term;
    private final UpfCounter counter;

    private UplinkUpfFlow(UpfTerminationUplink term, UpfCounter counter) {
        this.term = term;
        this.counter = counter;
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
            termString = "UE_ADDR=" + term.ueSessionId() + " --> ";
            if (term.needsDropping()) {
                termString += "DROP";
            } else {
                termString += "FWD(TC=" + term.trafficClass() + ")";
            }
        }

        String statString = "NO STATISTICS!";
        if (counter != null) {
            statString = String.format(
                    "%5d Ingress pkts -> %5d Egress pkts",
                    counter.getIngressPkts(), counter.getEgressPkts()
            );
        }
        return termString + " >> " + statString;
    }

    public static Builder builder() {
        return new Builder();
    }


    public static class Builder {
        private UpfTerminationUplink term = null;
        private UpfCounter counter = null;

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

        public UplinkUpfFlow build() {
            if (term != null && counter != null) {
                checkArgument(term.counterId() == counter.getCellId(),
                              "UPF Counter must refer to the given UPF Termination");
            }
            return new UplinkUpfFlow(term, counter);
        }
    }
}
