package com.github.andreyasadchy.xtra.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatTimeoutDurationTest {
    @Test
    fun `missing duration uses the Twitch default of ten minutes`() {
        assertEquals(600, ChatTimeoutDuration.seconds(null))
        assertEquals(600, ChatTimeoutDuration.seconds(""))
        assertEquals(600, ChatTimeoutDuration.seconds("  "))
    }

    @Test
    fun `plain numbers are seconds`() {
        assertEquals(1, ChatTimeoutDuration.seconds("1"))
        assertEquals(300, ChatTimeoutDuration.seconds("300"))
    }

    @Test
    fun `unit suffixes convert to seconds in either case`() {
        assertEquals(30, ChatTimeoutDuration.seconds("30s"))
        assertEquals(600, ChatTimeoutDuration.seconds("10m"))
        assertEquals(600, ChatTimeoutDuration.seconds("10M"))
        assertEquals(3_600, ChatTimeoutDuration.seconds("1h"))
        assertEquals(172_800, ChatTimeoutDuration.seconds("2d"))
        assertEquals(1_209_600, ChatTimeoutDuration.seconds("2w"))
    }

    @Test
    fun `unreadable durations are rejected instead of becoming a ban`() {
        assertNull(ChatTimeoutDuration.seconds("spam"))
        assertNull(ChatTimeoutDuration.seconds("m"))
        assertNull(ChatTimeoutDuration.seconds("10x"))
        assertNull(ChatTimeoutDuration.seconds("1h30m"))
        assertNull(ChatTimeoutDuration.seconds("-5"))
        assertNull(ChatTimeoutDuration.seconds("1.5h"))
    }

    @Test
    fun `durations outside the Helix range are rejected`() {
        assertNull(ChatTimeoutDuration.seconds("0"))
        assertNull(ChatTimeoutDuration.seconds("0m"))
        assertNull(ChatTimeoutDuration.seconds("1209601"))
        assertNull(ChatTimeoutDuration.seconds("3w"))
        assertNull(ChatTimeoutDuration.seconds("99999999999999999999"))
        assertNull(ChatTimeoutDuration.seconds("9999999999d"))
    }
}
