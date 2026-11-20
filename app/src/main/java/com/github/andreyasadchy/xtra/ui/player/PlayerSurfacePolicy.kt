package com.github.andreyasadchy.xtra.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.github.andreyasadchy.xtra.R
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.ceil
import kotlin.math.max

/**
 * Classifies the active player surface and derives gesture-feedback placement
 * and presentation.
 *
 * All sizing is based on the measured player surface in density-independent
 * pixels, not on the display or the physical device, so the policy stays
 * correct in split-screen and resizable windows.
 */
enum class PlayerSurfaceClass {
    COMPACT,
    LARGE,
}

enum class PlayerGestureFeedbackKind {
    BRIGHTNESS,
    DEVICE_VOLUME,
    SEEK,
    PLAYBACK_SPEED,
    PINCH,
}

/** The one child of the feedback overlay a placement shows. */
enum class PlayerFeedbackIndicator {
    /** Top pill: badge, optional bar and text (plain messages such as hints). */
    PILL,

    /** Vertical slider at the finger's edge for brightness and device volume. */
    EDGE_SLIDER,

    /** Horizontal slider at the top of the video for seek, playback speed and pinch. */
    TOP_SLIDER,

    /** One number in the center of the video, in the "number in the center" style. */
    CENTER_VALUE,
}

/**
 * Where the player surface sits in its window, and how wide the band the
 * feedback should stay clear of is on each physical side (display cutout or
 * back-gesture zone), all in pixels. Left and right are physical, not
 * start/end: the brightness and volume halves are physical too.
 */
data class PlayerFeedbackEdges(
    val surfaceLeftPx: Int = 0,
    val surfaceRightPx: Int = 0,
    val unsafeLeftPx: Int = 0,
    val unsafeRightPx: Int = 0,
) {
    companion object {
        val NONE = PlayerFeedbackEdges()
    }
}

data class PlayerFeedbackPlacement(
    val gravity: Int,
    val maxWidthPx: Int,
    val marginLeftPx: Int,
    val marginRightPx: Int,
    val topPaddingPx: Int,
    val fixedContainerWidthPx: Int?,
    val fixedContainerHeightPx: Int?,
    val indicator: PlayerFeedbackIndicator,
) {
    val verticalPill: Boolean get() = indicator == PlayerFeedbackIndicator.EDGE_SLIDER
}

object PlayerSurfacePolicy {

    const val LARGE_SURFACE_MIN_WIDTH_DP = 600
    const val CENTER_FEEDBACK_MAX_WIDTH_DP = 360

    /** Length of the vertical slider, not counting its pad. */
    const val EDGE_SLIDER_HEIGHT_DP = 176
    const val FEEDBACK_HOLD_MS = 800L
    const val FEEDBACK_FADE_MS = 150L
    const val ENTRANCE_SCALE = 0.85f
    const val ENTRANCE_SLIDE_DP = 8

    /**
     * The edge slider never covers more than this share of the player height,
     * so short split-screen surfaces cannot clip it.
     */
    const val EDGE_SLIDER_MAX_HEIGHT_FRACTION = 0.6f

    /** Space between the top of the video and the top slider. */
    const val TOP_SLIDER_TOP_DP = 16

    /** Players shorter or narrower than this get the compact slider and number. */
    const val COMPACT_BELOW_DP = 320f
    private const val FEEDBACK_MARGIN_DP = 16
    private const val COMPACT_TOP_PADDING_DP = 24
    private const val OUTLINE_DP = 1

    /** Tabular digits keep changing numbers (seek time, speed, zoom) the same width. */
    private const val TABULAR_DIGITS = "tnum"

    /** Three digits cover any zoom up to 999%; a wider label only grows the slot. */
    private const val PINCH_SLOT_ZOOM_PERCENT = 888

