/*
 * Copyright 2019-present Open Networking Foundation
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

import org.onosproject.net.pi.model.PiPipeconfId;

public final class AppConstants {
    // hide default constructor
    private AppConstants() {
    }
    public static final String APP_NAME = "org.onosproject.up4";
    public static final PiPipeconfId PIPECONF_ID = new PiPipeconfId("org.onosproject.up4");
    public static final int GRPC_SERVER_PORT = 51001;
    public static final String P4INFO_PATH = "/p4info.txt";
    public static final String SUPPORTED_PIPECONF_NAME = "fabric-spgw";
}
