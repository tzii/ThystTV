package com.github.andreyasadchy.xtra.util.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentIdsTest {

    @Test
    fun `rejects redelivered ids and forgets the oldest past capacity`() {
        val ids = RecentIds(3)
        assertTrue(ids.add("a"))
        assertTrue(ids.add("b"))
        assertFalse(ids.add("a"))
        assertTrue(ids.add("c"))
        // Seeing "a" again did not refresh it, so it is still the oldest.
        assertTrue(ids.add("d"))
        assertTrue(ids.add("a"))
        assertFalse(ids.add("c"))
        assertFalse(ids.add("d"))
        assertTrue(ids.add("b"))
    }
}
