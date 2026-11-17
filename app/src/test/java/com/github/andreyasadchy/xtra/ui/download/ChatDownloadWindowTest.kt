package com.github.andreyasadchy.xtra.ui.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatDownloadWindowTest {

    @Test
    fun `a clip with its VoD place and length saves that stretch of chat`() {
        val window = ChatDownloadWindow.of("123", sourceStartPositionMs = 95_000L, durationMs = 30_000L)!!
        assertEquals(95, window.startSeconds)
        assertEquals(30, window.durationSeconds)
        assertEquals(125, window.endSeconds)
    }

    @Test
    fun `a clip at the very start of its VoD still has chat`() {
        assertEquals(ChatDownloadWindow(0, 12), ChatDownloadWindow.of("123", 0L, 12_000L))
    }

    @Test
    fun `a VoD id without a known place has no chat window instead of failing the download`() {
        // A clip made during the broadcast: Twitch reports its VoD before its offset.
        assertNull(ChatDownloadWindow.of("123", sourceStartPositionMs = null, durationMs = 30_000L))
        // Older downloads stored an unknown offset as -1 second.
        assertNull(ChatDownloadWindow.of("123", sourceStartPositionMs = -1_000L, durationMs = 30_000L))
    }

    @Test
    fun `no VoD or no known length has no chat window`() {
        assertNull(ChatDownloadWindow.of(null, 95_000L, 30_000L))
        assertNull(ChatDownloadWindow.of("", 95_000L, 30_000L))
        assertNull(ChatDownloadWindow.of("123", 95_000L, null))
        assertNull(ChatDownloadWindow.of("123", 95_000L, 0L))
    }
}
