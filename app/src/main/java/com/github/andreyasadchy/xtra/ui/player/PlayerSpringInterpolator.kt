package com.github.andreyasadchy.xtra.ui.player

import android.animation.TimeInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Damped-spring easing for one-shot view animations, after the Material 3
 * Expressive motion springs.
 *
 * It maps the animation fraction to the position of a unit-mass spring
 * released at 0 toward 1, sampled over [durationMs]. Under-damped springs
 * overshoot slightly and settle; the curve is pinned to exactly 0 and 1 at the
 * ends so the animator always lands on its target.
 */
class PlayerSpringInterpolator(
    private val dampingRatio: Float = FAST_SPATIAL_DAMPING,
    stiffness: Float = FAST_SPATIAL_STIFFNESS,
    private val durationMs: Long = ENTRANCE_MS,
) : TimeInterpolator {

    private val omega = sqrt(stiffness.coerceAtLeast(1f))

    override fun getInterpolation(input: Float): Float {
        if (input <= 0f) return 0f
        if (input >= 1f) return 1f
        val t = input * durationMs / 1000f
        return if (dampingRatio < 1f) {
            val damped = omega * sqrt(1f - dampingRatio * dampingRatio)
            val decay = exp(-dampingRatio * omega * t)
            1f - decay * (cos(damped * t) + (dampingRatio * omega / damped) * sin(damped * t))
        } else {
            1f - exp(-omega * t) * (1f + omega * t)
        }
    }

    companion object {
        /** Material 3 Expressive "fast spatial" spring. */
        const val FAST_SPATIAL_DAMPING = 0.6f
        const val FAST_SPATIAL_STIFFNESS = 800f

        /** Long enough for the fast spatial spring to settle within 1%. */
        const val ENTRANCE_MS = 350L
    }
}
