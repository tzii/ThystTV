package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sign

/**
 * Pure rules for the horizontal scrub gestures, seek and playback speed: when
 * a sideways drag may claim the gesture, the detent that holds the starting
 * value, how far a seek goes, the slider positions, and the haptic steps.
 */
object PlayerScrubPolicy {

    /**
     * Sideways travel from touch-down before a drag can become seek or speed.
     * A tap that slides a little, or the first finger of a pinch, stays below it.
     */
    const val HORIZONTAL_LOCK_DP = 24f

    /** The drag must stay within about 27 degrees of horizontal: |dx| >= 2 |dy|. */
    const val HORIZONTAL_LOCK_RATIO = 2f

    /**
     * Width of the band at the starting point that holds the start value. The
     * gesture locks at the band's far edge, so changes begin right away, and
     * dragging back into the band returns exactly to the start.
     */
    const val START_DETENT_DP = 10f

    /** Step ticks closer together than this are dropped, so a fast drag does not buzz. */
    const val STEP_TICK_MIN_INTERVAL_MS = 30L

    const val SPEED_MIN = 0.25f
    const val SPEED_MAX = 4f

    /** Speed ticks on every quarter, 1.00x included. */
    const val SPEED_TICK_HUNDREDTHS = 25L

    /** Brightness ticks every 10%. */
    const val BRIGHTNESS_TICK_PERCENT = 10L

    /** Seek time per dp of finger travel at the start of a swipe. */
    const val SEEK_FINE_MS_PER_DP = 300f

    /** Time the curve adds at [SEEK_CURVE_UNIT_DP] of travel, growing with the cube of the travel. */
    const val SEEK_CURVE_MS = 10_000f
    const val SEEK_CURVE_UNIT_DP = 100f

    /** Finger travel between seek ticks, at whatever pace the swipe has reached. */
    const val SEEK_TICK_TRAVEL_DP = 32f

    private val SEEK_TICK_STEPS_MS = longArrayOf(
        1_000, 2_000, 5_000, 10_000, 15_000, 30_000,
        60_000, 120_000, 300_000, 600_000, 900_000, 1_800_000, 3_600_000,
    )

    private const val MINUS = '−'

    enum class Direction { UNDECIDED, VERTICAL, HORIZONTAL }

    /**
     * Classifies a drag of [dx], [dy] pixels from touch-down. Vertical wins as
     * soon as it dominates, as before. Horizontal needs [lockPx] of travel
     * within the lock angle; until then the drag stays undecided, so a
     * diagonal swipe or a wobbling tap never seeks, and a vertical swipe that
     * starts sideways still becomes brightness or volume.
     */
    fun classify(dx: Float, dy: Float, lockPx: Float): Direction {
        val ax = abs(dx)
        val ay = abs(dy)
        return when {
            ay > ax -> Direction.VERTICAL
            ax >= lockPx && ax >= HORIZONTAL_LOCK_RATIO * ay -> Direction.HORIZONTAL
            else -> Direction.UNDECIDED
        }
    }

    /**
     * Where travel is measured from for a gesture that locked at [lockX] after
     * moving [dx] from touch-down: the near edge of the start detent, so the
     * first movement past the lock already changes the value.
     */
    fun anchorX(lockX: Float, dx: Float, detentPx: Float): Float = lockX - sign(dx) * detentPx

    /** Travel past the start detent: zero inside the band, continuous outside it. */
    fun detentTravel(travel: Float, detentPx: Float): Float {
        if (abs(travel) <= detentPx) return 0f
        return travel - sign(travel) * detentPx
    }

    /**
     * How far a seek goes, in milliseconds, for [travelDp] of finger travel
     * past the start detent. The same travel gives the same time on every
     * video: fine at first (0.3 s per dp, so 20 dp is about 6 s), then
     * accelerating to about an hour across a landscape phone. A drag across
     * the whole [surfaceWidthDp] never covers more than the whole video, so a
     * short video still uses the full width. [sensitivity] scales the result.
     */
    fun seekOffsetMs(travelDp: Float, surfaceWidthDp: Float, durationMs: Long, sensitivity: Float = 1f): Long {
        val distance = abs(travelDp)
        if (distance == 0f || durationMs <= 0L) return 0L
        val unit = distance / SEEK_CURVE_UNIT_DP
        val curve = SEEK_FINE_MS_PER_DP * distance + SEEK_CURVE_MS * unit * unit * unit
        val wholeVideo = if (surfaceWidthDp > 0f) durationMs * distance / surfaceWidthDp else curve
        return (sign(travelDp) * sensitivity * min(curve, wholeVideo)).toLong()
    }

