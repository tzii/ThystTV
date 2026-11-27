package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * Rounded, theme-tinted floating chat card that clips chat content to its
 * corners. It resizes from its corner grip only (see [FloatingChatController]).
 *
 * The top [dragBandHeight] pixels are a drag band that reserves no space: the
 * chat runs under it. A touch there goes to the chat as usual, so a tap still
 * opens a chatter or closes a poll card; once the finger passes the touch slop
 * the card cancels the chat's gesture and hands it, from its first touch, to
 * [dragListener]. Holding still there grabs the card just before the chat's own
 * long-press would fire. This runs in [dispatchTouchEvent], not
 * onInterceptTouchEvent, so a chat list that is still scrolling cannot veto the
 * drag with requestDisallowInterceptTouchEvent.
 */
class FloatingChatCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val radius = CORNER_RADIUS_DP * density
    private val surface = GradientDrawable().apply { cornerRadius = radius }
    private var surfaceVisible = true

    var dragListener: OnTouchListener? = null
    /** Height of the top drag band in pixels, 0 while the header handles drags. */
    var dragBandHeight = 0
    /** A child that keeps its own touches inside the band, such as the resize grip on a short card. */
    var dragBandExclusion: View? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    /** Before the chat's own long-press (a row's tooltip) fires, a still finger grabs the card. */
    private val grabDelay = ViewConfiguration.getLongPressTimeout() * 3L / 4
    private var bandDown: MotionEvent? = null
    private var bandPointerId = MotionEvent.INVALID_POINTER_ID
    private var dragging = false
    private val grabOnHold = Runnable { claimBand(null) }

    init {
        // One gesture at a time: a second finger joins the first one's handle (drag band,
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            cancelDrag()
            if (dragListener != null && ev.y >= 0f && ev.y < dragBandHeight && !inExclusion(ev)) {
                bandDown = MotionEvent.obtain(ev)
                bandPointerId = ev.getPointerId(0)
                postDelayed(grabOnHold, grabDelay)
            }
        }
        if (dragging) {
            dragListener?.onTouch(this, ev)
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) dragging = false
            return true
        }
        val start = bandDown
        if (start != null) {
            if (action == MotionEvent.ACTION_MOVE) {
                val index = ev.findPointerIndex(bandPointerId)
                if (index < 0) {
                    clearBand()
                } else {
                    // getRawX(int) needs API 29; the card is never scaled.
                    val rawX = ev.getX(index) - ev.x + ev.rawX
                    val rawY = ev.getY(index) - ev.y + ev.rawY
                    if (hypot(rawX - start.rawX, rawY - start.rawY) > touchSlop && claimBand(ev)) return true
                }
            } else if (action == MotionEvent.ACTION_POINTER_UP && ev.getPointerId(ev.actionIndex) == bandPointerId) {
                // The band finger lifted before it moved: leave the other fingers to the chat.
                clearBand()
            }
        }
        val inBand = bandDown != null
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) clearBand()
        val handled = super.dispatchTouchEvent(ev)
        // Keep the gesture coming even when nothing under the band takes the touch.
        return handled || inBand
    }

    /**
     * Hands the band gesture to [dragListener] from its first touch, then [ev]
     * if the finger moved; the chat under the band gets a cancel.
     */
    private fun claimBand(ev: MotionEvent?): Boolean {
        val start = bandDown ?: return false
        bandDown = null
        bandPointerId = MotionEvent.INVALID_POINTER_ID
        removeCallbacks(grabOnHold)
        val listener = dragListener
        val claimed = listener != null && listener.onTouch(this, start)
        if (claimed) {
            dragging = true
            val cancel = MotionEvent.obtain(ev ?: start).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            if (ev != null) listener?.onTouch(this, ev)
        }
        start.recycle()
        return claimed
    }

    /** Drops a pending band touch and ends a band drag, telling [dragListener] it was cancelled. */
    fun cancelDrag() {
        clearBand()
        if (dragging) {
            dragging = false
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            dragListener?.onTouch(this, cancel)
            cancel.recycle()
        }
    }

    private fun clearBand() {
        removeCallbacks(grabOnHold)
        bandDown?.recycle()
        bandDown = null
        bandPointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun inExclusion(ev: MotionEvent): Boolean {
        val view = dragBandExclusion ?: return false
        if (view.visibility != VISIBLE) return false
        val left = view.left + view.translationX
        val top = view.top + view.translationY
        return ev.x >= left && ev.x < left + view.width && ev.y >= top && ev.y < top + view.height
    }

    override fun onDetachedFromWindow() {
        cancelDrag()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val CORNER_RADIUS_DP = 16f
        const val ELEVATION_DP = 6f
    }
}
