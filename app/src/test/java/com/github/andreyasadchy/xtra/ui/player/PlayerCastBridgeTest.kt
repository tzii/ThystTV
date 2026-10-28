package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.view.SurfaceView
import android.os.Looper
import android.net.Uri
import androidx.media3.cast.CastPlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.media3.datasource.DefaultDataSource
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class PlayerCastBridgeTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test fun `stop or clear cancels a delayed outgoing transfer without losing the configured factory`() {
        for (stop in listOf(true, false)) {
            lateinit var local: ExoPlayer
            val bridge = PlayerCastBridge(context, true,
                createLocal = { ExoPlayer.Builder(context).build().also { local = it } },
                endCurrentSession = {}, createCast = { player, _ -> ForwardingPlayer(player) })
            val queued = mutableListOf<Runnable>()
            val factories = mutableListOf<MediaSource.Factory?>()
            val handoff = CastPlaybackHandoff(local, sourceFactoryChanged = { factories += it },
                main = Executor { it.run() }, background = { queued += it })
            ReflectionHelpers.setField(bridge, "handoff", handoff)
            try {
                val item = MediaItem.Builder().setUri("https://example.com/live.m3u8")
                    .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2000).build()).build()
                val factory = HlsMediaSource.Factory(DefaultDataSource.Factory(context))
                assertTrue(handoff.setSource(bridge.player, factory, item) {
                    HlsPlaylistParser().parse(Uri.parse("https://example.com/live.m3u8"),
                        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,CODECS=\"avc1.4d401f\"\nhttps://example.com/variant.m3u8\n".byteInputStream())
                })
                local.playWhenReady = true
                val remote = mock<Player>()
                handoff.transfer(local, remote) { fail("Live transfer should resolve") }
                assertTrue(handoff.hasPendingSource)
                // Match the service's extra SimpleBasePlayer layer, including its idle-stop shortcut.
                val sessionPlayer = bridge.guardCommands(object : ForwardingSimpleBasePlayer(bridge.player) {})
                if (stop) sessionPlayer.stop() else sessionPlayer.clearMediaItems()
                assertFalse(handoff.hasPendingSource)
                queued.single().run()
                verify(remote, never()).setMediaItem(any())
                verify(remote, never()).setPlayWhenReady(true)
                assertEquals(listOf(factory), factories)
            } finally { bridge.dispose(); bridge.player.release() }
        }
    }

    @Test fun `user pause during a receiver lookup loads the receiver paused`() {
        lateinit var local: ExoPlayer
        val bridge = PlayerCastBridge(context, true,
            createLocal = { ExoPlayer.Builder(context).build().also { local = it } },
            endCurrentSession = {}, createCast = { player, _ -> ForwardingPlayer(player) })
        val queued = mutableListOf<Runnable>()
        val handoff = CastPlaybackHandoff(local, main = Executor { it.run() }, background = { queued += it })
        ReflectionHelpers.setField(bridge, "handoff", handoff)
        try {
            val item = MediaItem.Builder().setUri("https://example.com/live.m3u8")
                .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2000).build()).build()
            val factory = HlsMediaSource.Factory(DefaultDataSource.Factory(context))
            assertTrue(handoff.setSource(bridge.player, factory, item) {
                HlsPlaylistParser().parse(Uri.parse("https://example.com/live.m3u8"),
                    "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,CODECS=\"avc1.4d401f\"\nhttps://example.com/variant.m3u8\n".byteInputStream())
            })
            local.playWhenReady = true
            val remote = mock<Player>()
            handoff.transfer(local, remote) { fail("Live transfer should resolve") }
            // Internal handoff commands use bridge.player and must not count as user intent.
            bridge.player.pause()
            bridge.player.play()
            val sessionPlayer = bridge.guardCommands(object : ForwardingSimpleBasePlayer(bridge.player) {}, userIntent = true)
            sessionPlayer.pause()
            queued.single().run()
            verify(remote).setMediaItem(any())
            verify(remote).setPlayWhenReady(false)
            verify(remote, never()).setPlayWhenReady(true)
        } finally { bridge.dispose(); bridge.player.release() }
    }

    @Test fun `queued fallback cannot disconnect a newer source on the same Cast route`() {
        var ended = 0
        lateinit var local: ExoPlayer
        val bridge = PlayerCastBridge(context, true,
            createLocal = { ExoPlayer.Builder(context).build().also { local = it } },
            endCurrentSession = { ended++ }, createCast = { player, _ -> ForwardingPlayer(player) })
        try {
            shadowOf(Looper.getMainLooper()).pause()
            fun failLookup() = PlayerCastBridge::class.java.getDeclaredMethod("returnToLocal", Integer.TYPE, Integer.TYPE)
                .apply { isAccessible = true }.invoke(bridge, 0, com.github.andreyasadchy.xtra.R.string.cast_receiver_unavailable)
            failLookup()
            val item = MediaItem.fromUri("https://example.com/new.mp4")
            bridge.handoff.setSource(local, androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                DefaultDataSource.Factory(context)), item)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, ended)
            failLookup()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, ended)
        } finally { bridge.dispose(); bridge.player.release() }
    }

    @Test fun `setting changes replace only the idle route while keeping the session player stable`() {
        val locals = mutableListOf<ExoPlayer>()
        var casts = 0
        val bridge = PlayerCastBridge(context, false,
            createLocal = { spy(ExoPlayer.Builder(context).build()).also(locals::add) },
            endCurrentSession = {}, createCast = { local, _ -> casts++; ForwardingPlayer(local) })
        val sessionPlayer = bridge.player
        try {
            assertFalse(bridge.castAvailable)
            assertEquals(0, casts)
            sessionPlayer.setMediaItem(MediaItem.fromUri("https://example.com/old.m3u8"))
            sessionPlayer.playWhenReady = true
            clearInvocations(locals[0])
            bridge.configure(true)
            assertSame(sessionPlayer, bridge.player)
            assertTrue(bridge.castAvailable)
            assertNull(sessionPlayer.currentMediaItem)
            assertFalse(sessionPlayer.playWhenReady)
            inOrder(locals[0]) {
                verify(locals[0]).pause()
                verify(locals[0]).stop()
                verify(locals[0]).clearMediaItems()
                verify(locals[0]).release()
            }
            bridge.configure(true)
            assertEquals(2, locals.size)
            bridge.configure(false)
            assertFalse(bridge.castAvailable)
            assertSame(sessionPlayer, bridge.player)
            assertEquals(3, locals.size)
            verify(locals[1]).release()
            assertEquals(1, casts)
        } finally {
            bridge.dispose()
            sessionPlayer.release()
        }
    }

    @Test fun `explicit disconnect cleans an existing SDK session even with a local-only bridge`() {
        var ended = 0
        val bridge = PlayerCastBridge(context, false, { ExoPlayer.Builder(context).build() },
            endCurrentSession = { ended++ }, createCast = { _, _ -> error("Cast must remain lazy") })
        try {
            bridge.endSession()
            assertEquals(1, ended)
        } finally { bridge.dispose(); bridge.player.release() }
    }

    @Test fun `retired Cast transfer callbacks cannot revive an old source`() {
        lateinit var retired: CastPlayer.TransferCallback
        val bridge = PlayerCastBridge(context, true, { ExoPlayer.Builder(context).build() },
            endCurrentSession = {}, createCast = { local, callback ->
                retired = callback
                ForwardingPlayer(local)
            })
        try {
            bridge.configure(false)
            val from = mock<Player>()
            val to = mock<Player>()
            retired.transferState(from, to)
            verifyNoInteractions(from, to)
        } finally { bridge.dispose(); bridge.player.release() }
    }

    @Test fun `explicit close invalidates a rejected local source before delayed return`() {
        for (uri in listOf("file:///storage/video.mp4", "http://127.0.0.1/live")) {
            lateinit var callback: CastPlayer.TransferCallback
            lateinit var local: ExoPlayer
            val bridge = PlayerCastBridge(context, true,
                createLocal = { ExoPlayer.Builder(context).build().also { local = it } },
                endCurrentSession = {}, createCast = { player, transfer ->
                    callback = transfer
                    ForwardingPlayer(player)
                })
            try {
                val remote = mock<Player>()
                local.setMediaItem(MediaItem.fromUri(uri))
                local.playWhenReady = true
                callback.transferState(local, remote)
                // Media3Fragment.closeSession sends these before END_CAST_SESSION.
                bridge.player.pause()
                bridge.player.stop()
                bridge.player.clearMediaItems()
                bridge.endSession()
                callback.transferState(remote, local)
                assertNull(local.currentMediaItem)
                assertFalse(local.playWhenReady)
            } finally { bridge.dispose(); bridge.player.release() }
        }
    }

    @Test fun `explicit disconnect clears owned source before synchronous receiver return`() {
        lateinit var callback: CastPlayer.TransferCallback
        lateinit var local: ExoPlayer
        val remote = mock<Player>()
        val item = MediaItem.fromUri("https://example.com/video.m3u8")
        whenever(remote.currentMediaItem).thenReturn(item)
        val bridge = PlayerCastBridge(context, true,
            createLocal = { ExoPlayer.Builder(context).build().also { local = it } },
            endCurrentSession = { callback.transferState(remote, local) },
            createCast = { player, transfer -> callback = transfer; ForwardingPlayer(player) })
        try {
            val source = mock<MediaSource>()
            whenever(source.mediaItem).thenReturn(item)
            bridge.handoff.setSource(local, source)
            bridge.endSession()
            assertNull(local.currentMediaItem)
            // Explicit close does not retire the bridge's future transfer callbacks.
            local.setMediaItem(MediaItem.fromUri("file:///storage/next.mp4"))
            local.playWhenReady = false
            callback.transferState(local, remote)
            callback.transferState(remote, local)
            assertEquals("file:///storage/next.mp4", local.currentMediaItem?.localConfiguration?.uri.toString())
        } finally { bridge.dispose(); bridge.player.release() }
    }

    @Test fun `source defaults affect local controls and leave receiver volume and speed unchanged`() {
        val local = mock<Player>()
        whenever(local.deviceInfo).thenReturn(DeviceInfo.UNKNOWN)
        local.applyLocalPlaybackDefaults(0.2f, 1.25f)
        verify(local).setVolume(0.2f)
        verify(local).setPlaybackSpeed(1.25f)
        val remote = mock<Player>()
        whenever(remote.deviceInfo).thenReturn(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build())
        remote.applyLocalPlaybackDefaults(0.2f, 1.25f)
        verify(remote, never()).setVolume(any())
        verify(remote, never()).setPlaybackSpeed(any())
    }

    @Test fun `unavailable Cast SDK reports local capability and keeps playback usable`() {
        val bridge = PlayerCastBridge(context, true, { ExoPlayer.Builder(context).build() },
            endCurrentSession = {}, createCast = { _, _ -> throw IllegalStateException("No Cast SDK") })
        try {
            assertFalse(bridge.castAvailable)
            val item = MediaItem.fromUri("https://example.com/local.m3u8")
            bridge.player.setMediaItem(item)
            assertEquals(item, bridge.player.currentMediaItem)
        } finally { bridge.dispose(); bridge.player.release() }
    }

    @Test fun `route replacement rebinds the current surface and never revives a cleared output`() {
        val locals = mutableListOf<ExoPlayer>()
        val bridge = PlayerCastBridge(context, false,
            createLocal = { spy(ExoPlayer.Builder(context).build()).also(locals::add) },
            endCurrentSession = {}, createCast = { local, _ -> ForwardingPlayer(local) })
        try {
            val surface = SurfaceView(context)
            bridge.player.setVideoSurfaceView(surface)
            verify(locals[0]).setVideoSurfaceView(surface)
            bridge.configure(true)
            verify(locals[0]).clearVideoSurfaceView(surface)
            verify(locals[1]).setVideoSurfaceView(surface)
            bridge.player.clearVideoSurfaceView(surface)
            bridge.configure(false)
            verify(locals[2], never()).setVideoSurfaceView(any())
        } finally { bridge.dispose(); bridge.player.release() }
    }
}
