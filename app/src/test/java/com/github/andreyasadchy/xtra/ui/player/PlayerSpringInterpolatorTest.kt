package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSpringInterpolatorTest {

    @Test
    fun `curve is pinned at both ends`() {
        val spring = PlayerSpringInterpolator()
        assertEquals(0f, spring.getInterpolation(0f), 0f)
        assertEquals(0f, spring.getInterpolation(-1f), 0f)
        assertEquals(1f, spring.getInterpolation(1f), 0f)
        assertEquals(1f, spring.getInterpolation(2f), 0f)
    }

    @Test
    fun `fast spatial spring overshoots a little and settles`() {
        val spring = PlayerSpringInterpolator()
        val samples = (1..99).map { spring.getInterpolation(it / 100f) }
        val peak = samples.max()
        // An expressive bounce, not a wobble: about 9% for damping 0.6.
        assertTrue("overshoots: $peak", peak > 1.05f)
        assertTrue("stays subtle: $peak", peak < 1.12f)
        assertEquals("settled before the end", 1f, samples.last(), 0.01f)
    }

    @Test
    fun `critically damped spring never overshoots`() {
        val spring = PlayerSpringInterpolator(dampingRatio = 1f, stiffness = 800f, durationMs = 400L)
        var previous = 0f
        for (step in 1..100) {
            val value = spring.getInterpolation(step / 100f)
            assertTrue(value <= 1f)
            assertTrue(value >= previous)
            previous = value
        }
    }
}
