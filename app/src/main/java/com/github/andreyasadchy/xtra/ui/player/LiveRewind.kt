package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import kotlin.math.abs
import kotlin.math.max

/**
 * Recording matching adapted from thiscallnet/Xtra LiveRewind.kt (fbffe567), AGPL-3.0.
 * ThystTV deliberately uses its established single-player VoD handoff, not a second
 * playback service. A recording is not guaranteed to exist, so a rewind can also play
 * the viewer's own [LiveDvr] buffer through that same handoff.
 */
internal data class LiveRewindCandidate(
    val video: Video,
    val createdAtMs: Long,
    val durationMs: Long,
)

internal data class PreparedLiveRewind(
    val origin: Stream,
    val video: Video,
    val playlistUrl: String,
    val positionMs: Long,
    val local: Boolean = false,
)

internal object LiveRewind {
    const val ORIGIN = "liveRewindOrigin"
    const val PLAYLIST = "liveRewindPlaylist"
    const val POSITION = "liveRewindPosition"
    const val LOCAL = "liveRewindLocal"
    const val MAX_START_DELTA_MS = 180_000L
    const val DURATION_QUANTUM_MS = 10_000L

    fun sameSession(expectedId: String?, expectedStart: String?, actualId: String?, actualStart: String?): Boolean =
        !actualId.isNullOrBlank() && !actualStart.isNullOrBlank() &&
            (expectedId.isNullOrBlank() || expectedId == actualId) &&
            (expectedStart.isNullOrBlank() || expectedStart == actualStart)

    fun selectRecording(candidates: List<LiveRewindCandidate>, streamStartMs: Long, nowMs: Long): LiveRewindCandidate? {
        if (streamStartMs <= 0 || streamStartMs > nowMs) return null
        val age = nowMs - streamStartMs
        val tolerance = max(120_000L, age / 10)
        return candidates.asSequence().filter {
            !it.video.id.isNullOrBlank() && it.createdAtMs > 0 && it.durationMs > 0 &&
                abs(it.createdAtMs - streamStartMs) <= MAX_START_DELTA_MS &&
                // A short archive from a just-ended broadcast can satisfy both
                // donor tolerances. It must actually extend into this session.
                (it.createdAtMs >= streamStartMs || it.durationMs > streamStartMs - it.createdAtMs) &&
                abs(it.durationMs - age) <= tolerance
        }.minWithOrNull(compareBy<LiveRewindCandidate> { abs(it.createdAtMs - streamStartMs) }
            .thenByDescending { it.createdAtMs })
    }

    /** Never seek beyond a recording's reported complete ten-second chunk. */
    fun targetPosition(durationMs: Long, rewindMs: Long?): Long {
        val edge = (durationMs.coerceAtLeast(0) / DURATION_QUANTUM_MS) * DURATION_QUANTUM_MS
        return if (rewindMs == null) 0 else (edge - rewindMs.coerceAtLeast(DURATION_QUANTUM_MS)).coerceAtLeast(0)
    }

    /** Twitch archives report their total; otherwise sum the listed segments. */
    fun mediaPlaylistDurationMs(body: String?): Long? {
        if (body?.trimStart()?.startsWith("#EXTM3U") != true) return null
        val seconds = Regex("""#EXT-X-TWITCH-TOTAL-SECS:([0-9.]+)""").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: Regex("""#EXTINF:([0-9.]+)""").findAll(body).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
        return (seconds * 1000).toLong().takeIf { it > 0 }
    }

    fun isPlayablePlaylist(body: String?, errorCode: Int?): Boolean = errorCode == null &&
        body?.trimStart()?.startsWith("#EXTM3U") == true &&
        (body.contains("#EXT-X-STREAM-INF:") || body.contains("#EXTINF:"))

    /** Playback above this multiple of realtime counts as catching up to live. */
    const val LIVE_CATCH_UP_SPEED = 1.05f

    /** Recording positions within this distance of the reported edge count as caught up. */
    const val LIVE_EDGE_SLACK_MS = 5_000L

    /** How often the edge is checked while a recording plays, controls shown or not. */
    const val LIVE_CATCH_UP_POLL_MS = 500L

    /** Real time left before the edge when the reset happens, so a poll always lands first. */
    const val LIVE_EDGE_REACTION_MS = 1_500L

    /** Faster playback covers more media per poll, so the caught-up zone grows with speed. */
    fun edgeSlackMs(speed: Float): Long = max(LIVE_EDGE_SLACK_MS, (speed * LIVE_EDGE_REACTION_MS).toLong())

    fun shouldResetSpeedAtLiveEdge(
        hasLiveRewindOrigin: Boolean,
        currentSpeed: Float?,
        positionMs: Long?,
        durationMs: Long?,
    ): Boolean {
        if (!hasLiveRewindOrigin) return false
        if (currentSpeed == null || currentSpeed <= LIVE_CATCH_UP_SPEED) return false
        if (positionMs == null || durationMs == null || durationMs <= 0L) return false
        return durationMs - positionMs <= edgeSlackMs(currentSpeed)
    }

    /**
     * The viewer's own buffer needs no network and no recording, so it wins whenever it
     * reaches back far enough. "From the beginning" is only ever a recording's to offer.
     */
    fun localBufferCovers(rewindMs: Long?, availableMs: Long): Boolean =
        rewindMs != null && availableMs >= rewindMs

    fun shouldShowFullscreenLiveRewind(
        videoType: String?,
        hasLiveRewindOrigin: Boolean,
        isMaximized: Boolean,
    ): Boolean =
        videoType == PlayerFragment.STREAM &&
            !hasLiveRewindOrigin &&
            isMaximized
}
