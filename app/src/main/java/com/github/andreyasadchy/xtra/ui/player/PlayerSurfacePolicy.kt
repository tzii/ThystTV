package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * Classifies the active player surface and derives gesture-feedback placement.
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

data class PlayerFeedbackPlacement(
    val gravity: Int,
    val maxWidthPx: Int,
    val marginStartPx: Int,
    val marginEndPx: Int,
    val topPaddingPx: Int,
    val fixedContainerWidthPx: Int?,
)

object PlayerSurfacePolicy {

    const val LARGE_SURFACE_MIN_WIDTH_DP = 600
    const val EDGE_FEEDBACK_MAX_WIDTH_DP = 280
    const val CENTER_FEEDBACK_MAX_WIDTH_DP = 360
    const val FEEDBACK_HOLD_MS = 800L
    const val FEEDBACK_FADE_MS = 150L
    private const val FEEDBACK_MARGIN_DP = 16
    private const val COMPACT_TOP_PADDING_DP = 24

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

    fun placementFor(
        kind: PlayerGestureFeedbackKind,
        surfaceClass: PlayerSurfaceClass,
        density: Float,
        insetStartPx: Int = 0,
        insetEndPx: Int = 0,
    ): PlayerFeedbackPlacement {
        val margin = (FEEDBACK_MARGIN_DP * density).toInt()
        return when (surfaceClass) {
            PlayerSurfaceClass.COMPACT -> PlayerFeedbackPlacement(
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                maxWidthPx = (CENTER_FEEDBACK_MAX_WIDTH_DP * density).toInt(),
                marginStartPx = margin,
                marginEndPx = margin,
                topPaddingPx = (COMPACT_TOP_PADDING_DP * density).toInt(),
                fixedContainerWidthPx = null,
            )
            PlayerSurfaceClass.LARGE -> when (kind) {
                PlayerGestureFeedbackKind.BRIGHTNESS -> PlayerFeedbackPlacement(
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START,
                    maxWidthPx = (EDGE_FEEDBACK_MAX_WIDTH_DP * density).toInt(),
                    marginStartPx = margin + insetStartPx.coerceAtLeast(0),
                    marginEndPx = margin,
                    topPaddingPx = 0,
                    fixedContainerWidthPx = (EDGE_FEEDBACK_MAX_WIDTH_DP * density).toInt(),
                )
                PlayerGestureFeedbackKind.DEVICE_VOLUME -> PlayerFeedbackPlacement(
                    gravity = Gravity.CENTER_VERTICAL or Gravity.END,
                    maxWidthPx = (EDGE_FEEDBACK_MAX_WIDTH_DP * density).toInt(),
                    marginStartPx = margin,
                    marginEndPx = margin + insetEndPx.coerceAtLeast(0),
                    topPaddingPx = 0,
                    fixedContainerWidthPx = (EDGE_FEEDBACK_MAX_WIDTH_DP * density).toInt(),
                )
                PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackKind.PLAYBACK_SPEED, PlayerGestureFeedbackKind.PINCH -> PlayerFeedbackPlacement(
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    maxWidthPx = (CENTER_FEEDBACK_MAX_WIDTH_DP * density).toInt(),
                    marginStartPx = margin,
                    marginEndPx = margin,
                    topPaddingPx = (COMPACT_TOP_PADDING_DP * density).toInt(),
                    fixedContainerWidthPx = null,
                )
            }
        }
    }

    fun applyPlacement(feedbackRoot: View, container: LinearLayout, placement: PlayerFeedbackPlacement) {
        if (feedbackRoot.parent is android.widget.FrameLayout) {
            feedbackRoot.layoutParams = (feedbackRoot.layoutParams as? android.widget.FrameLayout.LayoutParams)?.apply {
                gravity = placement.gravity
                leftMargin = placement.marginStartPx
                rightMargin = placement.marginEndPx
            } ?: feedbackRoot.layoutParams
        }
        feedbackRoot.setPadding(
            feedbackRoot.paddingLeft,
            placement.topPaddingPx,
            feedbackRoot.paddingRight,
            feedbackRoot.paddingBottom,
        )
        container.layoutParams = container.layoutParams.let { params ->
            // LinearLayout has no public maxWidth setter; use a fixed width for
            // edge indicators and rely on the XML maxWidth elsewhere.
            (params as? LinearLayout.LayoutParams)?.apply {
                width = placement.fixedContainerWidthPx ?: LinearLayout.LayoutParams.WRAP_CONTENT
            } ?: params
        }
    }

    /**
     * Places and shows a gesture-feedback pill using player-surface dimensions,
     * then schedules the shared idle-hold hide.
     */
    fun presentFeedback(
        context: Context,
        feedbackRoot: View,
        container: LinearLayout,
        kind: PlayerGestureFeedbackKind,
        surfaceWidthPx: Int,
        insets: androidx.core.graphics.Insets?,
        hideRunnable: Runnable,
    ) {
        applyPlacement(
            feedbackRoot = feedbackRoot,
            container = container,
            placement = placementFor(
                kind = kind,
                surfaceClass = classify(surfaceWidthPx, context.resources.displayMetrics.density),
                density = context.resources.displayMetrics.density,
                insetStartPx = insets?.left ?: 0,
                insetEndPx = insets?.right ?: 0,
            ),
        )
        feedbackRoot.animate().cancel()
        feedbackRoot.alpha = 1f
        feedbackRoot.visibility = View.VISIBLE
        feedbackRoot.removeCallbacks(hideRunnable)
        feedbackRoot.postDelayed(hideRunnable, FEEDBACK_HOLD_MS)
    }
}
