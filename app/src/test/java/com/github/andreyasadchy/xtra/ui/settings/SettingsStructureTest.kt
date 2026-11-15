package com.github.andreyasadchy.xtra.ui.settings

import android.app.Application
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.UiTestRender
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h1600dp-mdpi")
class SettingsStructureTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun inflate(xml: Int): PreferenceScreen =
        PreferenceManager(context).inflateFromResource(context, xml, null)

    private val screens = listOf(
        R.xml.root_preferences, R.xml.theme_preferences, R.xml.ui_preferences, R.xml.chat_preferences,
        R.xml.player_preferences, R.xml.player_button_preferences, R.xml.player_menu_preferences,
        R.xml.buffer_preferences, R.xml.proxy_preferences, R.xml.playback_preferences,
        R.xml.api_token_preferences, R.xml.download_preferences, R.xml.update_preferences, R.xml.debug_preferences,
        R.xml.spoiler_preferences,
    )

    @Test fun `root is a short list of sections with an icon and summary on every row`() {
        val root = inflate(R.xml.root_preferences)
        val rows = root.leafPreferences()
        assertTrue("root should only hold navigation rows and one switch", rows.size <= 20)
        assertEquals(listOf("live_notifications_enabled"), rows.filterIsInstance<SwitchPreferenceCompat>().map { it.key })
        for (row in rows) {
            assertNotNull("${row.key} needs an icon", row.icon)
            if (row.key !in setOf("check_updates", "about_settings", "new_version_available")) {
                assertFalse("${row.key} needs a summary", row.summary.isNullOrBlank())
            }
        }
        for (index in 0 until root.preferenceCount) {
            val child = root.getPreference(index)
            assertTrue("only the update banner sits outside a section", child is PreferenceCategory || child.key == "new_version_available")
        }
    }

    @Test fun `settings moved off the root live on their section screens`() {
        val moved = mapOf(
            R.xml.ui_preferences to listOf("ui_language", "ui_draw_behind_cutouts"),
            R.xml.player_preferences to listOf("player_picture_in_picture", "player_background_audio", "player_background_audio_locked"),
            R.xml.proxy_preferences to listOf("player_stream_proxy", "player_proxy_url"),
            R.xml.spoiler_preferences to listOf("spoiler_shield_manage", "spoiler_shield_hide_titles", "spoiler_shield_hide_thumbnails", "player_spoiler_free_vods"),
        )
        val root = inflate(R.xml.root_preferences)
        for ((xml, keys) in moved) {
            val screen = inflate(xml)
            for (key in keys) {
                assertNotNull("$key missing from its section", screen.findPreference<Preference>(key))
                assertNull("$key still on the root", root.findPreference<Preference>(key))
            }
        }
    }

    @Test fun `spoiler settings share one screen and the rewind buffer sits under Live`() {
        for (key in listOf("spoiler_shield_manage", "spoiler_shield_hide_titles", "player_spoiler_free_vods")) {
            assertNull("$key still on UI", inflate(R.xml.ui_preferences).findPreference<Preference>(key))
            assertNull("$key still on Player", inflate(R.xml.player_preferences).findPreference<Preference>(key))
        }
        val player = inflate(R.xml.player_preferences)
        for (key in listOf("player_live_dvr", "player_live_dvr_size")) {
            val section = player.findPreference<Preference>(key)?.parent as? PreferenceCategory
            assertEquals(key, context.getString(R.string.live), section?.title)
        }
        assertEquals(context.getString(R.string.settings_group_videos),
            (player.findPreference<Preference>("player_use_videopositions")?.parent as? PreferenceCategory)?.title)
    }

    @Test fun `search covers every settings screen`() {
        val searched = settingsSearchScreens(context).map { it.xml }
        for (xml in screens) {
            assertTrue("${context.resources.getResourceEntryName(xml)} is not searched", xml in searched)
        }
    }

    @Test fun `every setting has one home and search reaches settings inside sections`() {
        val seen = mutableMapOf<String, Int>()
        // The root reuses "player_settings" as a row key (also the quality button
        // toggle), so uniqueness is checked across the section screens.
        for (xml in screens - R.xml.root_preferences) {
            val leaves = inflate(xml).leafPreferences()
            assertTrue(leaves.none { it is PreferenceCategory })
            leaves.mapNotNull { it.key }.forEach { key ->
                assertNull("$key appears on two screens", seen.put(key, xml))
            }
        }
        // Nested inside categories, which the old top-level search walk skipped.
        val chat = inflate(R.xml.chat_preferences).leafPreferences().map { it.key }
        assertTrue("floating_chat_enabled" in chat && "chat_translate" in chat)
        assertNotNull(inflate(R.xml.chat_preferences).findPreference<PreferenceCategory>("chat_translation_category"))
    }

    @Test fun `root and player screens render`() {
        for ((name, theme) in listOf("dark" to R.style.BaseDarkTheme, "light" to R.style.BaseLightTheme)) {
            for ((screen, xml) in listOf("root" to R.xml.root_preferences, "player" to R.xml.player_preferences)) {
                val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
                controller.get().setTheme(theme)
                controller.setup()
                val activity = controller.get()
                val container = FrameLayout(activity).apply { id = View.generateViewId() }
                activity.setContentView(container)
                val fragment = PreviewFragment().apply { arguments = Bundle().apply { putInt("xml", xml) } }
                activity.supportFragmentManager.beginTransaction().add(container.id, fragment).commitNow()
                container.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(container, android.R.attr.colorBackground))
                container.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
                container.layout(0, 0, 360, 1600)
                UiTestRender.save(container, "settings-$screen-$name")
                controller.pause().stop().destroy()
            }
        }
    }

    class PreviewFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(requireArguments().getInt("xml"), rootKey)
            findPreference<Preference>("check_updates")?.summary = "Current version 1.4.0"
        }
    }
}
