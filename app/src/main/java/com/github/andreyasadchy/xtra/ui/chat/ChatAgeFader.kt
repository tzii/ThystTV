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
            if (value) schedule() else recyclerView.removeCallbacks(tick)
        }

    var reveal = false
        set(value) {
            if (field == value) return
            field = value
            refresh()
        }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            schedule()
        }
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

    private fun schedule() {
        recyclerView.removeCallbacks(tick)
        if (enabled) recyclerView.postDelayed(tick, TICK_MS)
    }

    companion object {
        const val FADE_AFTER_MS = 10_000L
        private const val FADE_OUT_MS = 900L
        private const val FADE_IN_MS = 200L
        private const val TICK_MS = 500L
        private const val OLD = Long.MIN_VALUE / 2
        private val TAG_KEY = com.github.andreyasadchy.xtra.R.id.chatRowFadeTarget
    }
}
