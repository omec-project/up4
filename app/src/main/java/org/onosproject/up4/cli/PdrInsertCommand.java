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
package org.onosproject.up4.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.IpAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;

import java.util.List;
import java.util.stream.Collectors;

import org.onosproject.up4.impl.UpfComponent;

/**
 * UPF PDR Insert Command
 */
@Service
@Command(scope = "onos", name = "pdr-insert",
         description = "Insert a packet detection rule into the UPF dataplane")
public class PdrInsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "uri", description = "Device ID",
              required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String uri = null;

    @Argument(index = 1, name = "session-id",
            description = "Session ID for this PDR",
            required = true, multiValued = true)
    int sessionId = 0;

    @Argument(index = 2, name = "ue-ipv4-addr",
            description = "Address of the UE for which this PDR applies",
            required = true, multiValued = true)
    String ueAddr = null;

    @Argument(index = 3, name = "far-id",
            description = "ID of the FAR that should apply if this PDR matches",
            required = true, multiValued = true)
    int farId = 0;

    @Override
    protected void doExecute() {
        DeviceService deviceService = get(DeviceService.class);
        UpfComponent app = get(UpfComponent.class);

        Device device = deviceService.getDevice(DeviceId.deviceId(uri));
        if (device == null) {
            print("Device \"%s\" is not found", uri);
            return;
        }

        Ip4Address ueAddr = Ip4Address.valueOf(this.ueAddr);

        print("Installing PDR on device %s", uri);
        app.addPdr(device.id(), sessionId, 1, farId, ueAddr);
    }

}
