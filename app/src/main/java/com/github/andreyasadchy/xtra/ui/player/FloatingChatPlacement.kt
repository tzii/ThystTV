package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class FloatingChatSide { LEFT, RIGHT }

/**
 * Saved floating chat position and size: fractions of the free travel inside
 * the usable screen (0 = flush left/top, 1 = flush right/bottom) and size in dp,
 * so the card restores cleanly after rotation, window resizing, or IME changes.
 */
internal data class FloatingChatLayout(
    val xFraction: Float,
    val yFraction: Float,
    val widthDp: Float,
    val heightDp: Float,
) {
    /** The half of the screen the card sits on; the resize grip faces the video. */
    val side get() = if (xFraction < 0.5f) FloatingChatSide.LEFT else FloatingChatSide.RIGHT
}

/** Usable area the card may occupy, in host pixels. */
internal data class FloatingChatBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Resolved card geometry in host pixels; width/height may shrink on narrow or short windows. */
internal data class FloatingChatPlaced(val x: Float, val y: Float, val width: Int, val height: Int)

internal object FloatingChatPlacement {
    const val MIN_WIDTH_DP = 170f
    const val MIN_HEIGHT_DP = 140f
    const val MAX_WIDTH_SHARE = 0.55f
    /** A short window may trim the card, but never below this share of the minimum height. */
    const val SHORT_SCREEN_MIN_SHARE = 0.8f

    fun defaultLayout(tablet: Boolean) = FloatingChatLayout(
        xFraction = 1f,
        yFraction = 0f,
        widthDp = if (tablet) 300f else 224f,
        heightDp = if (tablet) 380f else 240f,
    )

    /**
     * Host bounds minus side cutouts/insets and top/bottom system bar or IME
     * bands. No margin: the card may sit flush against any edge.
     */
    fun bounds(
        parentWidth: Int,
        parentHeight: Int,
        safeLeft: Int = 0,
        safeRight: Int = 0,
        keepClearTop: Int = 0,
        keepClearBottom: Int = 0,
    ): FloatingChatBounds {
        val left = safeLeft.toFloat()
        val top = keepClearTop.toFloat()
        return FloatingChatBounds(
            left = left,
            top = top,
            right = max(left, (parentWidth - safeRight).toFloat()),
            bottom = max(top, (parentHeight - keepClearBottom).toFloat()),
        )
    }

    /**
     * Height the card can show inside [bounds]. Above the [keyboard] it fits
     * whatever room remains so the composer stays above the keys.
     */
    fun visibleHeight(heightPx: Int, bounds: FloatingChatBounds, density: Float, keyboard: Boolean = false): Int {
        if (keyboard) return min(heightPx, bounds.height.toInt()).coerceAtLeast(1)
        val floor = (MIN_HEIGHT_DP * SHORT_SCREEN_MIN_SHARE * density).roundToInt()
        return min(heightPx, max(floor, bounds.height.toInt()))
    }

    /**
     * Resolves [layout] inside [rest] (clear screen without IME/nav bar) and
     * clamps it into [free] (also excluding nav bar or keyboard).
     */
    fun place(
        layout: FloatingChatLayout,
        widthPx: Int,
        heightPx: Int,
        rest: FloatingChatBounds,
        free: FloatingChatBounds,
        density: Float,
        keyboard: Boolean = false,
    ): FloatingChatPlaced {
        val width = min(widthPx, free.width.toInt()).coerceAtLeast(1)
        val height = visibleHeight(heightPx, free, density, keyboard)
        val restHeight = visibleHeight(heightPx, rest, density)
        val x = rest.left + max(0f, rest.width - width) * layout.xFraction.coerceIn(0f, 1f)
        val y = rest.top + max(0f, rest.height - restHeight) * layout.yFraction.coerceIn(0f, 1f)
        return FloatingChatPlaced(
            x = x.coerceIn(free.left, max(free.left, free.right - width)),
            y = y.coerceIn(free.top, max(free.top, free.bottom - height)),
            width = width,
            height = height,
        )
    }

    /** Converts a card position in host pixels into saved fractions without moving or snapping it. */
    fun toLayout(
        x: Float,
        y: Float,
        widthPx: Int,
        heightPx: Int,
        rest: FloatingChatBounds,
        free: FloatingChatBounds,
        density: Float,
        stored: FloatingChatLayout,
        keyboard: Boolean = false,
    ): FloatingChatLayout {
        val width = min(widthPx, free.width.toInt()).coerceAtLeast(1)
        val height = visibleHeight(heightPx, free, density, keyboard)
        val clampedX = x.coerceIn(free.left, max(free.left, free.right - width))
        val clampedY = y.coerceIn(free.top, max(free.top, free.bottom - height))
        val travelX = max(0f, rest.width - width)
        val restHeight = visibleHeight(heightPx, rest, density)
        val travelY = max(0f, rest.height - restHeight)
        return stored.copy(
            xFraction = if (travelX <= 0f) 0f else ((clampedX - rest.left) / travelX).coerceIn(0f, 1f),
            yFraction = if (travelY <= 0f) 0f else ((clampedY - rest.top) / travelY).coerceIn(0f, 1f),
        )
    }

    /** Clamps width and height between the minimum card size and what the window allows. */
    fun clampSize(widthPx: Float, heightPx: Float, parentWidth: Int, parentHeight: Int, density: Float): Pair<Int, Int> {
        val minWidth = MIN_WIDTH_DP * density
        val minHeight = MIN_HEIGHT_DP * density
        val maxWidth = max(minWidth, parentWidth * MAX_WIDTH_SHARE)
        val maxHeight = max(minHeight, parentHeight.toFloat())
        return widthPx.coerceIn(minWidth, maxWidth).roundToInt() to heightPx.coerceIn(minHeight, maxHeight).roundToInt()
    }

    /**
     * New size while dragging the corner grip facing the video: bottom-left on
     * a right-side card, bottom-right on a left-side card.
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
