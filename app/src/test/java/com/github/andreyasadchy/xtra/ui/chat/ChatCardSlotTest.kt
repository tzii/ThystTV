package com.github.andreyasadchy.xtra.ui.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatCardSlotTest {

    private fun TestScope.slot() = ChatCardSlot<String>(backgroundScope, clock = { testScheduler.currentTime })

    private fun TestScope.at(ms: Long) {
        advanceTimeBy(ms - testScheduler.currentTime)
        runCurrent()
    }

    private fun ChatCardSlot<String>.poll(id: String, status: String, countdownMs: Long? = null) =
        update(id, "$id $status", pollPhase(status), pollReopens(status)) { countdownMs }

    private fun ChatCardSlot<String>.prediction(id: String, status: String) =
        update(id, "$id $status", predictionPhase(status), predictionReopens(status))

    @Test
    fun `a poll counts down, stays closed while voting, then shows its result for twenty seconds`() = runTest {
        val poll = slot()
        assertTrue(poll.poll("a", "ACTIVE", countdownMs = 60_000))
        assertEquals(ChatCard("a ACTIVE", endsAt = 60_000L), poll.card.value)

        at(5_000)
        poll.close()
        assertFalse(poll.poll("a", "ACTIVE", countdownMs = 60_000))
        assertNull(poll.card.value)

        at(60_000)
        poll.poll("a", "COMPLETED")
        assertEquals(ChatCard("a COMPLETED"), poll.card.value)
        at(79_999)
        assertEquals(ChatCard("a COMPLETED"), poll.card.value)
        at(80_000)
        assertNull(poll.card.value)
    }

    @Test
    fun `later updates keep the first deadline, and an unknown status ends the card`() = runTest {
        val poll = slot()
        poll.poll("a", "ACTIVE", countdownMs = 60_000)
        at(10_000)
        poll.poll("a", "ACTIVE", countdownMs = 600_000)
        assertEquals(60_000L, poll.card.value?.endsAt)

        poll.poll("a", "ARCHIVED")
        assertNull(poll.card.value)
        poll.poll("a", "ACTIVE")
        assertNull(poll.card.value)
    }

    @Test
    fun `a new poll replaces a result without being hidden by the old result's timeout`() = runTest {
        val poll = slot()
        poll.poll("a", "COMPLETED")
        at(10_000)
        assertTrue(poll.poll("b", "ACTIVE", countdownMs = 999))
        at(40_000)
        // Under a second left is not worth a countdown.
        assertEquals(ChatCard("b ACTIVE"), poll.card.value)
    }

    @Test
    fun `a closed prediction reopens for a lock or pending outcome but not the final outcome`() = runTest {
        val prediction = slot()
        prediction.prediction("p", "ACTIVE")
        prediction.close()
        prediction.prediction("p", "LOCKED")
        assertEquals(ChatCard("p LOCKED"), prediction.card.value)

        prediction.close()
        prediction.prediction("p", "RESOLVED")
        assertNull(prediction.card.value)
        prediction.prediction("p", "RESOLVE_PENDING")
        assertEquals(ChatCard("p RESOLVE_PENDING"), prediction.card.value)
        at(20_000)
        assertNull(prediction.card.value)
    }

    @Test
    fun `a disconnect forgets the card, so the same id counts as new again`() = runTest {
        val raid = slot()
        assertTrue(raid.update("r", "raid", CardPhase.OPEN))
        assertFalse(raid.update("r", "raid", CardPhase.OPEN))
        raid.close()
        raid.update("r", "raid", CardPhase.OPEN)
        assertNull(raid.card.value)

        raid.reset()
        assertTrue(raid.update("r", "raid", CardPhase.OPEN))
        assertEquals(ChatCard("raid"), raid.card.value)
    }

    @Test
    fun `the countdown shows whole seconds rounded up and wakes as each one passes`() {
        assertEquals(60L, secondsUntil(60_000, 0))
        assertEquals(1_000L, nextTickIn(60_000, 0))
        assertEquals(60L, secondsUntil(60_000, 500))
        assertEquals(500L, nextTickIn(60_000, 500))
        assertEquals(1L, secondsUntil(60_000, 59_999))
        assertEquals(1L, nextTickIn(60_000, 59_999))
        assertEquals(0L, secondsUntil(60_000, 60_000))
        assertNull(nextTickIn(60_000, 60_000))
        // After the device slept through part or all of it.
        assertEquals(29L, secondsUntil(60_000, 31_500))
        assertEquals(0L, secondsUntil(60_000, 88_000))
        assertNull(nextTickIn(60_000, 88_000))
    }
}
