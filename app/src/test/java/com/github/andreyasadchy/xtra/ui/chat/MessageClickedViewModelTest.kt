package com.github.andreyasadchy.xtra.ui.chat

import android.app.Application
import com.github.andreyasadchy.xtra.model.helix.follower.Follower
import com.github.andreyasadchy.xtra.model.helix.follower.FollowersResponse
import com.github.andreyasadchy.xtra.model.helix.user.User
import com.github.andreyasadchy.xtra.model.helix.user.UsersResponse
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class MessageClickedViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val graphQL: GraphQLRepository = mock()
    private val helix: HelixRepository = mock()
    private val headers = mapOf(C.HEADER_TOKEN to "fixture-token")

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun fallback(targetId: String? = "channel") {
        whenever(graphQL.loadQueryUserMessageClicked("OkHttp", emptyMap(), "viewer", null, targetId))
            .thenThrow(IllegalStateException("fixture GraphQL failure"))
        whenever(helix.getUsers("OkHttp", headers, listOf("viewer"), null))
            .thenReturn(UsersResponse(listOf(User(id = "viewer", login = "viewer_login", displayName = "Viewer"))))
    }

    private fun load(targetId: String? = "channel", helixHeaders: Map<String, String> = headers) =
        MessageClickedViewModel(graphQL, helix).also {
            it.loadUser("viewer", "viewer_login", targetId, "OkHttp", emptyMap(), helixHeaders, false)
        }

    @Test fun `moderator fallback includes the matching follower date`() = runTest {
        fallback()
        whenever(helix.getUserFollowers("OkHttp", headers, "channel", "viewer"))
            .thenReturn(FollowersResponse(listOf(Follower(id = "viewer", followedAt = "2026-09-01T12:00:00Z"))))
        val vm = load()
        advanceUntilIdle()
        assertEquals("Viewer", vm.user.value!!.first!!.name)
        assertEquals("2026-09-01T12:00:00Z", vm.user.value!!.first!!.followedAt)
        assertFalse(vm.user.value!!.second!!)
    }

    @Test fun `denied follower permission preserves the user profile`() = runTest {
        fallback()
        whenever(helix.getUserFollowers("OkHttp", headers, "channel", "viewer"))
            .thenThrow(IllegalStateException("fixture permission denied"))
        val vm = load()
        advanceUntilIdle()
        assertEquals("Viewer", vm.user.value!!.first!!.name)
        assertNull(vm.user.value!!.first!!.followedAt)
        assertFalse(vm.user.value!!.second!!)
    }

    @Test fun `another user cannot supply the selected follow date`() = runTest {
        fallback()
        whenever(helix.getUserFollowers("OkHttp", headers, "channel", "viewer"))
            .thenReturn(FollowersResponse(listOf(Follower(id = "other", followedAt = "2026-09-01T12:00:00Z"))))
        val vm = load()
        advanceUntilIdle()
        assertNull(vm.user.value!!.first!!.followedAt)
    }

    @Test fun `missing target channel skips follower request`() = runTest {
        fallback(targetId = null)
        val vm = load(targetId = null)
        advanceUntilIdle()
        assertEquals("Viewer", vm.user.value!!.first!!.name)
        verify(helix).getUsers("OkHttp", headers, listOf("viewer"), null)
        verifyNoMoreInteractions(helix)
    }

    @Test fun `anonymous fallback makes no authenticated requests`() = runTest {
        fallback()
        val vm = load(helixHeaders = emptyMap())
        advanceUntilIdle()
        assertNull(vm.user.value!!.first)
        verifyNoInteractions(helix)
    }

    @Test fun `cancelled GraphQL lookup does not start fallback`() = runTest {
        whenever(graphQL.loadQueryUserMessageClicked("OkHttp", emptyMap(), "viewer", null, "channel"))
            .thenThrow(CancellationException("fixture cancellation"))
        val vm = load()
        advanceUntilIdle()
        assertNull(vm.user.value)
        verifyNoInteractions(helix)
    }

    @Test fun `cancelled follower lookup does not publish a stale profile`() = runTest {
        fallback()
        whenever(helix.getUserFollowers("OkHttp", headers, "channel", "viewer"))
            .thenThrow(CancellationException("fixture cancellation"))
        val vm = load()
        advanceUntilIdle()
        assertNull(vm.user.value)
    }
}
