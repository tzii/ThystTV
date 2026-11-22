package com.github.andreyasadchy.xtra.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.hypot

/**
 * The floating chat header. Its buttons fill a narrow card, so a drag that
 * starts on a button moves the card too: once the finger passes the touch
 * slop the header takes the gesture from the button and hands it, from its
 * first touch, to [dragListener]. A tap still reaches the button.
 */
class FloatingChatHeaderLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var dragListener: OnTouchListener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var down: MotionEvent? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val listener = dragListener ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clearDown()
                down = MotionEvent.obtain(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                val start = down ?: return false
                if (hypot(ev.rawX - start.rawX, ev.rawY - start.rawY) > touchSlop) {
                    down = null
                    // The button under the finger gets a cancel; the drag starts where the touch did.
                    val claimed = listener.onTouch(this, start)
                    start.recycle()
                    return claimed
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> clearDown()
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val listener = dragListener ?: return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) clearDown()
        return listener.onTouch(this, event)
    }

    private fun clearDown() {
        down?.recycle()
        down = null
    }

    override fun onDetachedFromWindow() {
        clearDown()
        super.onDetachedFromWindow()
    }
}
