package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.max
import kotlin.math.min

/**
 * Pure geometry for pinch display-mode previews.
 *
 * With the renderer in Fit, a uniform view scale of
 * [fillToFitRatio] reproduces Fill exactly, so previews interpolate the view
 * scale between the two geometries. Every resting geometry is expressed as an
 * [anchorScale]: previews interpolate between two anchors, elastic endpoints
 * deform a few percent away from one, and settles animate back to the
 * committed mode's anchor — the single source of resting scale. Stretch is not
 * on the Fit-to-Fill zoom continuum: previews from Stretch anchor at the Fill
 * scale in both directions. When the source aspect ratio effectively matches
 * the viewport, the ratio collapses to 1 and feedback is label-only — no
 * artificial crop is manufactured to exaggerate the preview. Elastic endpoint
 * deformation is feedback rather than a mode preview, so it still applies at a
 * collapsed ratio.
 */
object PlayerDisplayModePreviewer {

    const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
    const val ELASTIC_MAX_SCALE_DELTA = 0.05f

    fun fillToFitRatio(videoAspectRatio: Float, viewportWidthPx: Int, viewportHeightPx: Int): Float {
        val aspect = if (videoAspectRatio > 0f) videoAspectRatio else DEFAULT_VIDEO_ASPECT_RATIO
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) {
            return 1f
        }
        val fittedWidth = min(viewportWidthPx.toFloat(), viewportHeightPx * aspect)
        val filledWidth = max(viewportWidthPx.toFloat(), viewportHeightPx * aspect)
        if (fittedWidth <= 0f) {
            return 1f
        }
        return (filledWidth / fittedWidth).coerceAtLeast(1f)
    }

    /**
     * The resting view scale of a display mode under Fit rendering: the
     * invariant anchor that previews, elastic endpoints, and settles resolve
     * against. Stretch anchors at the Fill scale like its previews do.
     */
    fun anchorScale(mode: PlayerDisplayMode, fillToFitRatio: Float): Float {
        return if (mode == PlayerDisplayMode.FIT) 1f else fillToFitRatio.coerceAtLeast(1f)
    }

    fun previewScale(from: PlayerDisplayMode, toward: PlayerDisplayMode, progress: Float, fillToFitRatio: Float): Float {
        val anchor = anchorScale(from, fillToFitRatio)
        val target = anchorScale(toward, fillToFitRatio)
        return anchor + (target - anchor) * progress.coerceIn(0f, 1f)
    }

    /**
     * Elastic endpoint deformation for a dead-direction pinch: a restrained
     * [ELASTIC_MAX_SCALE_DELTA] fraction away from the committed anchor,
     * driven by the controller's normalized deformation. Unreachable for
     * Stretch, which always has a live target; it anchors like Fill for
     * safety.
     */
    fun elasticScale(from: PlayerDisplayMode, deformation: Float, fillToFitRatio: Float): Float {
        val anchor = anchorScale(from, fillToFitRatio)
        val delta = ELASTIC_MAX_SCALE_DELTA * deformation.coerceIn(0f, 1f)
        return if (from == PlayerDisplayMode.FIT) {
            anchor - anchor * delta
        } else {
            anchor + anchor * delta
        }
    }
}
