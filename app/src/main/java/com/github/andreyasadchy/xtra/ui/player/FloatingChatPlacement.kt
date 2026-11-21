package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class FloatingChatSide { LEFT, RIGHT }

internal enum class FloatingChatAnchor { TOP, BOTTOM, FREE }

/**
 * Where the floating chat card rests. Stored in dp and as an anchor rather than
 * pixels, so the card lands in the same place on rotation, other screens and
 * after the controls or keyboard move it.
 */
internal data class FloatingChatLayout(
    val side: FloatingChatSide,
    val anchor: FloatingChatAnchor,
    /** Share of the free vertical travel above the card; only used when [anchor] is FREE. */
    val yFraction: Float,
    val widthDp: Float,
    val heightDp: Float,
)

/** Area the card may occupy, in host pixels. */
internal data class FloatingChatBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** A resolved card position. [height] can be shorter than the stored size on short screens. */
internal data class FloatingChatPlaced(val x: Float, val y: Float, val height: Int)

/** Result of releasing a dragged card. */
internal data class FloatingChatRelease(val layout: FloatingChatLayout, val tuckedSide: FloatingChatSide?)

internal object FloatingChatPlacement {
    const val MARGIN_DP = 12f
    const val MAGNET_DP = 30f
    /** How far a release velocity carries the card before the side is chosen. */
    const val PROJECTION_S = 0.15f
    const val TUCK_FLING_DP_S = 1200f
    const val TUCK_DIRECTION_RATIO = 1.8f
    /** Share of the card that must be dragged past a screen edge to tuck it. */
    const val TUCK_OVERHANG = 0.3f
    const val MIN_WIDTH_DP = 170f
    const val MIN_HEIGHT_DP = 140f
    const val MAX_WIDTH_SHARE = 0.55f
    /** A short screen may cut the card down, but never below this share of the minimum height. */
    const val SHORT_SCREEN_MIN_SHARE = 0.8f

    fun defaultLayout(tablet: Boolean) = FloatingChatLayout(
        side = FloatingChatSide.RIGHT,
        anchor = FloatingChatAnchor.TOP,
        yFraction = 0f,
        widthDp = if (tablet) 300f else 224f,
        heightDp = if (tablet) 380f else 240f,
    )

    /**
     * The parent minus a margin, the cutout or rounded-corner insets on each side,
     * and keep-clear bands at the top and bottom (player controls, keyboard).
     */
    fun bounds(
        parentWidth: Int,
        parentHeight: Int,
        density: Float,
        safeLeft: Int = 0,
        safeRight: Int = 0,
        keepClearTop: Int = 0,
        keepClearBottom: Int = 0,
    ): FloatingChatBounds {
        val margin = MARGIN_DP * density
        return FloatingChatBounds(
            left = safeLeft + margin,
            top = keepClearTop + margin,
            right = max(safeLeft + margin, parentWidth - safeRight - margin),
            bottom = max(keepClearTop + margin, parentHeight - keepClearBottom - margin),
        )
    }

    /** Height the card can show inside [bounds]; a short screen drops the oldest (top) lines. */
    fun visibleHeight(heightPx: Int, bounds: FloatingChatBounds, density: Float): Int {
        val floor = (MIN_HEIGHT_DP * SHORT_SCREEN_MIN_SHARE * density).roundToInt()
        return min(heightPx, max(floor, bounds.height.toInt()))
    }

    /**
     * Resting position of [layout]. A FREE card keeps the height it has on the
     * clear screen ([rest]) and only moves when [current] keep-clear bands
     * reach it; TOP and BOTTOM cards follow their edge.
     */
    fun place(
        layout: FloatingChatLayout,
        widthPx: Int,
        heightPx: Int,
        rest: FloatingChatBounds,
        current: FloatingChatBounds,
        density: Float,
    ): FloatingChatPlaced {
        val height = visibleHeight(heightPx, current, density)
        val x = when (layout.side) {
            FloatingChatSide.LEFT -> current.left
            FloatingChatSide.RIGHT -> current.right - widthPx
        }
        val lowest = max(current.top, current.bottom - height)
        val y = when (layout.anchor) {
            FloatingChatAnchor.TOP -> current.top
            FloatingChatAnchor.BOTTOM -> lowest
            FloatingChatAnchor.FREE -> {
                val restHeight = visibleHeight(heightPx, rest, density)
                val travel = max(0f, rest.height - restHeight)
                (rest.top + travel * layout.yFraction.coerceIn(0f, 1f)).coerceIn(current.top, lowest)
            }
        }
        return FloatingChatPlaced(x, y, height)
    }

