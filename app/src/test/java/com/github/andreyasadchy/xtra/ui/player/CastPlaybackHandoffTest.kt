package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.media3.exoplayer.source.MediaSource
import java.io.IOException
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class CastPlaybackHandoffTest {
    private val local = mock<ExoPlayer>().also {
        whenever(it.trackSelectionParameters).thenReturn(TrackSelectionParameters.Builder().build())
    }
    private val remote = mock<Player>().also {
        whenever(it.deviceInfo).thenReturn(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build())
        whenever(it.playbackParameters).thenReturn(PlaybackParameters(1.25f))
        whenever(it.currentPosition).thenReturn(42_000L)
        whenever(it.playWhenReady).thenReturn(false)
    }
    private val direct = Executor { it.run() }
    private val handoff = CastPlaybackHandoff(local, main = direct, background = direct)
    private fun source(url: String = "https://example.com/clip.mp4", live: Boolean = false): MediaSource {
        val item = MediaItem.Builder().setUri(url).setMimeType(if (url.endsWith(".m3u8")) "application/x-mpegURL" else "video/mp4").apply {
            if (live) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2000).build())
        }.build()
        return mock<MediaSource>().also { whenever(it.mediaItem).thenReturn(item) }
    }
    private fun liveSource(url: String = "https://usher.example/live.m3u8") = source(url, live = true)
    private fun multivariant(): HlsMultivariantPlaylist = HlsPlaylistParser().parse(Uri.parse("https://usher.example/live.m3u8"), """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=9000000,RESOLUTION=1920x1080,CODECS="hvc1.1.2.L123.90,mp4a.40.2",FRAME-RATE=60.000
        https://cdn.example/1080p60-hevc.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,CODECS="avc1.64002A,mp4a.40.2",FRAME-RATE=60.000
        https://cdn.example/1080p60.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720,CODECS="avc1.4D401F,mp4a.40.2",FRAME-RATE=60.000
        https://cdn.example/720p60.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,CODECS="avc1.4D401F,mp4a.40.2",FRAME-RATE=30.000
        https://cdn.example/720p30.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=160000,CODECS="mp4a.40.2"
        https://cdn.example/audio_only.m3u8
    """.trimIndent().byteInputStream()) as HlsMultivariantPlaylist
    private fun receiverUri(item: MediaItem) = (item.localConfiguration?.tag as? CastReceiverUri)?.uri?.toString()
    private fun selectLocalQuality(height: Int, frameRate: Float) {
        val format = Format.Builder().setHeight(height).setFrameRate(frameRate).setSampleMimeType("video/avc").build()
        whenever(local.trackSelectionParameters).thenReturn(TrackSelectionParameters.Builder()
            .setOverrideForType(TrackSelectionOverride(TrackGroup(format), 0)).build())
    }

    @Test fun `local playback keeps its exact configured media source`() {
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        val source = source()
        handoff.setSource(local, source)
        verify(local).setMediaSource(source)
        verify(local, never()).setMediaItem(any())
    }

    @Test fun `new remote content is sent to the active receiver instead of starting local video`() {
        val source = source()
        handoff.setSource(remote, source)
        verify(remote).setMediaItem(source.mediaItem)
        verifyNoInteractions(local)
    }

    @Test fun `a saved position goes with the source on the phone and the receiver`() {
        val onPhone = source("https://example.com/phone.mp4")
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        handoff.setSource(local, onPhone, 600_000L)
        verify(local).setMediaSource(onPhone, 600_000L)
        val onReceiver = source("https://example.com/receiver.mp4")
        handoff.setSource(remote, onReceiver, 600_000L)
        verify(remote).setMediaItem(onReceiver.mediaItem, 600_000L)
        verify(remote, never()).seekTo(any())
    }

    @Test fun `selected quality returns through the configured source factory`() {
        val master = source()
        val selected = source("https://example.com/720p.mp4")
        val masterItem = master.mediaItem
        val selectedItem = selected.mediaItem
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(masterItem)).thenReturn(master)
        whenever(factory.createMediaSource(selectedItem)).thenReturn(selected)
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        whenever(local.currentMediaItem).thenReturn(selectedItem)
        whenever(remote.currentMediaItem).thenReturn(selectedItem)
        var transfers = 0
        val handoff = CastPlaybackHandoff(local) { _, _, _ -> transfers++ }
        handoff.setSource(local, factory, masterItem)
        handoff.transfer(local, remote) { fail("Online quality should transfer") }
        handoff.transfer(remote, local) { fail("Return should not reject") }
        assertEquals(1, transfers)
        verify(factory).createMediaSource(selectedItem)
        verify(local).setMediaSource(selected, 42_000L)
        verify(local).setPlayWhenReady(false)
    }

    @Test fun `returning clip reuses parser and HTTP source with receiver position pause and speed`() {
        val source = source()
        handoff.setSource(remote, source)
        val item = source.mediaItem
        whenever(remote.currentMediaItem).thenReturn(item)
        assertTrue(handoff.restoreFrom(remote))
        verify(local).setMediaSource(source, 42_000L)
        verify(local).setPlaybackParameters(PlaybackParameters(1.25f))
        verify(local).prepare()
        verify(local).setPlayWhenReady(false)
    }

    @Test fun `returning live uses live edge rather than a receiver window offset`() {
        val source = liveSource()
        handoff.setSource(remote, mock<MediaSource.Factory>().also { whenever(it.createMediaSource(any())).thenReturn(source) },
            source.mediaItem) { multivariant() }
        val item = source.mediaItem
        whenever(remote.currentMediaItem).thenReturn(item)
        whenever(remote.playWhenReady).thenReturn(true)
        assertTrue(handoff.restoreFrom(remote))
        verify(local).setMediaSource(source, C.TIME_UNSET)
        verify(local).setPlayWhenReady(true)
    }

    @Test fun `old receiver media cannot resurrect a replaced stream`() {
        val old = source("https://example.com/old.m3u8")
        handoff.setSource(remote, old)
        handoff.setSource(remote, source("https://example.com/new.m3u8"))
        val item = old.mediaItem
        whenever(remote.currentMediaItem).thenReturn(item)
        assertFalse(handoff.restoreFrom(remote))
        verifyNoInteractions(local)
    }

    @Test fun `cleared session cannot restore a stale source`() {
        val source = source()
        handoff.setSource(remote, source)
        val item = source.mediaItem
        whenever(remote.currentMediaItem).thenReturn(item)
        handoff.clear()
        assertFalse(handoff.restoreFrom(remote))
        verifyNoInteractions(local)
    }

    @Test fun `clear retires receiver ownership while retaining the configured local source factory`() {
        val source = source()
        val item = source.mediaItem
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(item)).thenReturn(source)
        val changes = mutableListOf<MediaSource.Factory?>()
        val handoff = CastPlaybackHandoff(local, sourceFactoryChanged = { changes += it })
        assertTrue(handoff.setSource(remote, factory, item))
        handoff.cancelPending(clearOwnership = true)
        whenever(remote.currentMediaItem).thenReturn(item)
        assertFalse(handoff.restoreFrom(remote))
        assertEquals(listOf(factory), changes)
        verifyNoInteractions(local)
    }

    @Test fun `receiver URLs require reachable online media rather than phone files or loopback`() {
        assertTrue(CastPlaybackHandoff.canCast(MediaItem.fromUri("https://example.com/live.m3u8")))
        assertTrue(CastPlaybackHandoff.canCast(MediaItem.fromUri("https://localhost.example.com/live.m3u8")))
        assertTrue(CastPlaybackHandoff.canCast(MediaItem.fromUri("http://8.8.8.8/live")))
        assertTrue(CastPlaybackHandoff.canCast(MediaItem.fromUri("http://[2001:4860:4860::8888]/live")))
        for (uri in listOf("file:///storage/video.mp4", "content://downloads/1", "http://LOCALHOST/live", "http://localhost/live",
            "http://localhost./live", "http://video.localhost/live", "http://VIDEO.LOCALHOST./live", "http://127.0.0.1/live", "http://[::1]/live",
            "http://[0:0:0:0:0:0:0:1]/live", "http://[::ffff:127.0.0.1]/live", "http://[::]/live", "http://2130706433/live",
            "http://999.10.0.1/live", "http://256.1.1.1/live", "http://999999999/live")) {
            assertFalse(uri, CastPlaybackHandoff.canCast(MediaItem.fromUri(uri)))
        }
        assertFalse(CastPlaybackHandoff.canCast(null))
    }

    @Test fun `rejected phone download returns at the same paused position without loading the receiver`() {
        val item = MediaItem.fromUri("file:///storage/video.mp4")
        whenever(local.currentMediaItem).thenReturn(item)
        whenever(local.currentPosition).thenReturn(9_500L)
        whenever(local.playWhenReady).thenReturn(false)
        whenever(local.playbackParameters).thenReturn(PlaybackParameters(1.5f))
        var rejected = 0
        handoff.transfer(local, remote) { rejected++ }
        assertEquals(1, rejected)
        verify(remote).clearMediaItems()
        verify(remote, never()).setMediaItems(any<List<MediaItem>>(), any<Int>(), any<Long>())
        handoff.transfer(remote, local) { fail("Return to local must not reject again") }
        verify(local).setMediaItem(item, 9_500L)
        verify(local).setPlaybackParameters(PlaybackParameters(1.5f))
        verify(local).setPlayWhenReady(false)
        verify(local).prepare()
    }

    @Test fun `empty session does not show an unsupported media error`() {
        handoff.transfer(local, remote) { fail("Nothing was playing") }
        verify(remote).clearMediaItems()
        verifyNoMoreInteractions(remote)
    }

    @Test fun `localhost names reject outgoing transfer without loading receiver media`() {
        for (uri in listOf("http://localhost./live", "http://video.localhost/live", "http://VIDEO.LOCALHOST./live")) {
            val local = mock<ExoPlayer>()
            val remote = mock<Player>()
            val item = MediaItem.fromUri(uri)
            whenever(local.currentMediaItem).thenReturn(item)
            whenever(local.playbackParameters).thenReturn(PlaybackParameters(1f))
            val handoff = CastPlaybackHandoff(local, transferOnline = { _, _, _ -> fail("Localhost must not transfer") })
            var rejected = false
            handoff.transfer(local, remote) { rejected = true }
            assertTrue(uri, rejected)
            verify(remote).clearMediaItems()
            verifyNoMoreInteractions(remote)
        }
    }

    @Test fun `new source invalidates a rejected local-only snapshot before transfer returns`() {
        val blocked = MediaItem.fromUri("file:///storage/old.mp4")
        whenever(local.currentMediaItem).thenReturn(blocked)
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        whenever(local.playbackParameters).thenReturn(PlaybackParameters(1f))
        handoff.transfer(local, remote) { }
        val next = source("https://example.com/new.m3u8")
        val nextItem = next.mediaItem
        assertTrue(handoff.setSource(local, next))
        whenever(remote.currentMediaItem).thenReturn(nextItem)
        handoff.transfer(remote, local) { fail("Returning from Cast must not reject") }
        verify(local).setMediaSource(next, 42_000L)
        verify(local, never()).setMediaItem(eq(blocked), any<Long>())
    }

    @Test fun `successful new cast transfer invalidates a rejected old snapshot`() {
        val blocked = MediaItem.fromUri("file:///storage/old.mp4")
        val next = source("https://example.com/new.mp4")
        val nextItem = next.mediaItem
        val handoff = CastPlaybackHandoff(local, transferOnline = { _, _, _ -> })
        whenever(local.currentMediaItem).thenReturn(blocked)
        whenever(local.playbackParameters).thenReturn(PlaybackParameters(1f))
        handoff.transfer(local, remote) { }
        whenever(local.currentMediaItem).thenReturn(nextItem)
        handoff.transfer(local, remote) { fail("Online transfer must succeed") }
        whenever(remote.currentMediaItem).thenReturn(nextItem)
        handoff.transfer(remote, local) { fail("Return to local must not reject") }
        verify(local).clearMediaItems()
        verify(local, never()).setMediaItem(eq(blocked), any<Long>())
    }

    @Test fun `unowned receiver media is cleared on return rather than paired with stale chat`() {
        whenever(remote.currentMediaItem).thenReturn(MediaItem.fromUri("https://other.example/video"))
        handoff.transfer(remote, local) { fail("Return to local must not reject") }
        verify(local).clearMediaItems()
        verify(local, never()).prepare()
    }

    @Test fun `remote local-only source switches preserve the receiver and retained factory`() {
        val old = source()
        val oldItem = old.mediaItem
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(oldItem)).thenReturn(old)
        val changes = mutableListOf<MediaSource.Factory?>()
        val handoff = CastPlaybackHandoff(local, sourceFactoryChanged = { changes.add(it) })
        assertTrue(handoff.setSource(remote, factory, oldItem))
        clearInvocations(remote)
        for (url in listOf("http://localhost/live", "http://localhost./live", "http://video.localhost/live", "http://VIDEO.LOCALHOST./live",
            "http://127.0.0.1/live", "http://[::1]/live", "http://[::ffff:127.0.0.1]/live", "http://0.0.0.0/live", "file:///storage/video.mp4")) {
            val replacementFactory = mock<MediaSource.Factory>()
            assertFalse(url, handoff.setSource(remote, replacementFactory, source(url).mediaItem))
            assertFalse(url, handoff.setSource(remote, source(url)))
            verifyNoInteractions(replacementFactory)
        }
        verify(remote, never()).setMediaItem(any())
        assertEquals(listOf(factory), changes)
        whenever(remote.currentMediaItem).thenReturn(oldItem)
        assertTrue(handoff.restoreFrom(remote))
        verify(local).setMediaSource(old, 42_000L)
    }

    @Test fun `receiver loads a live variant because usher playlists reject receiver origins`() {
        val sent = mutableListOf<MediaItem>()
        val handoff = CastPlaybackHandoff(local, main = direct, background = direct, transferOnline = { _, _, item -> sent += item })
        val item = liveSource().mediaItem
        whenever(local.currentMediaItem).thenReturn(item)
        whenever(local.playbackParameters).thenReturn(PlaybackParameters(1f))
        whenever(local.currentManifest).thenReturn(
            HlsManifest::class.java.getDeclaredConstructor(HlsMultivariantPlaylist::class.java, HlsMediaPlaylist::class.java)
                .apply { isAccessible = true }.newInstance(multivariant(), null))
        selectLocalQuality(720, 30f)
        handoff.transfer(local, remote) { fail("Live must cast") }
        assertEquals("https://usher.example/live.m3u8", sent.single().localConfiguration?.uri.toString())
        assertEquals("https://cdn.example/720p30.m3u8", receiverUri(sent.single()))
    }

    @Test fun `live started while casting resolves its variant with the source loader`() {
        val source = liveSource()
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(source.mediaItem)).thenReturn(source)
        val pending = mutableListOf<Runnable>()
        val handoff = CastPlaybackHandoff(local, main = direct, background = { pending += it })
        assertTrue(handoff.setSource(remote, factory, source.mediaItem) { multivariant() })
        verify(remote).pause()
        verify(remote, never()).setMediaItem(any())
        verify(remote, never()).setPlayWhenReady(true)
        pending.single().run()
        val sent = argumentCaptor<MediaItem>()
        inOrder(remote) {
            verify(remote).setMediaItem(sent.capture())
            verify(remote).setPlayWhenReady(true)
        }
        assertEquals(source.mediaItem.localConfiguration?.uri, sent.firstValue.localConfiguration?.uri)
        assertEquals("https://cdn.example/1080p60.m3u8", receiverUri(sent.firstValue))
    }

    @Test fun `a slow playlist lookup cannot replace a newer source or a returned route`() {
        val pending = mutableListOf<Runnable>()
        val handoff = CastPlaybackHandoff(local, main = direct, background = { pending += it })
        val first = liveSource("https://usher.example/first.m3u8")
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(first.mediaItem)).thenReturn(first)
        val second = source("https://example.com/second.mp4")
        assertTrue(handoff.setSource(remote, factory, first.mediaItem) { multivariant() })
        assertTrue(handoff.setSource(remote, second))
        assertTrue(handoff.setSource(remote, factory, first.mediaItem) { multivariant() })
        whenever(remote.currentMediaItem).thenReturn(null)
        handoff.transfer(remote, local) { fail("Return must not reject") }
        assertEquals(2, pending.size)
        pending.forEach { it.run() }
        verify(remote).setMediaItem(second.mediaItem)
        verify(remote, times(1)).setMediaItem(any())
    }

    @Test fun `receiver ownership remains pending until its own load acknowledgement and cancels on replacement`() {
        val ready = mutableListOf<() -> Unit>()
        var cancellations = 0
        val handoff = CastPlaybackHandoff(local, main = direct, background = direct,
            loadReceiver = { _, _, _, accepted, _ ->
                ready += accepted
                val cancel: () -> Unit = { cancellations++ }
                cancel
            })
        val first = liveSource()
        val second = liveSource("https://usher.example/next.m3u8")
        val factory = mock<MediaSource.Factory>()
        val firstItem = first.mediaItem
        val secondItem = second.mediaItem
        whenever(factory.createMediaSource(firstItem)).thenReturn(first)
        whenever(factory.createMediaSource(secondItem)).thenReturn(second)
        handoff.setSource(remote, factory, firstItem) { multivariant() }
        assertTrue(handoff.hasPendingSource)
        handoff.setSource(remote, factory, secondItem) { multivariant() }
        assertEquals(1, cancellations)
        ready[0]()
        assertTrue(handoff.hasPendingSource)
        ready[1]()
        assertFalse(handoff.hasPendingSource)
    }

    @Test fun `pause during a receiver LOAD carries into the acknowledgement and the timeout fallback`() {
        val source = liveSource()
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(source.mediaItem)).thenReturn(source)
        var play: () -> Boolean = { fail("LOAD not started"); true }
        var failed: () -> Unit = {}
        val handoff = CastPlaybackHandoff(local, main = direct, background = direct,
            loadReceiver = { _, _, intent, _, failure ->
                play = intent
                failed = failure
                val cancel: () -> Unit = {}
                cancel
            })
        assertTrue(handoff.setSource(remote, factory, source.mediaItem) { multivariant() })
        assertTrue(play())
        handoff.playIntent(false)
        assertFalse(play())
        failed()
        handoff.transfer(remote, local) { fail("Return must not reject") }
        verify(local).setMediaSource(source, C.TIME_UNSET)
        verify(local).setPlayWhenReady(false)
        verify(local, never()).setPlayWhenReady(true)
        // Without a pending load there is no snapshot for a later intent to change.
        handoff.playIntent(true)
        assertFalse(handoff.hasPendingSource)
    }

    @Test fun `failed receiver lookup returns the live stream to the phone at the live edge`() {
        val source = liveSource()
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(source.mediaItem)).thenReturn(source)
        var unavailable = 0
        val handoff = CastPlaybackHandoff(local, receiverUnavailable = { unavailable++ }, main = direct, background = direct)
        assertTrue(handoff.setSource(remote, factory, source.mediaItem) { throw IOException("offline") })
        assertEquals(1, unavailable)
        verify(remote, never()).setMediaItem(any())
        handoff.transfer(remote, local) { fail("Return must not reject") }
        verify(local).setMediaSource(source, C.TIME_UNSET)
        verify(local).setPlayWhenReady(true)
    }

    @Test fun `disconnecting during a playlist lookup keeps the stream playing on the phone`() {
        val pending = mutableListOf<Runnable>()
        val handoff = CastPlaybackHandoff(local, main = direct, background = { pending += it })
        val source = liveSource()
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(source.mediaItem)).thenReturn(source)
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        assertTrue(handoff.setSource(local, factory, source.mediaItem) { multivariant() })
        // Buffering: no parsed manifest yet, so the transfer waits on the loader.
        val item = source.mediaItem
        whenever(local.currentMediaItem).thenReturn(item)
        whenever(local.playWhenReady).thenReturn(true)
        whenever(local.playbackParameters).thenReturn(PlaybackParameters(1.5f))
        handoff.transfer(local, remote) { fail("Live must cast") }
        clearInvocations(local)
        whenever(remote.currentMediaItem).thenReturn(null)
        handoff.transfer(remote, local) { fail("Return must not reject") }
        verify(local).setMediaSource(source, C.TIME_UNSET)
        verify(local).setPlaybackParameters(PlaybackParameters(1.5f))
        verify(local).setPlayWhenReady(true)
        verify(local, never()).clearMediaItems()
        pending.single().run()
        verify(remote, never()).setMediaItem(any())
    }

    @Test fun `switching live channels while casting passes the service pre-check`() {
        // PlaybackService validates a URI-only item before START_STREAM adds live configuration.
        assertTrue(CastPlaybackHandoff.canCast(MediaItem.fromUri("https://usher.example/next.m3u8")))
        val next = liveSource("https://usher.example/next.m3u8")
        assertTrue(CastPlaybackHandoff.canSetSource(remote, next.mediaItem))
    }

    @Test fun `receiver lookup uses the playback parser and skips portrait variants`() {
        val bytes = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=9000000,RESOLUTION=1080x1920,CODECS="avc1.64002A,mp4a.40.2",FRAME-RATE=60.000,STABLE-VARIANT-ID="1080p60-portrait"
            https://cdn.example/1080p60-portrait.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,CODECS="avc1.64002A,mp4a.40.2",FRAME-RATE=60.000,STABLE-VARIANT-ID="1080p60"
            https://cdn.example/1080p60.m3u8
        """.trimIndent().toByteArray()
        val loader = castPlaylistLoader({ ByteArrayDataSource(bytes) }, PlaybackService.CustomHlsPlaylistParserFactory())
        assertEquals("https://cdn.example/1080p60.m3u8",
            CastReceiverVariants.select(loader.load(Uri.parse("https://usher.example/live.m3u8")), null).toString())
    }

    @Test fun `a variant the receiver cannot reach returns the stream to the phone`() {
        val source = liveSource()
        val factory = mock<MediaSource.Factory>()
        whenever(factory.createMediaSource(source.mediaItem)).thenReturn(source)
        var unavailable = 0
        val handoff = CastPlaybackHandoff(local, receiverUnavailable = { unavailable++ }, main = direct, background = direct)
        val proxied = HlsPlaylistParser().parse(Uri.parse("https://usher.example/live.m3u8"), """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,CODECS="avc1.64002A,mp4a.40.2"
            http://127.0.0.1:8080/1080p.m3u8
        """.trimIndent().byteInputStream())
        assertTrue(handoff.setSource(remote, factory, source.mediaItem) { proxied })
        assertEquals(1, unavailable)
        verify(remote, never()).setMediaItem(any())
    }

    @Test fun `Twitch VoD playlists stay on the phone while live and clips can cast`() {
        assertFalse(CastPlaybackHandoff.canReceive(source("https://usher.example/vod.m3u8").mediaItem))
        assertFalse(CastPlaybackHandoff.canReceive(MediaItem.fromUri("https://usher.example/vod.m3u8")))
        assertTrue(CastPlaybackHandoff.canReceive(liveSource().mediaItem))
        assertTrue(CastPlaybackHandoff.canReceive(source().mediaItem))
        val vod = source("https://usher.example/vod.m3u8").mediaItem
        whenever(local.currentMediaItem).thenReturn(vod)
        whenever(local.playbackParameters).thenReturn(PlaybackParameters(1f))
        var rejected = 0
        handoff.transfer(local, remote) { rejected++ }
        assertEquals(1, rejected)
        assertFalse(handoff.setSource(remote, source("https://usher.example/other.m3u8")))
    }

    @Test fun `receiver variants prefer H264 and follow the local quality choice`() {
        val playlist = multivariant()
        fun pick(height: Int?, fps: Float = 60f) = CastReceiverVariants.select(playlist,
            height?.let { Format.Builder().setHeight(it).setFrameRate(fps).build() }).toString()
        assertEquals("https://cdn.example/1080p60.m3u8", pick(null))
        assertEquals("https://cdn.example/1080p60.m3u8", pick(1080))
        assertEquals("https://cdn.example/720p60.m3u8", pick(720))
        assertEquals("https://cdn.example/720p30.m3u8", pick(720, 30f))
        assertEquals("https://cdn.example/720p60.m3u8", pick(900))
        assertEquals("https://cdn.example/720p30.m3u8", pick(160))
        val audio = HlsPlaylistParser().parse(Uri.parse("https://usher.example/a.m3u8"),
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=160000,CODECS=\"mp4a.40.2\"\nhttps://cdn.example/audio_only.m3u8\n".byteInputStream())
        assertEquals("https://cdn.example/audio_only.m3u8", CastReceiverVariants.select(audio, null).toString())
        val hevcOnly = HlsPlaylistParser().parse(Uri.parse("https://usher.example/h.m3u8"),
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=9000000,RESOLUTION=1920x1080,CODECS=\"hvc1.1.2.L123.90,mp4a.40.2\"\nhttps://cdn.example/1080p-hevc.m3u8\n".byteInputStream())
        assertNull(CastReceiverVariants.select(hevcOnly, null))
    }

    @Test fun `phone-local proxy remains playable locally`() {
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        val proxy = source("http://127.0.0.1/live")
        assertTrue(handoff.setSource(local, proxy))
        verify(local).setMediaSource(proxy)
        verifyNoInteractions(remote)
    }

}
