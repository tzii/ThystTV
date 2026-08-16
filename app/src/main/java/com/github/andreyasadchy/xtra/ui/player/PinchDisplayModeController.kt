package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.abs

/**
 * Deterministic pinch state machine for Fit/Fill display-mode control.
 * Independent of Android views: callers feed cumulative scale and render the
 * emitted events. Arbitrary zoom values are never produced; only Fit and Fill
 * can be committed.
 *
 * Reversal hysteresis keeps an armed target from flickering at the arm
 * threshold: after arming, the armed state persists until scale retreats by
 * [reversalHysteresis] beyond the corresponding arm threshold.
 */
class PinchDisplayModeController(
    private val outwardArmThreshold: Float = OUTWARD_ARM_THRESHOLD,
    private val inwardArmThreshold: Float = INWARD_ARM_THRESHOLD,
    private val reversalHysteresis: Float = REVERSAL_HYSTERESIS,
) {

    companion object {
        const val OUTWARD_ARM_THRESHOLD = 1.08f
        const val INWARD_ARM_THRESHOLD = 0.92f
        const val REVERSAL_HYSTERESIS = 0.04f
        private const val DIRECTION_EPSILON = 0.0005f
        private const val COMPARISON_EPSILON = 0.0001f
    }

    sealed class Event {
        /** Continuous preview toward [toward]; [progress] is 0..1, 1 when armed. */
        data class Preview(val from: PlayerDisplayMode, val toward: PlayerDisplayMode, val progress: Float) : Event()

        /** The current direction has no display-mode target (for example inward from Fit). */
        data class NoPreview(val from: PlayerDisplayMode) : Event()

        /** [target] became armed; a haptic tick may accompany a change of armed target. */
        data class Armed(val target: PlayerDisplayMode) : Event()

        /** The armed target was released back to neutral preview. */
        data class Disarmed(val toward: PlayerDisplayMode?) : Event()

        /** Sequence released while armed: commit [mode]. */
        data class Commit(val mode: PlayerDisplayMode) : Event()

        /** Sequence released while neutral: keep the committed mode, drop preview transforms. */
        data class Restore(val mode: PlayerDisplayMode) : Event()

        /** Sequence cancelled: restore [mode] and drop preview transforms. */
        data class Cancelled(val mode: PlayerDisplayMode) : Event()
    }

    private enum class Phase { NEUTRAL, ARM_FILL, ARM_FIT }

    var committedMode: PlayerDisplayMode = PlayerDisplayMode.FIT
        private set

    private var phase = Phase.NEUTRAL
    private var armedTarget: PlayerDisplayMode? = null

    /**
     * Must be called when a pinch sequence is recognized, before [update].
     */
    fun begin(committedMode: PlayerDisplayMode) {
        this.committedMode = committedMode
        phase = Phase.NEUTRAL
        armedTarget = null
    }

    fun update(cumulativeScale: Float): List<Event> {
        val events = mutableListOf<Event>()
        if (phase != Phase.NEUTRAL) {
            val armed = armedTarget
            if (armed != null) {
                val disarmed = if (armed == PlayerDisplayMode.FILL) {
                    cumulativeScale <= outwardArmThreshold - reversalHysteresis + COMPARISON_EPSILON
                } else {
                    cumulativeScale >= inwardArmThreshold + reversalHysteresis - COMPARISON_EPSILON
                }
                if (disarmed) {
                    phase = Phase.NEUTRAL
                    armedTarget = null
                    events += Event.Disarmed(targetFor(cumulativeScale))
                }
            }
        }
        if (phase == Phase.NEUTRAL) {
            val target = targetFor(cumulativeScale)
            if (target != null) {
                val armed = when (target) {
                    PlayerDisplayMode.FILL -> cumulativeScale >= outwardArmThreshold
                    PlayerDisplayMode.FIT -> cumulativeScale <= inwardArmThreshold
                    else -> false
                }
                if (armed) {
                    phase = if (target == PlayerDisplayMode.FILL) Phase.ARM_FILL else Phase.ARM_FIT
                    armedTarget = target
                    events += Event.Armed(target)
                    events += Event.Preview(committedMode, target, 1f)
                } else {
                    events += Event.Preview(committedMode, target, progressToward(target, cumulativeScale))
                }
            } else {
                events += Event.NoPreview(committedMode)
            }
        } else {
            val target = armedTarget
            if (target != null) {
                events += Event.Preview(committedMode, target, 1f)
            }
        }
        return events
    }

    /**
     * Final release of the pinch. Releasing in a neutral state preserves the
     * committed mode; releasing an armed state commits Fit or Fill.
     */
    fun release(): Event {
        val target = armedTarget
        return if (target != null) {
            val event = Event.Commit(target)
            committedMode = target
            phase = Phase.NEUTRAL
            armedTarget = null
            event
        } else {
            val event = Event.Restore(committedMode)
            phase = Phase.NEUTRAL
            armedTarget = null
            event
        }
    }

    /**
     * Cancelled sequence: restore the last committed mode and remove transient
     * preview transforms.
     */
    fun cancel(): Event {
        val event = Event.Cancelled(committedMode)
        phase = Phase.NEUTRAL
        armedTarget = null
        return event
    }

    /**
     * The meaningful target for the current direction from the committed mode,
     * or null when the direction cannot change anything (inward from Fit,
     * outward from Fill). Stretch targets Fill outward and Fit inward; Stretch
     * is not treated as lying on the Fit-to-Fill zoom continuum.
     */
    private fun targetFor(cumulativeScale: Float): PlayerDisplayMode? {
        if (abs(cumulativeScale - 1f) < DIRECTION_EPSILON) {
            return null
        }
        val outward = cumulativeScale > 1f
        return when (committedMode) {
            PlayerDisplayMode.FIT -> if (outward) PlayerDisplayMode.FILL else null
            PlayerDisplayMode.FILL -> if (outward) null else PlayerDisplayMode.FIT
            PlayerDisplayMode.STRETCH -> if (outward) PlayerDisplayMode.FILL else PlayerDisplayMode.FIT
        }
    }

    private fun progressToward(target: PlayerDisplayMode, cumulativeScale: Float): Float {
        return when (target) {
            PlayerDisplayMode.FILL -> ((cumulativeScale - 1f) / (outwardArmThreshold - 1f)).coerceIn(0f, 1f)
            else -> ((1f - cumulativeScale) / (1f - inwardArmThreshold)).coerceIn(0f, 1f)
        }
    }
}
