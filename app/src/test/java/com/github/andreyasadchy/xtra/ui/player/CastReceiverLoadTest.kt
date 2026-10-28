package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.cast.Cast
import androidx.media3.cast.CastTrackSelector
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
class CastReceiverLoadTest {
    private val context = RuntimeEnvironment.getApplication()
    private val client = mock<RemoteMediaClient>()
    private var status: MediaStatus? = null
    private val helper = CastReceiverLoad(client = { client })
    private val item = MediaItem.Builder().setUri("https://usher.example/live.m3u8")
        .setMimeType("application/x-mpegURL")
        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2000).build()).build()
        .withReceiverUri(Uri.parse("https://cdn.example/live.m3u8"))

    private fun remote(): RemoteCastPlayer {
        val converter = CastReceiverMediaItemConverter()
        val constructor = RemoteCastPlayer::class.java.getDeclaredConstructor(Context::class.java, Cast::class.java,
            MediaItemConverter::class.java, CastTrackSelector::class.java,
            java.lang.Long.TYPE, java.lang.Long.TYPE, java.lang.Long.TYPE).apply { isAccessible = true }
        // Use Media3's real LOAD/PLAY code without initializing a physical Cast SDK session.
        val remote = constructor.newInstance(context, mock<Cast>(), converter, mock<CastTrackSelector>(), 10_000L, 10_000L, 3000L)
        ReflectionHelpers.setField(remote, "remoteMediaClient", client)
        whenever(client.mediaStatus).thenAnswer { status }
        whenever(client.mediaQueue).thenReturn(mock())
        val pending = mock<PendingResult<RemoteMediaClient.MediaChannelResult>>()
        whenever(client.load(any<MediaLoadRequestData>())).thenReturn(pending)
        whenever(client.play()).thenReturn(pending)
        whenever(client.pause()).thenReturn(pending)
        return remote
    }

    private fun acknowledge(media: MediaInfo, callback: RemoteMediaClient.Callback) {
        val current = mock<MediaStatus>()
        whenever(current.mediaInfo).thenReturn(media)
        status = current
        callback.onStatusUpdated()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `empty receiver starts only after the matching LOAD has a media status`() {
        val remote = remote()
        var ready = 0
        val cancel = helper.start(remote, item, { true }, { ready++ }, { fail("Receiver should acknowledge") })
        try {
            val request = argumentCaptor<MediaLoadRequestData>()
            verify(client).load(request.capture())
            assertEquals(false, request.firstValue.autoplay)
            verify(client, never()).play()
            val callback = argumentCaptor<RemoteMediaClient.Callback>()
            verify(client).registerCallback(callback.capture())
            acknowledge(request.firstValue.queueData!!.items!!.single().media!!, callback.firstValue)
            verify(client).play()
            assertEquals(1, ready)
        } finally { cancel(); remote.release() }
    }

    @Test fun `playing target is staged paused and a pause before acknowledgement wins`() {
        val remote = remote()
        remote.playWhenReady = true
        var play = true
        var ready = 0
        val cancel = helper.start(remote, item, { play }, { ready++ }, { fail("Receiver should acknowledge") })
        try {
            val request = argumentCaptor<MediaLoadRequestData>()
            verify(client).load(request.capture())
            assertEquals(false, request.firstValue.autoplay)
            play = false
            val callback = argumentCaptor<RemoteMediaClient.Callback>()
            verify(client).registerCallback(callback.capture())
            acknowledge(request.firstValue.queueData!!.items!!.single().media!!, callback.firstValue)
            verify(client, never()).play()
            assertFalse(remote.playWhenReady)
            assertEquals(1, ready)
        } finally { cancel(); remote.release() }
    }

    @Test fun `old paused receiver and cancelled acknowledgement never receive a stale PLAY`() {
        val remote = remote()
        val cancel = helper.start(remote, item, { true }, { fail("Cancelled source must not become ready") }, { fail("Cancellation is not failure") })
        try {
            val request = argumentCaptor<MediaLoadRequestData>()
            verify(client).load(request.capture())
            val callback = argumentCaptor<RemoteMediaClient.Callback>()
            verify(client).registerCallback(callback.capture())
            acknowledge(MediaInfo.Builder("https://example.com/old").setContentType("video/mp4").build(), callback.firstValue)
            verify(client, never()).play()
            // A matching status already queued when close happens must also be ignored.
            val current = mock<MediaStatus>()
            val media = request.firstValue.queueData!!.items!!.single().media!!
            whenever(current.mediaInfo).thenReturn(media)
            status = current
            callback.firstValue.onStatusUpdated()
            cancel()
            shadowOf(Looper.getMainLooper()).idle()
            verify(client, never()).play()
        } finally { cancel(); remote.release() }
    }

    @Test fun `missing receiver acknowledgement fails once and cleans its callback`() {
        val remote = remote()
        var failed = 0
        val cancel = helper.start(remote, item, { true }, { fail("No status was delivered") }, { failed++ })
        try {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
            assertEquals(1, failed)
            verify(client).unregisterCallback(any())
            verify(client, never()).play()
        } finally { cancel(); remote.release() }
    }
}
