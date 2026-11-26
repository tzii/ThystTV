package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A 1000x500 px landscape player at density 1, so dp and px match. */
class FloatingChatPlacementTest {
    private val density = 1f
    private val rest = FloatingChatPlacement.bounds(1000, 500, density)
    private val layout = FloatingChatPlacement.defaultLayout(tablet = false)

    @Test fun `bounds keep a margin and clear the cutout and system bars`() {
        val bounds = FloatingChatPlacement.bounds(1000, 500, density, safeLeft = 40, safeRight = 0, keepClearTop = 56, keepClearBottom = 96)
        assertEquals(52f, bounds.left, 0f)
        assertEquals(68f, bounds.top, 0f)
        assertEquals(988f, bounds.right, 0f)
        assertEquals(392f, bounds.bottom, 0f)
    }

    @Test fun `a new card rests in the top right corner`() {
        val placed = FloatingChatPlacement.place(layout, 224, 240, rest, rest, density)
        assertEquals(988f - 224f, placed.x, 0f)
        assertEquals(12f, placed.y, 0f)
        assertEquals(240, placed.height)
        assertEquals(FloatingChatSide.RIGHT, layout.side)
    }

    @Test fun `a card keeps its share of the screen in both directions`() {
        val middle = layout.copy(xFraction = 0.5f, yFraction = 0.5f)
        val placed = FloatingChatPlacement.place(middle, 224, 240, rest, rest, density)
        // Travel is 976 - 224 = 752 across and 476 - 240 = 236 down.
        assertEquals(12f + 376f, placed.x, 0f)
        assertEquals(12f + 118f, placed.y, 0f)
        assertEquals(FloatingChatSide.RIGHT, middle.side)
        assertEquals(FloatingChatSide.LEFT, middle.copy(xFraction = 0.49f).side)
    }

    @Test fun `a short screen cuts the card down but not below the floor`() {
        val tight = FloatingChatPlacement.bounds(1000, 300, density, keepClearTop = 56, keepClearBottom = 96)
        // 300 - 56 - 96 - 2 x 12 leaves 124 px, still above the 0.8 x 140 = 112 px floor.
        assertEquals(124, FloatingChatPlacement.place(layout, 224, 240, tight, tight, density).height)
        val tighter = FloatingChatPlacement.bounds(1000, 260, density, keepClearTop = 56, keepClearBottom = 96)
        assertEquals(112, FloatingChatPlacement.place(layout, 224, 240, tighter, tighter, density).height)
    }

    @Test fun `above the keyboard the card fits the room left, below the usual floor`() {
        val keyboard = FloatingChatPlacement.bounds(1000, 500, density, keepClearBottom = 400)
        // 500 - 400 - 2 x 12 leaves 76 px, under the 112 px floor.
        val bottom = layout.copy(yFraction = 1f)
        val placed = FloatingChatPlacement.place(bottom, 224, 240, rest, keyboard, density, keyboard = true)
        assertEquals(76, placed.height)
        assertEquals(12f, placed.y, 0f)
        assertEquals(112, FloatingChatPlacement.place(layout, 224, 240, rest, keyboard, density).height)
    }

    @Test fun `a window narrower than the card narrows it and keeps the margins`() {
        val narrow = FloatingChatPlacement.bounds(150, 500, density)
        val placed = FloatingChatPlacement.place(layout, 224, 240, narrow, narrow, density)
        assertEquals(126, placed.width)
        assertEquals(12f, placed.x, 0f)
    }

    @Test fun `a released card stays where it was let go`() {
        val dropped = release(x = 400f, y = 150f)
        assertNull(dropped.tuckedSide)
        assertEquals((400f - 12f) / 752f, dropped.layout.xFraction, 0.0001f)
        assertEquals((150f - 12f) / 236f, dropped.layout.yFraction, 0.0001f)
        val placed = FloatingChatPlacement.place(dropped.layout, 224, 240, rest, rest, density)
        assertEquals(400f, placed.x, 0.01f)
        assertEquals(150f, placed.y, 0.01f)
    }

    @Test fun `a card let go near an edge lands flush with it`() {
        assertEquals(0f, release(x = 20f).layout.xFraction, 0f)
        assertEquals(1f, release(x = 760f).layout.xFraction, 0f)
        assertEquals(0f, release(x = 400f, y = 18f).layout.yFraction, 0f)
        assertEquals(1f, release(x = 400f, y = 240f).layout.yFraction, 0f)
        // 9 px from the left edge is a deliberate spot, not a magnet.
        assertEquals(9f / 752f, release(x = 21f).layout.xFraction, 0.0001f)
    }

    @Test fun `a fling never carries the card past where it was let go`() {
        assertEquals((300f - 12f) / 752f, release(x = 300f, vx = 1000f).layout.xFraction, 0.0001f)
        assertEquals((300f - 12f) / 752f, release(x = 300f, vx = 5000f, startX = 300f).layout.xFraction, 0.0001f)
        assertEquals((100f - 12f) / 236f, release(x = 300f, y = 100f, vy = -2000f).layout.yFraction, 0.0001f)
    }

