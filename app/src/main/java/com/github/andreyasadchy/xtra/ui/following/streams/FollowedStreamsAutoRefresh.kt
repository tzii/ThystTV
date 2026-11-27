package com.github.andreyasadchy.xtra.ui.following.streams

/**
 * When the Following > Live list reloads by itself, so streams that ended
 * leave it without a pull to refresh: every [INTERVAL_MS] while the list is
 * on screen, and at once when it comes back on screen with an older list.
 */
internal object FollowedStreamsAutoRefresh {
    const val INTERVAL_MS = 2 * 60 * 1000L

    /** How long to wait from [now] until the list loaded at [loadedAt] is due a reload; 0 when it already is. */
    fun delayUntilDue(loadedAt: Long, now: Long, interval: Long = INTERVAL_MS): Long =
        (loadedAt + interval - now).coerceIn(0L, interval)
}
