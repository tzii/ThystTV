package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.min

/**
 * Pure sizing and placement policy for the player-owned popup host.
 *
 * Popups prefer the space immediately above their trigger and clamp within
 * the visible player surface. If trigger geometry is unavailable, they fall
 * back to the bottom-end edge (bottom-start in RTL) instead of drifting to the
 * center on large screens.
 */
object PlayerPopupPolicy {

    const val COMPACT_MAX_WIDTH_DP = 336
    const val LARGE_MAX_WIDTH_DP = 384
    const val COMPACT_EDGE_INSET_DP = 16
    const val LARGE_EDGE_INSET_DP = 24
    const val TRIGGER_GAP_DP = 8

    data class Insets(
        val left: Int = 0,
        val top: Int = 0,
        val right: Int = 0,
        val bottom: Int = 0,
    )

    data class Rect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val centerX: Int get() = left + (right - left) / 2
        val isValid: Boolean get() = right > left && bottom > top
    }

    data class Placement(
        val left: Int,
        val top: Int,
        val width: Int,
        val maxHeight: Int,
    )

    fun panelWidthPx(surfaceWidthPx: Int, density: Float): Int {
        if (surfaceWidthPx <= 0 || density <= 0f) return 0
        val large = PlayerSurfacePolicy.classify(surfaceWidthPx, density) == PlayerSurfaceClass.LARGE
        val edge = dp(if (large) LARGE_EDGE_INSET_DP else COMPACT_EDGE_INSET_DP, density)
        val maxWidth = dp(if (large) LARGE_MAX_WIDTH_DP else COMPACT_MAX_WIDTH_DP, density)
        return min(maxWidth, surfaceWidthPx - edge * 2).coerceAtLeast(0)
    }

    /** Converts the policy's physical-left coordinate to a relative start margin. */
    fun startMarginPx(
        surfaceWidthPx: Int,
        placementLeftPx: Int,
        placementWidthPx: Int,
        isRtl: Boolean,
    ): Int {
        return if (isRtl) {
            surfaceWidthPx - placementLeftPx - placementWidthPx
        } else {
            placementLeftPx
        }.coerceAtLeast(0)
    }

    fun place(
        surfaceWidthPx: Int,
        surfaceHeightPx: Int,
        measuredPanelHeightPx: Int,
        density: Float,
        insets: Insets = Insets(),
        trigger: Rect? = null,
        isRtl: Boolean = false,
    ): Placement {
        val large = density > 0f &&
            PlayerSurfacePolicy.classify(surfaceWidthPx, density) == PlayerSurfaceClass.LARGE
        val edge = dp(if (large) LARGE_EDGE_INSET_DP else COMPACT_EDGE_INSET_DP, density)
        val gap = dp(TRIGGER_GAP_DP, density)
        val safeLeft = insets.left + edge
        val safeTop = insets.top + edge
        val safeRight = (surfaceWidthPx - insets.right - edge).coerceAtLeast(safeLeft)
        val safeBottom = (surfaceHeightPx - insets.bottom - edge).coerceAtLeast(safeTop)
        val width = min(panelWidthPx(surfaceWidthPx, density), safeRight - safeLeft).coerceAtLeast(0)
        val maxHeight = (safeBottom - safeTop).coerceAtLeast(0)
        val height = measuredPanelHeightPx.coerceIn(0, maxHeight)

        val validTrigger = trigger?.takeIf { it.isValid }
        val fallbackLeft = if (isRtl) safeLeft else safeRight - width
        val desiredLeft = validTrigger?.let { it.centerX - width / 2 } ?: fallbackLeft
        val maxLeft = (safeRight - width).coerceAtLeast(safeLeft)
        val left = desiredLeft.coerceIn(safeLeft, maxLeft)

        val desiredAbove = validTrigger?.top?.minus(gap)?.minus(height)
        val desiredBelow = validTrigger?.bottom?.plus(gap)
        val top = when {
            desiredAbove != null && desiredAbove >= safeTop -> desiredAbove
            desiredBelow != null && desiredBelow + height <= safeBottom -> desiredBelow
            validTrigger != null -> (validTrigger.top - gap - height).coerceIn(safeTop, (safeBottom - height).coerceAtLeast(safeTop))
            else -> (safeBottom - height).coerceAtLeast(safeTop)
        }

        return Placement(left = left, top = top, width = width, maxHeight = maxHeight)
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}
