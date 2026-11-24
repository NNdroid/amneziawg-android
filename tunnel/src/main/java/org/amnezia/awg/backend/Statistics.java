/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import android.os.SystemClock;

import org.amnezia.awg.crypto.Key;
import org.amnezia.awg.util.NonNullForAll;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;

/**
 * Class representing transfer statistics for a {@link Tunnel} instance.
 */
@NonNullForAll
public class Statistics {
    /**
     * A snapshot of a peer's statistics, including its current connection
     * address. {@code endpoint} is the real decoded address the backend is
     * connected to; when {@code isIp4p} is true the peer uses an IP4P address
     * and {@code ip4pHost} holds the original hostname that resolves to it,
     * {@code ip4pOrig} the full original endpoint (host:port) as configured,
     * and {@code ip4pUpdatedEpochMillis} when the IP4P endpoint was last
     * successfully resolved.
     */
    public record PeerStats(String endpoint, boolean isIp4p, String ip4pHost, String ip4pOrig,
                            long ip4pUpdatedEpochMillis,
                            long rxBytes, long txBytes, long latestHandshakeEpochMillis) { }
    private final Map<Key, PeerStats> stats = new HashMap<>();
    private long lastTouched = SystemClock.elapsedRealtime();

    Statistics() {
    }

    /**
     * Add a peer and its current stats to the internal map.
     *
     * @param key               An AmneziaWG public key bound to a particular peer
     * @param rxBytes           The received traffic for the {@link org.amnezia.config.Peer} referenced by
     *                          the provided {@link Key}. This value is in bytes
     * @param txBytes           The transmitted traffic for the {@link org.amnezia.config.Peer} referenced by
     *                          the provided {@link Key}. This value is in bytes.
     * @param latestHandshake   The timestamp of the latest handshake for the {@link org.amnezia.config.Peer}
     *                          referenced by the provided {@link Key}. The value is in epoch milliseconds.
     * @param endpoint          The real decoded connection address, or {@code null} if unknown.
     * @param isIp4p            Whether the peer uses an IP4P address.
     * @param ip4pHost          The original hostname for an IP4P peer, or {@code null}.
     * @param ip4pOrig          The full original endpoint (host:port) for an IP4P peer, or {@code null}.
     * @param ip4pUpdatedMillis When the IP4P endpoint was last resolved, in epoch millis, or 0.
     */
    void add(final Key key, final long rxBytes, final long txBytes, final long latestHandshake,
             @Nullable final String endpoint, final boolean isIp4p, @Nullable final String ip4pHost,
             @Nullable final String ip4pOrig, final long ip4pUpdatedMillis) {
        stats.put(key, new PeerStats(endpoint, isIp4p, ip4pHost, ip4pOrig, ip4pUpdatedMillis,
                rxBytes, txBytes, latestHandshake));
        lastTouched = SystemClock.elapsedRealtime();
    }

    /**
     * Check if the statistics are stale, indicating the need for the {@link Backend} to update them.
     *
     * @return boolean indicating if the current statistics instance has stale values.
     */
    public boolean isStale() {
        return SystemClock.elapsedRealtime() - lastTouched > 900;
    }

    /**
     * Get the statistics for the {@link org.amnezia.config.Peer} referenced by the provided {@link Key}
     *
     * @param peer A {@link Key} representing a {@link org.amnezia.config.Peer}.
     * @return a {@link PeerStats} representing various statistics about this peer.
     */
    @Nullable
    public PeerStats peer(final Key peer) {
        return stats.get(peer);
    }

    /**
     * Get the list of peers being tracked by this instance.
     *
     * @return An array of {@link Key} instances representing AmneziaWG
     * {@link org.amnezia.config.Peer}s
     */
    public Key[] peers() {
        return stats.keySet().toArray(new Key[0]);
    }

    /**
     * Get the total received traffic by all the peers being tracked by this instance
     *
     * @return a long representing the number of bytes received by the peers being tracked.
     */
    public long totalRx() {
        long rx = 0;
        for (final PeerStats val : stats.values()) {
            rx += val.rxBytes;
        }
        return rx;
    }

    /**
     * Get the total transmitted traffic by all the peers being tracked by this instance
     *
     * @return a long representing the number of bytes transmitted by the peers being tracked.
     */
    public long totalTx() {
        long tx = 0;
        for (final PeerStats val : stats.values()) {
            tx += val.txBytes;
        }
        return tx;
    }
}
