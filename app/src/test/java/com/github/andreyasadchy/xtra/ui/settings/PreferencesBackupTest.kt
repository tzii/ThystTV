package com.github.andreyasadchy.xtra.ui.settings

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class PreferencesBackupTest {
    private val backup = """
        <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
        <map>
            <string name="ui_language">de</string>
            <string name="empty"></string>
            <string name="escaped">a &amp; b &lt;c&gt;</string>
            <boolean name="live_notifications_enabled" value="true" />
            <int name="chat_limit" value="300" />
            <long name="last_check" value="1763800000000" />
            <float name="speed" value="1.25" />
            <set name="tabs">
                <string>following</string>
                <string>games</string>
            </set>
            <null name="nothing" />
        </map>
    """.trimIndent()

    private fun read(xml: String) = PreferencesBackup.read(xml.byteInputStream())

    @Test
    fun `reads every SharedPreferences value type`() {
        val values = read(backup)!!

        assertEquals("de", values["ui_language"])
        assertEquals("", values["empty"])
        assertEquals("a & b <c>", values["escaped"])
        assertEquals(true, values["live_notifications_enabled"])
        assertEquals(300, values["chat_limit"])
        assertEquals(1_763_800_000_000L, values["last_check"])
        assertEquals(1.25f, values["speed"])
        assertEquals(setOf("following", "games"), values["tabs"])
        assertFalse(values.containsKey("nothing"))
    }

    @Test
    fun `restored values survive the next settings change`() {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("restore_test", Context.MODE_PRIVATE)
        preferences.edit().putString("ui_language", "en").putBoolean("stale", true).commit()

        assertTrue(PreferencesBackup.restore(preferences, read(backup)!!))
        preferences.edit().putBoolean("changed_after_restore", true).commit()

        assertEquals("de", preferences.getString("ui_language", null))
        assertEquals(300, preferences.getInt("chat_limit", 0))
        assertEquals(setOf("following", "games"), preferences.getStringSet("tabs", null))
        assertFalse(preferences.contains("stale"))
        assertTrue(preferences.getBoolean("changed_after_restore", false))
    }

    @Test
    fun `other files are not treated as a settings backup`() {
        assertNull(read("""<resources><string name="a">b</string></resources>"""))
        assertNull(read("not xml at all"))
        assertNull(read(""))
    }
}