    fun classify(surfaceWidthPx: Int, density: Float): PlayerSurfaceClass {
        if (density <= 0f || surfaceWidthPx <= 0) {
            return PlayerSurfaceClass.COMPACT
        }
        return if (surfaceWidthPx / density >= LARGE_SURFACE_MIN_WIDTH_DP) {
            PlayerSurfaceClass.LARGE
        } else {
            PlayerSurfaceClass.COMPACT
        }
    }

    /**
     * Places the indicator a [layout] asks for. The edge slider puts
     * brightness on the physical left and volume on the physical right,
     * matching the screen halves that drive them; the top slider and the
     * message pill sit at the top center and the centered value in the middle
     * of the video. Slider sizes include their pad, so margins and the top
     * gap are measured from the drawn slider.
     */
    // Physical LEFT/RIGHT on purpose: the gesture halves do not mirror in RTL.
    @SuppressLint("RtlHardcoded")
    fun placementFor(
        kind: PlayerGestureFeedbackKind,
        layout: PlayerGestureFeedbackLayout,
        density: Float,
        edges: PlayerFeedbackEdges = PlayerFeedbackEdges.NONE,
        surfaceHeightPx: Int = 0,
        surfaceWidthPx: Int = 0,
    ): PlayerFeedbackPlacement {
        val margin = (FEEDBACK_MARGIN_DP * density).toInt()
        val topPadding = (COMPACT_TOP_PADDING_DP * density).toInt()
        val pad = sliderPadPx(density)
        when (layout) {
            PlayerGestureFeedbackLayout.EDGE_VERTICAL -> {
                val width = (PlayerGestureSliderView.HANDLE_LENGTH_DP * density).toInt() + 2 * pad
                val (marginLeft, marginRight) = edgeMargins(edges, margin)
                return PlayerFeedbackPlacement(
                    gravity = Gravity.CENTER_VERTICAL or
                        if (kind == PlayerGestureFeedbackKind.BRIGHTNESS) Gravity.LEFT else Gravity.RIGHT,
                    maxWidthPx = width,
                    marginLeftPx = (marginLeft - pad).coerceAtLeast(0),
                    marginRightPx = (marginRight - pad).coerceAtLeast(0),
                    topPaddingPx = 0,
                    fixedContainerWidthPx = width,
                    fixedContainerHeightPx = edgeSliderHeightPx(density, surfaceHeightPx) + 2 * pad,
                    indicator = PlayerFeedbackIndicator.EDGE_SLIDER,
                )
            }
            PlayerGestureFeedbackLayout.TOP_SLIDER -> {
                val compact = compactFeedback(density, surfaceWidthPx, surfaceHeightPx)
                val width = topSliderWidthPx(density, surfaceWidthPx, margin, compact) + 2 * pad
                return PlayerFeedbackPlacement(
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    maxWidthPx = width,
                    marginLeftPx = 0,
                    marginRightPx = 0,
                    topPaddingPx = (TOP_SLIDER_TOP_DP * density).toInt() - pad,
                    fixedContainerWidthPx = width,
                    // A seek adds its caption under the track; the track
                    // itself stays where speed and pinch draw theirs.
                    fixedContainerHeightPx = (PlayerGestureSliderView.HANDLE_LENGTH_DP * density).toInt() + 2 * pad +
                        if (kind == PlayerGestureFeedbackKind.SEEK) captionBandPx(density) else 0,
                    indicator = PlayerFeedbackIndicator.TOP_SLIDER,
                )
            }
            PlayerGestureFeedbackLayout.CENTER_VALUE -> {
                val compact = compactFeedback(density, surfaceWidthPx, surfaceHeightPx)
                val (width, height) = valueSizePx(density, surfaceWidthPx, surfaceHeightPx, compact)
                return PlayerFeedbackPlacement(
                    gravity = Gravity.CENTER,
                    maxWidthPx = width,
                    marginLeftPx = 0,
                    marginRightPx = 0,
                    topPaddingPx = 0,
                    fixedContainerWidthPx = width,
                    fixedContainerHeightPx = height,
                    indicator = PlayerFeedbackIndicator.CENTER_VALUE,
                )
            }
            PlayerGestureFeedbackLayout.COMPACT_HORIZONTAL,
            PlayerGestureFeedbackLayout.CENTERED_HORIZONTAL -> return PlayerFeedbackPlacement(
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                maxWidthPx = (CENTER_FEEDBACK_MAX_WIDTH_DP * density).toInt(),
                marginLeftPx = margin,
                marginRightPx = margin,
                topPaddingPx = topPadding,
                fixedContainerWidthPx = null,
                fixedContainerHeightPx = null,
                indicator = PlayerFeedbackIndicator.PILL,
            )
        }
    }

