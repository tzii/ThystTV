package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScrubPolicyTest {

    private val lock = 24f
    private val detent = 10f

    @Test
    fun `a short sideways slide stays undecided instead of seeking`() {
        // Past the 8 dp touch slop, but well short of the lock: a tap that slid.
        assertEquals(PlayerScrubPolicy.Direction.UNDECIDED, PlayerScrubPolicy.classify(12f, 2f, lock))
        assertEquals(PlayerScrubPolicy.Direction.UNDECIDED, PlayerScrubPolicy.classify(-23.9f, 0f, lock))
        assertEquals(PlayerScrubPolicy.Direction.HORIZONTAL, PlayerScrubPolicy.classify(24f, 0f, lock))
        assertEquals(PlayerScrubPolicy.Direction.HORIZONTAL, PlayerScrubPolicy.classify(-40f, 10f, lock))
    }

    @Test
    fun `a diagonal drag never locks horizontal`() {
        // 40 px across and 25 px down: past the lock distance, outside the angle.
        assertEquals(PlayerScrubPolicy.Direction.UNDECIDED, PlayerScrubPolicy.classify(40f, 25f, lock))
        assertEquals(PlayerScrubPolicy.Direction.UNDECIDED, PlayerScrubPolicy.classify(200f, 101f, lock))
        assertEquals(PlayerScrubPolicy.Direction.HORIZONTAL, PlayerScrubPolicy.classify(200f, 100f, lock))
    }

    @Test
    fun `vertical wins as soon as it dominates, without extra travel`() {
        assertEquals(PlayerScrubPolicy.Direction.VERTICAL, PlayerScrubPolicy.classify(3f, 9f, lock))
        // A swipe that started sideways and turned vertical is brightness or volume.
        assertEquals(PlayerScrubPolicy.Direction.VERTICAL, PlayerScrubPolicy.classify(18f, 30f, lock))
    }

    @Test
    fun `the gesture locks at the far edge of the start detent`() {
        // Locked at x = 300 moving right: travel already sits on the detent edge,
        // so the next pixel changes the value.
        val anchor = PlayerScrubPolicy.anchorX(300f, 24f, detent)
        assertEquals(290f, anchor, 0f)
        assertEquals(0f, PlayerScrubPolicy.detentTravel(300f - anchor, detent), 0f)
        assertEquals(1f, PlayerScrubPolicy.detentTravel(301f - anchor, detent), 0f)
        // Moving left mirrors it.
        assertEquals(310f, PlayerScrubPolicy.anchorX(300f, -24f, detent), 0f)
    }

    @Test
    fun `dragging back into the detent band returns exactly to the start`() {
        for (travel in listOf(-10f, -4f, 0f, 6f, 10f)) {
            assertEquals(0f, PlayerScrubPolicy.detentTravel(travel, detent), 0f)
        }
        // Outside the band travel stays continuous with no jump at the edge.
        assertEquals(0.5f, PlayerScrubPolicy.detentTravel(10.5f, detent), 1e-4f)
        assertEquals(-30f, PlayerScrubPolicy.detentTravel(-40f, detent), 0f)
    }

    @Test
    fun `seek track centers the start and follows the finger`() {
        assertEquals(0.5f, PlayerScrubPolicy.relativeFraction(0f, 1000), 0f)
        assertEquals(0.75f, PlayerScrubPolicy.relativeFraction(250f, 1000), 0f)
        assertEquals(0.4f, PlayerScrubPolicy.relativeFraction(-100f, 1000), 1e-6f)
        // Half the player width reaches either end and stays there.
        assertEquals(1f, PlayerScrubPolicy.relativeFraction(500f, 1000), 0f)
        assertEquals(0f, PlayerScrubPolicy.relativeFraction(-2000f, 1000), 0f)
        assertEquals(0.5f, PlayerScrubPolicy.relativeFraction(300f, 0), 0f)
    }

    @Test
    fun `a short seek on a long VoD still separates the handle from the start`() {
        // 2,400 px landscape player: a 30 px drag past the detent is a couple
        // of minutes on a six-hour VoD, and moves the handle 1.25% of the track.
        val fraction = PlayerScrubPolicy.relativeFraction(30f, 2400)
        assertEquals(0.5125f, fraction, 1e-6f)
    }

    @Test
    fun `the handle stops where the seek reached the end of the video`() {
        // Moving freely: no pin.
        assertNull(PlayerScrubPolicy.pinFraction(null, 0.6f, atLimit = false))
        // Reaching the end pins the handle there; pushing on keeps it.
        val pinned = PlayerScrubPolicy.pinFraction(null, 0.7f, atLimit = true)
        assertEquals(0.7f, pinned)
        assertEquals(0.7f, PlayerScrubPolicy.pinFraction(pinned, 0.95f, atLimit = true))
        // Coming back off the end releases it.
        assertNull(PlayerScrubPolicy.pinFraction(pinned, 0.69f, atLimit = false))
    }

    @Test
    fun `speed fraction is logarithmic with 1x in the middle`() {
        assertEquals(0f, PlayerScrubPolicy.speedFraction(0.25f), 0f)
        assertEquals(0.25f, PlayerScrubPolicy.speedFraction(0.5f), 1e-6f)
        assertEquals(0.5f, PlayerScrubPolicy.speedFraction(1f), 1e-6f)
        assertEquals(0.75f, PlayerScrubPolicy.speedFraction(2f), 1e-6f)
        assertEquals(1f, PlayerScrubPolicy.speedFraction(4f), 1e-6f)
        assertEquals(1f, PlayerScrubPolicy.speedFraction(8f), 0f)
        assertEquals(0f, PlayerScrubPolicy.speedFraction(0.1f), 0f)
    }

    @Test
    fun `signed change reads as a delta`() {
        val format = { ms: Long ->
            val seconds = ms / 1000
            if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
            else "%d:%02d".format(seconds / 60, seconds % 60)
        }
        assertEquals("+1:25", PlayerScrubPolicy.signedDuration(85_000L, format))
        assertEquals("−0:30", PlayerScrubPolicy.signedDuration(-30_000L, format))
        assertEquals("+1:00:00", PlayerScrubPolicy.signedDuration(3_600_000L, format))
        // Under a second shows no sign, so the start reads as no change.
        assertEquals("0:00", PlayerScrubPolicy.signedDuration(0L, format))
        assertEquals("0:00", PlayerScrubPolicy.signedDuration(-999L, format))
    }

    private val sixHours = 6 * 3_600_000L
    private val landscapeDp = 800f

    @Test
    fun `seek starts fine and accelerates the same way on every long video`() {
        // 0.3 s per dp at first, then the cubic term takes over.
        assertEquals(6_080L, PlayerScrubPolicy.seekOffsetMs(20f, landscapeDp, sixHours))
        assertEquals(40_000L, PlayerScrubPolicy.seekOffsetMs(100f, landscapeDp, sixHours))
        assertEquals(140_000L, PlayerScrubPolicy.seekOffsetMs(200f, landscapeDp, sixHours))
        assertEquals(360_000L, PlayerScrubPolicy.seekOffsetMs(300f, landscapeDp, sixHours))
        assertEquals(3_640_000L, PlayerScrubPolicy.seekOffsetMs(700f, landscapeDp, sixHours))
        // A two-hour VoD gets the same short seeks as a six-hour one.
        assertEquals(40_000L, PlayerScrubPolicy.seekOffsetMs(100f, landscapeDp, 2 * 3_600_000L))
        // Each further 50 dp covers more time than the last.
        var previousGain = 0L
        for (travel in 50..700 step 50) {
            val gain = PlayerScrubPolicy.seekOffsetMs(travel.toFloat(), landscapeDp, sixHours) -
                PlayerScrubPolicy.seekOffsetMs(travel - 50f, landscapeDp, sixHours)
            assertTrue("gain at $travel dp", gain > previousGain)
            previousGain = gain
        }
    }

    @Test
    fun `a short video spreads over the full width instead`() {
        val minute = 60_000L
        assertEquals(7_500L, PlayerScrubPolicy.seekOffsetMs(100f, landscapeDp, minute))
        assertEquals(minute, PlayerScrubPolicy.seekOffsetMs(landscapeDp, landscapeDp, minute))
        // Without a measured width only the curve applies.
        assertEquals(40_000L, PlayerScrubPolicy.seekOffsetMs(100f, 0f, minute))
    }

    @Test
    fun `seek offset is symmetric and scales with sensitivity`() {
        assertEquals(-40_000L, PlayerScrubPolicy.seekOffsetMs(-100f, landscapeDp, sixHours))
        assertEquals(60_000L, PlayerScrubPolicy.seekOffsetMs(100f, landscapeDp, sixHours, sensitivity = 1.5f))
        assertEquals(20_000L, PlayerScrubPolicy.seekOffsetMs(100f, landscapeDp, sixHours, sensitivity = 0.5f))
        assertEquals(0L, PlayerScrubPolicy.seekOffsetMs(0f, landscapeDp, sixHours))
        assertEquals(0L, PlayerScrubPolicy.seekOffsetMs(100f, landscapeDp, 0L))
    }

    @Test
    fun `seek tick steps follow the pace so ticks stay evenly spaced`() {
        // About one tick per 32 dp, rounded up to a readable step.
        assertEquals(10_000L, PlayerScrubPolicy.seekTickStepMs(0f, landscapeDp, sixHours))
        assertEquals(120_000L, PlayerScrubPolicy.seekTickStepMs(300f, landscapeDp, sixHours))
        assertEquals(600_000L, PlayerScrubPolicy.seekTickStepMs(600f, landscapeDp, sixHours))
        assertEquals(3_600_000L, PlayerScrubPolicy.seekTickStepMs(5_000f, 10_000f, 1_000 * sixHours))
        // A one-minute video ticks every 5 s; nothing to seek ticks every second.
        assertEquals(5_000L, PlayerScrubPolicy.seekTickStepMs(0f, landscapeDp, 60_000L))
        assertEquals(1_000L, PlayerScrubPolicy.seekTickStepMs(0f, landscapeDp, 0L))
        // Travel in either direction gives the same step.
        assertEquals(
            PlayerScrubPolicy.seekTickStepMs(300f, landscapeDp, sixHours),
            PlayerScrubPolicy.seekTickStepMs(-300f, landscapeDp, sixHours),
        )
    }

    @Test
    fun `a growing step keeps counting from the start`() {
        val tracker = PlayerScrubHapticTracker(start = 100_000L, min = 0L, max = sixHours, step = 10_000L, origin = 100_000L, startDetent = true)
        assertEquals(PlayerGestureHaptic.STEP, tracker.update(110_000L))
        assertNull(tracker.update(150_000L, step = 60_000L))
        // +60 s from the start, not a multiple of 60 s on the clock.
        assertEquals(PlayerGestureHaptic.STEP, tracker.update(160_000L, step = 60_000L))
        assertNull(tracker.update(200_000L, step = 120_000L))
        assertEquals(PlayerGestureHaptic.STEP, tracker.update(220_000L, step = 120_000L))
    }

    @Test
    fun `seek ticks count intervals from the start in both directions`() {
        val tracker = PlayerScrubHapticTracker(start = 100_000L, min = 0L, max = 600_000L, step = 30_000L, origin = 100_000L, startDetent = true)
        assertNull(tracker.update(129_000L))
        assertEquals(PlayerGestureHaptic.STEP, tracker.update(130_000L))
        assertNull(tracker.update(150_000L))
        assertEquals(PlayerGestureHaptic.STEP, tracker.update(161_000L))
        // Moving back ticks on landing on +30 s, not one step later.
        assertEquals(PlayerGestureHaptic.STEP, tracker.update(130_000L))
        assertNull(tracker.update(120_000L))
        // Before the start the steps count backwards from it.
        assertEquals(PlayerGestureHaptic.DETENT, tracker.update(70_000L))
        assertNull(tracker.update(70_000L))
        assertNull(tracker.update(69_000L))
        assertEquals(PlayerGestureHaptic.STEP, tracker.update(40_000L))
    }

    @Test
    fun `returning to or passing over the start is a detent`() {
        val tracker = PlayerScrubHapticTracker(start = 100_000L, min = 0L, max = 600_000L, step = 30_000L, origin = 100_000L, startDetent = true)
        // Leaving the start is silent; that moment already had the start tick.
        assertNull(tracker.update(110_000L))
        assertEquals(PlayerGestureHaptic.DETENT, tracker.update(100_000L))
        assertNull(tracker.update(95_000L))
        // A fast drag that jumps over the start still lands the detent.
        assertEquals(PlayerGestureHaptic.DETENT, tracker.update(112_000L))
    }

    @Test
    fun `reaching either end of the video is a limit, once per arrival`() {
        val tracker = PlayerScrubHapticTracker(start = 50_000L, min = 0L, max = 60_000L, step = 5_000L, origin = 50_000L, startDetent = true)
        assertEquals(PlayerGestureHaptic.LIMIT, tracker.update(60_000L))
        // Pushing on past the end changes nothing.
        assertNull(tracker.update(60_000L))
        assertEquals(PlayerGestureHaptic.STEP, tracker.update(55_000L))
        assertEquals(PlayerGestureHaptic.LIMIT, tracker.update(0L))
    }

    @Test
    fun `speed ticks on every quarter and holds its start`() {
        val tracker = PlayerScrubHapticTracker(start = 110L, min = 25L, max = 400L, step = 25L, startDetent = true)
        assertNull(tracker.update(120L))
        assertEquals(PlayerGestureHaptic.STEP, tracker.update(125L))
        assertEquals(PlayerGestureHaptic.DETENT, tracker.update(110L))
        assertEquals(PlayerGestureHaptic.STEP, tracker.update(100L))
        assertNull(tracker.update(95L))
        assertEquals(PlayerGestureHaptic.LIMIT, tracker.update(25L))
    }

    @Test
    fun `volume ticks every step and brightness every ten percent`() {
        val volume = PlayerScrubHapticTracker(start = 7L, min = 0L, max = 15L, step = 1L)
        assertEquals(PlayerGestureHaptic.STEP, volume.update(8L))
        assertEquals(PlayerGestureHaptic.STEP, volume.update(7L))
        assertEquals(PlayerGestureHaptic.LIMIT, volume.update(0L))
        val brightness = PlayerScrubHapticTracker(start = 45L, min = 0L, max = 100L, step = 10L)
        assertNull(brightness.update(48L))
        assertEquals(PlayerGestureHaptic.STEP, brightness.update(50L))
        // Without a start detent, coming back to 45% is silent.
        assertNull(brightness.update(45L))
        assertEquals(PlayerGestureHaptic.LIMIT, brightness.update(100L))
    }
}
