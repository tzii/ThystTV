package com.github.andreyasadchy.xtra.ui.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ClipCreationTest {
    @Test fun `accepted response yields the clip and its editor`() {
        val body = """{"data":[{"id":"FiveWordsForClipSlug","edit_url":"https://www.twitch.tv/twitchdev/clip/FiveWordsForClipSlug","extra":1}]}"""
        assertEquals(
            ClipCreationResult.Created("FiveWordsForClipSlug", "https://www.twitch.tv/twitchdev/clip/FiveWordsForClipSlug"),
            ClipCreation.parseResponse(202, body),
        )
        assertEquals(ClipCreationResult.Created("a", null), ClipCreation.parseResponse(202, """{"data":[{"id":"a","edit_url":""}]}"""))
    }

    @Test fun `accepted response without a usable clip is a failure`() {
        for (body in listOf("""{"data":[]}""", """{"data":[{"id":""}]}""", """{}""", "not json", "")) {
            assertEquals(body, ClipCreationResult.Failed, ClipCreation.parseResponse(202, body))
        }
    }

    @Test fun `twitch refusals stay distinct`() {
        assertEquals(ClipCreationResult.NotClippable, ClipCreation.parseResponse(400, ""))
        assertEquals(ClipCreationResult.SignInRequired, ClipCreation.parseResponse(401, ""))
        assertEquals(ClipCreationResult.NotAllowed, ClipCreation.parseResponse(403, ""))
        assertEquals(ClipCreationResult.NotLive, ClipCreation.parseResponse(404, ""))
        assertEquals(ClipCreationResult.RateLimited, ClipCreation.parseResponse(429, ""))
        assertEquals(ClipCreationResult.Unavailable, ClipCreation.parseResponse(503, "not json"))
        assertEquals(ClipCreationResult.Failed, ClipCreation.parseResponse(500, ""))
    }

    @Test fun `a refused title is told apart from a channel that can't be clipped`() {
        val automod = """{"error":"Bad Request","status":400,"message":"The title failed AutoMod"}"""
        assertEquals(ClipCreationResult.TitleRejected, ClipCreation.parseResponse(400, automod))
        val other = """{"error":"Bad Request","status":400,"message":"The broadcaster has restricted the ability to capture clips"}"""
        assertEquals(ClipCreationResult.NotClippable, ClipCreation.parseResponse(400, other))
    }

    @Test fun `a request trims its title and stays within Twitch's limits`() {
        assertEquals(ClipRequest("Big play", 20f), ClipRequest.of("  Big play ", 20f))
        assertEquals("a blank title means none", null, ClipRequest.of("   ", 30f).title)
        assertEquals(null, ClipRequest.of(null, 30f).title)
        assertEquals(ClipCreation.MAX_TITLE_LENGTH, ClipRequest.of("x".repeat(500), 30f).title?.length)
        assertEquals(5f, ClipRequest.of(null, 1f).durationSeconds)
        assertEquals(60f, ClipRequest.of(null, 90f).durationSeconds)
    }

    @Test fun `confirmation stops at the first sighting and survives failed checks`() = runTest {
        var checks = 0
        assertTrue(ClipCreation.awaitConfirmation {
            checks++
            if (checks == 1) throw IOException("offline")
            checks == 3
        })
        assertEquals(3, checks)
        assertEquals(3 * ClipCreation.CONFIRM_POLL_MS, currentTime)
    }

    @Test fun `confirmation gives up at the documented deadline`() = runTest {
        assertFalse(ClipCreation.awaitConfirmation { false })
        assertEquals(ClipCreation.CONFIRM_TIMEOUT_MS, currentTime)
    }

    @Test fun `a cancellable suspended check stops at the confirmation deadline`() = runTest {
        assertFalse(ClipCreation.awaitConfirmation { awaitCancellation() })
        assertEquals(60_000L, currentTime)
    }
}
