package com.github.andreyasadchy.xtra.ui.following

import com.github.andreyasadchy.xtra.ui.following.streams.FollowedStreamsAutoRefresh
import org.junit.Assert.assertEquals
import org.junit.Test

class FollowedStreamsAutoRefreshTest {

    private val interval = FollowedStreamsAutoRefresh.INTERVAL_MS

    @Test fun `a fresh list waits the whole interval`() {
        assertEquals(interval, FollowedStreamsAutoRefresh.delayUntilDue(loadedAt = 1_000L, now = 1_000L))
    }

    @Test fun `a list loaded a while ago waits only for the rest`() {
        assertEquals(interval - 30_000L, FollowedStreamsAutoRefresh.delayUntilDue(loadedAt = 0L, now = 30_000L))
    }

    @Test fun `a list older than the interval reloads at once`() {
        assertEquals(0L, FollowedStreamsAutoRefresh.delayUntilDue(loadedAt = 0L, now = interval * 5))
    }

    @Test fun `a load stamped after now never waits longer than the interval`() {
        assertEquals(interval, FollowedStreamsAutoRefresh.delayUntilDue(loadedAt = 10_000L, now = 0L))
    }
}
