/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.config;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigTest {

    @Test(expected = BadConfigException.class)
    public void invalid_config_throws() throws IOException, BadConfigException {
        try (final InputStream is = Objects.requireNonNull(getClass().getClassLoader()).getResourceAsStream("broken.conf")) {
            Config.parse(is);
        }
    }

    @Test
    public void valid_config_parses_correctly() throws IOException, ParseException {
        Config config = null;
        final Collection<InetNetwork> expectedAllowedIps = new HashSet<>(Arrays.asList(InetNetwork.parse("0.0.0.0/0"), InetNetwork.parse("::0/0")));
        try (final InputStream is = Objects.requireNonNull(getClass().getClassLoader()).getResourceAsStream("working.conf")) {
            config = Config.parse(is);
        } catch (final BadConfigException e) {
            fail("'working.conf' should never fail to parse");
        }
        assertNotNull("config cannot be null after parsing", config);
        assertTrue(
                "No applications should be excluded by default",
                config.getInterface().getExcludedApplications().isEmpty()
        );
        assertEquals("Test config has exactly one peer", 1, config.getPeers().size());
        assertEquals("Test config's allowed IPs are 0.0.0.0/0 and ::0/0", config.getPeers().get(0).getAllowedIps(), expectedAllowedIps);
        assertEquals("Test config has one DNS server", 1, config.getInterface().getDnsServers().size());
    }

    /**
     * Regression test: IP4P fields (bootstrap DNS and IP4P prefix) must survive a
     * serialize ({@code toAwgQuickString}) → parse ({@code Config.parse}) round
     * trip. Previously these were omitted from the awg-quick representation, so
     * they appeared empty after a save/reload cycle.
     */
    @Test
    public void ip4p_fields_round_trip() throws IOException, BadConfigException {
        final String source =
                "[Interface]\n" +
                "PrivateKey = TFlmmEUC7V7VtiDYLKsbP5rySTKLIZq1yn8lMqK83wo=\n" +
                "BootstrapDns = 1.1.1.1\n" +
                "Ip4pPrefix = 2001::\n" +
                "[Peer]\n" +
                "PublicKey = vBN7qyUTb5lJtWYJ8LhbPio1Z4RcyBPGnqFBGn6O6Qg=\n";

        final Config parsed = parseString(source);
        assertEquals("BootstrapDns parses", "1.1.1.1", parsed.getInterface().getBootstrapDns().orElse(null));
        assertEquals("Ip4pPrefix parses", "2001::", parsed.getInterface().getIp4pPrefix().orElse(null));

        // Serialize and re-parse: the fields must survive the round trip.
        final String quick = parsed.toAwgQuickString();
        final Config reloaded = parseString(quick);
        assertEquals("BootstrapDns survives round trip", "1.1.1.1", reloaded.getInterface().getBootstrapDns().orElse(null));
        assertEquals("Ip4pPrefix survives round trip", "2001::", reloaded.getInterface().getIp4pPrefix().orElse(null));
    }

    private static Config parseString(final String source) throws IOException, BadConfigException {
        try (final InputStream is = new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))) {
            return Config.parse(is);
        }
    }
}
