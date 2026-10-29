package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerChatSwipePolicyTest {

    private fun starts(isLive: Boolean, startX: Float, isRtl: Boolean = false) = PlayerChatSwipePolicy.startsChatSwipe(
        isLive = isLive,
        startX = startX,
        surfaceWidthPx = 1000f,
        chatSideExclusionPx = 30f,
        stripWidthPx = 56f,
        isRtl = isRtl,
    )

    @Test
    fun `live accepts a swipe from anywhere on the surface`() {
        assertTrue(starts(isLive = true, startX = 100f))
        assertTrue(starts(isLive = true, startX = 500f))
        assertTrue(starts(isLive = true, startX = 950f))
    }

    @Test
    fun `seekable media accept only the strip inside the chat side exclusion`() {
        // Strip: more than 30px and at most 86px from the right edge.
        assertTrue(starts(isLive = false, startX = 969f))
        assertTrue(starts(isLive = false, startX = 914f))
        assertFalse(starts(isLive = false, startX = 970f)) // inside the system edge zone
        assertFalse(starts(isLive = false, startX = 913f)) // seek and speed keep the rest
        assertFalse(starts(isLive = false, startX = 500f))
        assertFalse(starts(isLive = false, startX = 50f)) // the other edge is not the chat side
    }

    @Test
    fun `rtl puts the seekable strip on the left`() {
        assertTrue(starts(isLive = false, startX = 31f, isRtl = true))
        assertTrue(starts(isLive = false, startX = 86f, isRtl = true))
        assertFalse(starts(isLive = false, startX = 30f, isRtl = true))
        assertFalse(starts(isLive = false, startX = 87f, isRtl = true))
        assertFalse(starts(isLive = false, startX = 950f, isRtl = true))
    }

    @Test
    fun `an unmeasured surface never starts a chat swipe`() {
        assertFalse(
            PlayerChatSwipePolicy.startsChatSwipe(
                isLive = true, startX = 0f, surfaceWidthPx = 0f,
                chatSideExclusionPx = 30f, stripWidthPx = 56f, isRtl = false,
            )
        )
    }

    @Test
    fun `swiping away from the chat side shows chat and toward it hides chat`() {
        assertEquals(PlayerChatSwipeAction.SHOW, PlayerChatSwipePolicy.action(dxPx = -80f, isRtl = false, chatVisible = false))
        assertEquals(PlayerChatSwipeAction.HIDE, PlayerChatSwipePolicy.action(dxPx = 80f, isRtl = false, chatVisible = true))
        assertEquals(PlayerChatSwipeAction.SHOW, PlayerChatSwipePolicy.action(dxPx = 80f, isRtl = true, chatVisible = false))
        assertEquals(PlayerChatSwipeAction.HIDE, PlayerChatSwipePolicy.action(dxPx = -80f, isRtl = true, chatVisible = true))
    }

    @Test
    fun `a swipe never cycles chat that is already in the requested state`() {
        assertNull(PlayerChatSwipePolicy.action(dxPx = -80f, isRtl = false, chatVisible = true))
        assertNull(PlayerChatSwipePolicy.action(dxPx = 80f, isRtl = false, chatVisible = false))
        assertNull(PlayerChatSwipePolicy.action(dxPx = 80f, isRtl = true, chatVisible = true))
        assertNull(PlayerChatSwipePolicy.action(dxPx = -80f, isRtl = true, chatVisible = false))
    }

    @Test
    fun `trigger distance is a minimum length or a share of wide surfaces`() {
        assertEquals(132f, PlayerChatSwipePolicy.triggerDistancePx(surfaceWidthPx = 1000f, density = 2.75f), 0.001f)
        assertEquals(192f, PlayerChatSwipePolicy.triggerDistancePx(surfaceWidthPx = 2400f, density = 2.75f), 0.001f)
    }

    @Test
    fun `trigger needs the distance and a mostly sideways path`() {
        assertFalse(PlayerChatSwipePolicy.reachedTrigger(dxPx = -99f, dyPx = 0f, triggerPx = 100f))
        assertTrue(PlayerChatSwipePolicy.reachedTrigger(dxPx = -100f, dyPx = 20f, triggerPx = 100f))
        assertTrue(PlayerChatSwipePolicy.reachedTrigger(dxPx = 100f, dyPx = -20f, triggerPx = 100f))
        assertFalse(PlayerChatSwipePolicy.reachedTrigger(dxPx = 120f, dyPx = 130f, triggerPx = 100f))
    }
}
