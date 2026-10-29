package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.abs

enum class PlayerChatSwipeAction {
    SHOW,
    HIDE,
}

/**
 * Pure rules for the full-screen sideways swipe that shows or hides chat.
 *
 * Positions are player-surface pixels. The chat side is the end edge, where
 * the sidebar opens (right, or left in RTL). Live streams have no horizontal
 * gesture, so the swipe may start anywhere. Seekable media keep seek and speed
 * on the surface and accept the swipe only from a strip just inside the edge
 * exclusion on the chat side, away from the system Back gesture.
 */
object PlayerChatSwipePolicy {

    /** Width of the seekable-media start strip on the chat side. */
    const val SEEKABLE_STRIP_DP = 56f

    /** The swipe acts once it travels this far, or [TRIGGER_FRACTION] of the width if larger. */
    const val MIN_TRIGGER_DP = 48f
    const val TRIGGER_FRACTION = 0.08f

    fun startsChatSwipe(
        isLive: Boolean,
        startX: Float,
        surfaceWidthPx: Float,
        chatSideExclusionPx: Float,
        stripWidthPx: Float,
        isRtl: Boolean,
    ): Boolean {
        if (surfaceWidthPx <= 0f) return false
        if (isLive) return true
        val fromChatEdge = if (isRtl) startX else surfaceWidthPx - startX
        return fromChatEdge > chatSideExclusionPx && fromChatEdge <= chatSideExclusionPx + stripWidthPx
    }

    fun triggerDistancePx(surfaceWidthPx: Float, density: Float): Float {
        return maxOf(MIN_TRIGGER_DP * density, surfaceWidthPx * TRIGGER_FRACTION)
    }

    /** The swipe has travelled far enough, and mostly sideways. */
    fun reachedTrigger(dxPx: Float, dyPx: Float, triggerPx: Float): Boolean {
        return abs(dxPx) >= triggerPx && abs(dxPx) > abs(dyPx)
    }

    /**
     * Moving away from the chat side pulls chat in; moving toward it pushes
     * chat away. Returns null when chat is already in the requested state.
     */
    fun action(dxPx: Float, isRtl: Boolean, chatVisible: Boolean): PlayerChatSwipeAction? {
        val towardChatSide = if (isRtl) dxPx < 0f else dxPx > 0f
        return when {
            towardChatSide && chatVisible -> PlayerChatSwipeAction.HIDE
            !towardChatSide && !chatVisible -> PlayerChatSwipeAction.SHOW
            else -> null
        }
    }
}
