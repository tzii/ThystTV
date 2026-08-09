package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class VodRetentionPolicyTest {

    @Test
    fun `null or blank effective type uses seven days`() {
        assertEquals(7, vodRetentionDays(userType = null, userBroadcasterType = null))
        assertEquals(7, vodRetentionDays(userType = "", userBroadcasterType = null))
        assertEquals(7, vodRetentionDays(userType = "   ", userBroadcasterType = null))
    }

    @Test
    fun `affiliate uses fourteen days case insensitively`() {
        assertEquals(14, vodRetentionDays(userType = "affiliate", userBroadcasterType = null))
        assertEquals(14, vodRetentionDays(userType = "AfFiLiAtE", userBroadcasterType = null))
    }

    @Test
    fun `other nonblank types use sixty days`() {
        assertEquals(60, vodRetentionDays(userType = "partner", userBroadcasterType = null))
        assertEquals(60, vodRetentionDays(userType = "prime", userBroadcasterType = null))
        assertEquals(60, vodRetentionDays(userType = "turbo", userBroadcasterType = null))
    }

    @Test
    fun `fallback to broadcaster type occurs only when primary type is null`() {
        assertEquals(14, vodRetentionDays(userType = null, userBroadcasterType = "affiliate"))
        assertEquals(7, vodRetentionDays(userType = "", userBroadcasterType = "affiliate"))
    }
}
