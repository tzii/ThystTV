package com.github.andreyasadchy.xtra.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.common.util.Util
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

internal data class DvrSegment(
    val url: String,
    val durationMs: Long,
    val sequence: Long,
    val programDateTimeMs: Long?,
    val discontinuity: Boolean,
    val initUrl: String?,
)

internal data class DvrPlaylist(val url: String, val segments: List<DvrSegment>, val ended: Boolean)

@OptIn(UnstableApi::class)
internal object DvrPlaylistParser {
    /** Null for a multivariant playlist or anything else that is not a media playlist. */
    fun parse(body: String, baseUrl: String): DvrPlaylist? {
        if (!body.trimStart().startsWith("#EXTM3U") || body.contains("#EXT-X-STREAM-INF")) return null
        var sequence = 0L
        var durationMs: Long? = null
        var dateTimeMs: Long? = null
        var discontinuity = false
        var initUrl: String? = null
        var encrypted = false
        var unsupported = false
        var ended = false
        val segments = ArrayList<DvrSegment>()
        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> line.substringAfter(':').toLongOrNull()?.let { sequence = it }
                line.startsWith("#EXTINF:") -> durationMs = line.substringAfter(':').substringBefore(',').toDoubleOrNull()?.times(1000)?.toLong()
                line.startsWith("#EXT-X-PROGRAM-DATE-TIME:") -> dateTimeMs = parseDateTime(line.substringAfter(':'))
                line == "#EXT-X-DISCONTINUITY" -> discontinuity = true
                line.startsWith("#EXT-X-MAP:") -> {
                    initUrl = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)?.let { UriUtil.resolve(baseUrl, it) }
                    unsupported = unsupported || line.contains("BYTERANGE")
                }
                line.startsWith("#EXT-X-KEY:") -> encrypted = !line.contains("METHOD=NONE")
                line.startsWith("#EXT-X-BYTERANGE:") -> unsupported = true
                line == "#EXT-X-ENDLIST" -> ended = true
                line.startsWith("#") -> {}
                else -> {
                    val duration = durationMs
                    if (duration != null) {
                        if (duration > 0 && !encrypted && !unsupported) {
                            segments.add(DvrSegment(UriUtil.resolve(baseUrl, line), duration, sequence, dateTimeMs, discontinuity, initUrl))
                        }
                        sequence++
                        dateTimeMs = dateTimeMs?.plus(duration)
                        discontinuity = false
                        unsupported = false
                    }
                    durationMs = null
                }
            }
        }
        return DvrPlaylist(baseUrl, segments, ended)
    }

    private fun parseDateTime(value: String): Long? = try {
        Util.parseXsDateTime(value.trim())
    } catch (_: Exception) {
        null
    }
}

/**
 * Bytes of one segment on their way into the buffer. A disk failure only abandons this
 * copy: the data source feeding it must keep serving the player either way.
 */
internal class DvrSegmentWriter internal constructor(
    private val buffer: LiveDvrBuffer,
    private val url: String,
    private val file: File,
) {
    private var output: BufferedOutputStream? = try {
        BufferedOutputStream(FileOutputStream(file), WRITE_BUFFER_BYTES)
    } catch (_: IOException) {
        null
    }
    private var bytes = 0L

    fun write(data: ByteArray, offset: Int, length: Int) {
        val stream = output ?: return
        try {
            stream.write(data, offset, length)
            bytes += length
            if (bytes > MAX_SEGMENT_BYTES) abort()
        } catch (_: IOException) {
            abort()
        }
    }

    /** Only a segment read through to its end may be stored. */
    fun finish() {
        val stream = output ?: return
        output = null
        try {
            stream.close()
        } catch (_: IOException) {
            file.delete()
            return
        }
        buffer.commit(url, file, bytes)
    }

    fun abort() {
        val stream = output ?: return
        output = null
        try {
            stream.close()
        } catch (_: IOException) {
        }
        file.delete()
    }

    private companion object {
        const val WRITE_BUFFER_BYTES = 64 * 1024
        const val MAX_SEGMENT_BYTES = 256L * 1024 * 1024
    }
}

