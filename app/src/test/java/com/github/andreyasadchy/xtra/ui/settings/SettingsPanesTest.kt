package com.github.andreyasadchy.xtra.ui.settings

import android.app.Application
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import org.robolectric.RuntimeEnvironment
import com.github.andreyasadchy.xtra.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class SettingsPanesTest {
    private val sections = listOf(
        R.id.themeSettingsFragment,
        R.id.uiSettingsFragment,
        R.id.playerSettingsFragment,
        R.id.playerButtonSettingsFragment,
        R.id.playerMenuSettingsFragment,
        R.id.chatSettingsFragment,
        R.id.downloadSettingsFragment,
        R.id.apiTokenSettingsFragment,
        R.id.playbackSettingsFragment,
        R.id.bufferSettingsFragment,
        R.id.proxySettingsFragment,
        R.id.updateSettingsFragment,
        R.id.changelogSettingsFragment,
        R.id.debugSettingsFragment,
    )

    @Test fun `every section marks a row that exists in the sections list`() {
        val context = RuntimeEnvironment.getApplication().apply { setTheme(R.style.BaseDarkTheme) }
        val root = PreferenceManager(context).inflateFromResource(context, R.xml.root_preferences, null)
        sections.forEach { destination ->
            val key = SettingsPanes.sectionKey(destination)
            assertNotNull("destination $destination has no row", key)
            assertNotNull("row $key is missing from root_preferences", root.findPreference<Preference>(key!!))
        }
    }

    @Test fun `the opening section is the first row`() {
        assertEquals("theme_settings", SettingsPanes.sectionKey(SettingsPanes.DEFAULT_SECTION))
    }

    @Test fun `screens without their own row leave the mark alone`() {
        assertNull(SettingsPanes.sectionKey(R.id.settingsFragment))
        assertNull(SettingsPanes.sectionKey(R.id.settingsSearchFragment))
    }
}
