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
package org.onosproject.up4.impl;


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

    /**
     * Direction of user data flow. Maybe will be used for PDR-related method.
     */
    enum Direction {
        UNKNOWN,
        UPLINK,
        DOWNLINK,
        /**
         * For flexible PDRs that do not care about direction
         */
        BOTH,
    }

    /**
     * Type of UPF/SPGW interface. Currently unused
     */
    enum InterfaceType {
        UNKNOWN         (0),
        ACCESS          (1),
        CORE            (2),
        N6_LAN          (3),
        VN_INTERNAL     (4),
        CONTROL_PLANE   (5);  // N4 and N4-u

        public final int value;

        private InterfaceType(int value) {
            this.value = value;
        }
    }

    /**
     *
     */
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

    public class TunnelDesc {
        Ip4Address src;
        Ip4Address dst;
        int teid;

        public TunnelDesc(Ip4Address src, Ip4Address dst, int teid) {
            this.src = src;
            this.dst = dst;
            this.teid = teid;
        }
    }


    void someMethod();

    void addPdr(DeviceId deviceId, int sessionId, int ctrId, int farId, Ip4Address ueAddr);

    void addPdr(DeviceId deviceId, int sessionId, int ctrId, int farId,
                        Ip4Address ueAddr, int teid, Ip4Address tunnelDst);

    void addFar(DeviceId deviceId, int sessionId, int farId, boolean drop, boolean notifyCp, TunnelDesc desc);

    void addFar(DeviceId deviceId, int sessionId, int farId, boolean drop, boolean notifyCp);

    void addUePool(DeviceId deviceId, Ip4Prefix poolPrefix);

    void addS1uInterface(DeviceId deviceId, Ip4Address s1uAddr);

}