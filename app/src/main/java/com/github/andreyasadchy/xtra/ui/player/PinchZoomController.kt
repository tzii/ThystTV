package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.abs
import kotlin.math.ln

/**
 * Continuous, centered pinch zoom between Fit and Fill for API 24+ surfaces.
 * Independent of Android views, like [PinchDisplayModeController].
 *
 * Zoom is relative to Fit: 1 is Fit and the Fill-to-Fit ratio is Fill. The
 * picture follows the fingers (start zoom times cumulative span scale).
 * Within [DETENT_ZOOM] of either end it snaps to that end, and pinching past
 * an end resists elastically, then settles back on release. Releasing between
 * the detents keeps that zoom.
 *
 * Extended zoom (a magnify cap above Fill) lets a pinch push through Fill's
 * full resistance and keep magnifying, continuous with that resistance, up to
 * the cap, which resists in turn. Releasing there keeps Fill plus a
 * magnification relative to Fill; releasing within [MAGNIFY_DETENT] of Fill
 * returns to plain Fill. Where Fit and Fill are too close for a free band
 * (see [supports]) they become neighbouring detents instead.
 */
class PinchZoomController {

    companion object {
        /** Each end captures zooms within this factor of it (5%). */
        const val DETENT_ZOOM = 1.05f
        /** Smallest free band that must remain between the two detents. */
        private const val MIN_FREE_BAND_ZOOM = 1.02f
        /** Overshoot past an end at which elastic resistance saturates. */
        private const val ELASTIC_SATURATION_ZOOM = 1.12f
        private const val UNCHANGED_EPSILON = 0.001f

        /** Extended zoom reaches this Fit-relative zoom... */
        private const val MAX_MAGNIFY_ZOOM = 4f
        /** ...or at least this multiple of Fill where Fill is already large. */
        private const val MIN_MAGNIFY_OVER_FILL = 2f
        /** Magnification over Fill below which a release returns to Fill. */
        const val MAGNIFY_DETENT = 1.1f

        /**
         * Raw-to-drawn factor past Fill's resistance: magnification starts at
         * the resisted size instead of jumping when the fingers break through.
         */
        private const val MAGNIFY_SCALE =
            (1f + PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA) / ELASTIC_SATURATION_ZOOM

        /**
         * Whether a viewport whose Fill-to-Fit ratio is [maxZoom] leaves a
         * usable band between the detents (ratio of about 1.12 or more).
         * Otherwise Fit and Fill stay a toggle.
         */
        fun supports(maxZoom: Float): Boolean {
            return maxZoom.isFinite() && ln(maxZoom) >= 2 * ln(DETENT_ZOOM) + ln(MIN_FREE_BAND_ZOOM)
        }

        /** Fit-relative zoom extended zoom stops at when Fill is [maxZoom]. */
        fun magnifyCap(maxZoom: Float): Float = maxOf(MAX_MAGNIFY_ZOOM, maxZoom * MIN_MAGNIFY_OVER_FILL)
    }

    enum class Target { FIT, ZOOM, FILL, MAGNIFIED }

    /**
     * One rendered step. [zoom] is what a release would keep (1 at Fit, the
     * maximum at Fill, the Fit-relative zoom when magnified); [displayZoom] is
     * what to draw now, including elastic overshoot. [fraction] runs 0..1 from
     * Fit to Fill in log scale and stays 1 past Fill. [reachedEnd] is true on
     * the step that enters Fit, Fill or the magnified range.
     */
    data class Frame(
        val target: Target,
        val zoom: Float,
        val displayZoom: Float,
        val fraction: Float,
        val reachedEnd: Boolean,
    )

    /**
     * Final state: Fill, or Fit with [zoom] (1 for plain Fit). A magnified
     * release is Fill with a [magnification] above 1, relative to Fill.
     * [target] and [fraction] describe it like a [Frame].
     */
    data class Result(
        val mode: PlayerDisplayMode,
        val zoom: Float,
        val target: Target,
        val fraction: Float,
        val changed: Boolean,
        val magnification: Float = 1f,
    )

    var maxZoom = 1f
        private set

    /** Fit-relative magnify cap; extended zoom is off unless it exceeds [maxZoom]. */
    var magnifyCap = 1f
        private set

    private val extended get() = magnifyCap > maxZoom

    /** Extended zoom on a ratio [supports] rejects: no band between Fit and Fill. */
    private var collapsed = false

    /**
     * Raw zoom at which Fill sits: [maxZoom], or far enough from Fit for both
     * detents when [collapsed].
     */
    private var fillRawZoom = 1f

    private var startMode = PlayerDisplayMode.FIT
    private var startStoredZoom = 1f
    private var startMagnification = 1f
    private var startZoom = 1f
    private var lastTarget = Target.FIT
    private var current = frameFor(1f)
    private var startFrame = current

