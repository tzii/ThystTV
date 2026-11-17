package com.github.andreyasadchy.xtra.util

import android.os.SystemClock
import kotlin.random.Random

/**
 * The wait between [WebSocket] connection attempts.
 *
 * Sockets used to stop after 20 failed attempts one second apart, so about 20 seconds of
 * bad signal ended chat until the player was reopened: Android often still reports a weak
 * network as validated, so no "connection restored" event restarts it. This never gives up.
 * The wait doubles up to [MAX_DELAY_MS], so a long outage costs one attempt every half
 * minute, and a random half of each wait spreads out reconnects after a server restart.
 *
 * Only a connection that stayed up for [HEALTHY_MS] makes the next wait short again. One
 * that the server closes at once, as after a rejected chat login, or that the app drops
 * on its first message, backs off like a failed attempt instead of retrying every second.
 */
internal class ReconnectPolicy(
    private val random: Random = Random.Default,
    /** Keeps running in deep sleep, so a connection held overnight counts as healthy. */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var failures = 0
    private var openedAtMs: Long? = null

    /** A connection opened. If it lasts [HEALTHY_MS], the next drop retries quickly again. */
    fun connected() {
        openedAtMs = clock()
    }

    /** The wait before the next attempt after a dropped or failed connection. */
    fun nextDelayMs(rateLimited: Boolean = false): Long {
        openedAtMs?.let { openedAt ->
            if (clock() - openedAt >= HEALTHY_MS) failures = 0
            openedAtMs = null
        }
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

        /** How long a connection must stay up before its drop counts as a fresh outage. */
        const val HEALTHY_MS = 10_000L
    }
}
