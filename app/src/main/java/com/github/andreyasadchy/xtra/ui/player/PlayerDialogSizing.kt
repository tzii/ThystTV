package com.github.andreyasadchy.xtra.ui.player

import android.app.Dialog
import android.os.Build
import android.view.Gravity
import android.view.Window
import kotlin.math.min

/**
 * Shared responsive presentation policy for player control surfaces (Quality,
 * Speed, Stream volume, More).
 *
 * Compact player surfaces keep the current bottom-anchored phone sheets.
 * Large/windowed surfaces (>=600dp) use centered, constrained surfaces capped
 * at 420dp instead of full-width sheets. Sizes derive from the dialog's own
 * window metrics so split-screen and freeform windows measure correctly.
 */
object PlayerDialogSizing {

    const val MAX_PANEL_WIDTH_DP = 420
    private const val COMPACT_MAX_PANEL_WIDTH_DP = 500
    private const val COMPACT_HORIZONTAL_MARGIN_DP = 40
    private const val LARGE_HORIZONTAL_MARGIN_DP = 32

    fun isLargeSurface(surfaceWidthPx: Int, density: Float): Boolean {
        return PlayerSurfacePolicy.classify(surfaceWidthPx, density) == PlayerSurfaceClass.LARGE
    }

    fun panelWidthPx(surfaceWidthPx: Int, density: Float): Int {
        return if (isLargeSurface(surfaceWidthPx, density)) {
            min(
                dp(MAX_PANEL_WIDTH_DP, density),
                surfaceWidthPx - dp(LARGE_HORIZONTAL_MARGIN_DP, density),
            ).coerceAtLeast(0)
        } else {
            min(
                dp(COMPACT_MAX_PANEL_WIDTH_DP, density),
                surfaceWidthPx - dp(COMPACT_HORIZONTAL_MARGIN_DP, density),
            ).coerceAtLeast(0)
        }
    }

    fun windowGravity(surfaceWidthPx: Int, density: Float): Int {
        return if (isLargeSurface(surfaceWidthPx, density)) {
            Gravity.CENTER
        } else {
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
    }

    /**
     * The dialog's own window width, which follows split-screen and freeform
     * bounds, unlike activity display metrics.
     */
    fun surfaceWidthPx(dialog: Dialog): Int {
        val window: Window = dialog.window ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.windowManager?.currentWindowMetrics?.bounds?.width() ?: 0
        } else {
            window.windowManager?.defaultDisplay?.width ?: 0
        }
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}
