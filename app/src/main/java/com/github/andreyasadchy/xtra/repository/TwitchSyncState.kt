package com.github.andreyasadchy.xtra.repository

import kotlinx.serialization.Serializable

/** A separate account-owned outbox. Local VideoPosition and Stats are never imported. */
@Serializable
data class TwitchProgress(
    val videoId: String,
    val title: String = "",
    val seconds: Long? = null,
    val baseline: Long? = null,
    val baselineKnown: Boolean = false,
    val revision: Long = 0,
    val pending: Boolean = false,
    val conflict: Boolean = false,
    val remote: Long? = null,
    val unavailable: Boolean = false,
    val remoteChosen: Boolean = false,
) {
    fun checkpoint(position: Long) = copy(seconds = position, revision = revision + 1, pending = true, remoteChosen = false)

    fun conflictsWith(observed: Long?): Boolean =
        observed != seconds && (!baselineKnown && observed != null || baselineKnown && observed != baseline)

    fun acknowledge(sent: TwitchProgress, observed: Long) = copy(
        baseline = observed, baselineKnown = true, remote = observed,
        pending = revision != sent.revision, conflict = false,
    )
}

@Serializable
data class TwitchRecentVideo(val videoId: String, val title: String, val seconds: Long)

@Serializable
data class TwitchStreak(val channelId: String, val channel: String, val count: Int, val timestamp: Long, val eventId: String)

@Serializable
data class TwitchSyncState(
    val progress: List<TwitchProgress> = emptyList(),
    val recent: List<TwitchRecentVideo> = emptyList(),
    val streaks: List<TwitchStreak> = emptyList(),
)

/** Counts observed wall time, not media position deltas, so speed and seeks cannot earn minutes. */
class TwitchPlaybackClock {
    private var identity: String? = null
    private var previousTime = 0L
    private var previousPosition = 0L
    private var wasPlaying = false
    private var watched = 0L
    var advancing = false
        private set
    val observedSeconds: Int get() = (watched / 1000).toInt()

    fun sample(key: String, now: Long, position: Long, playing: Boolean): Boolean {
        if (identity != key) {
            identity = key
            watched = 0
            wasPlaying = false
        }
        val elapsed = now - previousTime
        advancing = playing && wasPlaying && position > previousPosition && elapsed in 1..2500
        if (advancing) watched += elapsed
        previousTime = now
        previousPosition = position
        wasPlaying = playing
        if (watched < 60_000) return false
        watched -= 60_000
        return true
    }

    fun reset() { identity = null; wasPlaying = false; watched = 0; advancing = false }
}
