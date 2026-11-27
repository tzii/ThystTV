package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/**
 * Rounded, theme-tinted floating chat card that clips chat content to its
 * corners and supports two-finger pinch-to-resize at any time.
 */
class FloatingChatCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    interface PinchListener {
        fun onPinchStart(): Boolean
        fun onPinch(scale: Float)
        fun onPinchEnd()
    }

    private val density = resources.displayMetrics.density
    private val radius = CORNER_RADIUS_DP * density
    private val surface = GradientDrawable().apply { cornerRadius = radius }
    private var surfaceVisible = true

    var pinchListener: PinchListener? = null
    private var pinching = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var startSpan = 0f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            val listener = pinchListener ?: return false
            if (!listener.onPinchStart()) return false
            startSpan = detector.currentSpan.coerceAtLeast(1f)
            pinching = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            pinchListener?.onPinch(detector.currentSpan / startSpan)
            return false
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            endPinch()
        }
    }).apply { isQuickScaleEnabled = false }

    init {
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

    /** Clears active pinch tracking when the card is hidden mid-gesture. */
    fun cancelPinch() {
        pinching = false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (pinchListener != null || pinching) {
            val wasPinching = pinching
            scaleDetector.onTouchEvent(ev)
            if (pinching) {
                if (!wasPinching) {
                    // The chat list, drag or grip resize may already own the first finger.
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) endPinch()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun endPinch() {
        if (!pinching) return
        pinching = false
        pinchListener?.onPinchEnd()
    }

    private companion object {
        const val CORNER_RADIUS_DP = 16f
        const val ELEVATION_DP = 6f
    }
}
