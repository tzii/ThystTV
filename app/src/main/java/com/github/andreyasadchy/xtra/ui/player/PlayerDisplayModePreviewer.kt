package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.max
import kotlin.math.min

/**
 * Pure geometry for pinch display-mode previews.
 *
 * With the renderer in Fit, a uniform view scale of
 * [fillToFitRatio] reproduces Fill exactly, so previews interpolate the view
 * scale between the two geometries. Stretch is not on the Fit-to-Fill zoom
 * continuum: previews from Stretch anchor at the Fill scale in both
 * directions. When the source aspect ratio effectively matches the viewport,
 * the ratio collapses to 1 and feedback is label-only — no artificial crop is
 * manufactured to exaggerate the preview.
 */
object PlayerDisplayModePreviewer {

    const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f

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

    fun previewScale(from: PlayerDisplayMode, toward: PlayerDisplayMode, progress: Float, fillToFitRatio: Float): Float {
        val ratio = fillToFitRatio.coerceAtLeast(1f)
        val clampedProgress = progress.coerceIn(0f, 1f)
        val targetScale = if (toward == PlayerDisplayMode.FILL) ratio else 1f
        val anchorScale = if (from == PlayerDisplayMode.FIT) 1f else ratio
        return anchorScale + (targetScale - anchorScale) * clampedProgress
    }
}
