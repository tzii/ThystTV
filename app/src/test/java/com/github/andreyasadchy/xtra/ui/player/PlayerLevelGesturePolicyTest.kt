package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerLevelGesturePolicyTest {

    @Test
    fun `level follows the drag and stays in range`() {
        assertEquals(0.53f, PlayerLevelGesturePolicy.level(0.5f, 0.03f), 0.0001f)
        assertEquals(1f, PlayerLevelGesturePolicy.level(0.9f, 0.4f), 0f)
        assertEquals(0f, PlayerLevelGesturePolicy.level(0.1f, -0.3f), 0f)
    }

    @Test
    fun `the shown value changes one percent at a time`() {
        // A full-height drag covers the range, so every 1% of the player's
        // height is one percent, in both directions.
        val shown = (0..100).map { PlayerLevelGesturePolicy.percent(PlayerLevelGesturePolicy.level(0f, it / 100f)) }
        assertEquals((0..100).toList(), shown)
        assertEquals(47, PlayerLevelGesturePolicy.percent(0.4749f))
        assertEquals(48, PlayerLevelGesturePolicy.percent(0.4751f))
    }

    @Test
    fun `brightness keeps whole percents above its floor and turns Auto below it`() {
        assertNull(PlayerLevelGesturePolicy.brightnessPercent(0f))
        assertNull(PlayerLevelGesturePolicy.brightnessPercent(0.049f))
        assertEquals(5, PlayerLevelGesturePolicy.brightnessPercent(0.05f))
        assertEquals(6, PlayerLevelGesturePolicy.brightnessPercent(0.06f))
        assertEquals(37, PlayerLevelGesturePolicy.brightnessPercent(0.371f))
        assertEquals(100, PlayerLevelGesturePolicy.brightnessPercent(1f))
        // Every percent from the floor up is reachable.
        val reached = (5..100).map { PlayerLevelGesturePolicy.brightnessPercent(it / 100f) }
        assertEquals((5..100).toList(), reached)
    }

    @Test
    fun `volume follows the nearest device step and only 0 percent mutes`() {
        // 15 steps, the usual media stream.
        assertEquals(0, PlayerLevelGesturePolicy.volumeIndex(0f, 15))
        assertEquals(0, PlayerLevelGesturePolicy.volumeIndex(0.004f, 15))
        // A shown 1% is never silent.
        assertEquals(1, PlayerLevelGesturePolicy.volumeIndex(0.01f, 15))
        assertEquals(7, PlayerLevelGesturePolicy.volumeIndex(0.46f, 15))
        assertEquals(8, PlayerLevelGesturePolicy.volumeIndex(0.54f, 15))
        assertEquals(15, PlayerLevelGesturePolicy.volumeIndex(1f, 15))
        // Steps never go backwards as the level rises.
        val steps = (0..100).map { PlayerLevelGesturePolicy.volumeIndex(it / 100f, 25) }
        assertEquals(steps.sorted(), steps)
        assertEquals((0..25).toSet(), steps.toSet())
        // A stream without steps stays at 0.
        assertEquals(0, PlayerLevelGesturePolicy.volumeIndex(0.8f, 0))
    }

    @Test
    fun `a volume swipe continues where the last one left the slider`() {
        // The last swipe stopped at 47%, which set step 7 of 15 (46.7%).
        assertEquals(0.47f, PlayerLevelGesturePolicy.volumeStart(7, 15, lastLevel = 0.47f, lastIndex = 7), 0f)
        // The volume changed elsewhere since: start from the device step.
        assertEquals(8f / 15, PlayerLevelGesturePolicy.volumeStart(8, 15, lastLevel = 0.47f, lastIndex = 7), 0.0001f)
        // First swipe.
        assertEquals(3f / 15, PlayerLevelGesturePolicy.volumeStart(3, 15, lastLevel = null, lastIndex = -1), 0.0001f)
        assertEquals(0f, PlayerLevelGesturePolicy.volumeStart(3, 0, lastLevel = null, lastIndex = -1), 0f)
    }
}
