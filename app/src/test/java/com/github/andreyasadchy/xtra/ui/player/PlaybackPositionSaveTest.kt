package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class PlaybackPositionSaveTest {
    @Test fun `close captures loaded online and offline positions before IO and media clear`() {
        val context = RuntimeEnvironment.getApplication()
        for (offline in listOf(false, true)) {
            val exo = ExoPlayer.Builder(context).build()
            val tracks = Tracks(listOf(Tracks.Group(
                TrackGroup(Format.Builder().setSampleMimeType("video/avc").build()), false,
                intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))))
            val player = object : ForwardingSimpleBasePlayer(object : ForwardingPlayer(exo) {
                override fun getCurrentTracks() = tracks
            }) {}
            player.setMediaItem(MediaItem.fromUri("https://example.com/video.mp4"), 637_000L)
            val session = mock<MediaSession>()
            whenever(session.player).thenReturn(player)
            val service = mock<PlaybackService>(defaultAnswer = Mockito.CALLS_REAL_METHODS)
            service.liveDvr = mock()
            doReturn(context.packageName).whenever(service).packageName
            doReturn(context.prefs()).whenever(service).getSharedPreferences(any(), any())
            val onlineRepository = mock<PlayerRepository>()
            val offlineRepository = mock<OfflineRepository>()
            val saved = LinkedBlockingQueue<Pair<Long, Long>>()
            runBlocking {
                whenever(onlineRepository.saveVideoPosition(any())).thenAnswer {
                    val position = it.getArgument<VideoPosition>(0)
                    saved.add(position.id to position.position)
                    Unit
                }
                whenever(offlineRepository.updateVideoPosition(any(), any())).thenAnswer {
                    saved.add(it.getArgument<Int>(0).toLong() to it.getArgument<Long>(1))
                    Unit
                }
            }
            val errors = LinkedBlockingQueue<Throwable>()
            val executor = Executors.newSingleThreadExecutor()
            val dispatcher = executor.asCoroutineDispatcher()
            val scope = CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, error -> errors.add(error) })
            val gate = CountDownLatch(1)
            executor.submit { gate.await() }
            ReflectionHelpers.setField(service, "statsScope", scope)
            ReflectionHelpers.setField(service, "mediaSession", session)
            ReflectionHelpers.setField(service, "sourceRequest", "accepted")
            ReflectionHelpers.setField(service, "playerRepository", onlineRepository)
            ReflectionHelpers.setField(service, "offlineRepository", offlineRepository)
            if (offline) ReflectionHelpers.setField(service, "offlineVideoId", 42)
            else ReflectionHelpers.setField(service, "videoId", 42L)
            try {
                service.closeSource("accepted", disconnect = false)
                assertNull(player.currentMediaItem)
                gate.countDown() // IO deliberately runs after the player has been cleared.
                assertEquals(42L to 637_000L, saved.poll(3, TimeUnit.SECONDS))
                assertTrue("Player must never be accessed on IO: $errors", errors.isEmpty())
            } finally {
                gate.countDown()
                scope.cancel()
                dispatcher.close()
                player.release()
            }
        }
    }
}