    /** Room around a drawn slider, on each side. */
    fun sliderPadPx(density: Float): Int = (PlayerGestureSliderView.PAD_DP * density).toInt()

    /** Height a caption adds under the top slider, its gap included. */
    fun captionBandPx(density: Float): Int = (PlayerGestureSliderView.CAPTION_BAND_DP * density).toInt()

    /**
     * The top slider's drawn width, narrowed to leave the side margins on a
     * small surface.
     */
    fun topSliderWidthPx(density: Float, surfaceWidthPx: Int, marginPx: Int, compact: Boolean = false): Int {
        val widthDp = if (compact) PlayerGestureSliderView.COMPACT_WIDTH_DP else PlayerGestureSliderView.WIDTH_DP
        val desired = (widthDp * density).toInt()
        if (surfaceWidthPx <= 0) return desired
        return desired.coerceAtMost((surfaceWidthPx - 2 * marginPx).coerceAtLeast(0))
    }

    /** The centered value's size, which is its glow's, never larger than the player. */
    fun valueSizePx(density: Float, surfaceWidthPx: Int, surfaceHeightPx: Int, compact: Boolean): Pair<Int, Int> {
        val width = ((if (compact) PlayerGestureValueView.COMPACT_WIDTH_DP else PlayerGestureValueView.WIDTH_DP) * density).toInt()
        val height = ((if (compact) PlayerGestureValueView.COMPACT_HEIGHT_DP else PlayerGestureValueView.HEIGHT_DP) * density).toInt()
        return (if (surfaceWidthPx > 0) width.coerceAtMost(surfaceWidthPx) else width) to
            (if (surfaceHeightPx > 0) height.coerceAtMost(surfaceHeightPx) else height)
    }

    /**
     * A short or narrow player, such as the portrait player or a small
     * window, gets the compact slider and number so they never cover most of
     * the video.
     */
    fun compactFeedback(density: Float, surfaceWidthPx: Int, surfaceHeightPx: Int): Boolean {
        if (density <= 0f || surfaceWidthPx <= 0 || surfaceHeightPx <= 0) return false
        return minOf(surfaceWidthPx, surfaceHeightPx) / density < COMPACT_BELOW_DP
    }

    /**
     * Left and right margins, inside the surface, that put the brightness and
     * volume sliders the same distance from their sides.
     *
     * When both surface sides lie on the window edge (only a cutout or the
     * back-gesture zone between them), the sliders mirror around the physical
     * screen: a cutout on one side, whether the surface is padded clear of it
     * or drawn behind it, moves both sliders by the same amount. When a side
     * is inside the window, such as the side chat's edge, the sliders mirror
     * around the surface instead.
     */
    fun edgeMargins(edges: PlayerFeedbackEdges, marginPx: Int): Pair<Int, Int> {
        val left = edges.surfaceLeftPx.coerceAtLeast(0)
        val right = edges.surfaceRightPx.coerceAtLeast(0)
        val unsafeLeft = edges.unsafeLeftPx.coerceAtLeast(0)
        val unsafeRight = edges.unsafeRightPx.coerceAtLeast(0)
        return if (left <= unsafeLeft && right <= unsafeRight) {
            val fromWindowEdge = maxOf(left, unsafeLeft, right, unsafeRight) + marginPx
            (fromWindowEdge - left) to (fromWindowEdge - right)
        } else {
            val inset = max(unsafeLeft - left, unsafeRight - right).coerceAtLeast(0) + marginPx
            inset to inset
        }
    }

