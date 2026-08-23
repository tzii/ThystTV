package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPopupPolicyTest {

    @Test
    fun `compact width is capped with edge insets`() {
        assertEquals(672, PlayerPopupPolicy.panelWidthPx(surfaceWidthPx = 1000, density = 2f))
        assertEquals(536, PlayerPopupPolicy.panelWidthPx(surfaceWidthPx = 600, density = 2f))
    }

    @Test
    fun `large width is bounded instead of expanding with the surface`() {
        assertEquals(768, PlayerPopupPolicy.panelWidthPx(surfaceWidthPx = 2560, density = 2f))
    }

    @Test
    fun `popup prefers the space above its trigger`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1200,
            surfaceHeightPx = 800,
            measuredPanelHeightPx = 300,
            density = 2f,
            trigger = PlayerPopupPolicy.Rect(900, 700, 1000, 760),
        )

        assertEquals(232, placement.left)
        assertEquals(384, placement.top)
    }

    @Test
    fun `popup horizontal edge follows trigger side`() {
        val leftTrigger = PlayerPopupPolicy.place(
            surfaceWidthPx = 2000,
            surfaceHeightPx = 1000,
            measuredPanelHeightPx = 300,
            density = 2f,
            trigger = PlayerPopupPolicy.Rect(120, 700, 220, 760),
        )
        val rightTrigger = PlayerPopupPolicy.place(
            surfaceWidthPx = 2000,
            surfaceHeightPx = 1000,
            measuredPanelHeightPx = 300,
            density = 2f,
            trigger = PlayerPopupPolicy.Rect(1780, 700, 1880, 760),
        )

        assertEquals(120, leftTrigger.left)
        assertEquals(1112, rightTrigger.left)
    }

    @Test
    fun `popup moves below trigger when there is no room above`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1200,
            surfaceHeightPx = 800,
            measuredPanelHeightPx = 300,
            density = 2f,
            trigger = PlayerPopupPolicy.Rect(500, 40, 600, 100),
        )

        assertEquals(116, placement.top)
    }

    @Test
    fun `popup pins to bottom edge when neither side fits and trigger is low`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1080,
            surfaceHeightPx = 420,
            measuredPanelHeightPx = 300,
            density = 3f,
            trigger = PlayerPopupPolicy.Rect(48, 300, 144, 396),
        )

        assertEquals(72, placement.top)
    }

    @Test
    fun `popup pins to top edge when neither side fits and trigger is high`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1080,
            surfaceHeightPx = 420,
            measuredPanelHeightPx = 300,
            density = 3f,
            trigger = PlayerPopupPolicy.Rect(500, 60, 600, 120),
        )

        assertEquals(48, placement.top)
    }

    @Test
    fun `fallback follows end edge and mirrors in rtl`() {
        val ltr = PlayerPopupPolicy.place(1200, 800, 300, 2f)
        val rtl = PlayerPopupPolicy.place(1200, 800, 300, 2f, isRtl = true)

        assertEquals(384, ltr.left)
        assertEquals(48, rtl.left)
        assertEquals(452, ltr.top)
        assertEquals(ltr.top, rtl.top)
    }

    @Test
    fun `physical placement converts to the correct relative start margin`() {
        assertEquals(
            100,
            PlayerPopupPolicy.startMarginPx(1200, 100, 300, isRtl = false),
        )
        assertEquals(
            800,
            PlayerPopupPolicy.startMarginPx(1200, 100, 300, isRtl = true),
        )
    }

    @Test
    fun `system insets constrain width height and fallback position`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 500,
            surfaceHeightPx = 500,
            measuredPanelHeightPx = 900,
            density = 1f,
            insets = PlayerPopupPolicy.Insets(left = 200, top = 20, right = 200, bottom = 30),
        )

        assertEquals(216, placement.left)
        assertEquals(36, placement.top)
        assertEquals(68, placement.width)
        assertEquals(418, placement.maxHeight)
    }

    @Test
    fun `expansion only triggers on natural height overflow`() {
        assertTrue(PlayerPopupPolicy.shouldExpandToSurface(measuredPanelHeightPx = 501, maxHeightPx = 500))
        assertFalse(PlayerPopupPolicy.shouldExpandToSurface(measuredPanelHeightPx = 500, maxHeightPx = 500))
        assertFalse(PlayerPopupPolicy.shouldExpandToSurface(measuredPanelHeightPx = 100, maxHeightPx = 0))
    }

    @Test
    fun `expanded popup fills the whole safe surface regardless of trigger`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1080,
            surfaceHeightPx = 600,
            measuredPanelHeightPx = 3000,
            density = 3f,
            trigger = PlayerPopupPolicy.Rect(900, 400, 1000, 500),
            expandToSurface = true,
        )

        assertTrue(placement.fullSurface)
        assertEquals(48, placement.left)
        assertEquals(48, placement.top)
        assertEquals(984, placement.width)
        assertEquals(504, placement.maxHeight)
    }

    @Test
    fun `popup that fits stays anchored even when expansion is allowed`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1080,
            surfaceHeightPx = 600,
            measuredPanelHeightPx = 300,
            density = 3f,
            trigger = PlayerPopupPolicy.Rect(900, 400, 1000, 500),
            expandToSurface = true,
        )

        assertFalse(placement.fullSurface)
        assertEquals(48, placement.left)
        assertEquals(76, placement.top)
    }
}