    /** Off-screen x for a card tucked against [side]. */
    fun tuckedX(side: FloatingChatSide, widthPx: Int, parentWidth: Int, density: Float): Float {
        val gap = MARGIN_DP * density
        return when (side) {
            FloatingChatSide.LEFT -> -widthPx - gap
            FloatingChatSide.RIGHT -> parentWidth + gap
        }
    }

    /**
     * Where a released card goes. The release velocity is projected
     * [PROJECTION_S] ahead; the nearer side wins and the top or bottom edge pulls
     * the card in within [MAGNET_DP]. A fast sideways fling outward from
     * [startSide], or dragging [TUCK_OVERHANG] of the card past the edge of
     * [current], tucks it instead.
     */
    fun release(
        x: Float,
        y: Float,
        velocityX: Float,
        velocityY: Float,
        widthPx: Int,
        heightPx: Int,
        startSide: FloatingChatSide,
        rest: FloatingChatBounds,
        current: FloatingChatBounds,
        density: Float,
        stored: FloatingChatLayout,
    ): FloatingChatRelease {
        val tuck = tuckSide(x, velocityX, velocityY, widthPx, current, startSide, density)
        val centerX = x + widthPx / 2f + velocityX * PROJECTION_S
        val side = tuck ?: if (centerX < (current.left + current.right) / 2f) FloatingChatSide.LEFT else FloatingChatSide.RIGHT
        val height = visibleHeight(heightPx, current, density)
        val lowest = max(current.top, current.bottom - height)
        val projectedY = (y + velocityY * PROJECTION_S).coerceIn(current.top, lowest)
        val magnet = MAGNET_DP * density
        val anchor = when {
            projectedY - current.top <= magnet -> FloatingChatAnchor.TOP
            lowest - projectedY <= magnet -> FloatingChatAnchor.BOTTOM
            else -> FloatingChatAnchor.FREE
        }
        val restHeight = visibleHeight(heightPx, rest, density)
        val restTravel = rest.height - restHeight
        val fraction = if (anchor == FloatingChatAnchor.FREE && restTravel > 0f) {
            ((projectedY - rest.top) / restTravel).coerceIn(0f, 1f)
        } else if (anchor == FloatingChatAnchor.BOTTOM) 1f else 0f
        return FloatingChatRelease(
            layout = stored.copy(side = side, anchor = anchor, yFraction = fraction),
            tuckedSide = tuck,
        )
    }

    fun tuckSide(
        x: Float,
        velocityX: Float,
        velocityY: Float,
        widthPx: Int,
        bounds: FloatingChatBounds,
        startSide: FloatingChatSide,
        density: Float,
    ): FloatingChatSide? {
        val overhang = TUCK_OVERHANG * widthPx
        if (x < bounds.left - overhang) return FloatingChatSide.LEFT
        if (x + widthPx > bounds.right + overhang) return FloatingChatSide.RIGHT
        val outward = when (startSide) {
            FloatingChatSide.LEFT -> velocityX < 0f
            FloatingChatSide.RIGHT -> velocityX > 0f
        }
        val fast = abs(velocityX) / density > TUCK_FLING_DP_S
        val sideways = abs(velocityX) > TUCK_DIRECTION_RATIO * abs(velocityY)
        return if (outward && fast && sideways) startSide else null
    }

    /** Width and height kept between the minimum size and what the screen allows. */
    fun clampSize(widthPx: Float, heightPx: Float, parentWidth: Int, parentHeight: Int, density: Float): Pair<Int, Int> {
        val minWidth = MIN_WIDTH_DP * density
        val minHeight = MIN_HEIGHT_DP * density
        val maxWidth = max(minWidth, parentWidth * MAX_WIDTH_SHARE)
        val maxHeight = max(minHeight, parentHeight - 2 * MARGIN_DP * density)
        return widthPx.coerceIn(minWidth, maxWidth).roundToInt() to heightPx.coerceIn(minHeight, maxHeight).roundToInt()
    }

    /**
     * Size while dragging the grip on the corner that faces the video: the
     * bottom-left corner of a right-side card, the bottom-right of a left-side one.
     */
    fun resize(
        startWidth: Int,
        startHeight: Int,
        dx: Float,
        dy: Float,
        side: FloatingChatSide,
        parentWidth: Int,
        parentHeight: Int,
        density: Float,
    ): Pair<Int, Int> {
        val width = when (side) {
            FloatingChatSide.LEFT -> startWidth + dx
            FloatingChatSide.RIGHT -> startWidth - dx
        }
        return clampSize(width, startHeight + dy, parentWidth, parentHeight, density)
    }
}
