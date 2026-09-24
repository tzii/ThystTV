package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.github.andreyasadchy.xtra.model.helix.follows.Follow
import com.github.andreyasadchy.xtra.model.helix.follows.FollowsResponse
import com.github.andreyasadchy.xtra.model.ui.LocalFollowChannel
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerViewModel
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class LocalFollowFallbackTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `player with retained account id and no tokens reads local follows`() = checkLocal(true)
    @Test fun `channel with retained account id and no tokens reads local follows`() = checkLocal(false)
    @Test fun `player keeps authenticated Helix fallback`() = checkHelix(true)
    @Test fun `channel keeps authenticated Helix fallback`() = checkHelix(false)

    private fun checkLocal(player: Boolean) = runTest {
        for (isFollowed in listOf(true, false)) {
            val fixture = Fixture(player)
            whenever(fixture.local.getFollowByUserId("channel"))
                .thenReturn(if (isFollowed) LocalFollowChannel(userId = "channel") else null)
            fixture.check(emptyMap(), mapOf(C.HEADER_TOKEN to "  "))
            advanceUntilIdle()
            assertEquals(isFollowed, fixture.status.value)
            verify(fixture.local).getFollowByUserId("channel")
            verifyNoInteractions(fixture.gql, fixture.helix)
        }
    }

    private fun checkHelix(player: Boolean) = runTest {
        val fixture = Fixture(player)
        val headers = mapOf(C.HEADER_TOKEN to "test-token")
        whenever(fixture.helix.getUserFollows(
            networkLibrary = "OkHttp", headers = headers, userId = "retained-account", targetId = "channel",
        )).thenReturn(FollowsResponse(listOf(Follow(id = "channel"))))
        fixture.check(emptyMap(), headers)
        advanceUntilIdle()
        assertEquals(true, fixture.status.value)
        verifyNoInteractions(fixture.local, fixture.gql)
        verify(fixture.helix).getUserFollows(
            networkLibrary = "OkHttp", headers = headers, userId = "retained-account", targetId = "channel",
        )
    }

    private class Fixture(player: Boolean) {
        val local: LocalFollowChannelRepository = mock()
        val gql: GraphQLRepository = mock()
        val helix: HelixRepository = mock()
        val status: StateFlow<Boolean?>
        val check: (Map<String, String>, Map<String, String>) -> Unit

        init {
            if (player) {
                val vm = PlayerViewModel(gql, helix, local, mock(), mock(), mock(), null, null, mock(), mock(), mock(), mock(), mock())
                status = vm.isFollowing
                check = { gqlHeaders, helixHeaders ->
                    vm.isFollowingChannel("retained-account", "channel", "channel", 0, "OkHttp", gqlHeaders, helixHeaders)
                }
            } else {
                val vm = ChannelPagerViewModel(local, mock(), mock(), mock(), mock(), gql, helix, null, null, mock(), mock(), SavedStateHandle())
                status = vm.isFollowing
                check = { gqlHeaders, helixHeaders ->
                    vm.isFollowingChannel("retained-account", "channel", "channel", 0, "OkHttp", gqlHeaders, helixHeaders)
                }
            }
        }
    }
}
