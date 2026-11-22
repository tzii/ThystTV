package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A 1000x500 px landscape player at density 1, so dp and px match. */
class FloatingChatPlacementTest {
    private val density = 1f
    private val rest = FloatingChatPlacement.bounds(1000, 500, density)
    private val layout = FloatingChatLayout(FloatingChatSide.RIGHT, FloatingChatAnchor.TOP, 0f, 224f, 240f)

    @Test fun `bounds keep a margin and clear the cutout and control bands`() {
        val bounds = FloatingChatPlacement.bounds(1000, 500, density, safeLeft = 40, safeRight = 0, keepClearTop = 56, keepClearBottom = 96)
        assertEquals(52f, bounds.left, 0f)
        assertEquals(68f, bounds.top, 0f)
        assertEquals(988f, bounds.right, 0f)
        assertEquals(392f, bounds.bottom, 0f)
    }

    @Test fun `a top card rests in the top corner of its side`() {
        val placed = FloatingChatPlacement.place(layout, 224, 240, rest, rest, density)
        assertEquals(988f - 224f, placed.x, 0f)
        assertEquals(12f, placed.y, 0f)
        assertEquals(240, placed.height)
    }

    @Test fun `controls push a top card down and a bottom card up`() {
        val current = FloatingChatPlacement.bounds(1000, 500, density, keepClearTop = 56, keepClearBottom = 96)
        assertEquals(68f, FloatingChatPlacement.place(layout, 224, 240, rest, current, density).y, 0f)
        val bottom = layout.copy(anchor = FloatingChatAnchor.BOTTOM)
        assertEquals(392f - 240f, FloatingChatPlacement.place(bottom, 224, 240, rest, current, density).y, 0f)
    }

