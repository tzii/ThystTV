package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A local replay ready to hand to the player. */
internal data class LocalReplay(val playlistUrl: String, val positionMs: Long, val availableMs: Long)

/**
 * Keeps a replay's playlist growing while the player reads local files instead of the live
 * stream. Nothing else fetches new segments during that time. When the stream ends, stops
 * making progress for a long time or the buffer reaches its limit, the playlist is closed so
 * the player finishes what is saved and returns to live.
 */
@OptIn(UnstableApi::class)
internal class LiveDvrRecorder(
    private val buffer: LiveDvrBuffer,
    private val upstream: DataSource.Factory,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val pollMs: Long = POLL_MS,
    private val noProgressLimitMs: Long = NO_PROGRESS_LIMIT_MS,
) {
    private var lastProgress = 0L

    suspend fun run() {
        lastProgress = now()
        while (true) {
            delay(pollMs)
            var finished = false
            try {
                finished = recordOnce()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A failed poll is ordinary while the network recovers; the limit below handles the rest.
            }
            publishProgress()
            if (finished || now() - lastProgress >= noProgressLimitMs) break
        }
        buffer.publish(true)
    }

    /** True when there is nothing further to capture. */
    private suspend fun recordOnce(): Boolean {
        val url = buffer.lastPlaylistUrl ?: return true
        val body = runInterruptible { ByteArrayOutputStream().also { read(url, MAX_PLAYLIST_BYTES, it::write) }.toString(Charsets.UTF_8.name()) }
        val playlist = DvrPlaylistParser.parse(body, url) ?: return false
        buffer.onPlaylist(playlist)
        for (segment in buffer.missing(playlist)) {
            if (!buffer.canGrow()) return true
            buffer.missingInit(segment)?.let { download(it) }
            download(segment.url)
            // Right away: catching up can take longer than the player waits for something new.
            publishProgress()
        }
        return playlist.ended
    }

    private fun publishProgress() {
        if (buffer.publish(false)) lastProgress = now()
    }

    private suspend fun download(url: String) {
        val writer = buffer.openWriter(url) ?: return
        var finished = false
        try {
            runInterruptible { read(url, MAX_SEGMENT_BYTES, writer::write) }
            writer.finish()
            finished = true
        } finally {
            if (!finished) writer.abort()
        }
    }

    private fun read(url: String, limit: Long, sink: (ByteArray, Int, Int) -> Unit) {
        val source = upstream.createDataSource()
        try {
            source.open(DataSpec(url.toUri()))
            val chunk = ByteArray(READ_BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = source.read(chunk, 0, chunk.size)
                if (read == C.RESULT_END_OF_INPUT) return
                total += read
                check(total <= limit) { "Response too large" }
                sink(chunk, 0, read)
            }
        } finally {
            source.close()
        }
    }

    companion object {
        const val POLL_MS = 2_000L

        /**
         * Long enough to outlast an ordinary network drop, since one stalled download alone can
         * take over 10 seconds to fail. A viewer who reaches the end of the saved video during
         * the wait gets the player's own stuck-playlist error, which also returns to live.
         */
        const val NO_PROGRESS_LIMIT_MS = 60_000L
        private const val READ_BUFFER_BYTES = 16 * 1024
        private const val MAX_PLAYLIST_BYTES = 4L * 1024 * 1024
        private const val MAX_SEGMENT_BYTES = 256L * 1024 * 1024
    }
}

/**
 * Rewind without a recording: an opt-in rolling copy of the live segments the player has
 * already received, on disk under the app's cache, kept for one channel at a time.
 *
 * It outlives the player screens because a rewind and "Go live" each replace the player
 * fragment and source. So closing a source only starts a grace period, which the matching
 * next start cancels; otherwise the copy is deleted. Leftovers from a killed process go
 * when the next capture begins.
 */
