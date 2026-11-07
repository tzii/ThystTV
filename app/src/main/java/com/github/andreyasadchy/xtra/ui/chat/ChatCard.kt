package com.github.andreyasadchy.xtra.ui.chat

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A raid, poll or prediction card as the chat shows it. [endsAt] is the
 * [SystemClock.elapsedRealtime] at which voting closes; the view counts down to it.
 */
data class ChatCard<out T>(val item: T, val endsAt: Long? = null)

/** What an update means for its card. */
internal enum class CardPhase {
    /** Voting is open: show the card, with its countdown if it has one. */
    OPEN,

    /** A lock or a result: show it without a countdown, then hide it after [ChatCardSlot.RESULT_MS]. */
    RESULT,

    /** Any other status: hide the card and keep it hidden until the next one. */
    ENDED,
}

/**
 * One raid, poll or prediction card held as a single value, [card], which is null while hidden.
 *
 * This replaces five fields per card (the last id, a closed flag, a countdown timer, a hide
 * job and the seconds left) that the chat view used to write from many places. The rules
 * live here instead: a new id clears an earlier dismissal, `reopen` shows a dismissed card
 * again (a poll result after the viewer closed the vote), a result hides itself after
 * [RESULT_MS], and the countdown is a deadline, so it is right after sleep and needs no
 * timer while the chat is not on screen.
 */
internal class ChatCardSlot<T : Any>(
    private val scope: CoroutineScope,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _card = MutableStateFlow<ChatCard<T>?>(null)
    val card: StateFlow<ChatCard<T>?> = _card.asStateFlow()

    private var id: String? = null
    private var dismissed = false
    private var endsAt: Long? = null
    private var hideJob: Job? = null

    /**
     * Applies an update about [id] and returns whether [id] is new. [countdownMs], the time
     * left to vote, is read only for a new id, so later updates keep the same deadline.
     */
    @Synchronized
    fun update(
        id: String?,
        item: T,
        phase: CardPhase,
        reopen: Boolean = false,
        countdownMs: () -> Long? = { null },
    ): Boolean {
        val isNew = id != this.id
        if (isNew) {
            this.id = id
            dismissed = false
            hideJob?.cancel()
            hideJob = null
            endsAt = countdownMs()?.takeIf { it >= 1000 }?.let { clock() + it }
        } else if (reopen) {
            dismissed = false
        }
        if (!dismissed) {
            when (phase) {
                CardPhase.OPEN -> _card.value = ChatCard(item, endsAt)
                CardPhase.RESULT -> {
                    endsAt = null
                    _card.value = ChatCard(item)
                    hideJob?.cancel()
                    hideJob = scope.launch {
                        delay(RESULT_MS)
                        synchronized(this@ChatCardSlot) {
                            // A newer update cancels this job; do not hide what it showed.
                            if (isActive) _card.value = null
                        }
                    }
                }
                CardPhase.ENDED -> close()
            }
        }
        return isNew
    }

    /** The viewer closed the card, or its event ended: hidden until a new id or a reopen. */
    @Synchronized
    fun close() {
        dismissed = true
        endsAt = null
        hideJob?.cancel()
        hideJob = null
        _card.value = null
    }

    /** The chat disconnected: hide the card and forget which one it was. */
    @Synchronized
    fun reset() {
        close()
        id = null
    }

    companion object {
        /** How long a lock or result stays on screen. */
        const val RESULT_MS = 20_000L
    }
}

internal fun pollPhase(status: String?) = when (status) {
    "ACTIVE" -> CardPhase.OPEN
    "COMPLETED", "TERMINATED" -> CardPhase.RESULT
    else -> CardPhase.ENDED
}

/** A poll result shows even if the viewer closed the vote. */
internal fun pollReopens(status: String?) = status == "COMPLETED" || status == "TERMINATED"

internal fun predictionPhase(status: String?) = when (status) {
    "ACTIVE" -> CardPhase.OPEN
    "LOCKED", "CANCELED", "CANCEL_PENDING", "RESOLVED", "RESOLVE_PENDING" -> CardPhase.RESULT
    else -> CardPhase.ENDED
}

/** A lock or a pending outcome shows again after the viewer closed the card; the final outcome does not. */
internal fun predictionReopens(status: String?) =
    status == "LOCKED" || status == "CANCEL_PENDING" || status == "RESOLVE_PENDING"

/** Whole seconds left until [endsAt], rounded up as a countdown shows them. */
internal fun secondsUntil(endsAt: Long, now: Long): Long = ((endsAt - now + 999) / 1000).coerceAtLeast(0)

/** Milliseconds until the countdown to [endsAt] should next change, or null once it shows zero. */
internal fun nextTickIn(endsAt: Long, now: Long): Long? {
    val shown = secondsUntil(endsAt, now)
    return if (shown == 0L) null else endsAt - now - (shown - 1) * 1000
}