/**
 * The segments of a live stream the viewer has already received, kept on disk in broadcast
 * order and joined with the playlist that describes them. It never fetches anything itself.
 *
 * Normally the oldest segments are evicted to stay under [maxBytes]. Once [pin]ned for a
 * replay nothing is evicted and the playlist only grows at its end, since a player holding
 * an EVENT playlist depends on the listed segments staying in place.
 */
internal class LiveDvrBuffer(
    private val directory: File,
    private val maxBytes: Long,
    private val minFreeBytes: Long = DEFAULT_MIN_FREE_BYTES,
    private val freeBytes: () -> Long = { directory.usableSpace },
) {
    private class Stored(val segment: DvrSegment, val playlistUrl: String, val file: File, val bytes: Long)
    private class Unjoined(val file: File, val bytes: Long)
    private class StoredInit(val file: File, val bytes: Long)

    private val lock = Any()
    private val entries = ArrayList<Stored>()
    private val storedUrls = HashSet<String>()
    private val awaitingBytes = LinkedHashMap<String, Pair<DvrSegment, String>>()
    private val awaitingPlaylist = LinkedHashMap<String, Unjoined>()
    private val initUrls = HashSet<String>()
    private val inits = HashMap<String, StoredInit>()
    private var nextFile = 0L
    private var totalBytes = 0L
    private var totalMs = 0L
    private var closed = false
    private var pinned = false
    private var publishedEntries = 0
    private var publishedEnded = false
    private var publishedMs = 0L

    val replayFile: File get() = File(directory, REPLAY_FILE)

    /** The media playlist seen most recently, which is the variant being played. */
    @Volatile var lastPlaylistUrl: String? = null
        private set

    fun availableMs(): Long = synchronized(lock) {
        recoverIfCleared()
        totalMs
    }

    /** The system can clear an app's cache at any moment, taking the saved video with it. */
    private fun recoverIfCleared() {
        if (closed || directory.exists()) return
        entries.clear()
        storedUrls.clear()
        awaitingPlaylist.clear()
        inits.clear()
        totalBytes = 0L
        totalMs = 0L
        publishedEntries = 0
        publishedEnded = false
        publishedMs = 0L
        directory.mkdirs()
    }

    fun publishedDurationMs(): Long = synchronized(lock) { publishedMs }

    fun onPlaylist(playlist: DvrPlaylist) = synchronized(lock) {
        if (closed) return@synchronized
        lastPlaylistUrl = playlist.url
        for (segment in playlist.segments) {
            segment.initUrl?.let(initUrls::add)
            if (segment.url in storedUrls) continue
            val bytes = awaitingPlaylist.remove(segment.url)
            if (bytes != null) {
                store(segment, playlist.url, bytes.file, bytes.bytes)
            } else {
                awaitingBytes[segment.url] = segment to playlist.url
            }
        }
        if (awaitingBytes.size > MAX_AWAITING_BYTES) {
            while (awaitingBytes.size > MAX_AWAITING_BYTES) {
                awaitingBytes.remove(awaitingBytes.keys.first())
            }
            dropUnusedInits()
        }
    }

    /** Null when this segment needs no copy or there is no room for one. */
    fun openWriter(url: String): DvrSegmentWriter? = synchronized(lock) {
        recoverIfCleared()
        if (closed || url in storedUrls || url in inits || url in awaitingPlaylist) return null
        if (freeBytes() < minFreeBytes) return null
        DvrSegmentWriter(this, url, File(directory, "${nextFile++}.tmp"))
    }

    internal fun commit(url: String, file: File, bytes: Long) = synchronized(lock) {
        if (closed || bytes <= 0L) {
            file.delete()
            return@synchronized
        }
        val target = File(directory, "${file.nameWithoutExtension}.${extensionOf(url)}")
        if (!file.renameTo(target)) {
            file.delete()
            return@synchronized
        }
        if (url in initUrls) {
            if (inits.containsKey(url)) {
                target.delete()
            } else {
                inits[url] = StoredInit(target, bytes)
                totalBytes += bytes
                evict()
            }
            return@synchronized
        }
        val described = awaitingBytes.remove(url)
        when {
            url in storedUrls || url in awaitingPlaylist -> target.delete()
            described != null -> store(described.first, described.second, target, bytes)
            else -> {
                // A low-latency prefetch arrives before the playlist that describes it.
                awaitingPlaylist[url] = Unjoined(target, bytes)
                while (awaitingPlaylist.size > MAX_AWAITING_PLAYLIST) {
                    val oldest = awaitingPlaylist.keys.first()
                    awaitingPlaylist.remove(oldest)?.file?.delete()
                }
            }
        }
    }

    private fun store(segment: DvrSegment, playlistUrl: String, file: File, bytes: Long) {
        // Switching quality fetches the same moment from another variant; keep the first copy.
        val tail = entries.subList(max(0, entries.size - DUPLICATE_SCAN), entries.size)
        if (tail.any { sameMoment(it.segment, segment) }) {
            file.delete()
            return
        }
        var index = entries.size
        while (index > 0 && isAfter(entries[index - 1].segment, segment)) index--
        val unplayable = segment.initUrl.let { it != null && it !in inits }
        if (pinned && (index < entries.size || unplayable)) {
            file.delete()
            return
        }
        entries.add(index, Stored(segment, playlistUrl, file, bytes))
        storedUrls.add(segment.url)
        totalBytes += bytes
        totalMs += segment.durationMs
        evict()
    }

    private fun evict() {
        if (pinned) return
        val before = entries.size
        while (entries.size > 1 && (totalBytes > maxBytes || freeBytes() < minFreeBytes)) {
            forget(entries.removeAt(0))
        }
        if (entries.size != before) dropUnusedInits()
    }

    /** An init segment stays only while saved video, or video on its way, plays with it. */
    private fun dropUnusedInits() {
        val used = HashSet<String>()
        entries.mapNotNullTo(used) { it.segment.initUrl }
        awaitingBytes.values.mapNotNullTo(used) { it.first.initUrl }
        inits.keys.filter { it !in used }.forEach { url ->
            inits.remove(url)?.let {
                it.file.delete()
                totalBytes -= it.bytes
            }
        }
        initUrls.retainAll(used)
    }

    private fun forget(entry: Stored) {
        entry.file.delete()
        storedUrls.remove(entry.segment.url)
        totalBytes -= entry.bytes
        totalMs -= entry.segment.durationMs
    }

    /** What a recorder should fetch: only moments newer than everything stored. */
    fun missing(playlist: DvrPlaylist): List<DvrSegment> = synchronized(lock) {
        val last = entries.lastOrNull()?.segment
        playlist.segments.filter { segment ->
            segment.url !in storedUrls && (last == null || (isAfter(segment, last) && !sameMoment(segment, last)))
        }
    }

    fun missingInit(segment: DvrSegment): String? = synchronized(lock) {
        segment.initUrl?.takeUnless { it in inits }
    }

    /** A replay can hold more than the live cap, up to a hard limit that also protects free space. */
    fun canGrow(): Boolean = synchronized(lock) {
        !closed && totalBytes < maxBytes * PINNED_CEILING_FACTOR && freeBytes() >= minFreeBytes
    }

    /** From here on the replay's start is fixed, so nothing unplayable may stay in it. */
    fun pin() = synchronized(lock) {
        recoverIfCleared()
        pinned = true
        // Clearing a cache can also remove just some of its files.
        inits.values.removeAll { init ->
            val gone = !init.file.exists()
            if (gone) totalBytes -= init.bytes
            gone
        }
        entries.removeAll { entry ->
            val unplayable = !entry.file.exists() || entry.segment.initUrl.let { it != null && it !in inits }
            if (unplayable) forget(entry)
            unplayable
        }
    }

    /** Back to live: the replay playlist is finished with, so eviction resumes. */
    fun unpin() = synchronized(lock) {
        pinned = false
        publishedEntries = 0
        publishedEnded = false
        publishedMs = 0L
        evict()
    }

    /** Atomically rewrites the replay playlist if anything changed; true when it did. */
    fun publish(ended: Boolean): Boolean = synchronized(lock) {
        if (closed || !pinned || entries.isEmpty()) return false
        if (entries.size == publishedEntries && (!ended || publishedEnded)) return false
        val temporary = File(directory, "$REPLAY_FILE.tmp")
        try {
            temporary.writeText(renderPlaylist(ended))
            // Android replaces the target atomically; the second attempt is for desktop JVMs.
            if (!temporary.renameTo(replayFile) && !(replayFile.delete() && temporary.renameTo(replayFile))) {
                temporary.delete()
                return false
            }
        } catch (_: IOException) {
            temporary.delete()
            return false
        }
        publishedEntries = entries.size
        publishedEnded = ended
        publishedMs = totalMs
        true
    }

    private fun renderPlaylist(ended: Boolean): String {
        val targetSeconds = max(MIN_TARGET_DURATION_SECONDS, ceil((entries.maxOfOrNull { it.segment.durationMs } ?: 0L) / 1000.0).toInt())
        val text = StringBuilder()
            .append("#EXTM3U\n#EXT-X-VERSION:6\n#EXT-X-PLAYLIST-TYPE:EVENT\n")
            .append("#EXT-X-TARGETDURATION:").append(targetSeconds).append('\n')
            .append("#EXT-X-MEDIA-SEQUENCE:0\n")
        var previous: Stored? = null
        for (entry in entries) {
            val segment = entry.segment
            if (previous != null && startsNewTimeline(previous, entry)) text.append("#EXT-X-DISCONTINUITY\n")
            if (segment.initUrl != previous?.segment?.initUrl) {
                segment.initUrl?.let { text.append("#EXT-X-MAP:URI=\"").append(inits.getValue(it).file.name).append("\"\n") }
            }
            text.append("#EXTINF:").append(String.format(Locale.ROOT, "%.3f", segment.durationMs / 1000.0)).append(",\n")
                .append(entry.file.name).append('\n')
            previous = entry
        }
        if (ended) text.append("#EXT-X-ENDLIST\n")
        return text.toString()
    }

    private fun startsNewTimeline(previous: Stored, next: Stored): Boolean {
        if (next.segment.discontinuity || previous.playlistUrl != next.playlistUrl) return true
        val before = previous.segment
        val after = next.segment
        return if (before.programDateTimeMs != null && after.programDateTimeMs != null) {
            abs(after.programDateTimeMs - (before.programDateTimeMs + before.durationMs)) > CONTIGUOUS_TOLERANCE_MS
        } else {
            after.sequence != before.sequence + 1
        }
    }

    private fun isAfter(a: DvrSegment, b: DvrSegment): Boolean =
        if (a.programDateTimeMs != null && b.programDateTimeMs != null) a.programDateTimeMs > b.programDateTimeMs
        else a.sequence > b.sequence

    private fun sameMoment(a: DvrSegment, b: DvrSegment): Boolean =
        if (a.programDateTimeMs != null && b.programDateTimeMs != null) {
            abs(a.programDateTimeMs - b.programDateTimeMs) < minOf(a.durationMs, b.durationMs) / 2
        } else {
            a.sequence == b.sequence
        }

    private fun extensionOf(url: String): String =
        url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
            .takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) } ?: "ts"

    /** Deletes everything. Writers still running discard their bytes when they commit. */
    fun close() {
        synchronized(lock) {
            closed = true
            entries.clear()
            storedUrls.clear()
            awaitingBytes.clear()
            awaitingPlaylist.clear()
            inits.clear()
            totalBytes = 0L
            totalMs = 0L
        }
        directory.deleteRecursively()
    }

    companion object {
        const val REPLAY_FILE = "playlist.m3u8"

        /**
         * Android starts clearing every app's cache when free space falls below 150% of its
         * low-storage threshold, which comes to 750 MB at most. This buffer must never cause that.
         */
        const val DEFAULT_MIN_FREE_BYTES = 1024L * 1024 * 1024
        const val PINNED_CEILING_FACTOR = 2L
        private const val MAX_AWAITING_BYTES = 256
        private const val MAX_AWAITING_PLAYLIST = 8
        private const val DUPLICATE_SCAN = 64
        private const val CONTIGUOUS_TOLERANCE_MS = 1_000L

        /** Exo declares a playlist stuck after 3.5 target durations, so this allows ~21s of quiet. */
        private const val MIN_TARGET_DURATION_SECONDS = 6
    }
}
