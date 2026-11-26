package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class FloatingChatSide { LEFT, RIGHT }

/**
 * Where the floating chat card rests: anywhere the viewer dropped it. Stored as
 * shares of the free travel inside the clear screen (0 = flush left or top,
 * 1 = flush right or bottom) and a size in dp, so the card lands in the same
 * place on rotation, other screens and after the keyboard moved it.
 */
internal data class FloatingChatLayout(
    val xFraction: Float,
    val yFraction: Float,
    val widthDp: Float,
    val heightDp: Float,
) {
    /**
     * The half of the screen the card is on. The resize grip sits on the
     * corner that faces the other half, and the card tucks to this side.
     */
    val side get() = if (xFraction < 0.5f) FloatingChatSide.LEFT else FloatingChatSide.RIGHT
}

/** Area the card may occupy, in host pixels. */
internal data class FloatingChatBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * A resolved card position. [width] and [height] can be smaller than the stored
 * size on narrow or short windows.
 */
internal data class FloatingChatPlaced(val x: Float, val y: Float, val width: Int, val height: Int)

/** Result of releasing a dragged card. */
internal data class FloatingChatRelease(val layout: FloatingChatLayout, val tuckedSide: FloatingChatSide?)

/**
 * The visible player controls, in host pixels: each group of buttons or text
 * (top rows, bottom rows, seek bar, play/pause, the ±10 buttons) as its own
 * rectangle, so the card only fades for, and lets touches through to, what it
 * actually covers.
 */
internal data class FloatingChatControls(val obstacles: List<FloatingChatBounds> = emptyList())

internal object FloatingChatPlacement {
    const val MARGIN_DP = 12f
    /** A card dropped this close to an edge of the clear screen lands flush with it. */
    const val MAGNET_DP = 8f
    const val TUCK_FLING_DP_S = 1800f
    const val TUCK_DIRECTION_RATIO = 1.8f
    /** A flung card only tucks when the drag began this close to that edge (or it already overhangs it). */
    const val TUCK_START_EDGE_DP = 24f
    /** Share of the card that must be dragged past a screen edge to tuck it. */
    const val TUCK_OVERHANG = 0.35f
    const val MIN_WIDTH_DP = 170f
    const val MIN_HEIGHT_DP = 140f
    const val MAX_WIDTH_SHARE = 0.55f
    /** A short screen may cut the card down, but never below this share of the minimum height. */
    const val SHORT_SCREEN_MIN_SHARE = 0.8f

    fun defaultLayout(tablet: Boolean) = FloatingChatLayout(
        xFraction = 1f,
        yFraction = 0f,
        widthDp = if (tablet) 300f else 224f,
        heightDp = if (tablet) 380f else 240f,
    )

    /**
     * The parent minus a margin, the cutout or rounded-corner insets on each side,
     * and keep-clear bands at the top and bottom (status bar, navigation bar, keyboard).
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
     * Resting position of [layout]: its share of the travel inside [rest] (the
     * clear screen), kept inside [free] (which also leaves out the keyboard or
     * navigation bar). Above the [keyboard] the card fits whatever room is
     * left, with no floor, so the message box at its bottom stays above the keys.
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
        val height = if (keyboard) {
            min(heightPx, free.height.toInt()).coerceAtLeast(1)
        } else {
            visibleHeight(heightPx, free, density)
        }
        // A split-screen window can be narrower than the minimum card.
        val width = min(widthPx, free.width.toInt()).coerceAtLeast(1)
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

    /**
     * The controls never move the card: while they show, a card over any of
     * [obstacles] fades so they show through, and a touch on one goes to it.
     */
    fun covers(placed: FloatingChatPlaced, obstacles: List<FloatingChatBounds>): Boolean =
        obstacles.any { covers(placed.x, placed.y, placed.x + placed.width, placed.y + placed.height, it) }

    /** Whether the rectangle from ([left], [top]) to ([right], [bottom]) overlaps [obstacle]. */
    fun covers(left: Float, top: Float, right: Float, bottom: Float, obstacle: FloatingChatBounds) =
        left < obstacle.right && right > obstacle.left && top < obstacle.bottom && bottom > obstacle.top

    /** How much of the rectangle from ([left], [top]) to ([right], [bottom]) [obstacles] cover, in square pixels. */
    fun coveredArea(left: Float, top: Float, right: Float, bottom: Float, obstacles: List<FloatingChatBounds>): Float =
        obstacles.sumOf { obstacle ->
            val width = min(right, obstacle.right) - max(left, obstacle.left)
            val height = min(bottom, obstacle.bottom) - max(top, obstacle.top)
            if (width > 0f && height > 0f) (width * height).toDouble() else 0.0
        }.toFloat()