    @Test fun `dragging over a third of the card past an edge tucks it`() {
        // 35% of 224 is 78.4 px past the 988 px edge.
        assertEquals(FloatingChatSide.RIGHT, release(x = 764f + 80f).tuckedSide)
        assertNull(release(x = 764f + 70f).tuckedSide)
        assertEquals(FloatingChatSide.LEFT, release(x = 12f - 80f, startX = 12f).tuckedSide)
    }

    @Test fun `a fast outward fling tucks only a card that started at that edge`() {
        assertEquals(FloatingChatSide.RIGHT, release(x = 780f, vx = 2000f, startX = 764f).tuckedSide)
        // Within 24 px of the edge counts as at it.
        assertEquals(FloatingChatSide.RIGHT, release(x = 780f, vx = 2000f, startX = 745f).tuckedSide)
        assertEquals(FloatingChatSide.LEFT, release(x = 0f, vx = -2000f, startX = 12f).tuckedSide)
        // Tossed from the middle: it stays where it was let go.
        val tossed = release(x = 700f, vx = 2000f, startX = 400f)
        assertNull(tossed.tuckedSide)
        assertEquals((700f - 12f) / 752f, tossed.layout.xFraction, 0.0001f)
        // Too slow or too steep.
        assertNull(release(x = 780f, vx = 1500f, startX = 764f).tuckedSide)
        assertNull(release(x = 780f, vx = 2000f, vy = 1500f, startX = 764f).tuckedSide)
    }

    @Test fun `a tucked card waits just past the screen edge`() {
        assertEquals(-224f - 12f, FloatingChatPlacement.tuckedX(FloatingChatSide.LEFT, 224, 1000, density), 0f)
        assertEquals(1012f, FloatingChatPlacement.tuckedX(FloatingChatSide.RIGHT, 224, 1000, density), 0f)
    }

    @Test fun `size stays between the minimum and the screen share`() {
        assertEquals(170 to 140, FloatingChatPlacement.clampSize(10f, 10f, 1000, 500, density))
        assertEquals(550 to 476, FloatingChatPlacement.clampSize(900f, 900f, 1000, 500, density))
    }

    @Test fun `the grip grows the card toward the video on either side`() {
        assertEquals(274 to 290, FloatingChatPlacement.resize(224, 240, -50f, 50f, FloatingChatSide.RIGHT, 1000, 500, density))
        assertEquals(274 to 290, FloatingChatPlacement.resize(224, 240, 50f, 50f, FloatingChatSide.LEFT, 1000, 500, density))
    }

    // Controls on the 1000x500 player: the top right buttons, the right ±10 button and the seek row.
    private val topRight = FloatingChatBounds(850f, 5f, 990f, 50f)
    private val rightButton = FloatingChatBounds(890f, 220f, 950f, 280f)
    private val seekRow = FloatingChatBounds(80f, 440f, 920f, 470f)

    @Test fun `a card over a control covers it, one beside it does not`() {
        val middle = FloatingChatPlaced(388f, 130f, 224, 240)
        assertFalse(FloatingChatPlacement.covers(middle, listOf(topRight, rightButton, seekRow)))
        assertFalse(FloatingChatPlacement.covers(middle, emptyList()))
        assertTrue(FloatingChatPlacement.covers(FloatingChatPlaced(764f, 12f, 224, 240), listOf(topRight)))
        // Edge to edge is not covering.
        assertFalse(FloatingChatPlacement.covers(FloatingChatPlaced(626f, 12f, 224, 240), listOf(topRight)))
        assertTrue(FloatingChatPlacement.covers(FloatingChatPlaced(627f, 12f, 224, 240), listOf(topRight)))
    }

    @Test fun `covered area adds up each control's overlap`() {
        // The default card's top 48 px band against the top right buttons, and its bottom band against +10.
        assertEquals(138f * 38f, FloatingChatPlacement.coveredArea(764f, 12f, 988f, 60f, listOf(topRight)), 0.01f)
        assertEquals(60f * 32f, FloatingChatPlacement.coveredArea(764f, 204f, 988f, 252f, listOf(rightButton)), 0.01f)
        assertEquals(0f, FloatingChatPlacement.coveredArea(0f, 100f, 300f, 200f, listOf(topRight, rightButton)), 0f)
    }

    @Test fun `a point hits only the controls it is on`() {
        val controls = listOf(topRight, rightButton)
        assertTrue(FloatingChatPlacement.hits(900f, 20f, controls))
        assertTrue(FloatingChatPlacement.hits(890f, 220f, controls))
        assertFalse(FloatingChatPlacement.hits(950f, 250f, controls))
        assertFalse(FloatingChatPlacement.hits(500f, 250f, controls))
    }

    private fun release(
        x: Float,
        y: Float = 12f,
        vx: Float = 0f,
        vy: Float = 0f,
        startX: Float = 764f,
        free: FloatingChatBounds = rest,
        stored: FloatingChatLayout = layout,
    ) = FloatingChatPlacement.release(
        x = x,
        y = y,
        velocityX = vx,
        velocityY = vy,
        widthPx = 224,
        heightPx = 240,
        startX = startX,
        rest = rest,
        free = free,
        density = density,
        stored = stored,
    )
}
