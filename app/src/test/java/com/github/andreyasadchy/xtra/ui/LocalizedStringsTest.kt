package com.github.andreyasadchy.xtra.ui

import android.app.Application
import android.content.res.Configuration
import com.github.andreyasadchy.xtra.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Exercise packaged localized resources, including multi-byte and Windows-1252 edge cases. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class LocalizedStringsTest {
    @Test fun `localized text remains readable after source edits and resource packaging`() {
        val context = RuntimeEnvironment.getApplication()
        val cases = listOf(
            Triple("ar", R.string.games, "الألعاب"),
            Triple("de", R.string.select_quality, "Qualität auswählen"),
            Triple("es", R.string.connection_error, "Error al conectarse al servidor. Inténtalo de nuevo"),
            Triple("fr", R.string.downloads, "Téléchargements"),
            Triple("gl", R.string.connection_error, "Erro ao conectar co servidor. Téntao de novo."),
            Triple("id", R.string.irc_notice_unavailable_command, "Maaf, “%s” tidak tersedia melalui klien ini."),
            Triple("it", R.string.select_quality, "Seleziona qualità"),
            Triple("ja", R.string.games, "ゲーム"),
            Triple("pt-BR", R.string.this_month, "Este mês"),
            Triple("ru", R.string.games, "Игры"),
            Triple("tr", R.string.popular, "Popüler"),
            Triple("zh-CN", R.string.games, "游戏"),
            Triple("zh-TW", R.string.games, "遊戲"),
        )
        for ((locale, resource, expected) in cases) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(locale))
            }
            val localized = context.createConfigurationContext(configuration)
            assertEquals(locale, expected, localized.getString(resource))
        }
    }

    @Test fun `upstream translations preserve formatting quotes and plural arguments when packaged`() {
        val context = RuntimeEnvironment.getApplication()
        fun localized(locale: String) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(locale))
            },
        )

        assertEquals("Konto", localized("de").getString(R.string.account))
        assertEquals("Pestañas en \"Guardado\"", localized("es").getString(R.string.saved_tabs))

        val japanese = localized("ja")
        assertEquals("移動中： 42%", japanese.getString(R.string.download_moving, 42))
        assertEquals("Chat：切断済み - Offline", japanese.getString(R.string.websocket_disconnected, "Chat", "Offline"))
        for (quantity in listOf(1, 2)) {
            assertEquals("言語: $quantity", japanese.resources.getQuantityString(R.plurals.languages, quantity, quantity.toString()))
            assertEquals("タグ: $quantity", japanese.resources.getQuantityString(R.plurals.tags, quantity, quantity.toString()))
            assertEquals("$quantity メンバー", japanese.resources.getQuantityString(R.plurals.members, quantity, quantity.toString()))
        }

        val russian = localized("ru")
        assertEquals("Старое имя: Viewer", russian.getString(R.string.old_username, "Viewer"))
        assertTrue(russian.getString(R.string.external_tv_login_message).endsWith("\n• Вернитесь в ThystTV и нажмите \"Далее\"."))
    }
}
