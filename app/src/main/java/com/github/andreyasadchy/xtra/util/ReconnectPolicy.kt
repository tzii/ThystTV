package com.github.andreyasadchy.xtra.util

import kotlin.random.Random

/**
 * The wait between [WebSocket] connection attempts.
 *
 * Sockets used to stop after 20 failed attempts one second apart, so about 20 seconds of
 * bad signal ended chat until the player was reopened: Android often still reports a weak
 * network as validated, so no "connection restored" event restarts it. This never gives up.
 * The wait doubles up to [MAX_DELAY_MS], so a long outage costs one attempt every half
 * minute, and a random half of each wait spreads out reconnects after a server restart.
 */
internal class ReconnectPolicy(private val random: Random = Random.Default) {
    private var failures = 0

    /** A connection opened, so the next drop retries quickly again. */
    fun connected() {
        failures = 0
    }

    /** The wait before the next attempt after a dropped or failed connection. */
    fun nextDelayMs(rateLimited: Boolean = false): Long {
        if (rateLimited) return RATE_LIMITED_DELAY_MS
        val ceiling = minOf(MAX_DELAY_MS, FIRST_DELAY_MS shl minOf(failures, 5))
        failures++
        return ceiling / 2 + random.nextLong(ceiling / 2 + 1)
    }

    companion object {
        const val FIRST_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 30_000L

        /** After an HTTP 429 the server asked us to slow down. */
        const val RATE_LIMITED_DELAY_MS = 60_000L
    }
}
