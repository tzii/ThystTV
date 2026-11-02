package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMagnifyGeometryTest {

    @Test
    fun `fit size letterboxes or pillarboxes the video`() {
        val (phoneWidth, phoneHeight) = PlayerMagnifyGeometry.fitSize(16f / 9f, 2400, 1080)
        assertEquals(1920f, phoneWidth, 0.01f)
        assertEquals(1080f, phoneHeight, 0.01f)
        val (width, height) = PlayerMagnifyGeometry.fitSize(16f / 9f, 1000, 1000)
        assertEquals(1000f, width, 0.001f)
        assertEquals(562.5f, height, 0.001f)
        // An unknown aspect ratio assumes 16:9, and an unmeasured player has no picture.
        assertEquals(562.5f, PlayerMagnifyGeometry.fitSize(0f, 1000, 1000).second, 0.001f)
        assertEquals(0f to 0f, PlayerMagnifyGeometry.fitSize(16f / 9f, 0, 1000))
    }

    @Test
    fun `pan limit keeps the picture covering the player`() {
        assertEquals(500f, PlayerMagnifyGeometry.panLimit(fitExtent = 1000f, viewportExtent = 1000f, zoom = 2f), 0.001f)
        assertEquals(0f, PlayerMagnifyGeometry.panLimit(fitExtent = 562.5f, viewportExtent = 1000f, zoom = 1.5f), 0f)
    }

    @Test
    fun `anchored translation keeps the picture point under the fingers`() {
        val translation = 40f
        val fromFocus = 200f
        val fromZoom = 2f
        // Picture point under the focus, in unscaled picture pixels from its center.
        val point = (fromFocus - translation) / fromZoom
        val toFocus = 260f
        val toZoom = 3f
        val anchored = PlayerMagnifyGeometry.anchoredTranslation(translation, fromFocus, toFocus, fromZoom, toZoom)
        assertEquals(toFocus, anchored + point * toZoom, 0.001f)
    }

    @Test
    fun `pan fraction survives zoom and player size changes within the limits`() {
        val fraction = PlayerMagnifyGeometry.panFraction(translation = -300f, fitExtent = 1000f, zoom = 2f)
        assertEquals(-0.15f, fraction, 0.0001f)
        assertEquals(-450f, PlayerMagnifyGeometry.translation(fraction, fitExtent = 1000f, viewportExtent = 1000f, zoom = 3f), 0.001f)
        // A narrower player keeps the same share until the edge would show.
        assertEquals(-250f, PlayerMagnifyGeometry.translation(fraction, fitExtent = 1000f, viewportExtent = 1500f, zoom = 2f), 0.001f)
        assertEquals(0f, PlayerMagnifyGeometry.panFraction(translation = 10f, fitExtent = 0f, zoom = 2f), 0f)
    }

    @Test
    fun `a drag moves the picture until an edge would enter the player`() {
        // A 2000 px picture in a 1000 px player can move 500 px either way.
        val moved = PlayerMagnifyGeometry.pan(panFraction = 0f, delta = 120f, fitExtent = 1000f, viewportExtent = 1000f, zoom = 2f)
        assertEquals(0.06f, moved, 0.0001f)
        assertEquals(0.25f, PlayerMagnifyGeometry.pan(moved, delta = 900f, fitExtent = 1000f, viewportExtent = 1000f, zoom = 2f), 0.0001f)
        assertEquals(-0.25f, PlayerMagnifyGeometry.pan(0f, delta = -900f, fitExtent = 1000f, viewportExtent = 1000f, zoom = 2f), 0.0001f)
        // A picture that does not cover the player on this axis stays centered.
        assertEquals(0f, PlayerMagnifyGeometry.pan(0f, delta = 50f, fitExtent = 400f, viewportExtent = 1000f, zoom = 2f), 0f)
    }

    @Test
    fun `a drag moves from the drawn position when the stored pan is past the limit`() {
        // Stored at 0.25 of a 2000 px picture, but a 1500 px player only allows 250 px.
        val moved = PlayerMagnifyGeometry.pan(panFraction = 0.25f, delta = -50f, fitExtent = 1000f, viewportExtent = 1500f, zoom = 2f)
        assertEquals(0.1f, moved, 0.0001f)
    }
}
