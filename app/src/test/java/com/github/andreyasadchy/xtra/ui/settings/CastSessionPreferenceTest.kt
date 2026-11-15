package com.github.andreyasadchy.xtra.ui.settings

import android.app.Application
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.github.andreyasadchy.xtra.R
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class CastSessionPreferenceTest {
    private val preference = Preference(RuntimeEnvironment.getApplication())

    @Test fun `runtime action is absent from searchable XML and reused on view recreation`() {
        val context = RuntimeEnvironment.getApplication().apply { setTheme(R.style.BaseDarkTheme) }
        val screen = PreferenceManager(context).inflateFromResource(context, R.xml.root_preferences, null)
        assertNull(screen.findPreference<Preference>("stop_casting"))
        CastSessionPreference.attach(screen)
        val row = screen.findPreference<Preference>("stop_casting")!!
        assertFalse(row.isVisible)
        assertEquals(context.getString(R.string.cast_stop), row.title)
        assertEquals(-1, row.order)
        CastSessionPreference.attach(screen)
        assertSame(row, screen.findPreference<Preference>("stop_casting"))
    }

    @Test fun `uninitialized or unavailable Cast leaves the action hidden`() {
        CastSessionPreference(preference) { null }.apply { start(); stop() }
        assertFalse(preference.isVisible)
        CastSessionPreference(preference) { throw IllegalStateException("Unavailable") }.start()
        assertFalse(preference.isVisible)
    }

    @Test fun `stop uses the current session and ends receiver playback`() {
        val manager = mock<SessionManager>()
        whenever(manager.currentCastSession).thenReturn(mock<CastSession>())
        val controls = CastSessionPreference(preference) { manager }
        controls.start()
        assertTrue(preference.isVisible)
        preference.onPreferenceClickListener!!.onPreferenceClick(preference)
        verify(manager).endCurrentSession(true)
        whenever(manager.currentCastSession).thenReturn(null)
        preference.onPreferenceClickListener!!.onPreferenceClick(preference)
        verify(manager, times(1)).endCurrentSession(true)
        assertFalse(preference.isVisible)
        controls.stop()
    }

    @Test fun `session events refresh the action and stop removes its exact listener`() {
        val manager = mock<SessionManager>()
        val controls = CastSessionPreference(preference) { manager }
        controls.start()
        val listener = argumentCaptor<SessionManagerListener<CastSession>>()
        verify(manager).addSessionManagerListener(listener.capture(), eq(CastSession::class.java))
        assertFalse(preference.isVisible)
        val session = mock<CastSession>()
        whenever(manager.currentCastSession).thenReturn(session)
        listener.firstValue.onSessionStarted(session, "id")
        assertTrue(preference.isVisible)
        listener.firstValue.onSessionEnded(session, 0)
        assertFalse(preference.isVisible)
        controls.stop()
        verify(manager).removeSessionManagerListener(listener.firstValue, CastSession::class.java)
    }
}