    /**
     * Measures [surface] against its window: its offset from each physical
     * window side and the cutout or back-gesture band on that side. Root
     * window insets are read, so a listener that consumed the insets for
     * padding does not hide them.
     */
    fun measureEdges(surface: View?): PlayerFeedbackEdges {
        if (surface == null || !surface.isAttachedToWindow) return PlayerFeedbackEdges.NONE
        val windowWidth = surface.rootView.width
        if (windowWidth <= 0 || surface.width <= 0) return PlayerFeedbackEdges.NONE
        val location = IntArray(2)
        surface.getLocationInWindow(location)
        val insets = ViewCompat.getRootWindowInsets(surface)
        val cutout = insets?.getInsets(WindowInsetsCompat.Type.displayCutout())
        val gestures = insets?.getInsets(WindowInsetsCompat.Type.systemGestures())
        return PlayerFeedbackEdges(
            surfaceLeftPx = location[0],
            surfaceRightPx = windowWidth - (location[0] + surface.width),
            unsafeLeftPx = max(cutout?.left ?: 0, gestures?.left ?: 0),
            unsafeRightPx = max(cutout?.right ?: 0, gestures?.right ?: 0),
        )
    }

    /**
     * The edge slider's drawn length: its full height where it fits, never
     * more than [EDGE_SLIDER_MAX_HEIGHT_FRACTION] of the player.
     */
    fun edgeSliderHeightPx(density: Float, surfaceHeightPx: Int): Int {
        val desired = (EDGE_SLIDER_HEIGHT_DP * density).toInt()
        if (surfaceHeightPx <= 0) {
            return desired
        }
        return desired.coerceAtMost((surfaceHeightPx * EDGE_SLIDER_MAX_HEIGHT_FRACTION).toInt())
    }

    /**
     * Applies [placement] to the feedback root and the one child it shows. The
     * other [indicators] are hidden, and every geometry field is written, so a
     * prior gesture kind cannot leak its position into the next one.
     */
    fun applyPlacement(feedbackRoot: View, indicators: Map<PlayerFeedbackIndicator, View>, placement: PlayerFeedbackPlacement) {
        if (feedbackRoot.parent is FrameLayout) {
            feedbackRoot.layoutParams = (feedbackRoot.layoutParams as? FrameLayout.LayoutParams)?.apply {
                gravity = placement.gravity
                // The included root is match-parent width. Horizontal margins
                // belong on the shown child; retaining them here only shifts
                // the coordinate space.
                leftMargin = 0
                rightMargin = 0
            } ?: feedbackRoot.layoutParams
        }
        feedbackRoot.setPadding(
            feedbackRoot.paddingLeft,
            placement.topPaddingPx,
            feedbackRoot.paddingRight,
            feedbackRoot.paddingBottom,
        )
        val shown = indicators.getValue(placement.indicator)
        for (child in indicators.values) {
            if (child === shown) continue
            child.animate().cancel()
            child.visibility = View.GONE
        }
        shown.layoutParams = (shown.layoutParams as? FrameLayout.LayoutParams)?.apply {
            gravity = placement.gravity
            leftMargin = placement.marginLeftPx
            rightMargin = placement.marginRightPx
            width = placement.fixedContainerWidthPx ?: FrameLayout.LayoutParams.WRAP_CONTENT
            height = placement.fixedContainerHeightPx ?: FrameLayout.LayoutParams.WRAP_CONTENT
        } ?: shown.layoutParams
        shown.visibility = View.VISIBLE
    }

    /**
     * Width of the widest label a pinch can show (Fit, Fill, Stretch or a
     * three-digit zoom), measured with the label's own paint so large fonts and
     * translations size the slot too.
     */
    internal fun pinchLabelSlotWidth(context: Context, text: TextView): Int {
        val widest = listOf(
            context.getString(R.string.display_mode_fit),
            context.getString(R.string.display_mode_fill),
            context.getString(R.string.display_mode_stretch),
            context.getString(R.string.display_mode_zoom, PINCH_SLOT_ZOOM_PERCENT),
        ).maxOf { text.paint.measureText(it) }
        return ceil(widest).toInt() + text.compoundPaddingLeft + text.compoundPaddingRight
    }

