package com.github.andreyasadchy.xtra.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class KeepaliveWatchdogTest {

    private fun TestScope.watchdog() = KeepaliveWatchdog(clock = { testScheduler.currentTime })

    /** Moves virtual time to exactly [ms] and runs everything due by then. */
    private fun TestScope.at(ms: Long) {
        advanceTimeBy(ms - testScheduler.currentTime)
        runCurrent()
    }

    @Test
    fun `expires once after the idle period without traffic`() = runTest {
        val expired = mutableListOf<Long>()
        watchdog().start(backgroundScope, 10_000) { expired += testScheduler.currentTime }
        at(9_999)
        assertEquals(emptyList<Long>(), expired)
        at(10_000)
        at(60_000)
        assertEquals(listOf(10_000L), expired)
    }

    @Test
    fun `traffic pushes the deadline forward`() = runTest {
        val expired = mutableListOf<Long>()
        val watchdog = watchdog()
        watchdog.start(backgroundScope, 10_000) { expired += testScheduler.currentTime }
        // A busy session feeds many times per idle period; none of them may expire it.
        for (time in 1_000L..9_000L step 1_000L) {
            at(time)
            watchdog.feed()
        }
        at(18_999)
        assertTrue(expired.isEmpty())
        at(19_000)
        assertEquals(listOf(19_000L), expired)
        assertFalse(watchdog.isRunning)
    }

    @Test
    fun `a new session with a shorter timeout does not wait out the old one`() = runTest {
        val expired = mutableListOf<String>()
        val watchdog = watchdog()
        watchdog.start(backgroundScope, 600_000) { expired += "old" }
        at(1_000)
        // A reconnect's welcome announces 10 seconds.
        watchdog.start(backgroundScope, 10_000) { expired += "new at ${testScheduler.currentTime}" }
        at(11_000)
        assertEquals(listOf("new at 11000"), expired)
        at(700_000)
        assertEquals(listOf("new at 11000"), expired)
    }

    @Test
    fun `a restart while the old watch is waking keeps the new deadline`() = runTest {
        val events = mutableListOf<String>()
        var restartOnNextClockRead = false
        lateinit var watchdog: KeepaliveWatchdog
        fun watch(name: String) = watchdog.start(
            backgroundScope,
            270_000,
            probe = { events += "$name probed at ${testScheduler.currentTime}" },
            probeTimeoutMs = 10_000,
        ) { events += "$name expired at ${testScheduler.currentTime}" }
        // The clock read is where the old watch decides to probe; restart right there, as a
        // reconnect on the socket thread can while the old watch runs on the main thread.
        watchdog = KeepaliveWatchdog(clock = {
            if (restartOnNextClockRead) {
                restartOnNextClockRead = false
                watch("second")
            }
            testScheduler.currentTime
        })
        watch("first")
        at(269_999)
        restartOnNextClockRead = true
        at(270_000)
        at(539_999)
        // The first watch neither probed nor shortened the second one's idle period.
        assertEquals(emptyList<String>(), events)
        at(540_000)
        assertEquals(listOf("second probed at 540000"), events)
        at(550_000)
        assertEquals(listOf("second probed at 540000", "second expired at 550000"), events)
    }

    @Test
    fun `an idle connection is probed and expires only if the probe is not answered`() = runTest {
        val probes = mutableListOf<Long>()
        val expired = mutableListOf<Long>()
        val watchdog = watchdog()
        watchdog.start(backgroundScope, 270_000, probe = { probes += testScheduler.currentTime }, probeTimeoutMs = 10_000) {
            expired += testScheduler.currentTime
        }
        at(270_000)
        assertEquals(listOf(270_000L), probes)
        // PONG
        at(270_500)
        watchdog.feed()
        at(540_499)
        assertEquals(listOf(270_000L), probes)
        at(540_500)
        assertEquals(listOf(270_000L, 540_500L), probes)
        at(550_499)
        assertTrue(expired.isEmpty())
        at(550_500)
        assertEquals(listOf(550_500L), expired)
        assertEquals(2, probes.size)
    }

    @Test
    fun `a probe that cannot be written expires at once`() = runTest {
        val expired = mutableListOf<Long>()
        watchdog().start(backgroundScope, 1_000, probe = { throw IOException("closed") }, probeTimeoutMs = 10_000) {
            expired += testScheduler.currentTime
        }
        at(1_000)
        assertEquals(listOf(1_000L), expired)
    }

    @Test
    fun `a restart after the old watch decided to expire drops the old expiry`() = runTest {
        val expired = mutableListOf<String>()
        val watchdog = watchdog()
        watchdog.start(backgroundScope, 10_000) { expired += "first" }
        // Due at the same instant but queued after the first watch's wake-up, so this restart
        // lands after it chose to expire and before its expiry runs.
        backgroundScope.launch {
            delay(10_000)
            watchdog.start(backgroundScope, 10_000) { expired += "second at ${testScheduler.currentTime}" }
        }
        at(10_000)
        assertEquals(emptyList<String>(), expired)
        at(20_000)
        assertEquals(listOf("second at 20000"), expired)
    }

    @Test
    fun `stop and restart leave a single watch`() = runTest {
        val expired = mutableListOf<String>()
        val watchdog = watchdog()
        watchdog.start(backgroundScope, 10_000) { expired += "first" }
        at(5_000)
        // A reconnect restarts the watch for the new connection.
        watchdog.start(backgroundScope, 10_000) { expired += "second" }
        at(14_999)
        assertTrue(expired.isEmpty())
        at(15_000)
        assertEquals(listOf("second"), expired)

        watchdog.start(backgroundScope, 10_000) { expired += "third" }
        at(20_000)
        watchdog.stop()
        assertFalse(watchdog.isRunning)
        at(100_000)
        assertEquals(listOf("second"), expired)
    }

    @Test
    fun `cancelling the owning scope ends the watch`() = runTest {
        val expired = mutableListOf<Long>()
        val owner = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val watchdog = watchdog()
        watchdog.start(owner, 10_000) { expired += testScheduler.currentTime }
        at(5_000)
        owner.cancel()
        at(100_000)
        assertTrue(expired.isEmpty())
        assertFalse(watchdog.isRunning)
    }
}
