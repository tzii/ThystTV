package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlin.concurrent.thread
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File
import java.io.IOException
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LiveDvrTest {
    @get:Rule val folder = TemporaryFolder()

    private val base = "https://edge.example/v1/playlist/high.m3u8"
    private val start = 1_800_000_000_000L
    private var free = Long.MAX_VALUE
    private val directory get() = File(folder.root, "buffer").apply { mkdirs() }

    private fun buffer(maxBytes: Long = 1_000) = LiveDvrBuffer(directory, maxBytes, minFreeBytes = 1_000) { free }

    private fun segment(n: Long, durationMs: Long = 2_000, variant: String = "high", discontinuity: Boolean = false, initUrl: String? = null, dated: Boolean = true) = DvrSegment(
        url = "https://edge.example/$variant/$n.ts",
        durationMs = durationMs,
        sequence = n,
        programDateTimeMs = if (dated) start + n * 2_000 else null,
        discontinuity = discontinuity,
        initUrl = initUrl,
    )

    private fun playlist(vararg segments: DvrSegment, url: String = base, ended: Boolean = false) = DvrPlaylist(url, segments.toList(), ended)

    private fun LiveDvrBuffer.save(url: String, size: Int = 100) {
        openWriter(url)!!.apply {
            write(ByteArray(size), 0, size)
            finish()
        }
    }

    private fun LiveDvrBuffer.saveAll(vararg segments: DvrSegment) {
        onPlaylist(playlist(*segments))
        segments.forEach { save(it.url) }
    }

    private fun LiveDvrBuffer.replayLines(): List<String> {
        pin()
        publish(false)
        return replayFile.readLines()
    }

    private fun List<String>.entries() = filter { !it.startsWith("#") }

    // Playlist parsing.

    @Test fun `a Twitch media playlist parses in order with dates and discontinuities`() {
        val parsed = DvrPlaylistParser.parse("""
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:100
            #EXT-X-TWITCH-LIVE-SEQUENCE:100
            #EXT-X-PROGRAM-DATE-TIME:2027-01-15T08:00:00.000Z
            #EXTINF:2.000,live
            https://edge.example/100.ts
            #EXTINF:2.500,live
            101.ts
            #EXT-X-DISCONTINUITY
            #EXT-X-PROGRAM-DATE-TIME:2027-01-15T08:00:30.000Z
            #EXTINF:2.000,Amazon
            https://edge.example/ad.ts
            #EXT-X-TWITCH-PREFETCH:https://edge.example/103.ts
        """.trimIndent(), base)!!
        val first = parsed.segments[0]
        val second = parsed.segments[1]
        val third = parsed.segments[2]
        assertEquals(3, parsed.segments.size)
        assertEquals(100L, first.sequence)
        assertEquals(2_000L, first.durationMs)
        assertEquals("a later segment extends the last date", first.programDateTimeMs!! + 2_000, second.programDateTimeMs)
        assertEquals("relative entries resolve like the player's", "https://edge.example/v1/playlist/101.ts", second.url)
        assertFalse(second.discontinuity)
        assertTrue(third.discontinuity)
        assertEquals(102L, third.sequence)
        assertEquals(first.programDateTimeMs!! + 30_000, third.programDateTimeMs)
        assertFalse(parsed.ended)
    }

    @Test fun `only a media playlist parses, and an end tag is noticed`() {
        assertNull(DvrPlaylistParser.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhigh.m3u8\n", base))
        assertNull(DvrPlaylistParser.parse("<html>nope</html>", base))
        assertTrue(DvrPlaylistParser.parse("#EXTM3U\n#EXTINF:2,\n1.ts\n#EXT-X-ENDLIST\n", base)!!.ended)
    }

    @Test fun `init segments carry over and unusable segments are left out`() {
        val parsed = DvrPlaylistParser.parse("""
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:2.000,
            1.mp4
            #EXTINF:2.000,
            2.mp4
            #EXT-X-BYTERANGE:10@0
            #EXTINF:2.000,
            3.mp4
            #EXT-X-KEY:METHOD=AES-128,URI="key"
            #EXTINF:2.000,
            4.mp4
        """.trimIndent(), base)!!
        assertEquals(listOf(0L, 1L), parsed.segments.map { it.sequence })
        assertEquals("https://edge.example/v1/playlist/init.mp4", parsed.segments[1].initUrl)
    }

    // The buffer.

    @Test fun `nothing counts as saved until its bytes and description are both in`() {
        val buffer = buffer()
        buffer.onPlaylist(playlist(segment(1)))
        assertEquals(0L, buffer.availableMs())
        buffer.save(segment(1).url)
        assertEquals(2_000L, buffer.availableMs())
        // A low-latency prefetch: bytes first.
        buffer.save(segment(2).url)
        assertEquals(2_000L, buffer.availableMs())
        buffer.onPlaylist(playlist(segment(1), segment(2)))
        assertEquals(4_000L, buffer.availableMs())
    }

    @Test fun `an interrupted segment is discarded and can be saved later`() {
        val buffer = buffer()
        buffer.onPlaylist(playlist(segment(1)))
        buffer.openWriter(segment(1).url)!!.apply {
            write(ByteArray(10), 0, 10)
            abort()
        }
        assertEquals(0L, buffer.availableMs())
        assertTrue("no partial file stays behind", directory.listFiles()!!.none { it.name.endsWith(".tmp") })
        buffer.save(segment(1).url)
        assertEquals(2_000L, buffer.availableMs())
    }

    @Test fun `a segment already saved is not captured twice`() {
        val buffer = buffer()
        buffer.saveAll(segment(1))
        assertNull(buffer.openWriter(segment(1).url))
    }

    @Test fun `the oldest video goes first and the size limit holds`() {
        val buffer = buffer(maxBytes = 250)
        buffer.saveAll(segment(1), segment(2), segment(3), segment(4))
        assertEquals(4_000L, buffer.availableMs())
        assertEquals(listOf("2.ts", "3.ts"), buffer.replayLines().entries())
        val files = directory.listFiles()!!.filter { it.name.endsWith(".ts") }
        assertEquals("evicted segments leave the disk", 2, files.size)
    }

    @Test fun `low disk space evicts while live and stops new copies`() {
        val buffer = buffer(maxBytes = 10_000)
        buffer.saveAll(segment(1), segment(2))
        free = 10
        assertNull("no room for another copy", buffer.openWriter(segment(3).url))
        buffer.onPlaylist(playlist(segment(3)))
        assertFalse(buffer.canGrow())
    }

    @Test fun `changing quality keeps one copy of each moment`() {
        val buffer = buffer()
        buffer.saveAll(segment(1), segment(2))
        val other = segment(2, variant = "low").copy(programDateTimeMs = segment(2).programDateTimeMs!! + 50)
        buffer.onPlaylist(playlist(other, url = "https://edge.example/v1/playlist/low.m3u8"))
        buffer.save(other.url)
        assertEquals(4_000L, buffer.availableMs())
    }

    // The replay playlist.

    @Test fun `the replay is a sequence zero event playlist with relative files and no dates`() {
        val buffer = buffer()
        buffer.saveAll(segment(1), segment(2))
        val lines = buffer.replayLines()
        assertEquals("#EXTM3U", lines.first())
        assertTrue(lines.contains("#EXT-X-PLAYLIST-TYPE:EVENT"))
        assertTrue(lines.contains("#EXT-X-MEDIA-SEQUENCE:0"))
        assertTrue("dates would switch on the player's live speed control", lines.none { it.contains("PROGRAM-DATE-TIME") })
        assertFalse(lines.contains("#EXT-X-ENDLIST"))
        assertEquals(2, lines.entries().size)
        lines.entries().forEach {
            assertFalse(it.contains('/'))
            assertTrue("$it exists", File(directory, it).exists())
        }
        assertEquals(4_000L, buffer.publishedDurationMs())
    }

    @Test fun `a target duration tolerates a quiet network without tripping the stuck check`() {
        val buffer = buffer()
        buffer.saveAll(segment(1))
        assertTrue(buffer.replayLines().contains("#EXT-X-TARGETDURATION:6"))
    }

    @Test fun `a jump in time, a source break or another variant restart the timeline`() {
        val buffer = buffer(maxBytes = 100_000)
        val low = segment(4, variant = "low")
        buffer.saveAll(segment(1), segment(2))
        buffer.onPlaylist(playlist(low, url = "https://edge.example/v1/playlist/low.m3u8"))
        buffer.save(low.url)
        val ad = segment(5, variant = "low", discontinuity = true)
        buffer.onPlaylist(playlist(ad, url = "https://edge.example/v1/playlist/low.m3u8"))
        buffer.save(ad.url)
        val lines = buffer.replayLines()
        assertEquals(2, lines.count { it == "#EXT-X-DISCONTINUITY" })
        assertTrue("the first entry starts clean", lines.indexOf("#EXT-X-DISCONTINUITY") > lines.indexOfFirst { it.startsWith("#EXTINF") })
    }

    @Test fun `without dates, contiguous means consecutive sequence numbers`() {
        val buffer = buffer()
        buffer.saveAll(segment(1, dated = false), segment(2, dated = false), segment(5, dated = false))
        assertEquals(1, buffer.replayLines().count { it == "#EXT-X-DISCONTINUITY" })
    }

    @Test fun `init segments are listed once per change and required`() {
        val buffer = buffer()
        val init = "https://edge.example/init.mp4"
        val first = segment(1, initUrl = init)
        val second = segment(2, initUrl = init)
        buffer.onPlaylist(playlist(first, second))
        buffer.save(init)
        buffer.save(first.url)
        buffer.save(second.url)
        val lines = buffer.replayLines()
        assertEquals(1, lines.count { it.startsWith("#EXT-X-MAP:") })
        assertEquals(2, lines.entries().size)
    }

    @Test fun `video that can't play without a missing init segment never enters a replay`() {
        val buffer = buffer()
        val first = segment(1, initUrl = "https://edge.example/init.mp4")
        val second = segment(2, initUrl = "https://edge.example/init.mp4")
        buffer.onPlaylist(playlist(first, second))
        buffer.save(first.url)
        assertEquals(2_000L, buffer.availableMs())
        buffer.pin()
        assertEquals(0L, buffer.availableMs())
        assertFalse(buffer.publish(false))
        buffer.save(second.url)
        assertEquals(0L, buffer.availableMs())
    }

    @Test fun `init segments count toward the limit and go with the last video using them`() {
        val buffer = buffer(maxBytes = 1_000)
        (1L..12L).forEach { n ->
            val segment = segment(n, initUrl = "https://edge.example/init$n.mp4")
            buffer.onPlaylist(playlist(segment))
            buffer.save(segment.initUrl!!)
            buffer.save(segment.url)
        }
        val files = directory.listFiles().orEmpty().filter { it.isFile }
        assertTrue("disk use is ${files.sumOf { it.length() }}", files.sumOf { it.length() } <= 1_000)
        assertEquals("five pairs of video and init fit", 10, files.size)
        assertEquals(10_000L, buffer.availableMs())
        assertEquals(5, buffer.replayLines().count { it.startsWith("#EXT-X-MAP:") })
    }

    @Test fun `an init segment shared with newer video survives eviction of the old video`() {
        val buffer = buffer(maxBytes = 500)
        val init = "https://edge.example/init.mp4"
        (1L..10L).forEach { n ->
            val segment = segment(n, initUrl = init)
            buffer.onPlaylist(playlist(segment))
            if (n == 1L) buffer.save(init)
            buffer.save(segment.url)
        }
        assertNull("already saved", buffer.openWriter(init))
        val lines = buffer.replayLines()
        assertEquals(1, lines.count { it.startsWith("#EXT-X-MAP:") })
        assertEquals(4, lines.entries().size)
    }

    @Test fun `init segments count against a replay's hard limit`() {
        val buffer = buffer(maxBytes = 500)
        val first = segment(1, initUrl = "https://edge.example/init1.mp4")
        buffer.onPlaylist(playlist(first))
        buffer.save(first.initUrl!!)
        buffer.save(first.url)
        buffer.pin()
        (2L..5L).forEach { n ->
            val segment = segment(n, initUrl = "https://edge.example/init$n.mp4")
            buffer.onPlaylist(playlist(segment))
            assertTrue(buffer.canGrow())
            buffer.save(segment.initUrl!!)
            buffer.save(segment.url)
        }
        assertFalse("1,000 bytes of video and init reach twice the limit", buffer.canGrow())
    }

    @Test fun `only a replay publishes a playlist`() {
        val buffer = buffer()
        buffer.saveAll(segment(1))
        assertFalse(buffer.publish(false))
        assertFalse(buffer.replayFile.exists())
    }

    @Test fun `a replay holds its video past the live limit, then only grows at the end`() {
        val buffer = buffer(maxBytes = 200)
        buffer.saveAll(segment(2), segment(3))
        buffer.pin()
        assertTrue(buffer.publish(false))
        buffer.saveAll(segment(4), segment(5))
        assertEquals("nothing evicted during a replay", 8_000L, buffer.availableMs())
        buffer.saveAll(segment(1))
        assertEquals("a late arrival can't shift what the player already has", 8_000L, buffer.availableMs())
        assertTrue(buffer.publish(false))
        assertEquals(4, buffer.replayFile.readLines().entries().size)
        assertFalse("unchanged, so no rewrite", buffer.publish(false))
        assertTrue(buffer.publish(true))
        assertEquals("#EXT-X-ENDLIST", buffer.replayFile.readLines().last())
    }

    @Test fun `a replay has a hard limit and going back to live evicts again`() {
        val buffer = buffer(maxBytes = 200)
        buffer.saveAll(segment(1), segment(2))
        buffer.pin()
        assertTrue(buffer.canGrow())
        buffer.saveAll(segment(3), segment(4))
        assertFalse("twice the size limit", buffer.canGrow())
        buffer.unpin()
        assertEquals(4_000L, buffer.availableMs())
        assertTrue(buffer.canGrow())
    }

    @Test fun `the system clearing the cache empties the buffer and capture carries on`() {
        val buffer = buffer()
        buffer.saveAll(segment(1), segment(2))
        directory.deleteRecursively()
        assertEquals(0L, buffer.availableMs())
        assertTrue(directory.exists())
        buffer.saveAll(segment(3))
        assertEquals(2_000L, buffer.availableMs())
        assertEquals(1, buffer.replayLines().entries().size)
    }

    @Test fun `a partly cleared cache leaves a replay of what remains`() {
        val buffer = buffer()
        buffer.saveAll(segment(1), segment(2), segment(3))
        File(directory, "0.ts").delete()
        assertEquals(2, buffer.replayLines().entries().size)
        assertEquals(4_000L, buffer.availableMs())
    }

    @Test fun `the buffer stops well before Android would clear caches`() {
        assertTrue(LiveDvrBuffer.DEFAULT_MIN_FREE_BYTES > 750L * 1024 * 1024)
    }

    @Test fun `closing deletes everything and late writers leave nothing behind`() {
        val buffer = buffer()
        buffer.saveAll(segment(1))
        buffer.onPlaylist(playlist(segment(2)))
        val writer = buffer.openWriter(segment(2).url)!!
        writer.write(ByteArray(10), 0, 10)
        buffer.close()
        writer.finish()
        assertFalse(directory.listFiles()?.any { it.name.endsWith(".ts") } ?: false)
        assertEquals(0L, buffer.availableMs())
        assertNull(buffer.openWriter(segment(3).url))
    }

    // Capture.

    private class FakeSource(
        private val bodies: Map<String, ByteArray>,
        private val failing: Set<String> = emptySet(),
        private val redirects: Map<String, String> = emptyMap(),
    ) : DataSource {
        private var body: ByteArray? = null
        private var position = 0
        var closed = 0
        override fun addTransferListener(transferListener: TransferListener) {}
        private var opened: Uri? = null
        override fun open(dataSpec: DataSpec): Long {
            val url = dataSpec.uri.toString()
            if (url in failing) throw IOException("offline")
            body = bodies[url] ?: throw IOException("missing $url")
            opened = redirects[url]?.let(Uri::parse) ?: dataSpec.uri
            position = dataSpec.position.toInt()
            return C.LENGTH_UNSET.toLong()
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val current = body!!
            if (position >= current.size) return C.RESULT_END_OF_INPUT
            val count = minOf(length, current.size - position)
            current.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun getUri() = opened
        override fun close() { closed++ }
    }

    private fun capture(buffer: LiveDvrBuffer, bodies: Map<String, ByteArray>) =
        LiveDvrHlsDataSourceFactory({ FakeSource(bodies) }, buffer)

    private fun DataSource.drain(uri: String, position: Long = 0L, stopAfter: Int = Int.MAX_VALUE): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        open(DataSpec.Builder().setUri(uri).setPosition(position).build())
        val chunk = ByteArray(4)
        while (output.size() < stopAfter) {
            val read = read(chunk, 0, chunk.size)
            if (read == C.RESULT_END_OF_INPUT) break
            output.write(chunk, 0, read)
        }
        close()
        return output.toByteArray()
    }

    private val mediaPlaylist = """
        #EXTM3U
        #EXT-X-MEDIA-SEQUENCE:1
        #EXT-X-PROGRAM-DATE-TIME:2027-01-15T08:00:00.000Z
        #EXTINF:2.000,live
        https://edge.example/high/1.ts
    """.trimIndent().toByteArray()

    @Test fun `the player gets exactly the upstream bytes while a copy lands in the buffer`() {
        val buffer = buffer()
        val video = ByteArray(11) { it.toByte() }
        val factory = capture(buffer, mapOf(base to mediaPlaylist, "https://edge.example/high/1.ts" to video))
        assertTrue(mediaPlaylist.contentEquals(factory.createDataSource(C.DATA_TYPE_MANIFEST).drain(base)))
        assertEquals(base, buffer.lastPlaylistUrl)
        assertTrue(video.contentEquals(factory.createDataSource(C.DATA_TYPE_MEDIA).drain("https://edge.example/high/1.ts")))
        assertEquals(2_000L, buffer.availableMs())
    }

    @Test fun `video that redirects is saved under the address its playlist lists`() {
        val buffer = LiveDvrBuffer(directory, 100_000, minFreeBytes = 0) { Long.MAX_VALUE }
        val listedPlaylist = "https://proxy.example/live/high.m3u8"
        val finalPlaylist = "https://cdn.example/session/high.m3u8"
        val listedSegment = "https://cdn.example/session/1.ts"
        val body = "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:1\n#EXTINF:2.000,live\n1.ts\n".toByteArray()
        val factory = LiveDvrHlsDataSourceFactory({
            FakeSource(
                mapOf(listedPlaylist to body, listedSegment to ByteArray(10)),
                redirects = mapOf(listedPlaylist to finalPlaylist, listedSegment to "https://other.example/x/1.ts"),
            )
        }, buffer)
        factory.createDataSource(C.DATA_TYPE_MANIFEST).drain(listedPlaylist)
        assertEquals("relative entries resolve from where the playlist landed", finalPlaylist, buffer.lastPlaylistUrl)
        factory.createDataSource(C.DATA_TYPE_MEDIA).drain(listedSegment)
        assertEquals(2_000L, buffer.availableMs())
    }

    @Test fun `a load the player abandons or resumes is not saved`() {
        val buffer = buffer()
        val video = ByteArray(11) { it.toByte() }
        val factory = capture(buffer, mapOf(base to mediaPlaylist, "https://edge.example/high/1.ts" to video))
        factory.createDataSource(C.DATA_TYPE_MANIFEST).drain(base)
        factory.createDataSource(C.DATA_TYPE_MEDIA).drain("https://edge.example/high/1.ts", stopAfter = 4)
        assertEquals("abandoned", 0L, buffer.availableMs())
        assertEquals(7, factory.createDataSource(C.DATA_TYPE_MEDIA).drain("https://edge.example/high/1.ts", position = 4).size)
        assertEquals("resumed from the middle", 0L, buffer.availableMs())
    }

    @Test fun `disk trouble never reaches the player`() {
        val buffer = buffer()
        val video = ByteArray(11) { it.toByte() }
        val factory = capture(buffer, mapOf(base to mediaPlaylist, "https://edge.example/high/1.ts" to video))
        factory.createDataSource(C.DATA_TYPE_MANIFEST).drain(base)
        File(folder.root, "buffer").apply {
            deleteRecursively()
            writeText("a file where the directory should be")
        }
        assertTrue(video.contentEquals(factory.createDataSource(C.DATA_TYPE_MEDIA).drain("https://edge.example/high/1.ts")))
        assertEquals(0L, buffer.availableMs())
    }

    @Test fun `a playlist cut short is ignored and playlist garbage is harmless`() {
        val buffer = buffer()
        val factory = capture(buffer, mapOf(base to mediaPlaylist))
        factory.createDataSource(C.DATA_TYPE_MANIFEST).drain(base, stopAfter = 4)
        assertNull(buffer.lastPlaylistUrl)
        val garbage = capture(buffer, mapOf(base to byteArrayOf(0, 1, 2)))
        assertEquals(3, garbage.createDataSource(C.DATA_TYPE_MANIFEST).drain(base).size)
    }

    // Recording while the player reads local files.

    private class Upstream(var playlistBody: String = "") : DataSource.Factory {
        val bodies = HashMap<String, ByteArray>()
        val failing = HashSet<String>()
        val redirects = HashMap<String, String>()
        val requests = ArrayList<String>()
        var onOpen: (String) -> Unit = {}
        override fun createDataSource(): DataSource = object : DataSource {
            private var inner: FakeSource? = null
            override fun addTransferListener(transferListener: TransferListener) {}
            override fun open(dataSpec: DataSpec): Long {
                requests.add(dataSpec.uri.toString())
                onOpen(dataSpec.uri.toString())
                val all = bodies + ("https://edge.example/v1/playlist/high.m3u8" to playlistBody.toByteArray())
                inner = FakeSource(all, failing, redirects)
                return inner!!.open(dataSpec)
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int) = inner!!.read(buffer, offset, length)
            override fun getUri() = inner?.uri
            override fun close() { inner?.close() }
        }
    }

    private fun livePlaylist(vararg numbers: Long, ended: Boolean = false) = buildString {
        append("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:${numbers.first()}\n")
        append("#EXT-X-PROGRAM-DATE-TIME:2027-01-15T08:00:${"%02d".format(numbers.first() * 2)}.000Z\n")
        numbers.forEach { append("#EXTINF:2.000,live\nhttps://edge.example/high/$it.ts\n") }
        if (ended) append("#EXT-X-ENDLIST\n")
    }

    private fun Upstream.withVideo(vararg numbers: Long) = apply {
        numbers.forEach { bodies["https://edge.example/high/$it.ts"] = ByteArray(10) }
    }

    @Test fun `a replay keeps recording new video and stops cleanly when the stream ends`() = runTest {
        val buffer = LiveDvrBuffer(directory, 100_000, minFreeBytes = 0) { Long.MAX_VALUE }
        val upstream = Upstream(livePlaylist(1, 2, ended = false)).withVideo(1, 2, 3, 4)
        buffer.onPlaylist(DvrPlaylistParser.parse(livePlaylist(1), base)!!)
        buffer.save("https://edge.example/high/1.ts", 10)
        buffer.pin()
        buffer.publish(false)
        upstream.playlistBody = livePlaylist(1, 2, 3, 4, ended = true)
        LiveDvrRecorder(buffer, upstream, now = { currentTime }, pollMs = 1_000).run()
        assertEquals(1, upstream.requests.count { it == base })
        val lines = buffer.replayFile.readLines()
        assertEquals("#EXT-X-ENDLIST", lines.last())
        assertEquals(4, lines.entries().size)
        assertFalse("already saved video isn't downloaded again", upstream.requests.contains("https://edge.example/high/1.ts"))
    }

    @Test fun `a stream that stays quiet eventually closes the replay`() = runTest {
        val buffer = LiveDvrBuffer(directory, 100_000, minFreeBytes = 0) { Long.MAX_VALUE }
        val upstream = Upstream(livePlaylist(1)).withVideo(1)
        buffer.onPlaylist(DvrPlaylistParser.parse(livePlaylist(1), base)!!)
        buffer.save("https://edge.example/high/1.ts", 10)
        buffer.pin()
        buffer.publish(false)
        LiveDvrRecorder(buffer, upstream, now = { currentTime }).run()
        assertTrue(buffer.replayFile.readLines().last() == "#EXT-X-ENDLIST")
        assertEquals(LiveDvrRecorder.NO_PROGRESS_LIMIT_MS, currentTime)
    }

    @Test fun `a response trickling in can't hold a replay open past the limit`() = runTest {
        var clock = 0L
        val buffer = LiveDvrBuffer(directory, 100_000, minFreeBytes = 0) { Long.MAX_VALUE }
        buffer.onPlaylist(DvrPlaylistParser.parse(livePlaylist(1), base)!!)
        buffer.save("https://edge.example/high/1.ts", 10)
        buffer.pin()
        buffer.publish(false)
        val trickle = DataSource.Factory {
            object : DataSource {
                override fun addTransferListener(transferListener: TransferListener) {}
                override fun open(dataSpec: DataSpec) = C.LENGTH_UNSET.toLong()
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    clock += 5_000
                    return 1
                }
                override fun getUri(): Uri? = null
                override fun close() {}
            }
        }
        LiveDvrRecorder(buffer, trickle, now = { clock }).run()
        assertEquals("#EXT-X-ENDLIST", buffer.replayFile.readLines().last())
        assertEquals(LiveDvrRecorder.NO_PROGRESS_LIMIT_MS, clock)
    }

    @Test fun `a recorded playlist that redirects resolves its entries from where it landed`() = runTest {
        val buffer = LiveDvrBuffer(directory, 100_000, minFreeBytes = 0) { Long.MAX_VALUE }
        val moved = "https://moved.example/session/high.m3u8"
        val upstream = Upstream("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:2\n#EXTINF:2.000,live\n2.ts\n#EXT-X-ENDLIST\n")
        upstream.redirects[base] = moved
        upstream.bodies["https://moved.example/session/2.ts"] = ByteArray(10)
        buffer.onPlaylist(DvrPlaylistParser.parse(livePlaylist(1), base)!!)
        buffer.save("https://edge.example/high/1.ts", 10)
        buffer.pin()
        buffer.publish(false)
        LiveDvrRecorder(buffer, upstream, now = { currentTime }).run()
        assertEquals(2, buffer.replayFile.readLines().entries().size)
    }

    @Test fun `one stalled download doesn't end the replay`() = runTest {
        var clock = 0L
        val buffer = LiveDvrBuffer(directory, 100_000, minFreeBytes = 0) { Long.MAX_VALUE }
        val stalled = "https://edge.example/high/2.ts"
        val upstream = Upstream(livePlaylist(1, 2, 3)).withVideo(1, 2, 3)
        var attempts = 0
        upstream.onOpen = { url ->
            if (url == stalled && attempts++ == 0) {
                // Longer than the HTTP read timeout, which is what a real stall looks like.
                clock += 13_000
                upstream.failing.add(stalled)
            } else if (url == stalled) {
                upstream.failing.clear()
                upstream.playlistBody = livePlaylist(1, 2, 3, ended = true)
            }
        }
        buffer.onPlaylist(DvrPlaylistParser.parse(livePlaylist(1), base)!!)
        buffer.save("https://edge.example/high/1.ts", 10)
        buffer.pin()
        buffer.publish(false)
        LiveDvrRecorder(buffer, upstream, now = { clock }).run()
        assertEquals("the retry saved the rest", 3, buffer.replayFile.readLines().entries().size)
    }

    @Test fun `each saved segment reaches the player while a long catch-up is running`() = runTest {
        var clock = 0L
        val buffer = LiveDvrBuffer(directory, 100_000, minFreeBytes = 0) { Long.MAX_VALUE }
        val numbers = longArrayOf(1, 2, 3, 4, 5, 6)
        val upstream = Upstream(livePlaylist(*numbers, ended = true)).withVideo(*numbers)
        val publishedWhenOpening = ArrayList<Int>()
        upstream.onOpen = { url ->
            if (url.endsWith(".ts")) {
                clock += 20_000
                publishedWhenOpening.add(buffer.replayFile.readLines().entries().size)
            }
        }
        buffer.onPlaylist(DvrPlaylistParser.parse(livePlaylist(1), base)!!)
        buffer.save("https://edge.example/high/1.ts", 10)
        buffer.pin()
        buffer.publish(false)
        LiveDvrRecorder(buffer, upstream, now = { clock }).run()
        assertEquals(listOf(1, 2, 3, 4, 5), publishedWhenOpening)
        assertEquals(6, buffer.replayFile.readLines().entries().size)
    }

    @Test fun `a lost connection keeps retrying and recovers without losing the replay`() = runTest {
        val buffer = LiveDvrBuffer(directory, 100_000, minFreeBytes = 0) { Long.MAX_VALUE }
        val upstream = Upstream(livePlaylist(1, 2, ended = true)).withVideo(1, 2)
        upstream.failing.add(base)
        buffer.onPlaylist(DvrPlaylistParser.parse(livePlaylist(1), base)!!)
        buffer.save("https://edge.example/high/1.ts", 10)
        buffer.pin()
        buffer.publish(false)
        var checks = 0
        LiveDvrRecorder(buffer, upstream, now = {
            if (++checks > 3) upstream.failing.clear()
            currentTime
        }).run()
        assertEquals(2, buffer.replayFile.readLines().entries().size)
        assertEquals("#EXT-X-ENDLIST", buffer.replayFile.readLines().last())
    }

    @Test fun `running out of room closes the replay`() = runTest {
        var room = Long.MAX_VALUE
        val buffer = LiveDvrBuffer(directory, 5, minFreeBytes = 0) { room }
        val upstream = Upstream(livePlaylist(1, 2, 3)).withVideo(1, 2, 3)
        buffer.onPlaylist(DvrPlaylistParser.parse(livePlaylist(1), base)!!)
        buffer.save("https://edge.example/high/1.ts", 10)
        buffer.pin()
        buffer.publish(false)
        room = 0
        val before = upstream.requests.size
        LiveDvrRecorder(buffer, upstream, now = { currentTime }).run()
        assertEquals("only the playlist was fetched", before + 1, upstream.requests.size)
        assertEquals("#EXT-X-ENDLIST", buffer.replayFile.readLines().last())
        assertNotNull(buffer.lastPlaylistUrl)
    }

    // One capture across the player's source changes.

    private val cacheDir get() = File(folder.root, "cache")
    private val testScope = TestScope()
    private fun dvr() = LiveDvr(cacheDir, testScope)
    private val megabytes = 1024L * 1024
    private fun sessionDirectories() = File(cacheDir, "live-dvr").listFiles()?.toList().orEmpty()

    private fun LiveDvr.watch(channel: String, vararg segments: DvrSegment, maxBytes: Long = megabytes, upstream: DataSource.Factory = Upstream()): LiveDvrBuffer {
        val buffer = beginLive(channel, true, maxBytes, upstream)!!
        buffer.saveAll(*segments)
        return buffer
    }

    private fun settle() = testScope.testScheduler.runCurrent()

    private fun elapse(ms: Long) {
        testScope.testScheduler.advanceTimeBy(ms)
        settle()
    }

    @Test fun `turned off nothing is captured and an earlier capture is deleted`() {
        val dvr = dvr()
        dvr.watch("a", segment(1))
        assertNull(dvr.beginLive("a", false, megabytes, Upstream()))
        settle()
        assertEquals(0L, dvr.availableMs("a"))
        assertNull(dvr.beginLive(null, true, megabytes, Upstream()))
        assertNull(dvr.beginLive(" ", true, megabytes, Upstream()))
    }

    @Test fun `returning to the same channel keeps what was saved, another channel replaces it`() {
        val dvr = dvr()
        val first = dvr.watch("a", segment(1))
        assertTrue("same buffer", first === dvr.beginLive("a", true, megabytes, Upstream()))
        assertEquals(2_000L, dvr.availableMs("a"))
        assertEquals("another channel has no saved video", 0L, dvr.availableMs("b"))
        dvr.beginLive("b", true, megabytes, Upstream())
        settle()
        assertEquals(0L, dvr.availableMs("a"))
        assertEquals("only the new capture is on disk", 1, sessionDirectories().size)
    }

    @Test fun `changing the size starts over so the new limit applies`() {
        val dvr = dvr()
        val first = dvr.watch("a", segment(1))
        assertFalse(first === dvr.beginLive("a", true, 2 * megabytes, Upstream()))
        assertEquals(0L, dvr.availableMs("a"))
    }

    @Test fun `closing the player deletes the capture unless a replay or live start follows in time`() {
        val dvr = dvr()
        dvr.watch("a", segment(1))
        dvr.sourceClosed()
        elapse(29_000)
        assertEquals("still in the handoff window", 2_000L, dvr.availableMs("a"))
        dvr.beginLive("a", true, megabytes, Upstream())
        elapse(60_000)
        assertEquals("a live start cancels the delete", 2_000L, dvr.availableMs("a"))

        dvr.sourceClosed()
        elapse(10_000)
        dvr.replayStarted()
        elapse(60_000)
        assertEquals("so does the replay", 2_000L, dvr.availableMs("a"))

        dvr.sourceClosed()
        elapse(31_000)
        assertEquals(0L, dvr.availableMs("a"))
        assertTrue("nothing left on disk", sessionDirectories().isEmpty())
    }

    @Test fun `a delete that loses the race to a new start leaves the reused capture alone`() {
        val dvr = dvr()
        dvr.watch("a", segment(1))
        dvr.sourceClosed()
        val lock = ReflectionHelpers.getField<Any>(dvr, "lock")
        var cleanup: Thread? = null
        synchronized(lock) {
            // The grace period runs out on another thread, which then has to wait for this lock.
            cleanup = thread { elapse(30_000) }
            val deadline = System.currentTimeMillis() + 10_000
            while (cleanup!!.state != Thread.State.BLOCKED) {
                assertTrue("the delete never reached the lock", System.currentTimeMillis() < deadline)
                Thread.sleep(1)
            }
            dvr.beginLive("a", true, megabytes, Upstream())
        }
        cleanup!!.join(10_000)
        settle()
        assertEquals(2_000L, dvr.availableMs("a"))
    }

    @Test fun `playing something else deletes the capture at once`() {
        val dvr = dvr()
        dvr.watch("a", segment(1))
        dvr.otherSourceStarted()
        settle()
        assertEquals(0L, dvr.availableMs("a"))
        assertTrue(sessionDirectories().isEmpty())
    }

    @Test fun `a capture left by a killed process goes when the next one begins`() {
        val leftover = File(cacheDir, "live-dvr/old").apply { mkdirs() }
        File(leftover, "0.ts").writeText("video")
        val dvr = dvr()
        dvr.beginLive("a", true, megabytes, Upstream())
        settle()
        assertFalse(leftover.exists())
        assertEquals(1, sessionDirectories().size)
    }

    @Test fun `a replay starts the requested distance from the end, or at the oldest video`() = runTest {
        val dvr = dvr()
        dvr.watch("a", segment(1), segment(2), segment(3))
        val requested = dvr.prepareReplay("a", 2_000)!!
        assertEquals(4_000L, requested.positionMs)
        assertEquals(6_000L, requested.availableMs)
        assertTrue(dvr.isReplayUri(requested.playlistUrl))
        assertFalse(dvr.isReplayUri("https://edge.example/v1/playlist/high.m3u8"))
        assertFalse(dvr.isReplayUri(null))
        assertEquals(0L, dvr.prepareReplay("a", 60_000)!!.positionMs)
        assertEquals(0L, dvr.prepareReplay("a", null)!!.positionMs)
        assertNull("another channel's video", dvr.prepareReplay("b", 1_000))
        dvr.cancelReplay()
    }

    @Test fun `nothing saved means no replay and no pin left behind`() = runTest {
        val dvr = dvr()
        val buffer = dvr.beginLive("a", true, 150, Upstream())!!
        assertNull(dvr.prepareReplay("a", 1_000))
        buffer.saveAll(segment(1), segment(2), segment(3))
        assertEquals("still evicting", 2_000L, buffer.availableMs())
    }

    @Test fun `a cancelled replay records nothing further and goes back to evicting`() = runTest {
        val dvr = dvr()
        val upstream = Upstream(livePlaylist(1, 2)).withVideo(1, 2)
        val buffer = dvr.watch("a", segment(1), maxBytes = 150, upstream = upstream)
        assertNotNull(dvr.prepareReplay("a", 1_000))
        dvr.cancelReplay()
        elapse(60_000)
        assertTrue("recorder stopped before its first poll", upstream.requests.isEmpty())
        buffer.saveAll(segment(2), segment(3))
        assertEquals(2_000L, buffer.availableMs())
    }

    @Test fun `a replay left running closes its playlist through the recorder`() = runTest {
        val dvr = dvr()
        val upstream = Upstream(livePlaylist(1, ended = true)).withVideo(1)
        dvr.beginLive("a", true, megabytes, upstream)!!.apply {
            onPlaylist(DvrPlaylistParser.parse(livePlaylist(1), base)!!)
            save("https://edge.example/high/1.ts", 10)
        }
        val replay = dvr.prepareReplay("a", 1_000)!!
        elapse(LiveDvrRecorder.POLL_MS)
        assertEquals("#EXT-X-ENDLIST", File(android.net.Uri.parse(replay.playlistUrl).path!!).readLines().last())
    }

    // Choosing a source.

    @Test fun `the local buffer serves a rewind it fully covers, never the beginning`() {
        assertTrue(LiveRewind.localBufferCovers(30_000L, 30_000L))
        assertFalse(LiveRewind.localBufferCovers(60_000L, 30_000L))
        assertFalse(LiveRewind.localBufferCovers(null, Long.MAX_VALUE))
    }

    @Test fun `the size setting is read in megabytes with a safe default`() {
        assertEquals(250L * 1024 * 1024, LiveDvr.maxBytes("250"))
        assertEquals(500L * 1024 * 1024, LiveDvr.maxBytes(null))
        assertEquals(500L * 1024 * 1024, LiveDvr.maxBytes("junk"))
        assertEquals(500L * 1024 * 1024, LiveDvr.maxBytes("0"))
    }
}