    /**
     * Starts a pinch from [mode] (Fit or Fill) and the stored Fit zoom.
     * [maxZoom] is the current Fill-to-Fit ratio; see [supports]. A
     * [magnifyCap] above it enables extended zoom, and [magnification]
     * resumes a magnified Fill.
     */
    fun begin(
        mode: PlayerDisplayMode,
        zoom: Float,
        maxZoom: Float,
        magnifyCap: Float = maxZoom,
        magnification: Float = 1f,
    ) {
        this.maxZoom = maxZoom.coerceAtLeast(1f)
        this.magnifyCap = magnifyCap
        collapsed = extended && !supports(this.maxZoom)
        fillRawZoom = if (collapsed) maxOf(this.maxZoom, DETENT_ZOOM * DETENT_ZOOM) else this.maxZoom
        startMode = mode
        startStoredZoom = zoom
        startMagnification = if (extended && mode == PlayerDisplayMode.FILL) {
            magnification.coerceIn(1f, magnifyCap / this.maxZoom)
        } else {
            1f
        }
        startZoom = when {
            startMagnification > 1f -> fillRawZoom * startMagnification / MAGNIFY_SCALE
            mode == PlayerDisplayMode.FILL -> fillRawZoom
            // A kept Fit zoom draws as plain Fit where there is no band.
            collapsed -> 1f
            else -> zoom.coerceIn(1f, this.maxZoom)
        }
        current = frameFor(startZoom)
        startFrame = current
        lastTarget = current.target
    }

    /** [cumulativeScale] is the span scale relative to pinch start (1 = neutral). */
    fun update(cumulativeScale: Float): Frame {
        val frame = frameFor(startZoom * cumulativeScale)
        val reachedEnd = frame.target != Target.ZOOM && frame.target != lastTarget
        lastTarget = frame.target
        current = frame.copy(reachedEnd = reachedEnd)
        return current
    }

    /** Keeps the current Fit, Fill, in-between or magnified zoom. */
    fun release(): Result {
        val mode = when (current.target) {
            Target.FILL, Target.MAGNIFIED -> PlayerDisplayMode.FILL
            else -> PlayerDisplayMode.FIT
        }
        val zoom = when {
            current.target == Target.ZOOM -> current.zoom
            // Keep a Fit zoom this shape cannot draw for screens that can.
            collapsed && current.target == Target.FIT && startMode == PlayerDisplayMode.FIT -> startStoredZoom
            else -> 1f
        }
        val magnification = if (current.target == Target.MAGNIFIED) current.zoom / maxZoom else 1f
        val changed = mode != startMode ||
            abs(zoom - (if (startMode == PlayerDisplayMode.FIT) startStoredZoom else 1f)) > UNCHANGED_EPSILON ||
            abs(magnification - startMagnification) > UNCHANGED_EPSILON
        return Result(mode, zoom, current.target, current.fraction, changed, magnification)
    }

    /** Restores the state the pinch started from. */
    fun cancel(): Result = Result(
        mode = startMode,
        zoom = startStoredZoom,
        target = startFrame.target,
        fraction = startFrame.fraction,
        changed = false,
        magnification = startMagnification,
    )

    private fun frameFor(rawZoom: Float): Frame {
        val logMax = ln(maxZoom)
        val logFill = ln(fillRawZoom)
        val logDetent = ln(DETENT_ZOOM)
        val logZoom = ln(rawZoom.coerceAtLeast(0.0001f))
        return when {
            logZoom <= logDetent || (logFill <= 0f && !extended) -> Frame(
                target = Target.FIT,
                zoom = 1f,
                displayZoom = if (logZoom < 0f) 1f - elasticDelta(-logZoom) else 1f,
                fraction = 0f,
                reachedEnd = false,
            )
            extended && logZoom > logFill + ln(ELASTIC_SATURATION_ZOOM) ->
                magnifiedFrame(rawZoom * (maxZoom / fillRawZoom) * MAGNIFY_SCALE)
            logZoom >= logFill - logDetent || collapsed -> fillFrame(
                if (logZoom > logFill) maxZoom * (1f + elasticDelta(logZoom - logFill)) else maxZoom
            )
            else -> Frame(
                target = Target.ZOOM,
                zoom = rawZoom,
                displayZoom = rawZoom,
                fraction = (logZoom / logMax).coerceIn(0f, 1f),
                reachedEnd = false,
            )
        }
    }

    private fun fillFrame(displayZoom: Float) = Frame(
        target = Target.FILL,
        zoom = maxZoom,
        displayZoom = displayZoom,
        fraction = 1f,
        reachedEnd = false,
    )

    /** [zoom] is the Fit-relative zoom drawn past Fill's resistance. */
    private fun magnifiedFrame(zoom: Float): Frame {
        if (zoom < maxZoom * MAGNIFY_DETENT) return fillFrame(zoom)
        return Frame(
            target = Target.MAGNIFIED,
            zoom = zoom.coerceAtMost(magnifyCap),
            displayZoom = if (zoom > magnifyCap) magnifyCap * (1f + elasticDelta(ln(zoom / magnifyCap))) else zoom,
            fraction = 1f,
            reachedEnd = false,
        )
    }

    /**
     * Restrained endpoint deformation: the same quadratic ease-out and 5%
     * ceiling as the Fit/Fill toggle's elastic feedback.
     */
    private fun elasticDelta(logOvershoot: Float): Float {
        val normalized = (logOvershoot / ln(ELASTIC_SATURATION_ZOOM)).coerceIn(0f, 1f)
        val remaining = 1f - normalized
        return PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA * (1f - remaining * remaining)
    }
}
