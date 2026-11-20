package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How gesture feedback looks, chosen in settings. Both styles share the
 * gestures, haptics and timing.
 */
enum class PlayerGestureFeedbackStyle(val prefValue: String) {

    /**
     * Material 3 Expressive sliders: seek, speed and pinch lie flat at the top
     * of the video; brightness and volume stand at the finger's edge.
     */
    SLIDERS("sliders"),

    /** One clean number in the center of the video for every gesture. */
    NUMBER("number");

    companion object {
        val DEFAULT = SLIDERS

        fun fromPref(value: String?): PlayerGestureFeedbackStyle {
            return entries.firstOrNull { it.prefValue == value } ?: DEFAULT
        }
    }
}

/**
 * Pure presentation rules for the shared gesture-feedback overlay.
 *
 * A presentation fully describes the overlay for one gesture update. Applying
 * it resets every indicator, so a prior gesture cannot leak visibility,
 * orientation, progress, or placement into the next one.
 */
enum class PlayerGestureFeedbackLayout {

    /** Compact top-centered pill for messages: icon, optional level, text. */
    COMPACT_HORIZONTAL,

    /** Wide top-centered pill for messages: icon, optional level, text. */
    CENTERED_HORIZONTAL,

    /** Vertical slider at the finger's edge for brightness and volume. */
    EDGE_VERTICAL,

    /** Horizontal slider at the top of the video for seek, speed and pinch. */
    TOP_SLIDER,

    /** One number in the center of the video, for every gesture. */
    CENTER_VALUE,
}

/**
 * One reading of a gesture, enough to draw it in either style.
 *
 * [fraction] places the slider's handle, 0 at the left or bottom end and 1 at
 * the right or top. [startFraction], when present, is where the gesture
 * started, drawn as a dot on the track. [value] is the text in the slider's
 * track and the centered number; [detail] is a small line under the centered
 * number. [caption] is a small fixed line under the slider (a seek's target
 * time and the video's length). With [valueIcon] the centered number shows
 * the gesture's icon above it, for readings whose number alone could be
 * another gesture's.
 */
data class PlayerGestureReading(
    val fraction: Float,
    val value: String?,
    val startFraction: Float? = null,
    val detail: String? = null,
    val valueIcon: Boolean = false,
    val caption: String? = null,
)

data class PlayerGestureFeedbackPresentation(
    val layout: PlayerGestureFeedbackLayout,
    val levelVisible: Boolean,
    val level: Int,
    val text: String?,
    val reading: PlayerGestureReading? = null,
)

object PlayerGestureFeedbackState {

    /** Neutral center of the directional pinch slider. */
    const val PINCH_LEVEL_NEUTRAL = 50

    /**
     * Minimum pinch slider change worth a view write. Successive pinch
     * updates arrive faster than a frame; smaller deltas only churn drawing.
     */
    const val PINCH_UPDATE_MIN_LEVEL_DELTA = 2

    fun clampLevel(level: Int): Int = level.coerceIn(0, 100)

    /** Brightness at or below this percent shows the low icon. */
    const val BRIGHTNESS_LOW_MAX = 33

    /** Brightness at or below this percent shows the half icon; above it, the high icon. */
    const val BRIGHTNESS_HALF_MAX = 66

    /** Device volume below this percent shows the low-volume icon. */
    const val VOLUME_LOW_BELOW = 50

    /**
     * A gesture with a [reading] shows the chosen [style]: one centered
     * number, or a slider whose axis follows the gesture's (vertical at the
     * edge for brightness and volume, horizontal at the top for seek, speed
     * and pinch). Brightness and volume keep their edge slider even without a
     * reading. Plain messages keep the top pill.
     */
    fun layoutFor(
        kind: PlayerGestureFeedbackKind,
        surfaceClass: PlayerSurfaceClass,
        reading: Boolean = false,
        style: PlayerGestureFeedbackStyle = PlayerGestureFeedbackStyle.DEFAULT,
    ): PlayerGestureFeedbackLayout {
        val level = kind == PlayerGestureFeedbackKind.BRIGHTNESS || kind == PlayerGestureFeedbackKind.DEVICE_VOLUME
        return when {
            reading && style == PlayerGestureFeedbackStyle.NUMBER -> PlayerGestureFeedbackLayout.CENTER_VALUE
            level -> PlayerGestureFeedbackLayout.EDGE_VERTICAL
            reading -> PlayerGestureFeedbackLayout.TOP_SLIDER
            surfaceClass == PlayerSurfaceClass.COMPACT -> PlayerGestureFeedbackLayout.COMPACT_HORIZONTAL
            else -> PlayerGestureFeedbackLayout.CENTERED_HORIZONTAL
        }
    }

    /**
     * Builds a complete presentation. [level] carries the 0-100 value of a
     * message pill's bar or the edge slider; without one the edge slider
     * follows the [reading]. Seek and speed messages never show a bar.
     */
    fun presentation(
        kind: PlayerGestureFeedbackKind,
        surfaceClass: PlayerSurfaceClass,
        level: Int? = null,
        text: String? = null,
        reading: PlayerGestureReading? = null,
        style: PlayerGestureFeedbackStyle = PlayerGestureFeedbackStyle.DEFAULT,
    ): PlayerGestureFeedbackPresentation {
        val layout = layoutFor(kind, surfaceClass, reading != null, style)
        val shownLevel = level ?: reading?.let { (it.fraction.coerceIn(0f, 1f) * 100).roundToInt() }
        val levelVisible = when (kind) {
            PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackKind.PLAYBACK_SPEED -> false
            else -> shownLevel != null
        }
        return PlayerGestureFeedbackPresentation(
            layout = layout,
            levelVisible = levelVisible,
            level = if (levelVisible) clampLevel(shownLevel ?: 0) else 0,
            text = text,
            reading = when (layout) {
                PlayerGestureFeedbackLayout.TOP_SLIDER,
                PlayerGestureFeedbackLayout.CENTER_VALUE,
                PlayerGestureFeedbackLayout.EDGE_VERTICAL -> reading
                else -> null
            },
        )
    }

