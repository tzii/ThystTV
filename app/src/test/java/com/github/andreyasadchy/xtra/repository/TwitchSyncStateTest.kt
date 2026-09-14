package com.github.andreyasadchy.xtra.repository

import org.junit.Assert.*
import org.junit.Test

class TwitchSyncStateTest {
    @Test fun `rewind and zero are real changes not a maximum merge`() {
        val start = TwitchProgress("1", seconds = 500, baseline = 500, baselineKnown = true)
        val rewind = start.checkpoint(0)
        assertEquals(0L, rewind.seconds)
        assertTrue(rewind.pending)
        assertFalse(rewind.conflictsWith(500))
        assertTrue(rewind.conflictsWith(600))
    }

    @Test fun `unknown baseline cannot overwrite existing Twitch history`() {
        val queued = TwitchProgress("1").checkpoint(20)
        assertTrue(queued.conflictsWith(0))
        assertTrue(queued.conflictsWith(30))
        assertFalse(queued.conflictsWith(null))
        assertFalse(queued.conflictsWith(20))
    }

    @Test fun `known absent history is distinct from position zero`() {
        val queued = TwitchProgress("1", baselineKnown = true).checkpoint(20)
        assertFalse(queued.conflictsWith(null))
        assertTrue(queued.conflictsWith(0))
    }

    @Test fun `acknowledging old revision preserves a newer rewind`() {
        val sent = TwitchProgress("1", baselineKnown = true).checkpoint(200)
        val latest = sent.checkpoint(0).acknowledge(sent, 200)
        assertEquals(0L, latest.seconds)
        assertEquals(200L, latest.baseline)
        assertTrue(latest.pending)
        assertFalse(latest.acknowledge(latest, 0).pending)
    }

    @Test fun `minute requires sixty seconds of advancing playback`() {
        val clock = TwitchPlaybackClock()
        for (second in 0L..59) assertFalse(clock.sample("a", second * 1000, second * 1000, true))
        assertTrue(clock.sample("a", 60_000, 60_000, true))
        assertFalse(clock.sample("a", 61_000, 61_000, true))
    }

    @Test fun `pause buffering and long suspension earn no minutes`() {
        val clock = TwitchPlaybackClock()
        for (second in 0L..100) assertFalse(clock.sample("a", second * 1000, second * 1000, false))
        for (second in 101L..200) assertFalse(clock.sample("a", second * 1000, 5000, true))
        assertFalse(clock.sample("a", 400_000, 205_000, true))
    }

    @Test fun `speed and large forward seeks do not award extra elapsed time`() {
        val clock = TwitchPlaybackClock()
        for (second in 0L..59) assertFalse(clock.sample("a", second * 1000, second * 100_000, true))
        assertTrue(clock.sample("a", 60_000, 6_000_000, true))
    }

    @Test fun `switching account broadcast or player discards the partial minute`() {
        val clock = TwitchPlaybackClock()
        for (second in 0L..59) assertFalse(clock.sample("a", second * 1000, second * 1000, true))
        for (second in 60L..119) assertFalse(clock.sample("b", second * 1000, second * 1000, true))
        assertTrue(clock.sample("b", 120_000, 120_000, true))
        clock.reset()
        assertFalse(clock.sample("b", 121_000, 121_000, true))
    }
}
