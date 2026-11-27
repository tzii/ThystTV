package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** A 1000x500 px landscape player at density 1, so dp and px match. */
class FloatingChatPlacementTest {
    private val density = 1f
    private val rest = FloatingChatPlacement.bounds(1000, 500, density)
    private val layout = FloatingChatPlacement.defaultLayout(tablet = false)

    @Test fun `bounds keep a margin and clear the cutout and system bars`() {
        val bounds = FloatingChatPlacement.bounds(1000, 500, density, safeLeft = 40, safeRight = 20, keepClearTop = 56, keepClearBottom = 96)
        assertEquals(52f, bounds.left, 0f)
        assertEquals(68f, bounds.top, 0f)
        assertEquals(968f, bounds.right, 0f)
        assertEquals(392f, bounds.bottom, 0f)
    }

    @Test fun `a new card rests in the top right corner`() {
        val placed = FloatingChatPlacement.place(layout, 224, 240, rest, rest, density)
        assertEquals(988f - 224f, placed.x, 0f)
        assertEquals(12f, placed.y, 0f)
        assertEquals(224, placed.width)
        assertEquals(240, placed.height)
        assertEquals(FloatingChatSide.RIGHT, layout.side)
    }

    @Test fun `a card keeps its share of the screen in both directions`() {
        val middle = layout.copy(xFraction = 0.5f, yFraction = 0.5f)
        val placed = FloatingChatPlacement.place(middle, 224, 240, rest, rest, density)
        // Free travel is 976 - 224 = 752 across and 476 - 240 = 236 down.
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

    @Test fun `toLayout keeps the exact dropped spot with no edge magnet`() {
        // Dropped 4 px from the left edge and 5 px from the top: stays at that exact spot.
        val nearEdge = FloatingChatPlacement.toLayout(16f, 17f, 224, 240, rest, rest, density, layout)
        assertEquals(4f / 752f, nearEdge.xFraction, 0.0001f)
        assertEquals(5f / 236f, nearEdge.yFraction, 0.0001f)
        val placed = FloatingChatPlacement.place(nearEdge, 224, 240, rest, rest, density)
        assertEquals(16f, placed.x, 0.01f)
        assertEquals(17f, placed.y, 0.01f)
    }

    @Test fun `toLayout clamps coordinates on all four sides`() {
        val topLeft = FloatingChatPlacement.toLayout(-100f, -50f, 224, 240, rest, rest, density, layout)
        assertEquals(0f, topLeft.xFraction, 0f)
        assertEquals(0f, topLeft.yFraction, 0f)

        val bottomRight = FloatingChatPlacement.toLayout(1500f, 900f, 224, 240, rest, rest, density, layout)
        assertEquals(1f, bottomRight.xFraction, 0f)
        assertEquals(1f, bottomRight.yFraction, 0f)
    }

    @Test fun `rotation or resize re-places from saved fractions and clamps to the new bounds`() {
        val saved = FloatingChatPlacement.toLayout(600f, 200f, 224, 240, rest, rest, density, layout)
        val smaller = FloatingChatPlacement.bounds(500, 320, density)
        val placed = FloatingChatPlacement.place(saved, 224, 240, smaller, smaller, density)
        assertEquals(true, placed.x >= smaller.left && placed.x + placed.width <= smaller.right)
        assertEquals(true, placed.y >= smaller.top && placed.y + placed.height <= smaller.bottom)
    }

    @Test fun `size stays between the minimum and the screen share`() {
        assertEquals(170 to 140, FloatingChatPlacement.clampSize(10f, 10f, 1000, 500, density))
        assertEquals(550 to 476, FloatingChatPlacement.clampSize(900f, 900f, 1000, 500, density))
    }

    @Test fun `the grip grows the card toward the video on either side`() {
        assertEquals(274 to 290, FloatingChatPlacement.resize(224, 240, -50f, 50f, FloatingChatSide.RIGHT, 1000, 500, density))
        assertEquals(274 to 290, FloatingChatPlacement.resize(224, 240, 50f, 50f, FloatingChatSide.LEFT, 1000, 500, density))
    }
}
