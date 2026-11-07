package com.github.andreyasadchy.xtra.util

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connection liveness kept as a deadline that traffic pushes forward.
 *
 * Chat sockets used to cancel and recreate a `java.util.Timer` for each keepalive, and
 * EventSub and Hermes did so for every notification. Each Timer starts its own thread,
 * and a Timer could still fire after its connection had closed. Here one coroutine per
 * connection sleeps until the deadline. [feed] only moves the deadline, so calling it for
 * every message costs a field write, and the coroutine wakes at most once per idle period.
 * Cancelling the scope, or [stop], ends the watch.
 *
 * With a [probe] (an IRC `PING`), a missed idle deadline first runs the probe, then
 * expires if no [feed] arrives within the probe timeout. Without one, it expires directly.
 */
internal class KeepaliveWatchdog(
    /** Uptime, like the delays of Android's main dispatcher, so deep sleep is not idle time. */
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    @Volatile
    private var deadline = Long.MAX_VALUE

    @Volatile
    private var idleMs = 0L

    @Volatile
    private var probing = false
    private var job: Job? = null

    val isRunning: Boolean
        @Synchronized get() = job?.isActive == true

    /**
     * Watches in [scope] until [idleMs] passes without a [feed], replacing any earlier watch.
     * [onExpired] runs once, in its own coroutine, so a restart cannot cut short a disconnect.
     */
    @Synchronized
    fun start(
        scope: CoroutineScope,
        idleMs: Long,
        probe: (suspend () -> Unit)? = null,
        probeTimeoutMs: Long = 0L,
        onExpired: suspend () -> Unit,
    ) {
        job?.cancel()
        this.idleMs = idleMs
        probing = false
        deadline = clock() + idleMs
        job = scope.launch {
            while (true) {
                val wait = deadline - clock()
                if (wait > 0) {
                    delay(wait)
                } else if (probe != null && !probing) {
                    probing = true
                    deadline = clock() + probeTimeoutMs
                    try {
                        probe()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A probe that cannot be written means the connection is already gone.
                        deadline = 0L
                    }
                } else {
                    scope.launch { onExpired() }
                    return@launch
                }
            }
        }
    }

    /** Traffic arrived; restart the idle period, adopting [idleMs] when the server announced a new one. */
    fun feed(idleMs: Long = this.idleMs) {
        this.idleMs = idleMs
        probing = false
        deadline = clock() + idleMs
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        deadline = Long.MAX_VALUE
    }
}
