/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.config;

import org.junit.Assert;
import org.junit.Test;
import org.onosproject.net.config.InvalidFieldException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Tests for Up4DbufConfig.
 */
public class Up4DbufConfigTest {

    @Test
    public void testisValidAddrString() {
        Assert.assertTrue(Up4DbufConfig.isValidAddrString("foo:123", false));
        Assert.assertTrue(Up4DbufConfig.isValidAddrString("foo.com:123", false));
        Assert.assertTrue(Up4DbufConfig.isValidAddrString("1.2.3.4:123", false));
        Assert.assertTrue(Up4DbufConfig.isValidAddrString("1.2.3.4:123", true));

        try {
            Up4DbufConfig.isValidAddrString("", false);
        } catch (InvalidFieldException e) {
            assertThat(e.getMessage(), containsString("address string cannot be blank"));
        }

        try {
            Up4DbufConfig.isValidAddrString("foo123", false);
        } catch (InvalidFieldException e) {
            assertThat(e.getMessage(), containsString("invalid address, must be host:port"));
        }

        try {
            Up4DbufConfig.isValidAddrString("foo:123:4", false);
        } catch (InvalidFieldException e) {
            assertThat(e.getMessage(), containsString("invalid address, must be host:port"));
        }

        try {
            Up4DbufConfig.isValidAddrString("foo:123", true);
        } catch (InvalidFieldException e) {
            assertThat(e.getMessage(), containsString("invalid IPv4 address"));
        }

        try {
            Up4DbufConfig.isValidAddrString("foo:bar", false);
        } catch (InvalidFieldException e) {
            assertThat(e.getMessage(), containsString("invalid port number"));
        }

        try {
            Up4DbufConfig.isValidAddrString("foo:-1", false);
        } catch (InvalidFieldException e) {
            assertThat(e.getMessage(), containsString("invalid port number"));
        }
    }
}
