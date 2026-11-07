package com.github.andreyasadchy.xtra.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    fun `traffic pushes the deadline forward and adopts an announced timeout`() = runTest {
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
        watchdog.feed(30_000)
        at(48_998)
        assertTrue(expired.isEmpty())
        at(48_999)
        assertEquals(listOf(48_999L), expired)
        assertFalse(watchdog.isRunning)
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
