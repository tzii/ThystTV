package com.github.andreyasadchy.xtra.ui.chat

import android.os.SystemClock
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Optional "fade old messages" for the floating chat: a row fades away
 * [FADE_AFTER_MS] after it arrived, so a quiet chat leaves the picture clear.
 * Every row comes back while [reveal] is set (player controls up, or the
 * viewer scrolled back to read).
 *
 * Arrival times are kept in adapter order, so a row's age is known no matter
 * which view shows it. Messages that were already there count as old.
 */
internal class ChatAgeFader(
    private val recyclerView: RecyclerView,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private val arrivals = ArrayDeque<Long>()

    var enabled = false
        set(value) {
            if (field == value) return
            field = value
            refresh()
            schedule()
        }

    var reveal = false
        set(value) {
            if (field == value) return
            field = value
            refresh()
            schedule()
        }

    /** Uptime the pending fade runs at, or [NONE]. */
    private var tickAt = NONE

    private val tick = Runnable {
        tickAt = NONE
        refresh()
        schedule()
    }

    private val attachListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            val target = targetAlpha(recyclerView.getChildAdapterPosition(view))
            view.animate().cancel()
            view.alpha = target
            view.setTag(TAG_KEY, target)
        }

        override fun onChildViewDetachedFromWindow(view: View) {
            view.animate().cancel()
            view.alpha = 1f
            view.setTag(TAG_KEY, null)
        }
    }

    init {
        recyclerView.addOnChildAttachStateChangeListener(attachListener)
    }

    fun onAppended(removedFromHead: Int, inserted: Int) {
        repeat(minOf(removedFromHead, arrivals.size)) { arrivals.removeFirst() }
        val now = clock()
        repeat(inserted) { arrivals.addLast(now) }
        // A fade already waiting will schedule the next one; otherwise these rows start it.
        if (inserted > 0 && tickAt == NONE) schedule()
    }

    fun onPrepended(count: Int) {
        repeat(count) { arrivals.addFirst(OLD) }
    }

    fun onCleared(count: Int) {
        repeat(minOf(count, arrivals.size)) { arrivals.removeFirst() }
    }

    /** Re-aligns with the adapter if a change slipped past (rows added before this existed). */
    fun sync(itemCount: Int) {
        while (arrivals.size < itemCount) arrivals.addFirst(OLD)
        while (arrivals.size > itemCount) arrivals.removeFirst()
    }

    fun dispose() {
        recyclerView.removeCallbacks(tick)
        tickAt = NONE
        recyclerView.removeOnChildAttachStateChangeListener(attachListener)
    }

    internal fun targetAlpha(position: Int): Float {
        if (!enabled || reveal || position == RecyclerView.NO_POSITION) return 1f
        val arrived = arrivals.getOrNull(position) ?: return 1f
        return if (clock() - arrived >= FADE_AFTER_MS) 0f else 1f
    }

    private fun refresh() {
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val target = targetAlpha(recyclerView.getChildAdapterPosition(child))
            if (child.getTag(TAG_KEY) == target) continue
            child.setTag(TAG_KEY, target)
            child.animate().alpha(target)
                .setDuration(if (target == 0f) FADE_OUT_MS else FADE_IN_MS)
                .start()
        }
    }

    /**
     * Wakes up when the oldest row still showing reaches [FADE_AFTER_MS], not
     * on a fixed tick; nothing runs while the fade is off or rows are revealed.
     */
    private fun schedule() {
        recyclerView.removeCallbacks(tick)
        tickAt = NONE
        if (!enabled || reveal) return
        val now = clock()
        // Arrivals only grow toward the end, so the rows still to fade are the last ones.
        var next = NONE
        for (i in arrivals.indices.reversed()) {
            val arrived = arrivals[i]
            if (now - arrived >= FADE_AFTER_MS) break
            next = arrived
        }
        if (next == NONE) return
        tickAt = next + FADE_AFTER_MS
        recyclerView.postDelayed(tick, tickAt - now)
    }

    companion object {
        const val FADE_AFTER_MS = 10_000L
        private const val FADE_OUT_MS = 900L
        private const val FADE_IN_MS = 200L
        private const val OLD = Long.MIN_VALUE / 2
        private const val NONE = Long.MAX_VALUE
        private val TAG_KEY = com.github.andreyasadchy.xtra.R.id.chatRowFadeTarget
    }
}