    /**
     * A visible pill visibly jumps when its geometry changes, so a placement
     * change must be applied while hidden; an already-hidden pill can
     * reposition freely, and an unchanged placement just refreshes content.
     */
    fun requiresCleanReposition(
        lastPlacement: PlayerFeedbackPlacement?,
        nextPlacement: PlayerFeedbackPlacement,
        isVisible: Boolean,
    ): Boolean {
        return isVisible && lastPlacement != nextPlacement
    }

    /**
     * Places, styles, and shows gesture feedback from a complete
     * presentation. The presentation decides the indicator; every visual
     * property is written on every call, so no visibility, level, or text
     * state can leak between gesture kinds. A freshly shown indicator springs
     * in; then the shared idle-hold hide is scheduled. The last applied
     * placement is stored on the view itself so multiple player instances
     * never share state.
     */
    fun presentFeedback(
        context: Context,
        feedbackRoot: View,
        kind: PlayerGestureFeedbackKind,
        surfaceWidthPx: Int,
        surfaceHeightPx: Int,
        presentation: PlayerGestureFeedbackPresentation,
        iconRes: Int,
        a11yText: String?,
        hideRunnable: Runnable,
        holdMs: Long = FEEDBACK_HOLD_MS,
        edges: PlayerFeedbackEdges = measureEdges(feedbackRoot.parent as? View),
    ) {
        val pill = feedbackRoot.findViewById<LinearLayout>(R.id.feedbackContainer) ?: return
        val edgeSlider = feedbackRoot.findViewById<PlayerGestureLevelView>(R.id.feedbackLevel) ?: return
        val topSlider = feedbackRoot.findViewById<PlayerGestureSliderView>(R.id.feedbackSlider) ?: return
        val value = feedbackRoot.findViewById<PlayerGestureValueView>(R.id.feedbackValue) ?: return
        val badge = feedbackRoot.findViewById<View>(R.id.feedbackIconBadge)
        val icon = feedbackRoot.findViewById<ImageView>(R.id.feedbackIcon)
        val progress = feedbackRoot.findViewById<LinearProgressIndicator>(R.id.feedbackProgress)
        val text = feedbackRoot.findViewById<TextView>(R.id.feedbackText)
        val density = context.resources.displayMetrics.density
        val colors = feedbackColors(context, feedbackRoot)

        feedbackRoot.animate().cancel()
        val reading = presentation.reading
        // A slider or number layout without a reading has nothing to draw;
        // show the plain message instead.
        val layout = when (presentation.layout) {
            PlayerGestureFeedbackLayout.TOP_SLIDER,
            PlayerGestureFeedbackLayout.CENTER_VALUE -> if (reading == null) PlayerGestureFeedbackLayout.CENTERED_HORIZONTAL else presentation.layout
            else -> presentation.layout
        }
        val placement = placementFor(
            kind = kind,
            layout = layout,
            density = density,
            edges = edges,
            surfaceHeightPx = surfaceHeightPx,
            surfaceWidthPx = surfaceWidthPx,
        )
        val lastPlacement = feedbackRoot.getTag(R.id.feedbackPlacement) as? PlayerFeedbackPlacement
        if (requiresCleanReposition(lastPlacement, placement, feedbackRoot.isVisible)) {
            // Swap geometry while invisible so the indicator cannot be seen
            // teleporting from one gesture's position to another's.
            feedbackRoot.removeCallbacks(hideRunnable)
            feedbackRoot.visibility = View.GONE
            feedbackRoot.alpha = 1f
        }
        val freshlyShown = !feedbackRoot.isVisible
        // Writing layout params requests a layout of the whole player window.
        // Touch updates arrive every frame with the same placement, so only a
        // new or changed placement is written; the drawn value updates alone.
        if (freshlyShown || lastPlacement != placement) {
            applyPlacement(
                feedbackRoot,
                mapOf(
                    PlayerFeedbackIndicator.PILL to pill,
                    PlayerFeedbackIndicator.EDGE_SLIDER to edgeSlider,
                    PlayerFeedbackIndicator.TOP_SLIDER to topSlider,
                    PlayerFeedbackIndicator.CENTER_VALUE to value,
                ),
                placement,
            )
            feedbackRoot.setTag(R.id.feedbackPlacement, placement)
        }

        val shown: View = when (placement.indicator) {
            PlayerFeedbackIndicator.EDGE_SLIDER -> {
                edgeSlider.setColors(colors)
                edgeSlider.setIcon(iconRes)
                // A reading's fraction places the handle between whole percents.
                edgeSlider.setLevel(reading?.fraction?.let { it * 100f } ?: presentation.level.toFloat())
                edgeSlider
            }
            PlayerFeedbackIndicator.TOP_SLIDER -> {
                val shownReading = reading ?: return
                topSlider.setColors(colors)
                topSlider.setIcon(iconRes)
                topSlider.setReading(
                    shownReading.fraction,
                    shownReading.startFraction,
                    shownReading.value ?: shownReading.detail,
                    shownReading.caption,
                )
                topSlider
            }
            PlayerFeedbackIndicator.CENTER_VALUE -> {
                val shownReading = reading ?: return
                value.setColors(colors)
                value.setCompact(compactFeedback(density, surfaceWidthPx, surfaceHeightPx))
                value.setIcon(if (shownReading.valueIcon) iconRes else 0)
                value.setText(shownReading.value, shownReading.detail)
                value
            }
            PlayerFeedbackIndicator.PILL -> {
                showPill(context, kind, presentation, iconRes, freshlyShown, pill, badge, icon, progress, text, colors, density)
                pill
            }
        }

        feedbackRoot.contentDescription = a11yText
        feedbackRoot.alpha = 1f
        feedbackRoot.visibility = View.VISIBLE
        if (freshlyShown) {
            playEntrance(shown, placement, density)
        }
        feedbackRoot.removeCallbacks(hideRunnable)
        feedbackRoot.postDelayed(hideRunnable, holdMs)
    }

