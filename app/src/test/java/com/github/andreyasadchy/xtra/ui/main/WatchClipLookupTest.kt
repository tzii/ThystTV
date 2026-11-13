package com.github.andreyasadchy.xtra.ui.main

import android.app.Application
import com.github.andreyasadchy.xtra.model.gql.clip.ClipDataResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipVideoResponse
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Watch clip looks the clip up before playing it. The result belongs to the player that asked:
 * it must not open after that player closed or another started, even across a recreation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class WatchClipLookupTest {
    private val dispatcher = StandardTestDispatcher()
    private val gql = mock<GraphQLRepository>()
    private lateinit var viewModel: MainViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        viewModel = MainViewModel(
            RuntimeEnvironment.getApplication(), gql, mock(), mock(), mock(), mock(), null, null, mock(), mock(), mock(), mock(),
        )
        wheneverBlocking { gql.loadClipData(anyOrNull(), any(), anyOrNull()) }.thenReturn(
            ClipDataResponse(data = ClipDataResponse.Data(ClipDataResponse.Clip(ClipDataResponse.User(id = "1", login = "channel")))),
        )
        wheneverBlocking { gql.loadClipVideo(anyOrNull(), any(), anyOrNull()) }.thenReturn(videoResponse())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun videoResponse() = ClipVideoResponse(data = ClipVideoResponse.Data(ClipVideoResponse.Clip(durationSeconds = 30)))

    private fun watch(id: String, title: String? = null) =
        viewModel.loadWatchClip(id, title, "OkHttp", emptyMap(), emptyMap(), false)

    @Test fun `the clip plays while the player that asked is still open`() = runTest(dispatcher) {
        watch("clip", "Big play")
        advanceUntilIdle()
        val clip = viewModel.takeWatchClip(viewModel.watchClip.value!!)!!
        assertEquals("clip", clip.id)
        assertEquals("Big play", clip.title)
        assertEquals("channel", clip.channelLogin)
        assertNull(viewModel.watchClip.value)
    }

    @Test fun `the clip is dropped once that player closed or another started`() = runTest(dispatcher) {
        watch("clip")
        viewModel.onPlayerChanged()
        advanceUntilIdle()
        assertNull(viewModel.takeWatchClip(viewModel.watchClip.value!!))
        assertNull(viewModel.watchClip.value)
    }

    @Test fun `a result that arrives after the player changed is dropped too`() = runTest(dispatcher) {
        watch("clip")
        advanceUntilIdle()
        val result = viewModel.watchClip.value!!
        viewModel.onPlayerChanged()
        assertNull(viewModel.takeWatchClip(result))
    }

    @Test fun `a newer Watch replaces a slower older one`() = runTest(dispatcher) {
        val slow = CompletableDeferred<Unit>()
        wheneverBlocking { gql.loadClipVideo(anyOrNull(), any(), eq("old")) } doSuspendableAnswer {
            slow.await()
            videoResponse()
        }
        watch("old")
        advanceUntilIdle()
        watch("new")
        advanceUntilIdle()
        slow.complete(Unit)
        advanceUntilIdle()
        assertEquals("new", viewModel.watchClip.value!!.clip.id)
    }

    @Test fun `a failed lookup leaves nothing behind`() = runTest(dispatcher) {
        wheneverBlocking { gql.loadClipVideo(anyOrNull(), any(), anyOrNull()) }.thenThrow(IllegalStateException("offline"))
        watch("clip")
        advanceUntilIdle()
        assertNull(viewModel.watchClip.value)
    }

    @Test fun `clip links still open whatever the player did`() = runTest(dispatcher) {
        watch("watched")
        viewModel.onPlayerChanged()
        viewModel.loadClip("linked", "OkHttp", emptyMap(), emptyMap(), false)
        advanceUntilIdle()
        assertEquals("linked", viewModel.clip.value!!.id)
    }
}