    @Test fun `a free card keeps its place until a band reaches it`() {
        val free = layout.copy(anchor = FloatingChatAnchor.FREE, yFraction = 0.5f)
        // Rest travel is 476 - 240 = 236, so half way is 12 + 118.
        assertEquals(130f, FloatingChatPlacement.place(free, 224, 240, rest, rest, density).y, 0f)
        val smallBand = FloatingChatPlacement.bounds(1000, 500, density, keepClearTop = 56)
        assertEquals(130f, FloatingChatPlacement.place(free, 224, 240, rest, smallBand, density).y, 0f)
        val bigBand = FloatingChatPlacement.bounds(1000, 500, density, keepClearTop = 200)
        assertEquals(212f, FloatingChatPlacement.place(free, 224, 240, rest, bigBand, density).y, 0f)
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
        val placed = FloatingChatPlacement.place(layout, 224, 240, rest, keyboard, density, keyboard = true)
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

    @Test fun `release picks the nearer side after projecting the fling`() {
        val left = release(x = 300f, vx = 0f)
        assertEquals(FloatingChatSide.LEFT, left.layout.side)
        assertNull(left.tuckedSide)
        // Center at 412 is left of 500, but a rightward fling carries it over.
        val flung = release(x = 300f, vx = 900f)
        assertEquals(FloatingChatSide.RIGHT, flung.layout.side)
        assertNull(flung.tuckedSide)
    }

    @Test fun `release snaps to the top or bottom within the magnet and stays free between`() {
        assertEquals(FloatingChatAnchor.TOP, release(x = 700f, y = 40f).layout.anchor)
        val bottom = release(x = 700f, y = 230f)
        assertEquals(FloatingChatAnchor.BOTTOM, bottom.layout.anchor)
        assertEquals(1f, bottom.layout.yFraction, 0f)
        val free = release(x = 700f, y = 130f)
        assertEquals(FloatingChatAnchor.FREE, free.layout.anchor)
        assertEquals(0.5f, free.layout.yFraction, 0.001f)
    }

    @Test fun `with no room to move up or down the push decides and a sideways move keeps the anchor`() {
        // 300 - 56 - 96 - 2 x 12 leaves 124 px for a 124 px card: no vertical travel.
        val tight = FloatingChatPlacement.bounds(1000, 300, density, keepClearTop = 56, keepClearBottom = 96)
        val bottom = layout.copy(anchor = FloatingChatAnchor.BOTTOM, yFraction = 1f)
        assertEquals(FloatingChatAnchor.BOTTOM, release(x = 700f, y = 68f + 5f, current = tight, stored = bottom).layout.anchor)
        assertEquals(FloatingChatAnchor.TOP, release(x = 700f, y = 68f - 40f, current = tight, stored = bottom).layout.anchor)
        assertEquals(FloatingChatAnchor.BOTTOM, release(x = 700f, y = 68f + 40f, current = tight, stored = layout).layout.anchor)
        val free = layout.copy(anchor = FloatingChatAnchor.FREE, yFraction = 0.4f)
        val kept = release(x = 700f, y = 68f, current = tight, stored = free).layout
        assertEquals(FloatingChatAnchor.FREE, kept.anchor)
        assertEquals(0.4f, kept.yFraction, 0f)
    }

    @Test fun `dragging a third of the card past an edge tucks it`() {
        assertEquals(FloatingChatSide.RIGHT, release(x = 988f - 224f + 80f).tuckedSide)
        assertNull(release(x = 988f - 224f + 60f).tuckedSide)
        assertEquals(FloatingChatSide.LEFT, release(x = 12f - 80f, startSide = FloatingChatSide.LEFT).tuckedSide)
    }

    @Test fun `only a fast outward sideways fling tucks`() {
        assertEquals(FloatingChatSide.RIGHT, release(x = 700f, vx = 1500f).tuckedSide)
        // Too slow, inward, or too steep.
        assertNull(release(x = 700f, vx = 1000f).tuckedSide)
        assertNull(release(x = 700f, vx = -1500f).tuckedSide)
        assertNull(release(x = 700f, vx = 1500f, vy = 1000f).tuckedSide)
    }

    @Test fun `a fast throw from the other half lands on the side instead of tucking`() {
        // Started on the right, dragged to the left half (center 312), then thrown right.
        val thrown = release(x = 200f, vx = 1500f)
        assertNull(thrown.tuckedSide)
        assertEquals(FloatingChatSide.RIGHT, thrown.layout.side)
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

    // The ±10 buttons: 60dp, 50dp in from each edge, centred on the 500px player.
    private val controlsUp = FloatingChatPlacement.bounds(1000, 500, density, keepClearTop = 56, keepClearBottom = 96)
    private val rightButton = FloatingChatBounds(890f, 220f, 950f, 280f)
    private val leftButton = FloatingChatBounds(50f, 220f, 110f, 280f)

    @Test fun `a card over the seek button on its side steps inward past it`() {
        val placed = FloatingChatPlacement.place(layout, 224, 240, rest, controlsUp, density)
        val aside = FloatingChatPlacement.stepAside(placed, FloatingChatSide.RIGHT, controlsUp, rightButton, density)
        assertEquals(890f - 12f - 224f, aside.x, 0f)
        assertEquals(placed.y, aside.y, 0f)
        assertEquals(224, aside.width)
        assertEquals(240, aside.height)
    }

    @Test fun `on the left the card steps right of the button`() {
        val left = layout.copy(side = FloatingChatSide.LEFT)
        val placed = FloatingChatPlacement.place(left, 224, 240, rest, controlsUp, density)
        assertEquals(12f, placed.x, 0f)
        val aside = FloatingChatPlacement.stepAside(placed, FloatingChatSide.LEFT, controlsUp, leftButton, density)
        assertEquals(110f + 12f, aside.x, 0f)
        assertEquals(224, aside.width)
    }

    @Test fun `a card above or below the button, or with no button, stays at its edge`() {
        val placed = FloatingChatPlacement.place(layout, 224, 120, rest, controlsUp, density)
        // 68..188 ends above the button's 220.
        assertEquals(placed, FloatingChatPlacement.stepAside(placed, FloatingChatSide.RIGHT, controlsUp, rightButton, density))
        val below = FloatingChatBounds(890f, 330f, 950f, 390f)
        val tall = FloatingChatPlacement.place(layout, 224, 240, rest, controlsUp, density)
        assertEquals(tall, FloatingChatPlacement.stepAside(tall, FloatingChatSide.RIGHT, controlsUp, below, density))
        assertEquals(tall, FloatingChatPlacement.stepAside(tall, FloatingChatSide.RIGHT, controlsUp, null, density))
    }

    @Test fun `a wide card gives up width to clear the button, a narrow window keeps it`() {
        val wide = FloatingChatPlacement.place(layout, 900, 240, rest, controlsUp, density)
        val aside = FloatingChatPlacement.stepAside(wide, FloatingChatSide.RIGHT, controlsUp, rightButton, density)
        assertEquals(12f, aside.x, 0f)
        assertEquals(878 - 12, aside.width)

        val narrow = FloatingChatPlacement.bounds(300, 500, density, keepClearTop = 56, keepClearBottom = 96)
        val placed = FloatingChatPlacement.place(layout, 224, 240, rest, narrow, density)
        // Only 166px would be left, under the 170dp minimum card.
        val button = FloatingChatBounds(190f, 220f, 250f, 280f)
        assertEquals(placed, FloatingChatPlacement.stepAside(placed, FloatingChatSide.RIGHT, narrow, button, density))
    }

    private fun release(
        x: Float,
        y: Float = 12f,
        vx: Float = 0f,
        vy: Float = 0f,
        startSide: FloatingChatSide = FloatingChatSide.RIGHT,
        current: FloatingChatBounds = rest,
        stored: FloatingChatLayout = layout,
    ) = FloatingChatPlacement.release(
        x = x,
        y = y,
        velocityX = vx,
        velocityY = vy,
        widthPx = 224,
        heightPx = 240,
        startSide = startSide,
        rest = rest,
        current = current,
        density = density,
        stored = stored,
    )
}
