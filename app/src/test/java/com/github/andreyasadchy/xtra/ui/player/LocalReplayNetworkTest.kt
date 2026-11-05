package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.google.common.util.concurrent.Futures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.UseConstructor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class LocalReplayNetworkTest {
    private fun fragment(controller: MediaController, localReplay: Boolean): Media3Fragment {
        val fragment = mock<Media3Fragment>(
            defaultAnswer = Mockito.CALLS_REAL_METHODS,
            useConstructor = UseConstructor.parameterless(),
        )
        doReturn(true).whenever(fragment).isResumed
        doReturn(Bundle().apply { putBoolean(LiveRewind.LOCAL, localReplay) }).whenever(fragment).arguments
        ReflectionHelpers.setField(fragment, "videoType", PlayerFragment.VIDEO)
        ReflectionHelpers.setField(fragment, "controllerFuture", Futures.immediateFuture(controller))
        return fragment
    }

    @Test fun `losing the network leaves a local replay playing`() {
        val controller = mock<MediaController>()
        fragment(controller, localReplay = true).onNetworkLost()
        verify(controller, never()).stop()
    }

    @Test fun `a local replay can start with no connection while a VoD waits for one`() {
        val replay = fragment(mock(), localReplay = true)
        val vod = fragment(mock(), localReplay = false)
        assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(replay, "getInitializeWhileOffline"))
        assertFalse(ReflectionHelpers.callInstanceMethod<Boolean>(vod, "getInitializeWhileOffline"))
    }

    private fun viewModel() =
        PlayerViewModel(mock(), mock(), mock(), mock(), mock(), mock(), null, null, mock(), mock(), mock(), mock(), mock())

    private fun returning(
        fragment: Media3Fragment,
        vm: PlayerViewModel,
        started: Boolean,
        state: Int,
        error: PlaybackException? = null,
    ): Media3Fragment {
        vm.started = started
        doReturn(false).whenever(fragment).isResumed
        doReturn(Bundle().apply {
            putBoolean(LiveRewind.LOCAL, true)
            putParcelable(LiveRewind.ORIGIN, Stream(id = "live", channelLogin = "channel"))
        }).whenever(fragment).arguments
        ReflectionHelpers.setField(fragment, "viewModel\$delegate", lazyOf(vm))
        val controller = mock<MediaController>()
        doReturn(state).whenever(controller).playbackState
        doReturn(MediaItem.EMPTY).whenever(controller).currentMediaItem
        doReturn(error).whenever(controller).playerError
        fragment.finishEndedReplay(controller)
        return fragment
    }

    @Test fun `a replay that ended while its screen was away goes back to live on return`() {
        val vm = viewModel()
        returning(fragment(mock(), localReplay = true), vm, started = true, state = Player.STATE_ENDED)
        assertTrue(vm.liveRewindFailed)
    }

    @Test fun `a replay that failed while its screen was away goes back to live on return`() {
        val vm = viewModel()
        returning(fragment(mock(), localReplay = true), vm, started = true, state = Player.STATE_IDLE, error = readFailure())
        assertTrue(vm.liveRewindFailed)
    }

    @Test fun `a new replay isn't judged by the error of the source it is about to replace`() {
        val vm = viewModel()
        returning(fragment(mock(), localReplay = true), vm, started = false, state = Player.STATE_IDLE, error = readFailure())
        assertFalse(vm.liveRewindFailed)
    }

    private fun readFailure() = PlaybackException("gone", null, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)

    @Test fun `a replay still playing on return carries on`() {
        val vm = viewModel()
        returning(fragment(mock(), localReplay = true), vm, started = true, state = Player.STATE_READY)
        assertFalse(vm.liveRewindFailed)
    }

    @Test fun `a new replay isn't judged by the source it is about to replace`() {
        val vm = viewModel()
        returning(fragment(mock(), localReplay = true), vm, started = false, state = Player.STATE_ENDED)
        assertFalse(vm.liveRewindFailed)
    }

    @Test fun `losing the network still stops a VoD`() {
        val controller = mock<MediaController>()
        fragment(controller, localReplay = false).onNetworkLost()
        verify(controller).stop()
    }
}
