package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.roundToInt

/**
 * Pure rules for the vertical level gestures, brightness and device volume.
 *
 * Both follow the finger continuously: a drag over the full player height
 * covers the whole range, the slider handle moves with the finger and the
 * shown value changes one percent at a time. Device volume has only a few
 * steps (often 15 or 25), so the device follows at its own resolution while
 * the shown level stays smooth, as the system volume slider does.
 */
object PlayerLevelGesturePolicy {

    /** A brightness level below this gives the screen back to the system (Auto). */
    const val BRIGHTNESS_AUTO_BELOW = 0.05f

    /** The dimmest brightness a swipe sets before Auto. */
    const val BRIGHTNESS_MIN_PERCENT = 5

    /** Level, 0-1, after a drag of [travelFraction] of the player height up from [start]. */
    fun level(start: Float, travelFraction: Float): Float = (start + travelFraction).coerceIn(0f, 1f)

    /** Whole percent shown for [level]. */
    fun percent(level: Float): Int = (level.coerceIn(0f, 1f) * 100).roundToInt()

    /** Brightness percent the window gets for [level], or null for Auto. */
    fun brightnessPercent(level: Float): Int? {
        if (level < BRIGHTNESS_AUTO_BELOW) return null
        return percent(level).coerceAtLeast(BRIGHTNESS_MIN_PERCENT)
    }

    /**
     * Device volume step for [level] on a stream of [maxIndex] steps: the
     * nearest step, except that only 0% mutes, so a shown 1% is never silent.
     */
    fun volumeIndex(level: Float, maxIndex: Int): Int {
        if (maxIndex <= 0 || percent(level) == 0) return 0
        return (level * maxIndex).roundToInt().coerceIn(1, maxIndex)
    }

    /**
     * Where a volume swipe starts. Right after a swipe, the device is still
     * on the step that swipe set, and the next swipe continues from exactly
     * where the last one left the slider. Once the volume changed elsewhere
     * (buttons, another app, a refused change) it starts from the device step.
     */
    fun volumeStart(index: Int, maxIndex: Int, lastLevel: Float?, lastIndex: Int): Float {
        if (maxIndex <= 0) return 0f
        if (lastLevel != null && index == lastIndex) return lastLevel.coerceIn(0f, 1f)
        return (index.toFloat() / maxIndex).coerceIn(0f, 1f)
    }
}
