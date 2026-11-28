package com.github.andreyasadchy.xtra.ui.chat

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Lays out [extraTopSpace] above the list so rows exist to slide in during a glide. */
internal class ChatLayoutManager(context: Context) : LinearLayoutManager(context) {
    var extraTopSpace = 0
    var onLayoutCompleted: (() -> Unit)? = null

    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        super.calculateExtraLayoutSpace(state, extraLayoutSpace)
        if (extraTopSpace > extraLayoutSpace[0]) extraLayoutSpace[0] = extraTopSpace
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        onLayoutCompleted?.invoke()
    }
}

/**
 * New lines glide in instead of jumping. The list still snaps to the newest
 * message with a normal layout; every row is then drawn offset by the distance
 * the content jumped and a critically damped spring brings the offset back to
 * zero. Only translation changes per frame, so no layout or rebinding happens
 * while it moves.
 */
internal class ChatFollowGlide(
    private val recyclerView: RecyclerView,
    private val layoutManager: ChatLayoutManager,
) : Runnable {

    var enabled = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) cancel()
            layoutManager.extraTopSpace = if (value) EXTRA_TOP_SPACE_SCREENS * recyclerView.height else 0
        }

    private var offset = 0f
    private var velocity = 0f
    private var running = false
    private var lastFrameNanos = 0L
    private var pendingPosition = RecyclerView.NO_POSITION
    private var pendingTop = 0

    private val attachListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            view.translationY = offset
        }

        override fun onChildViewDetachedFromWindow(view: View) {
            // Recycled rows must never carry an offset into another position.
            view.translationY = 0f
        }
    }

    init {
        recyclerView.addOnChildAttachStateChangeListener(attachListener)
        layoutManager.onLayoutCompleted = ::onLayoutCompleted
        recyclerView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (enabled && bottom - top != oldBottom - oldTop) {
                layoutManager.extraTopSpace = EXTRA_TOP_SPACE_SCREENS * (bottom - top)
            }
        }
    }

    /** The newest row on screen and its top, before the adapter changes. */
    fun capture(): Pair<Int, Int>? {
        if (!enabled) return null
        val last = recyclerView.getChildAt(recyclerView.childCount - 1) ?: return null
        val position = recyclerView.getChildAdapterPosition(last)
        if (position == RecyclerView.NO_POSITION) return null
        return position to last.top
    }

    /** Glide from the captured row once the next layout places it at [position]. */
    fun expect(position: Int, top: Int) {
        pendingPosition = position
        pendingTop = top
    }

    fun cancel() {
        pendingPosition = RecyclerView.NO_POSITION
        running = false
        recyclerView.removeCallbacks(this)
        if (offset != 0f) {
            offset = 0f
            applyOffset()
        }
        velocity = 0f
    }

    private fun onLayoutCompleted() {
        val position = pendingPosition
        if (position == RecyclerView.NO_POSITION) return
        pendingPosition = RecyclerView.NO_POSITION
        if (!enabled) return
        val anchor = layoutManager.findViewByPosition(position) ?: run {
            // The old rows scrolled out of the extra space: a long burst. Show it as is.
            cancel()
            return
        }
        val delta = pendingTop - anchor.top
        if (delta <= 0) return
        offset += delta
        // New rows appear softly while the list settles.
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            if (recyclerView.getChildAdapterPosition(child) > position) {
                child.alpha = 0f
                child.animate().alpha(1f).setDuration(NEW_ROW_FADE_MS).start()
            }
        }
        applyOffset()
        if (!running) {
            running = true
            lastFrameNanos = 0L
            recyclerView.postOnAnimation(this)
        }
    }

    override fun run() {
        if (!running) return
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) FRAME_S else ((now - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = now
        // Exact step of a critically damped spring pulling the offset to 0.
        val decay = exp(-OMEGA * dt)
        val nextOffset = (offset + (velocity + OMEGA * offset) * dt) * decay
        val nextVelocity = (velocity - OMEGA * (velocity + OMEGA * offset) * dt) * decay
        offset = nextOffset
        velocity = nextVelocity
        if (abs(offset) < 0.5f && abs(velocity) < 20f) {
            offset = 0f
            velocity = 0f
            running = false
            applyOffset()
            return
        }
        applyOffset()
        recyclerView.postOnAnimation(this)
    }

    private fun applyOffset() {
        for (i in 0 until recyclerView.childCount) {
            recyclerView.getChildAt(i).translationY = offset
        }
    }

    private companion object {
        /** Critically damped (ζ 1.0) at stiffness 520, as agreed for the floating chat. */
        val OMEGA = sqrt(520f)
        const val FRAME_S = 1f / 60f
        const val NEW_ROW_FADE_MS = 150L
        const val EXTRA_TOP_SPACE_SCREENS = 1
    }
}