    /**
     * Haptic step for a seek at [travelDp]: the round time the swipe covers
     * in about [SEEK_TICK_TRAVEL_DP] of travel at its current pace, so ticks
     * stay evenly spaced under the finger while the seek speeds up.
     */
    fun seekTickStepMs(travelDp: Float, surfaceWidthDp: Float, durationMs: Long, sensitivity: Float = 1f): Long {
        val distance = abs(travelDp)
        val pace = seekOffsetMs(distance + SEEK_TICK_TRAVEL_DP, surfaceWidthDp, durationMs, sensitivity) -
            seekOffsetMs(distance, surfaceWidthDp, durationMs, sensitivity)
        return SEEK_TICK_STEPS_MS.firstOrNull { it >= pace } ?: SEEK_TICK_STEPS_MS.last()
    }

    /**
     * Spoiler-free seek track: the start sits in the middle and the handle
     * follows the finger, reaching either end after half the player width.
     * The track never reveals where the video is or how long it runs.
     */
    fun relativeFraction(travelPx: Float, surfaceWidthPx: Int): Float {
        if (surfaceWidthPx <= 0) return 0.5f
        return (0.5f + travelPx / surfaceWidthPx).coerceIn(0f, 1f)
    }

    /**
     * Holds the seek handle where the value reached an end of the video: the
     * first [fraction] seen [atLimit] stays [pinned] while the finger pushes
     * on, and the handle follows the finger again once the value moves. The
     * finger has to come back to the pinned spot before the value changes, so
     * releasing the pin never makes the handle jump.
     */
    fun pinFraction(pinned: Float?, fraction: Float, atLimit: Boolean): Float? {
        return if (atLimit) pinned ?: fraction else null
    }

    /**
     * Speed slider position on a log scale, so 1x sits in the middle of
     * 0.25x to 4x and halving or doubling moves the handle the same distance.
     */
    fun speedFraction(speed: Float): Float {
        val min = ln(SPEED_MIN)
        val max = ln(SPEED_MAX)
        return ((ln(speed.coerceIn(SPEED_MIN, SPEED_MAX)) - min) / (max - min)).coerceIn(0f, 1f)
    }

    /** Signed change for the seek value: `+1:25`, `−0:30`, or `0:00` with no sign. */
    fun signedDuration(deltaMs: Long, format: (Long) -> String): String {
        val magnitude = format(abs(deltaMs))
        return when {
            deltaMs >= 1_000L -> "+$magnitude"
            deltaMs <= -1_000L -> "$MINUS$magnitude"
            else -> magnitude
        }
    }

}

/** One haptic vocabulary for every player gesture. */
enum class PlayerGestureHaptic {
    /** A swipe claimed its gesture. */
    START,

    /** The value crossed a step: a volume level, 10% brightness, a seek interval, a quarter speed. */
    STEP,

    /** The value landed on a detent: back at the start of a scrub, or a pinch snapping to Fit or Fill. */
    DETENT,

    /** The value reached the end of its range. */
    LIMIT,
}

/**
 * Decides the haptic for each new value of one gesture. Values are integers:
 * milliseconds, percent, volume steps or hundredths of speed.
 *
 * Steps are multiples of [step] counted from [origin], ticked when a value
 * lands on or passes one; an update may pass its own step when the step
 * follows the gesture's pace. With [startDetent], landing on or passing back
 * over [start] is a detent rather than a step. Entering [min] or [max] is a
 * limit, once per arrival.
 */
class PlayerScrubHapticTracker(
    private val start: Long,
    private val min: Long,
    private val max: Long,
    private val step: Long,
    private val origin: Long = 0L,
    private val startDetent: Boolean = false,
) {
    private var previous = start
    private var atLimit = start <= min || start >= max

    fun update(value: Long, step: Long = this.step): PlayerGestureHaptic? {
        val prev = previous
        if (value == prev) return null
        previous = value
        val limit = value <= min || value >= max
        val enteredLimit = limit && !atLimit
        atLimit = limit
        if (enteredLimit) return PlayerGestureHaptic.LIMIT
        if (startDetent && prev != start && (value - start).sign != (prev - start).sign) {
            return PlayerGestureHaptic.DETENT
        }
        if (step > 0L && crossesStep(prev, value, step)) return PlayerGestureHaptic.STEP
        return null
    }

    private fun crossesStep(prev: Long, value: Long, step: Long): Boolean {
        return if (value > prev) {
            Math.floorDiv(value - origin, step) > Math.floorDiv(prev - origin, step)
        } else {
            ceilDiv(value - origin, step) < ceilDiv(prev - origin, step)
        }
    }

    private fun ceilDiv(value: Long, step: Long): Long = -Math.floorDiv(-value, step)
}
