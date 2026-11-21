package com.github.andreyasadchy.xtra.ui.player

import androidx.core.graphics.Insets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerEdgeZonePolicyTest {

    @Test
    fun `cutout padding reduces the surface-local edge instead of double-counting it`() {
        val exclusion = PlayerEdgeZonePolicy.resolve(
            gestureInsets = Insets.of(72, 90, 187, 90),
            fallbackPx = 90,
            surfaceLeftPx = 0,
            surfaceTopPx = 0,
            surfaceRightPx = 115,
            surfaceBottomPx = 0,
        )
        assertEquals(72, exclusion.leftPx)
        assertEquals(72, exclusion.rightPx)
        assertEquals(90, exclusion.topPx)
        assertEquals(90, exclusion.bottomPx)
        assertEquals(72f, exclusion.chatSideExclusionPx(isRtl = false), 0f)
    }

    @Test
    fun `open side chat leaves zero edge exclusion next to the chat divider`() {
        val exclusion = PlayerEdgeZonePolicy.resolve(
            gestureInsets = Insets.of(72, 90, 187, 90),
            fallbackPx = 90,
            surfaceRightPx = 835,
        )
        assertEquals(0, exclusion.rightPx)
        assertFalse(exclusion.isSideEdge(x = 1565f, surfaceWidthPx = 1565))
        assertTrue(exclusion.isSideEdge(x = 50f, surfaceWidthPx = 1565))
        assertEquals(0f, exclusion.chatSideExclusionPx(isRtl = false), 0f)
    }

    @Test
    fun `top and bottom edges are tracked separately from side edges`() {
        val exclusion = PlayerEdgeZonePolicy.resolve(
            gestureInsets = Insets.of(60, 80, 180, 80),
            fallbackPx = 90,
        )
        assertTrue(exclusion.isTopOrBottomEdge(y = 70f, surfaceHeightPx = 1080))
        assertTrue(exclusion.isTopOrBottomEdge(y = 1010f, surfaceHeightPx = 1080))
        assertFalse(exclusion.isTopOrBottomEdge(y = 540f, surfaceHeightPx = 1080))

        assertTrue(exclusion.isSideEdge(x = 50f, surfaceWidthPx = 2400))
        assertTrue(exclusion.isSideEdge(x = 2250f, surfaceWidthPx = 2400))
        assertFalse(exclusion.isSideEdge(x = 2200f, surfaceWidthPx = 2400))
    }

    @Test
    fun `side edge vertical claim requires a clearly vertical drag`() {
        assertTrue(PlayerEdgeZonePolicy.allowsVerticalFromSideEdge(dx = 0f, dy = -12f))
        assertTrue(PlayerEdgeZonePolicy.allowsVerticalFromSideEdge(dx = -10f, dy = -20f))
        assertFalse(PlayerEdgeZonePolicy.allowsVerticalFromSideEdge(dx = -10f, dy = -19f))
        assertFalse(PlayerEdgeZonePolicy.allowsVerticalFromSideEdge(dx = -24f, dy = -8f))
        assertFalse(PlayerEdgeZonePolicy.allowsVerticalFromSideEdge(dx = 0f, dy = 0f))
    }
}
