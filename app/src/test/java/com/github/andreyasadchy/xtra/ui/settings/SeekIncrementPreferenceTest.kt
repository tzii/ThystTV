package com.github.andreyasadchy.xtra.ui.settings

import android.app.Application
import android.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class SeekIncrementPreferenceTest {
    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
    private val manager = PreferenceManager(context).apply { sharedPreferencesName = "seek-increment-test" }
    private val prefs = manager.sharedPreferences!!.also { it.edit().clear().commit() }

    private fun preference(key: String = C.PLAYER_REWIND): SeekIncrementPreference =
        manager.inflateFromResource(context, R.xml.player_button_preferences, null).findPreference(key)!!

    @Test fun `fresh defaults display ten seconds and persist milliseconds`() {
        val preference = preference()
        assertEquals("10", preference.text)
        assertEquals("10000", prefs.getString(C.PLAYER_REWIND, null))
        assertEquals(context.getString(R.string.seconds_full, "10"), preference.summary)
    }

    @Test fun `existing preset values survive inflation and subsequent reopening`() {
        prefs.edit().putString(C.PLAYER_REWIND, "15000").putString(C.PLAYER_FORWARD, "60000").commit()
        assertEquals("15", preference().text)
        assertEquals("60", preference(C.PLAYER_FORWARD).text)
        assertEquals("15", preference().text)
        assertEquals("15000", prefs.getString(C.PLAYER_REWIND, null))
        assertEquals("60000", prefs.getString(C.PLAYER_FORWARD, null))
    }

    @Test fun `custom intervals reach the existing player storage without changing the other direction`() {
        val rewind = preference()
        assertTrue(rewind.callChangeListener("7"))
        rewind.text = "7"
        assertEquals("7000", prefs.getString(C.PLAYER_REWIND, null))
        assertEquals("10000", prefs.getString(C.PLAYER_FORWARD, null))
        assertEquals("7", preference().text)
    }

    @Test fun `invalid input is rejected without replacing the previous interval`() {
        val preference = preference()
        for (value in listOf("", " ", "0", "-1", "+1", "1.5", "3601", "9999999999999999999999", "hello")) {
            assertFalse(value, preference.callChangeListener(value))
            assertEquals("10000", prefs.getString(C.PLAYER_REWIND, null))
        }
    }

    @Test fun `minimum and maximum custom values are stored without overflow`() {
        val preference = preference()
        for ((seconds, ms) in listOf("1" to "1000", "3600" to "3600000")) {
            assertTrue(preference.callChangeListener(seconds))
            preference.text = seconds
            assertEquals(ms, prefs.getString(C.PLAYER_REWIND, null))
            assertEquals(seconds, preference().text)
        }
    }

    @Test fun `malformed stored values fall back to a usable player increment`() {
        for (value in listOf("0", "-1000", "invalid", Long.MAX_VALUE.toString())) {
            prefs.edit().putString(C.PLAYER_REWIND, value).commit()
            assertEquals("10", preference().text)
            assertEquals("10000", prefs.getString(C.PLAYER_REWIND, null))
        }
    }
}
