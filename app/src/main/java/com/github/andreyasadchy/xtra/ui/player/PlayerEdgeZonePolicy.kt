package com.github.andreyasadchy.xtra.ui.player

import android.view.View
import androidx.core.graphics.Insets
import kotlin.math.abs

/** Surface-local system-gesture exclusion margins inside the player view. */
internal data class PlayerEdgeExclusion(
    val leftPx: Int,
    val topPx: Int,
    val rightPx: Int,
    val bottomPx: Int,
) {
    fun isTopOrBottomEdge(y: Float, surfaceHeightPx: Int): Boolean {
        if (surfaceHeightPx <= 0) return false
        return (topPx > 0 && y <= topPx) || (bottomPx > 0 && y >= surfaceHeightPx - bottomPx)
    }

    fun isSideEdge(x: Float, surfaceWidthPx: Int): Boolean {
        if (surfaceWidthPx <= 0) return false
        return (leftPx > 0 && x <= leftPx) || (rightPx > 0 && x >= surfaceWidthPx - rightPx)
    }

    fun chatSideExclusionPx(isRtl: Boolean): Float = (if (isRtl) leftPx else rightPx).toFloat()
}

/**
 * Maps window-edge system gesture insets into player-surface coordinates.
 *
 * System gesture insets are measured from the physical window edges. Any padding
 * or margin that already insets the player surface from a window edge (display
 * cutout padding, rounded-corner padding, or an open side chat) reduces the
 * overlap on that edge instead of stacking a second deadzone inside the video.
 *
 * Top and bottom edges belong to vertical OS gestures (notification shade and
 * home bar) and block all swipe gestures. Left and right edges belong to
 * Android's horizontal Back gesture: they block horizontal player swipes
 * (chat swipe, seek, speed), while allowing clearly vertical brightness and
 * volume swipes so camera-cutout sides do not create a deadzone.
 */
internal object PlayerEdgeZonePolicy {

    /**
     * Clearly vertical angle required to claim brightness or volume from inside
     * a left/right system-back edge zone: |dy| >= 2 |dx| (within ~27° of
     * vertical), mirroring [PlayerScrubPolicy.HORIZONTAL_LOCK_RATIO].
     */
    const val SIDE_EDGE_VERTICAL_RATIO = PlayerScrubPolicy.HORIZONTAL_LOCK_RATIO

    fun resolve(
        gestureInsets: Insets?,
        fallbackPx: Int,
        surfaceLeftPx: Int = 0,
        surfaceTopPx: Int = 0,
        surfaceRightPx: Int = 0,
        surfaceBottomPx: Int = 0,
    ): PlayerEdgeExclusion {
        val fallback = fallbackPx.coerceAtLeast(0)
        val windowLeft = gestureInsets?.left?.takeIf { it > 0 } ?: fallback
        val windowTop = gestureInsets?.top?.takeIf { it > 0 } ?: fallback
        val windowRight = gestureInsets?.right?.takeIf { it > 0 } ?: fallback
        val windowBottom = gestureInsets?.bottom?.takeIf { it > 0 } ?: fallback
        return PlayerEdgeExclusion(
            leftPx = (windowLeft - surfaceLeftPx.coerceAtLeast(0)).coerceAtLeast(0),
            topPx = (windowTop - surfaceTopPx.coerceAtLeast(0)).coerceAtLeast(0),
            rightPx = (windowRight - surfaceRightPx.coerceAtLeast(0)).coerceAtLeast(0),
            bottomPx = (windowBottom - surfaceBottomPx.coerceAtLeast(0)).coerceAtLeast(0),
        )
    }

    fun measure(
        surface: View,
        gestureInsets: Insets?,
        fallbackPx: Int,
    ): PlayerEdgeExclusion {
        val root = surface.rootView
        if (!surface.isAttachedToWindow || root == null || root.width <= 0 || root.height <= 0 ||
            surface.width <= 0 || surface.height <= 0
        ) {
            return resolve(gestureInsets, fallbackPx)
        }
        val location = IntArray(2)
        surface.getLocationInWindow(location)
        return resolve(
            gestureInsets = gestureInsets,
            fallbackPx = fallbackPx,
            surfaceLeftPx = location[0],
            surfaceTopPx = location[1],
            surfaceRightPx = root.width - (location[0] + surface.width),
            surfaceBottomPx = root.height - (location[1] + surface.height),
        )
    }

    /** Whether a drag starting inside a left/right system-back edge is clearly vertical. */
    fun allowsVerticalFromSideEdge(dx: Float, dy: Float): Boolean {
        val ay = abs(dy)
        return ay > 0f && ay >= SIDE_EDGE_VERTICAL_RATIO * abs(dx)
    }
}
