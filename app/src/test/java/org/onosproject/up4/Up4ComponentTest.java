/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.onosproject.up4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onosproject.cfg.ComponentConfigAdapter;
import org.onosproject.core.CoreServiceAdapter;
import org.onosproject.net.device.DeviceServiceAdapter;
import org.onosproject.net.flow.FlowRuleServiceAdapter;
import org.onosproject.net.pi.PiPipeconfServiceAdapter;

/**
 * Set of tests of the ONOS application component.
 */
public class Up4ComponentTest {

    private Up4Component component;

    @Before
    public void setUp() {
        component = new Up4Component();
        component.cfgService = new ComponentConfigAdapter();
        component.coreService = new CoreServiceAdapter();
        component.flowRuleService = new FlowRuleServiceAdapter();
        component.deviceService = new DeviceServiceAdapter();
        component.piPipeconfService = new PiPipeconfServiceAdapter();
        component.activate();
    }

    @After
    public void tearDown() {
        component.deactivate();
    }

    @Test
    public void basics() {

    }

}