    /** Brightness icon that follows the level: auto, low, half or high sun. */
    fun brightnessIcon(percent: Int, auto: Boolean): Int {
        return when {
            auto -> R.drawable.ic_gesture_brightness_auto
            percent <= BRIGHTNESS_LOW_MAX -> R.drawable.ic_gesture_brightness_low
            percent <= BRIGHTNESS_HALF_MAX -> R.drawable.ic_gesture_brightness_half
            else -> R.drawable.ic_brightness_medium_black_24dp
        }
    }

    /** Device-volume icon that follows the level: off at zero, then low and high speaker. */
    fun volumeIcon(percent: Int): Int {
        return when {
            percent <= 0 -> R.drawable.baseline_volume_off_black_24
            percent < VOLUME_LOW_BELOW -> R.drawable.ic_gesture_volume_down
            else -> R.drawable.baseline_volume_up_black_24
        }
    }

    /**
     * Directional pinch position: the middle at neutral, moving to the right
     * end as the pinch arms Fill and to the left end as it arms Fit, so the
     * handle alone shows which way the pinch points. Stretch is never a pinch
     * target; a neutral preview from Stretch parks in the middle.
     */
    fun pinchLevel(progress: Float, toward: PlayerDisplayMode): Int {
        val clamped = progress.coerceIn(0f, 1f)
        return when (toward) {
            PlayerDisplayMode.FILL -> clampLevel((PINCH_LEVEL_NEUTRAL + PINCH_LEVEL_NEUTRAL * clamped).toInt())
            PlayerDisplayMode.FIT -> clampLevel((PINCH_LEVEL_NEUTRAL - PINCH_LEVEL_NEUTRAL * clamped).toInt())
            PlayerDisplayMode.STRETCH -> PINCH_LEVEL_NEUTRAL
        }
    }

    /**
     * Fit/Fill pinch: the start dot stays in the middle and the handle moves
     * toward Fill on the right or Fit on the left, including zero-progress
     * states, so the transition stays visible for the entire pinch. The value
     * is the mode the pinch points to.
     */
    fun pinchPresentation(
        surfaceClass: PlayerSurfaceClass,
        progress: Float,
        toward: PlayerDisplayMode,
        targetLabel: String,
        style: PlayerGestureFeedbackStyle = PlayerGestureFeedbackStyle.DEFAULT,
    ): PlayerGestureFeedbackPresentation {
        val level = pinchLevel(progress, toward)
        return presentation(
            kind = PlayerGestureFeedbackKind.PINCH,
            surfaceClass = surfaceClass,
            level = level,
            text = targetLabel,
            reading = PlayerGestureReading(
                fraction = level / 100f,
                value = targetLabel,
                startFraction = PINCH_LEVEL_NEUTRAL / 100f,
            ),
            style = style,
        )
    }

    /**
     * Free-zoom pinch: a position rather than a direction, Fit at the left end
     * and Fill at the right, with [fraction] the log-scale position between
     * them and [startFraction] where the pinch began.
     */
    fun pinchZoomPresentation(
        surfaceClass: PlayerSurfaceClass,
        fraction: Float,
        label: String,
        startFraction: Float = fraction,
        style: PlayerGestureFeedbackStyle = PlayerGestureFeedbackStyle.DEFAULT,
    ): PlayerGestureFeedbackPresentation {
        val clamped = fraction.coerceIn(0f, 1f)
        return presentation(
            kind = PlayerGestureFeedbackKind.PINCH,
            surfaceClass = surfaceClass,
            level = (clamped * 100).roundToInt(),
            text = label,
            reading = PlayerGestureReading(
                fraction = clamped,
                value = label,
                startFraction = startFraction.coerceIn(0f, 1f),
            ),
            style = style,
        )
    }

    /** Rounded Fit-relative zoom percent shown on the slider and in the `Zoom N%` description. */
    fun zoomPercent(zoom: Float): Int = (zoom * 100).roundToInt()

    /**
     * Whether this pinch update is worth a view write. Free-zoom frames arrive
     * per touch event; label-identical updates within a few slider points
     * only churn drawing. Small time gaps alone do not force a write: a held
     * finger would otherwise repaint the same label on every frame. Endpoint
     * arrivals and label changes always pass.
     */
    fun shouldUpdatePinchFeedback(
        lastLabel: String?,
        lastLevel: Int?,
        label: String,
        level: Int,
        reachedEnd: Boolean,
    ): Boolean {
        if (reachedEnd) return true
        if (label != lastLabel) return true
        if (lastLevel == null || abs(level - lastLevel) >= PINCH_UPDATE_MIN_LEVEL_DELTA) return true
        return false
    }

    /**
     * Whether this free-zoom frame should flash the Fill edge: only the step
     * that newly sticks to Fill, not dwelling at or leaving it.
     */
    fun shouldFlashFillEdge(target: PinchZoomController.Target, reachedEnd: Boolean): Boolean {
        return reachedEnd && target == PinchZoomController.Target.FILL
    }
}
