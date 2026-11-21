package com.github.andreyasadchy.xtra.ui.settings

import android.app.Application
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.SettingsNavGraphDirections
import com.github.andreyasadchy.xtra.model.ui.SettingsSearchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [34])
class SettingsSearchTest {
    private val context = RuntimeEnvironment.getApplication().apply { setTheme(R.style.BaseDarkTheme) }
    private val items by lazy { settingsSearchItems(context, settingsSearchScreens(context)) }

    private fun search(query: String) = items.filter { it.matches(query) }

    @Test fun `spoiler finds every spoiler setting, including rows named only by their section`() {
        val results = search("spoiler")
        val keys = results.map { it.key }
        for (key in listOf("spoiler_shield_manage", "spoiler_shield_hide_titles", "spoiler_shield_hide_thumbnails", "player_spoiler_free_vods")) {
            assertTrue("$key not found in $keys", key in keys)
        }
        val hideTitles = results.single { it.key == "spoiler_shield_hide_titles" }
        assertEquals(context.getString(R.string.spoiler_settings), hideTitles.location)
        assertEquals(context.getString(R.string.spoiler_shield), hideTitles.section.toString())
        assertEquals(R.id.action_global_spoilerSettingsFragment, hideTitles.navDirections.actionId)
    }

    @Test fun `live finds the rewind buffer in Player's Live section`() {
        val results = search("live")
        for (key in listOf("player_live_dvr", "player_live_dvr_size")) {
            val item = results.singleOrNull { it.key == key }
            assertTrue("$key not found", item != null)
            assertEquals(context.getString(R.string.player_settings), item!!.location)
            assertEquals(context.getString(R.string.live), item.section.toString())
        }
    }

    @Test fun `the screen name alone does not match, so a screen search stays short`() {
        val item = SettingsSearchItem(
            navDirections = SettingsNavGraphDirections.actionGlobalChatSettingsFragment(),
            location = "Chat",
            section = "Messages",
            title = "Show timestamps",
            summary = "Next to each message",
        )
        assertFalse(item.matches("chat"))
        assertTrue(item.matches("messages"))
        assertTrue(item.matches(" TIMESTAMPS "))
        assertTrue(item.matches("each message"))
        assertFalse(item.matches(""))
        assertFalse(item.matches("   "))
    }

    @Test fun `results keep the value shown for switches`() {
        val hideTitles = items.single { it.key == "spoiler_shield_hide_titles" }
        assertTrue(hideTitles.value in setOf(context.getString(R.string.enabled_setting), context.getString(R.string.disabled_setting)))
    }
}
