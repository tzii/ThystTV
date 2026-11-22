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
 * The floating chat card: a rounded, theme-tinted surface over the video that
 * clips the chat to its corners.
 *
 * With [passThrough] set the card ignores touches, so taps and swipes reach
 * the player underneath. With [pinchEnabled] set a two-finger pinch anywhere on
 * the card resizes it instead of scrolling the chat.
 */
class FloatingChatCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    interface PinchListener {
        fun onPinchStart()
        fun onPinch(scale: Float)
        fun onPinchEnd()
    }

    private val density = resources.displayMetrics.density
    private val radius = CORNER_RADIUS_DP * density
    private val surface = GradientDrawable().apply { cornerRadius = radius }
    private var surfaceVisible = true

    var passThrough = false
    var pinchEnabled = false
    var pinchListener: PinchListener? = null
    private var pinching = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var startSpan = 0f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (!pinchEnabled) return false
            startSpan = detector.currentSpan.coerceAtLeast(1f)
            pinching = true
            pinchListener?.onPinchStart()
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (passThrough && ev.actionMasked == MotionEvent.ACTION_DOWN) return false
        if (pinchEnabled || pinching) {
            val wasPinching = pinching
            scaleDetector.onTouchEvent(ev)
            if (pinching) {
                if (!wasPinching) {
                    // The chat list may already be scrolling with the first finger.
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
