package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.robolectric.RuntimeEnvironment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class LiveRewindLifecycleTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    }
    class TestPlayer : PlayerFragment() {
        var backendReady = false
        var failSynchronously = false
        var requestedPosition: Long? = null
        override fun onNetworkRestored() {}
        override fun isLiveRewindPlaybackReady() = backendReady
        override fun startVideo(url: String?, playbackPosition: Long?, multivariantPlaylist: Boolean) {
            requestedPosition = playbackPosition
            if (failSynchronously) throw IOException("fixture source failure")
        }
        fun begin() = startPlayer()
        fun ready() = markLiveRewindReady()
        fun fail() = handleLiveRewindFailure()
    }
    private class Fixture {
        val vm = PlayerViewModel(mock(), mock(), mock(), mock(), mock(), mock(), null, null, mock(), mock(), mock(), mock(), mock())
        val activity: MainActivity = mock()
        val owner = Owner()
        val fragment = spy(TestPlayer())
        val arguments = Bundle().apply {
            putString("type", "video")
            putString("videoId", "123")
            putBoolean("ignoreSavedPosition", true)
            putLong("offset", 570_000L)
            putParcelable(LiveRewind.ORIGIN, Stream(id = "live", channelLogin = "channel"))
            putString(LiveRewind.PLAYLIST, "https://example.test/master.m3u8")
            putLong(LiveRewind.POSITION, 570_000L)
        }
        init {
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
            doReturn(context).whenever(fragment).context
            doReturn(activity).whenever(fragment).activity
            doReturn(true).whenever(fragment).isResumed
            doReturn(owner).whenever(fragment).viewLifecycleOwner
            doReturn(arguments).whenever(fragment).arguments
            doReturn(true).whenever(activity).returnFromLiveRewind(any(), any())
            ReflectionHelpers.setField(fragment, "viewModel\$delegate", lazyOf(vm))
            ReflectionHelpers.setField(fragment, "videoType", "video")
            ReflectionHelpers.setField(fragment, "_binding", FragmentPlayerBinding.inflate(LayoutInflater.from(context)))
        }
    }

    @Test fun `prepared startup consumes explicit offset before later normal restart`() = runTest {
        val f = Fixture()
        f.fragment.begin()
        assertEquals(570_000L, f.fragment.requestedPosition)
        assertFalse(f.arguments.containsKey(LiveRewind.PLAYLIST))
        assertFalse(f.arguments.getBoolean("ignoreSavedPosition"))
        assertEquals(-1L, f.arguments.getLong("offset"))
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `ready source discovered on resume prevents false startup rollback`() = runTest {
        val f = Fixture()
        f.fragment.begin()
        f.fragment.backendReady = true
        f.fragment.onResume()
        advanceTimeBy(31_000L)
        runCurrent()
        assertFalse(f.vm.liveRewindPending)
        verify(f.activity, never()).returnFromLiveRewind(any(), any())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `wrong or buffering backend cannot satisfy recording readiness`() = runTest {
        val f = Fixture()
        f.fragment.begin()
        f.fragment.ready()
        assertTrue(f.vm.liveRewindPending)
        advanceTimeBy(31_000L)
        runCurrent()
        assertTrue(f.vm.liveRewindFailed)
        verify(f.activity).returnFromLiveRewind(any(), any())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `minimized timeout waits for restore rather than expanding over browsing`() = runTest {
        val f = Fixture()
        f.fragment.begin()
        ReflectionHelpers.setField(f.fragment, "isMaximized", false)
        advanceTimeBy(31_000L)
        runCurrent()
        assertTrue(f.vm.liveRewindFailed)
        verify(f.activity, never()).returnFromLiveRewind(any(), any())
        f.fragment.maximize()
        verify(f.activity).returnFromLiveRewind(any(), any())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `background error stays pending until resumed owner can recover`() = runTest {
        val f = Fixture()
        f.fragment.begin()
        doReturn(false).whenever(f.fragment).isResumed
        assertTrue(f.fragment.fail())
        verify(f.activity, never()).returnFromLiveRewind(any(), any())
        doReturn(true).whenever(f.fragment).isResumed
        f.fragment.onResume()
        verify(f.activity).returnFromLiveRewind(any(), any())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `synchronous source exception recovers without ordinary retry loop`() = runTest {
        val f = Fixture()
        f.fragment.failSynchronously = true
        f.fragment.begin()
        assertTrue(f.vm.liveRewindFailed)
        verify(f.activity).returnFromLiveRewind(any(), any())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `errors after the recording plays use ordinary VoD recovery`() = runTest {
        val f = Fixture()
        f.fragment.begin()
        f.fragment.backendReady = true
        f.fragment.ready()
        assertFalse(f.fragment.fail())
        assertFalse(f.vm.liveRewindFailed)
        advanceTimeBy(31_000L)
        runCurrent()
        verify(f.activity, never()).returnFromLiveRewind(any(), any())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `recreated recording without a pending handoff is an ordinary VoD`() = runTest {
        val f = Fixture()
        // After process death the arguments keep ORIGIN, but the consumed PLAYLIST and VM state are gone.
        f.arguments.remove(LiveRewind.PLAYLIST)
        f.vm.started = true
        assertFalse(f.vm.liveRewindPending)
        assertFalse(f.fragment.fail())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    private val prepared = PreparedLiveRewind(Stream(id = "live"), Video(id = "123"), "https://example.test/master.m3u8", 0L)

    @Test fun `rewind found while minimized is dropped and announced once on return`() = runTest {
        val f = Fixture()
        ReflectionHelpers.setField(f.fragment, "isMaximized", false)
        f.fragment.finishLiveRewindDiscovery(prepared)
        verify(f.activity, never()).startLiveRewind(any(), any())
        assertTrue(f.vm.liveRewindDropped)
        f.fragment.onResume()
        assertNull(ShadowToast.getLatestToast())
        ReflectionHelpers.setField(f.fragment, "isMaximized", true)
        f.fragment.onResume()
        assertEquals(RuntimeEnvironment.getApplication().getString(R.string.live_rewind_canceled_minimized), ShadowToast.getTextOfLatestToast())
        assertFalse(f.vm.liveRewindDropped)
        ShadowToast.reset()
        f.fragment.onResume()
        assertNull(ShadowToast.getLatestToast())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `a local replay prepared as the viewer minimizes goes back to evicting`() = runTest {
        val f = Fixture()
        val dvr = mock<LiveDvr>()
        f.fragment.liveDvr = dvr
        ReflectionHelpers.setField(f.fragment, "isMaximized", false)
        f.fragment.finishLiveRewindDiscovery(prepared.copy(local = true))
        verify(dvr).cancelReplay()
        verify(f.activity, never()).startLiveRewind(any(), any())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `a replay shorter than the request says how much is saved, a full one says nothing`() = runTest {
        val f = Fixture()
        val dvr = mock<LiveDvr>()
        f.fragment.liveDvr = dvr
        whenever(dvr.prepareReplay("channel", 180_000L)).thenReturn(LocalReplay("file:///replay.m3u8", 0L, 40_000L))
        assertNotNull(f.fragment.prepareLocalReplay("channel", 180_000L))
        val application = RuntimeEnvironment.getApplication()
        assertEquals(application.getString(R.string.live_dvr_partial, "00:40"), ShadowToast.getTextOfLatestToast())
        ShadowToast.reset()
        whenever(dvr.prepareReplay("channel", 30_000L)).thenReturn(LocalReplay("file:///replay.m3u8", 10_000L, 40_000L))
        assertNotNull(f.fragment.prepareLocalReplay("channel", 30_000L))
        assertNull(ShadowToast.getLatestToast())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `rewind result while backgrounded waits for resume`() = runTest {
        val f = Fixture()
        doReturn(false).whenever(f.fragment).isResumed
        f.fragment.finishLiveRewindDiscovery(null)
        assertTrue(f.vm.liveRewindDropped)
        assertNull(ShadowToast.getLatestToast())
        doReturn(true).whenever(f.fragment).isResumed
        f.fragment.onResume()
        assertEquals(RuntimeEnvironment.getApplication().getString(R.string.live_rewind_canceled_minimized), ShadowToast.getTextOfLatestToast())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `rewind result while maximized starts or reports unavailable`() = runTest {
        val f = Fixture()
        f.fragment.finishLiveRewindDiscovery(prepared)
        verify(f.activity).startLiveRewind(f.fragment, prepared)
        f.fragment.finishLiveRewindDiscovery(null)
        val application = RuntimeEnvironment.getApplication()
        assertEquals("with the buffer off, the message points to it", application.getString(R.string.live_dvr_turn_on_hint), ShadowToast.getTextOfLatestToast())
        application.prefs().edit().putBoolean(C.PLAYER_LIVE_DVR, true).commit()
        f.fragment.finishLiveRewindDiscovery(null)
        assertEquals(application.getString(R.string.live_rewind_unavailable), ShadowToast.getTextOfLatestToast())
        assertFalse(f.vm.liveRewindDropped)
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun `ordinary video error is not consumed by rewind recovery`() = runTest {
        val f = Fixture()
        f.arguments.remove(LiveRewind.ORIGIN)
        assertFalse(f.fragment.fail())
        verify(f.activity, never()).returnFromLiveRewind(any(), any())
        f.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }
}
