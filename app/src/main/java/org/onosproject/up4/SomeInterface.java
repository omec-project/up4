/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.up4;


import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip4Address;
import org.onosproject.net.DeviceId;


public interface SomeInterface {


    /**
     * Counters to implement Usage Reporting Rules.
     */
    enum Up4CounterType {
        /**
         * Count packets that hit PDRs, before QoS executes.
         */
        PRE_QOS_PDR,
        /**
         * Count packets that hit PDRs, after QoS executes.
         */
        POST_QOS_PDR,
    }

    enum Direction {
        UNKNOWN,
        UPLINK,
        DOWNLINK,
        BOTH,
    }

    enum InterfaceType {
        UNKNOWN         (0),
        ACCESS          (1),
        CORE            (2),
        N6_LAN          (3),  // unused
        VN_INTERNAL     (4),  // unused
        CONTROL_PLANE   (5);  // N4 and N4-u

        public final int value;

        private InterfaceType(int value) {
            this.value = value;
        }
    }

    public class RuleId {
        SessionId   sessionID;  // Session associated with this RuleID
        int         localID;    // Session-local Rule ID
        int         globalID;   // Globally unique Rule ID

        public RuleId(SessionId sessionID, int localID, int globalID) {
            this.sessionID = sessionID;
            this.localID = localID;
            this.globalID = globalID;
        }
    }

    public class SessionId {
        Ip4Address  endpoint;
        int         localID;

        public SessionId(Ip4Address endpoint, int localID) {
            this.endpoint = endpoint;
            this.localID = localID;
        }
    }


    void someMethod();

    void addPdr(DeviceId deviceId, int ctrId, RuleId farId,
                        Ip4Address ueAddr, int teid, Ip4Address tunnelDst);

    void addPdr(DeviceId deviceId, int ctrId, RuleId farId, Ip4Address ueAddr);


    void addFar(DeviceId deviceId, RuleId farID, boolean drop, boolean notifyCp, Ip4Address tunnelSrc, Ip4Address tunnelDst, int teid);

    void addFar(DeviceId deviceId, RuleId farID, boolean drop, boolean notifyCp);

    void addUePool(DeviceId deviceId, Ip4Prefix poolPrefix);

    void addS1uInterface(DeviceId deviceId, Ip4Address s1uAddr);

}