    /** Whether the point ([x], [y]) is on any of [obstacles]. */
    fun hits(x: Float, y: Float, obstacles: List<FloatingChatBounds>) =
        obstacles.any { x >= it.left && x < it.right && y >= it.top && y < it.bottom }

    /** Off-screen x for a card tucked against [side]. */
    fun tuckedX(side: FloatingChatSide, widthPx: Int, parentWidth: Int, density: Float): Float {
        val gap = MARGIN_DP * density
        return when (side) {
            FloatingChatSide.LEFT -> -widthPx - gap
            FloatingChatSide.RIGHT -> parentWidth + gap
        }
    }

    /**
     * Where a released card goes: exactly where it was let go, kept inside
     * [free] and pulled flush with any edge within [MAGNET_DP]. The release
     * velocity never carries it further. Dragging [TUCK_OVERHANG] of the card
     * past a side, or a fast sideways fling outward from a card that started
     * at that edge, tucks it instead.
     *
     * [heightPx] is the stored height; [startX] is where the card was when the drag began.
     */
    fun release(
        x: Float,
        y: Float,
        velocityX: Float,
        velocityY: Float,
        widthPx: Int,
        heightPx: Int,
        startX: Float,
        rest: FloatingChatBounds,
        free: FloatingChatBounds,
        density: Float,
        stored: FloatingChatLayout,
    ): FloatingChatRelease {
        val height = visibleHeight(heightPx, free, density)
        val lowestX = max(free.left, free.right - widthPx)
        val lowestY = max(free.top, free.bottom - height)
        val tuck = tuckSide(x, velocityX, velocityY, widthPx, startX, free, density)
        var dropX = when (tuck) {
            FloatingChatSide.LEFT -> free.left
            FloatingChatSide.RIGHT -> lowestX
            null -> x.coerceIn(free.left, lowestX)
        }
        var dropY = y.coerceIn(free.top, lowestY)
        val magnet = MAGNET_DP * density
        if (dropX - free.left <= magnet) dropX = free.left else if (lowestX - dropX <= magnet) dropX = lowestX
        if (dropY - free.top <= magnet) dropY = free.top else if (lowestY - dropY <= magnet) dropY = lowestY
        val layout = stored.copy(
            xFraction = fraction(dropX, free.left, lowestX, rest.left, max(0f, rest.width - widthPx)),
            yFraction = fraction(dropY, free.top, lowestY, rest.top, max(0f, rest.height - visibleHeight(heightPx, rest, density))),
        )
        return FloatingChatRelease(layout, tuck)
    }

    /**
     * Share of the [restTravel] from [restStart] for [value]. A card flush with
     * an edge of the free area stays flush with that edge of the clear screen.
     */
    private fun fraction(value: Float, freeStart: Float, freeEnd: Float, restStart: Float, restTravel: Float): Float = when {
        value <= freeStart -> 0f
        value >= freeEnd && freeEnd > freeStart -> 1f
        restTravel <= 0f -> 0f
        else -> ((value - restStart) / restTravel).coerceIn(0f, 1f)
    }

    /**
     * A card dragged far enough past an edge tucks there. A fast sideways
     * fling tucks it only toward an edge it started at, or already overhangs;
     * a card tossed from the middle of the screen lands at the edge instead.
     */
    fun tuckSide(
        x: Float,
        velocityX: Float,
        velocityY: Float,
        widthPx: Int,
        startX: Float,
        bounds: FloatingChatBounds,
        density: Float,
    ): FloatingChatSide? {
        val overhang = TUCK_OVERHANG * widthPx
        if (x < bounds.left - overhang) return FloatingChatSide.LEFT
        if (x + widthPx > bounds.right + overhang) return FloatingChatSide.RIGHT
        val fast = abs(velocityX) / density > TUCK_FLING_DP_S
        val sideways = abs(velocityX) > TUCK_DIRECTION_RATIO * abs(velocityY)
        if (!fast || !sideways) return null
        val near = TUCK_START_EDGE_DP * density
        return if (velocityX < 0f) {
            FloatingChatSide.LEFT.takeIf { startX - bounds.left <= near || x < bounds.left }
        } else {
            FloatingChatSide.RIGHT.takeIf { bounds.right - (startX + widthPx) <= near || x + widthPx > bounds.right }
        }
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
