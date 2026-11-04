package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.Futures
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

    @Test fun `losing the network still stops a VoD`() {
        val controller = mock<MediaController>()
        fragment(controller, localReplay = false).onNetworkLost()
        verify(controller).stop()
    }
}
