package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/**
 * Rounded, theme-tinted floating chat card that clips chat content to its
 * corners. It resizes from its corner grip only (see [FloatingChatController]).
 */
class FloatingChatCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val radius = CORNER_RADIUS_DP * density
    private val surface = GradientDrawable().apply { cornerRadius = radius }
    private var surfaceVisible = true

    init {
        // One gesture at a time: a second finger joins the first one's handle (drag strip,
        // header or grip) instead of starting a second drag or resize on another child.
        isMotionEventSplittingEnabled = false
        background = surface
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
                // Clear keeps the rounded clip but casts no shadow.
                outline.alpha = if (surfaceVisible) 1f else 0f
            }
        }
    }

    /** [color] includes the background opacity; [outline] is 0 for Clear. */
    fun setSurface(color: Int, outline: Int, elevated: Boolean) {
        surface.setColor(color)
        if (outline != 0) {
            surface.setStroke(density.toInt().coerceAtLeast(1), outline)
        } else {
            surface.setStroke(0, 0)
        }
        surfaceVisible = outline != 0
        elevation = if (elevated) ELEVATION_DP * density else 0f
        invalidateOutline()
    }

    private companion object {
        const val CORNER_RADIUS_DP = 16f
        const val ELEVATION_DP = 6f
    }
}