@Singleton
class LiveDvr internal constructor(cacheDir: File, private val scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.cacheDir, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private class Session(
        val channel: String,
        val maxBytes: Long,
        val directory: File,
        val buffer: LiveDvrBuffer,
        var upstream: DataSource.Factory,
    ) {
        var recorder: Job? = null
        var closing: Job? = null
    }

    private val root = File(cacheDir, DIRECTORY)
    private val lock = Any()
    private var session: Session? = null

    /**
     * Called for every live start. Returns the buffer to capture into, keeping what the same
     * channel already saved, or null when the feature is off.
     */
    internal fun beginLive(channel: String?, enabled: Boolean, maxBytes: Long, upstream: DataSource.Factory): LiveDvrBuffer? = synchronized(lock) {
        val current = session
        if (!enabled || channel.isNullOrBlank()) {
            clearLocked()
            return null
        }
        if (current != null && current.channel == channel && current.maxBytes == maxBytes) {
            current.closing?.cancel()
            current.recorder?.cancel()
            current.recorder = null
            current.upstream = upstream
            current.buffer.unpin()
            return current.buffer
        }
        clearLocked()
        val directory = File(root, UUID.randomUUID().toString())
        if (!directory.mkdirs()) return null
        scope.launch {
            // Listed first: a capture begun after this listing can't be mistaken for a leftover.
            val existing = root.listFiles().orEmpty()
            val inUse = synchronized(lock) { session?.directory }
            existing.filter { it != inUse }.forEach { it.deleteRecursively() }
        }
        val buffer = LiveDvrBuffer(directory, maxBytes)
        session = Session(channel, maxBytes, directory, buffer, upstream)
        buffer
    }

    /** Whether a media URL is the current local replay. */
    fun isReplayUri(uri: String?): Boolean = synchronized(lock) {
        uri != null && session?.let { Uri.fromFile(it.buffer.replayFile).toString() == uri } == true
    }

    /** The saved playback a rewind on this channel could use. */
    fun availableMs(channel: String?): Long = synchronized(lock) {
        session?.takeIf { it.channel == channel }?.buffer?.availableMs() ?: 0L
    }

    /**
     * Freezes the saved segments into a playlist and starts recording what happens next. With
     * no [rewindMs] the replay starts from the oldest saved moment. [cancelReplay] undoes this
     * when the handoff doesn't go ahead.
     */
    internal suspend fun prepareReplay(channel: String?, rewindMs: Long?): LocalReplay? = withContext(Dispatchers.IO) {
        val current = synchronized(lock) { session?.takeIf { it.channel == channel } } ?: return@withContext null
        current.buffer.pin()
        current.buffer.publish(false)
        val durationMs = current.buffer.publishedDurationMs()
        if (durationMs <= 0L) {
            current.buffer.unpin()
            return@withContext null
        }
        synchronized(lock) {
            if (session !== current) return@withContext null
            current.recorder?.cancel()
            current.recorder = scope.launch { LiveDvrRecorder(current.buffer, current.upstream).run() }
        }
        LocalReplay(
            playlistUrl = Uri.fromFile(current.buffer.replayFile).toString(),
            positionMs = rewindMs?.let { (durationMs - it).coerceAtLeast(0L) } ?: 0L,
            availableMs = durationMs,
        )
    }

    /** The replay never reached the player, which is still receiving the live stream. */
    fun cancelReplay() = synchronized(lock) {
        session?.let {
            it.recorder?.cancel()
            it.recorder = null
            it.buffer.unpin()
        }
    }

    fun replayStarted() = synchronized(lock) {
        session?.closing?.cancel()
    }

    /** A source that is neither this channel's live stream nor its replay. */
    fun otherSourceStarted() = synchronized(lock) { clearLocked() }

    /** Could be the first half of a rewind or "Go live", so wait to see what starts next. */
    fun sourceClosed() = synchronized(lock) {
        val current = session ?: return
        current.closing?.cancel()
        current.closing = scope.launch {
            delay(CLOSE_GRACE_MS)
            synchronized(lock) {
                if (session === current) clearLocked()
            }
        }
    }

    private fun clearLocked() {
        val current = session ?: return
        session = null
        current.closing?.cancel()
        current.recorder?.cancel()
        scope.launch { current.buffer.close() }
    }

    companion object {
        private const val DIRECTORY = "live-dvr"

        /** As long as a rewind's start may take before it gives up and returns to live. */
        private const val CLOSE_GRACE_MS = 30_000L
        private const val DEFAULT_SIZE_MB = 500L
        private const val BYTES_PER_MB = 1024L * 1024L

        /** The size setting, in megabytes, as a byte limit. */
        fun maxBytes(setting: String?): Long = (setting?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_SIZE_MB) * BYTES_PER_MB
    }
}