    private fun showPill(
        context: Context,
        kind: PlayerGestureFeedbackKind,
        presentation: PlayerGestureFeedbackPresentation,
        iconRes: Int,
        freshlyShown: Boolean,
        pill: LinearLayout,
        badge: View?,
        icon: ImageView?,
        progress: LinearProgressIndicator?,
        text: TextView?,
        colors: PlayerGestureFeedbackColors,
        density: Float,
    ) {
        stylePill(pill, badge, icon, progress, text, colors, density)
        icon?.setImageResource(iconRes)
        val pinchLabel = kind == PlayerGestureFeedbackKind.PINCH
        progress?.visibility = if (presentation.levelVisible) View.VISIBLE else View.GONE
        if (presentation.levelVisible && !freshlyShown) {
            // The bar glides between updates while the pill is up; a
            // freshly shown pill jumps instead of sweeping from the
            // previous gesture's level.
            if (progress?.progress != presentation.level) {
                progress?.setProgressCompat(presentation.level, true)
            }
        } else {
            progress?.progress = presentation.level
        }
        val newText = presentation.text.orEmpty()
        text?.visibility = if (presentation.text != null) View.VISIBLE else View.GONE
        if (text?.text?.toString() != newText) {
            text?.text = newText
        }
        text?.let { label ->
            label.maxLines = 1
            label.fontFeatureSettings = TABULAR_DIGITS
            // Pinch keeps one centered label slot so Fit, Zoom N% and Fill
            // swaps never resize the pill. Other gestures share this view
            // and get their wrap-to-content, end-aligned label back.
            if (pinchLabel) {
                label.gravity = Gravity.CENTER
                val slot = pinchLabelSlotWidth(context, label)
                if (label.minWidth != slot) label.minWidth = slot
            } else {
                label.gravity = Gravity.END
                if (label.minWidth != 0) label.minWidth = 0
            }
        }
    }

