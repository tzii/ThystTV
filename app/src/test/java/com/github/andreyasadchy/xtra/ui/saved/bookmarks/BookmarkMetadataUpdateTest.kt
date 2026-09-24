package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import android.app.Application
import com.github.andreyasadchy.xtra.model.helix.video.Video
import com.github.andreyasadchy.xtra.model.helix.video.VideosResponse
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class BookmarkMetadataUpdateTest {
    private val dispatcher = StandardTestDispatcher()
    private val bookmarks: BookmarksRepository = mock()
    private val gql: GraphQLRepository = mock()
    private val helix: HelixRepository = mock()
    private val headers = mapOf(C.HEADER_TOKEN to "test-token")
    private fun viewModel() = BookmarksViewModel(
        gql, helix, bookmarks, mock(), mock(), mock(), null, null, mock(), mock(),
    )

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `single refresh updates the existing row without mutating adapter snapshot`() = runTest {
        val original = bookmark()
        whenever(bookmarks.getBookmarkByVideoId("123")).thenReturn(original)
        whenever(gql.loadQueryVideo(any(), any(), any())).thenThrow(IllegalStateException("fallback"))
        whenever(helix.getVideos(networkLibrary = "OkHttp", headers = headers, ids = listOf("123")))
            .thenReturn(response())
        viewModel().updateVideo("unused", "123", "OkHttp", emptyMap(), headers, false)
        advanceUntilIdle()
        assertUpdated(original)
    }

    @Test fun `batch refresh preserves each persisted bookmark identity`() = runTest {
        val original = bookmark()
        whenever(bookmarks.loadBookmarks()).thenReturn(listOf(original))
        whenever(helix.getVideos(networkLibrary = "OkHttp", headers = headers, ids = listOf("123")))
            .thenReturn(response())
        viewModel().updateVideos("unused", "OkHttp", headers)
        advanceUntilIdle()
        assertUpdated(original)
    }

    private fun bookmark() = Bookmark(
        videoId = "123", userId = "channel", title = "Old title", duration = "60", gameId = "game",
    ).apply { id = 42 }

    // Transports are mocked; no thumbnail request reaches the network.
    private fun response() = VideosResponse(listOf(Video(id = "123", title = "Updated title", duration = "2m")))

    private suspend fun assertUpdated(original: Bookmark) {
        val captured = argumentCaptor<Bookmark>()
        verify(bookmarks).updateBookmark(captured.capture())
        assertEquals(42, captured.firstValue.id)
        assertEquals("123", captured.firstValue.videoId)
        assertEquals("Updated title", captured.firstValue.title)
        assertEquals("120", captured.firstValue.duration)
        assertEquals("game", captured.firstValue.gameId)
        assertNotSame(original, captured.firstValue)
        assertEquals("Old title", original.title)
    }
}
