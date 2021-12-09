/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onlab.packet.Ip4Address;
import org.onosproject.cfg.ComponentConfigAdapter;
import org.onosproject.common.event.impl.TestEventDispatcher;
import org.onosproject.core.CoreServiceAdapter;
import org.onosproject.net.behaviour.upf.GtpTunnelPeer;
import org.onosproject.net.behaviour.upf.UpfInterface;
import org.onosproject.net.behaviour.upf.UpfProgrammableException;
import org.onosproject.net.config.NetworkConfigRegistryAdapter;
import org.onosproject.net.device.DeviceServiceAdapter;
import org.onosproject.net.flow.FlowRuleServiceAdapter;
import org.onosproject.net.pi.PiPipeconfServiceAdapter;

import static org.omecproject.up4.impl.Up4DeviceManager.DBUF_TUNNEL_ID;
import static org.onosproject.net.NetTestTools.injectEventDispatcher;

/**
 * Set of tests of the ONOS application component.
 */
public class Up4DeviceManagerTest {

    private Up4DeviceManager component;

    private final UpfInterface dbufInterface = UpfInterface.createDbufReceiverFrom(Ip4Address.valueOf("10.0.0.1"));
    private final GtpTunnelPeer dbufTunnelPeer = GtpTunnelPeer.builder()
            .withTunnelPeerId(DBUF_TUNNEL_ID)
            .withSrcPort((short) 2152)
            .withSrcAddr(Ip4Address.valueOf("10.0.0.1"))
            .withDstAddr(Ip4Address.valueOf("10.0.0.2"))
            .build();

    @Before
    public void setUp() {
        component = new Up4DeviceManager();
        component.coreService = new CoreServiceAdapter();
        component.flowRuleService = new FlowRuleServiceAdapter();
        component.deviceService = new DeviceServiceAdapter();
        component.piPipeconfService = new PiPipeconfServiceAdapter();
        component.netCfgService = new NetworkConfigRegistryAdapter();
        component.componentConfigService = new ComponentConfigAdapter();
        component.up4Store = TestDistributedUp4Store.build();
        injectEventDispatcher(component, new TestEventDispatcher());
        component.activate();
    }

    @After
    public void tearDown() {
        component.deactivate();
    }

    @Test
    public void basics() {

    }

    @Test(expected = UpfProgrammableException.class)
    public void testPreventDbufInterfaceApply() throws UpfProgrammableException {
        component.apply(dbufInterface);
    }

    @Test(expected = UpfProgrammableException.class)
    public void testPreventDbufInterfaceDelete() throws UpfProgrammableException {
        component.delete(dbufInterface);
    }

    @Test(expected = UpfProgrammableException.class)
    public void testPreventDbufGtpTunnelApply() throws UpfProgrammableException {
        component.apply(dbufTunnelPeer);
    }

    @Test(expected = UpfProgrammableException.class)
    public void testPreventDbufGtpTunnelDelete() throws UpfProgrammableException {
        component.delete(dbufTunnelPeer);
    }

}
