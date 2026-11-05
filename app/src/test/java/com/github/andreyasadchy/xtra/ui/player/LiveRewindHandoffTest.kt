package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.content.SharedPreferences
import androidx.fragment.app.FragmentManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
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

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class LiveRewindHandoffTest {
    private val activity = Mockito.mock(MainActivity::class.java, Mockito.CALLS_REAL_METHODS)
    private val manager: FragmentManager = mock()
    private val source: PlayerFragment = mock()
    private val origin = Stream(id = "broadcast", channelLogin = "channel")

    private fun setup(current: PlayerFragment? = source, pending: PlayerFragment? = source, saved: Boolean = false) {
        doReturn(manager).whenever(activity).supportFragmentManager
        whenever(manager.isStateSaved).thenReturn(saved)
        whenever(manager.findFragmentById(R.id.playerContainer)).thenReturn(current)
        ReflectionHelpers.setField(activity, "playerFragment", pending)
        doReturn(true).whenever(activity).startStream(any())
    }

    @Test fun `owned recording returns through existing single player entrypoint`() {
        setup()
        assertTrue(activity.returnFromLiveRewind(source, origin))
        val stream = argumentCaptor<Stream>()
        verify(activity).startStream(stream.capture())
        assertEquals(origin.channelLogin, stream.firstValue.channelLogin)
        assertNull(stream.firstValue.id)
        assertNull(stream.firstValue.createdAt)
        assertNull(stream.firstValue.viewerCount)
    }

    @Test fun `late failure cannot replace an already displayed newer player`() {
        val newer: PlayerFragment = mock()
        setup(current = newer, pending = newer)
        assertFalse(activity.returnFromLiveRewind(source, origin))
        verify(activity, never()).startStream(any())
    }

    @Test fun `queued replacement protects against old fragment callback before transaction commits`() {
        setup(pending = mock())
        assertFalse(activity.returnFromLiveRewind(source, origin))
        verify(activity, never()).startStream(any())
    }

    @Test fun `a return to live that was refused doesn't claim to be returning`() {
        setup()
        doReturn(false).whenever(activity).startStream(any())
        assertFalse(activity.returnFromLiveRewind(source, origin))
    }

    @Test fun `a replay refused while another session holds media reports failure`() {
        setup()
        ReflectionHelpers.setField(activity, "mBase", RuntimeEnvironment.getApplication())
        val prefs: SharedPreferences = mock()
        whenever(prefs.getString(C.PLAYER, "ExoPlayer")).thenReturn("MediaPlayer")
        ReflectionHelpers.setField(activity, "prefs", prefs)
        val session: Player = mock()
        whenever(session.currentMediaItem).thenReturn(MediaItem.EMPTY)
        PlaybackSessionOwner.register(session)
        try {
            val prepared = PreparedLiveRewind(origin, Video(), "playlist", 0L, local = true)
            assertFalse("so the caller releases the buffer it pinned", activity.startLiveRewind(source, prepared))
            verify(manager, never()).beginTransaction()
            verify(source, never()).close()
        } finally {
            PlaybackSessionOwner.unregister(session)
        }
    }

    @Test fun `saved activity and closed player reject source handoff`() {
        setup(saved = true)
        assertFalse(activity.returnFromLiveRewind(source, origin))
        setup(current = null, pending = null)
        assertFalse(activity.returnFromLiveRewind(source, origin))
        verify(activity, never()).startStream(any())
    }
}
