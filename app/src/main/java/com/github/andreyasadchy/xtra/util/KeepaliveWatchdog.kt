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
 * connection sleeps until the deadline. [feed] only moves the deadline later, so calling it
 * for every message is cheap, and the coroutine wakes at most once per idle period.
 * Cancelling the scope, or [stop], ends the watch.
 *
 * With a [probe] (an IRC `PING`), a missed idle deadline first runs the probe, then
 * expires if no [feed] arrives within the probe timeout. Without one, it expires directly.
 *
 * [start] is called from the socket's thread while the watch runs in the connection's scope,
 * often on the main thread. State changes only under this object's lock and only for the
 * current watch, so a replaced watch that has not yet seen its cancellation cannot touch
 * its replacement's deadline.
 */
internal class KeepaliveWatchdog(
    /** Uptime, like the delays of Android's main dispatcher, so deep sleep is not idle time. */
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private var watch = 0L
    private var deadline = Long.MAX_VALUE
    private var idleMs = 0L
    private var probing = false
    private var job: Job? = null

    val isRunning: Boolean
        @Synchronized get() = job?.isActive == true

    private enum class Step { WAIT, PROBE, EXPIRE }

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
        // Set before launching: an immediate dispatcher runs the loop inside this call.
        val current = ++watch
        this.idleMs = idleMs
        probing = false
        deadline = clock() + idleMs
        job = scope.launch {
            while (true) {
                val now = clock()
                var wait = 0L
                val step = synchronized(this@KeepaliveWatchdog) {
                    if (watch != current) return@launch
                    wait = deadline - now
                    when {
                        wait > 0 -> Step.WAIT
                        probe != null && !probing -> {
                            probing = true
                            deadline = now + probeTimeoutMs
                            Step.PROBE
                        }
                        else -> Step.EXPIRE
                    }
                }
                when (step) {
                    Step.WAIT -> delay(wait)
                    Step.PROBE -> try {
                        probe?.invoke()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A probe that cannot be written means the connection is already gone.
                        synchronized(this@KeepaliveWatchdog) {
                            if (watch == current) deadline = 0L
                        }
                    }
                    Step.EXPIRE -> {
                        // Once begun, a restart cannot cut the expiry short; one that has
                        // not begun is dropped if a newer watch started in the meantime.
                        scope.launch {
                            if (isCurrent(current)) onExpired()
                        }
                        return@launch
                    }
                }
            }
        }
    }

    @Synchronized
    private fun isCurrent(watch: Long) = this.watch == watch

    /** Traffic arrived; restart the idle period. */
    @Synchronized
    fun feed() {
        probing = false
        deadline = clock() + idleMs
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        watch++
        deadline = Long.MAX_VALUE
    }
}
