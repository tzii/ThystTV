package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class PlayerResumeLookupTest {
    private val dispatcher = StandardTestDispatcher()
    private val playerRepository: PlayerRepository = mock()
    private val offlineRepository: OfflineRepository = mock()
    private fun viewModel() = PlayerViewModel(
        mock(), mock(), mock(), mock(), mock(), mock(), null, null, mock(), mock(),
        playerRepository, mock(), offlineRepository,
    )

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `network lookup converts seconds before resetting completion`() = runTest {
        whenever(playerRepository.getVideoPosition(1L)).thenReturn(VideoPosition(1L, 60_000L))
        val vm = viewModel()
        vm.getVideoPosition(1L, 60, "https://example.com/vod.jpg")
        advanceUntilIdle()
        assertEquals(0L, vm.savedPosition.value)
        vm.getVideoPosition(1L, 61, "https://example.com/vod.jpg")
        advanceUntilIdle()
        assertEquals(60_000L, vm.savedPosition.value)
    }

    @Test fun `explicit bookmark clip and deep link timestamps bypass saved resume`() = runTest {
        val vm = viewModel()
        for (offset in listOf(0L, 30_000L, 60_000L, 70_000L)) {
            vm.getVideoPosition(1L, 60, "https://example.com/vod.jpg", explicitPosition = offset)
            advanceUntilIdle()
            assertEquals(offset, vm.savedPosition.value)
        }
        verifyNoInteractions(playerRepository)
    }

    @Test fun `unknown network duration preserves saved position`() = runTest {
        whenever(playerRepository.getVideoPosition(1L)).thenReturn(VideoPosition(1L, 60_000L))
        val vm = viewModel()
        vm.getVideoPosition(1L, 0, "https://example.com/vod.jpg")
        advanceUntilIdle()
        assertEquals(60_000L, vm.savedPosition.value)
    }

    @Test fun `offline segment uses its local duration not source offset`() = runTest {
        val video = OfflineVideo(duration = 60_000L, lastWatchPosition = 60_000L, sourceStartPosition = 3_600_000L)
        whenever(offlineRepository.getVideoById(1)).thenReturn(video)
        val vm = viewModel()
        vm.getOfflineVideoPosition(1)
        advanceUntilIdle()
        assertEquals(0L, vm.savedOfflineVideoPosition.value)
        video.lastWatchPosition = 59_999L
        vm.getOfflineVideoPosition(1)
        advanceUntilIdle()
        assertEquals(59_999L, vm.savedOfflineVideoPosition.value)
    }

    @Test fun `processing and bookmarked vods retain positions beyond stale duration`() = runTest {
        whenever(playerRepository.getVideoPosition(1L)).thenReturn(VideoPosition(1L, 70_000L))
        val vm = viewModel()
        for (thumbnail in listOf(null, "", "https://vod-secure.twitch.tv/_404/404_processing_320x180.png", "/files/thumbnails/1")) {
            vm.getVideoPosition(1L, 60, thumbnail)
            advanceUntilIdle()
            assertEquals(70_000L, vm.savedPosition.value)
        }
        // Once the broadcast has final metadata, completed automatic resume restarts.
        vm.getVideoPosition(1L, 60, "https://example.com/final.jpg")
        advanceUntilIdle()
        assertEquals(0L, vm.savedPosition.value)
    }

    @Test fun `a resume lookup and its playlist count as loading until the start takes them`() = runTest {
        val lookup = CompletableDeferred<VideoPosition?>()
        whenever(playerRepository.getVideoPosition(1L)).doSuspendableAnswer { lookup.await() }
        val playlist = CompletableDeferred<Pair<String, List<String>>>()
        whenever(playerRepository.loadVideoPlaylistUrl(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any()))
            .doSuspendableAnswer { playlist.await() }
        val vm = viewModel()
        assertFalse(vm.sourceLoading)
        vm.getVideoPosition(1L, 0, "https://example.com/vod.jpg")
        runCurrent()
        assertTrue(vm.sourceLoading)
        lookup.complete(VideoPosition(1L, 30_000L))
        advanceUntilIdle()
        // Resolved but not yet taken by the screen's collector.
        assertTrue(vm.sourceLoading)
        vm.savedPosition.value = null
        vm.loadVideo(null, emptyMap(), "1", null, null, false)
        runCurrent()
        assertTrue(vm.sourceLoading)
        playlist.complete("https://example.com/vod.m3u8" to emptyList())
        advanceUntilIdle()
        assertTrue(vm.sourceLoading)
        vm.videoResult.value = null
        assertFalse(vm.sourceLoading)
    }

    @Test fun `a failed playlist or download lookup stops counting as loading`() = runTest {
        val lookup = CompletableDeferred<OfflineVideo?>()
        whenever(offlineRepository.getVideoById(1)).doSuspendableAnswer { lookup.await() }
        whenever(playerRepository.loadVideoPlaylistUrl(anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any()))
            .doSuspendableAnswer { throw java.io.IOException("offline") }
        val vm = viewModel()
        vm.getOfflineVideoPosition(1)
        runCurrent()
        assertTrue(vm.sourceLoading)
        lookup.complete(null)
        advanceUntilIdle()
        vm.savedOfflineVideoPosition.value = null
        assertFalse(vm.sourceLoading)
        vm.loadVideo(null, emptyMap(), "1", null, null, false)
        advanceUntilIdle()
        // Nothing will start, so the next connection may restart it.
        assertFalse(vm.sourceLoading)
    }

    @Test fun `missing download record starts from zero`() = runTest {
        val vm = viewModel()
        vm.getOfflineVideoPosition(1)
        advanceUntilIdle()
        assertEquals(0L, vm.savedOfflineVideoPosition.value)
    }
}
