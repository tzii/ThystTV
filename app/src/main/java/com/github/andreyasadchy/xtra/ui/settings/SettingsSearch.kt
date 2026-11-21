package com.github.andreyasadchy.xtra.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.XmlRes
import androidx.navigation.NavDirections
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.SettingsNavGraphDirections
import com.github.andreyasadchy.xtra.model.ui.SettingsSearchItem

/** A settings screen that search looks through, and the title its results show. */
internal class SettingsSearchScreen(
    @XmlRes val xml: Int,
    val directions: NavDirections,
    val title: String?,
)

internal fun settingsSearchScreens(context: Context): List<SettingsSearchScreen> = listOf(
    SettingsSearchScreen(R.xml.api_token_preferences, SettingsNavGraphDirections.actionGlobalApiTokenSettingsFragment(), context.getString(R.string.api_token_settings)),
    SettingsSearchScreen(R.xml.buffer_preferences, SettingsNavGraphDirections.actionGlobalBufferSettingsFragment(), context.getString(R.string.buffer_settings)),
    SettingsSearchScreen(R.xml.chat_preferences, SettingsNavGraphDirections.actionGlobalChatSettingsFragment(), context.getString(R.string.chat_settings)),
    SettingsSearchScreen(R.xml.debug_preferences, SettingsNavGraphDirections.actionGlobalDebugSettingsFragment(), context.getString(R.string.debug_settings)),
    SettingsSearchScreen(R.xml.download_preferences, SettingsNavGraphDirections.actionGlobalDownloadSettingsFragment(), context.getString(R.string.download_settings)),
    SettingsSearchScreen(R.xml.playback_preferences, SettingsNavGraphDirections.actionGlobalPlaybackSettingsFragment(), context.getString(R.string.playback_settings)),
    SettingsSearchScreen(R.xml.player_button_preferences, SettingsNavGraphDirections.actionGlobalPlayerButtonSettingsFragment(), context.getString(R.string.player_buttons)),
    SettingsSearchScreen(R.xml.player_menu_preferences, SettingsNavGraphDirections.actionGlobalPlayerMenuSettingsFragment(), context.getString(R.string.player_menu_settings)),
    SettingsSearchScreen(R.xml.player_preferences, SettingsNavGraphDirections.actionGlobalPlayerSettingsFragment(), context.getString(R.string.player_settings)),
    SettingsSearchScreen(R.xml.proxy_preferences, SettingsNavGraphDirections.actionGlobalProxySettingsFragment(), context.getString(R.string.proxy_settings)),
    SettingsSearchScreen(R.xml.root_preferences, SettingsNavGraphDirections.actionGlobalSettingsFragment(), null),
    SettingsSearchScreen(R.xml.spoiler_preferences, SettingsNavGraphDirections.actionGlobalSpoilerSettingsFragment(), context.getString(R.string.spoiler_settings)),
    SettingsSearchScreen(R.xml.theme_preferences, SettingsNavGraphDirections.actionGlobalThemeSettingsFragment(), context.getString(R.string.theme)),
    SettingsSearchScreen(R.xml.ui_preferences, SettingsNavGraphDirections.actionGlobalUiSettingsFragment(), context.getString(R.string.ui_settings)),
    SettingsSearchScreen(R.xml.update_preferences, SettingsNavGraphDirections.actionGlobalUpdateSettingsFragment(), context.getString(R.string.update_settings)),
)

/** One result per setting on [screens], each knowing its screen and the section it sits in. */
// The existing search inflater used this AndroidX internal API under the same suppression
// in SettingsSearchFragment. Keep it scoped to the extracted inflater.
@SuppressLint("RestrictedApi")
internal fun settingsSearchItems(context: Context, screens: List<SettingsSearchScreen>): List<SettingsSearchItem> {
    val preferenceManager = PreferenceManager(context)
    return screens.flatMap { screen ->
        preferenceManager.inflateFromResource(context, screen.xml, null).leafPreferences().mapNotNull {
            if (it is PreferenceCategory) return@mapNotNull null
            SettingsSearchItem(
                navDirections = screen.directions,
                location = screen.title,
                section = (it.parent as? PreferenceCategory)?.title,
                key = it.key,
                title = it.title,
                summary = it.summary,
                value = when (it) {
                    is SwitchPreferenceCompat -> context.getString(if (it.isChecked) R.string.enabled_setting else R.string.disabled_setting)
                    is SeekBarPreference -> it.value.toString()
                    else -> null
                },
            )
        }
    }
}

/**
 * Matches the setting's own name and description, and the section around it, so "spoiler"
 * finds Spoiler Shield's "Hide titles". The screen name is left out: "chat" would otherwise
 * return every chat setting.
 */
internal fun SettingsSearchItem.matches(query: String): Boolean =
    query.isNotBlank() && listOf(title, summary, section).any { it?.contains(query.trim(), ignoreCase = true) == true }