    /**
     * Springs the shown indicator in: the edge slider grows out of its side,
     * the centered value grows from its center, and the top slider and pill
     * drop in from above. Animator duration scale 0 (reduced motion) lands on
     * the final state at once.
     */
    // Reads back the physical gravity placementFor wrote.
    @SuppressLint("RtlHardcoded")
    private fun playEntrance(target: View, placement: PlayerFeedbackPlacement, density: Float) {
        target.animate().cancel()
        target.alpha = 0f
        target.scaleX = ENTRANCE_SCALE
        target.scaleY = ENTRANCE_SCALE
        when (placement.indicator) {
            PlayerFeedbackIndicator.EDGE_SLIDER -> {
                val atLeft = (placement.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT
                target.pivotX = if (atLeft) 0f else (placement.fixedContainerWidthPx ?: 0).toFloat()
                target.pivotY = (placement.fixedContainerHeightPx ?: 0) / 2f
                target.translationY = 0f
            }
            // These pivots are never set, so they scale about their centers
            // whatever size they measure to.
            PlayerFeedbackIndicator.CENTER_VALUE -> target.translationY = 0f
            PlayerFeedbackIndicator.TOP_SLIDER,
            PlayerFeedbackIndicator.PILL -> target.translationY = -ENTRANCE_SLIDE_DP * density
        }
        target.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(PlayerSpringInterpolator.ENTRANCE_MS)
            .setInterpolator(PlayerSpringInterpolator())
            .start()
    }

    private fun stylePill(
        pill: LinearLayout,
        badge: View?,
        icon: ImageView?,
        progress: LinearProgressIndicator?,
        text: TextView?,
        colors: PlayerGestureFeedbackColors,
        density: Float,
    ) {
        (pill.background?.mutate() as? GradientDrawable)?.apply {
            setColor(colors.container)
            setStroke((OUTLINE_DP * density).toInt().coerceAtLeast(1), colors.outline)
        }
        (badge?.background?.mutate() as? GradientDrawable)?.setColor(colors.accent)
        icon?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(colors.onAccent)) }
        progress?.let {
            if (it.indicatorColor.firstOrNull() != colors.accent) it.setIndicatorColor(colors.accent)
            if (it.trackColor != colors.track) it.trackColor = colors.track
        }
        if (text?.currentTextColor != colors.content) text?.setTextColor(colors.content)
    }

    /**
     * Theme colors resolved once per feedback view: a theme change recreates
     * the player views, so the cached palette cannot go stale, and per-touch
     * updates skip the theme lookups.
     */
    private fun feedbackColors(context: Context, feedbackRoot: View): PlayerGestureFeedbackColors {
        (feedbackRoot.getTag(R.id.feedbackColors) as? PlayerGestureFeedbackColors)?.let { return it }
        return PlayerPanelTheme.gestureFeedback(context).also { feedbackRoot.setTag(R.id.feedbackColors, it) }
    }

    /**
     * Clears levels, text, and the content description after the overlay
     * hides so nothing persists into the next gesture even if a future
     * presentation forgets to set a field.
     */
    fun resetFeedback(feedbackRoot: View) {
        feedbackRoot.findViewById<LinearProgressIndicator>(R.id.feedbackProgress)?.progress = 0
        feedbackRoot.findViewById<PlayerGestureLevelView>(R.id.feedbackLevel)?.setLevel(0f)
        feedbackRoot.findViewById<PlayerGestureSliderView>(R.id.feedbackSlider)?.clear()
        feedbackRoot.findViewById<PlayerGestureValueView>(R.id.feedbackValue)?.clear()
        feedbackRoot.findViewById<TextView>(R.id.feedbackText)?.text = ""
        feedbackRoot.contentDescription = null
    }
}
