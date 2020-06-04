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

import org.onosproject.up4.impl.SomeInterface;
import org.onosproject.up4.impl.SomeInterface.TunnelDesc;
import org.onosproject.up4.impl.UpfComponent;

/**
 * UPF Far Insert Command
 */
@Service
@Command(scope = "onos", name = "far-insert",
        description = "Insert a forwarding action rule into the UPF dataplane")
public class FarInsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "uri", description = "Device ID",
            required = true)
    @Completion(DeviceIdCompleter.class)
    String uri = null;

    @Argument(index = 1, name = "session-id",
            description = "Session ID for this PDR",
            required = true)
    int sessionId = 0;

    @Argument(index = 2, name = "far-id",
            description = "ID of the FAR",
            required = true)
    int farId = 0;

    @Argument(index = 3, name = "teid",
            description = "Teid of the tunnel the packet should enter",
            required = false)
    int teid = -1;

    @Argument(index = 4, name = "tunnel-src",
            description = "Src IP of the tunnel the packet should enter",
            required = false)
    String tunnelSrc = null;

    @Argument(index = 5, name = "tunnel-dst",
            description = "Dst IP of the tunnel the packet should enter",
            required = false)
    String tunnelDst = null;

    @Override
    protected void doExecute() {
        DeviceService deviceService = get(DeviceService.class);
        UpfComponent app = get(UpfComponent.class);

        Device device = deviceService.getDevice(DeviceId.deviceId(uri));
        if (device == null) {
            print("Device \"%s\" is not found", uri);
            return;
        }

        if (teid != -1) {
            if (tunnelSrc == null) {
                print("Tunnel source IP must be provided with TEID");
                return;
            }
            if (tunnelDst == null) {
                print("Tunnel destination IP must be provided with TEID");
                return;
            }
            Ip4Address tunnelDst = Ip4Address.valueOf(this.tunnelDst);
            Ip4Address tunnelSrc = Ip4Address.valueOf(this.tunnelSrc);
            TunnelDesc tunnel = new TunnelDesc(tunnelSrc, tunnelDst, teid);

            print("Installing *Downlink* FAR on device %s", uri);
            app.addFar(device.id(), sessionId, farId, false, false, tunnel);
        }
        else {
            print("Installing *Uplink* FAR on device %s", uri);
        }



        app.addFar(device.id(), sessionId, farId, false, false);
    }

}
