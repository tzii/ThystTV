package com.github.andreyasadchy.xtra.ui.settings

import android.content.SharedPreferences
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream

/**
 * Restores a settings backup (a copy of the app's SharedPreferences XML file).
 *
 * Copying the file over the live one is not enough: the process keeps the preferences in
 * memory, and its next write saves those old values back over the restored file. The values
 * are read here and written through the [SharedPreferences] editor instead.
 */
internal object PreferencesBackup {

    /** Reads the backup, or returns null when it is not a SharedPreferences file. */
    fun read(input: InputStream): Map<String, Any>? = try {
        readMap(input)
    } catch (e: XmlPullParserException) {
        null
    }

    private fun readMap(input: InputStream): Map<String, Any>? {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val values = LinkedHashMap<String, Any>()
        var sawMap = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if (parser.depth == 1) {
                    if (parser.name != "map") return null
                    sawMap = true
                } else if (parser.depth == 2) {
                    readValue(parser)?.let { (name, value) -> values[name] = value }
                }
            }
            event = parser.next()
        }
        return values.takeIf { sawMap }
    }

    /** Replaces every stored value with [values]; returns whether the write succeeded. */
    fun restore(preferences: SharedPreferences, values: Map<String, Any>): Boolean {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
            }
        }
        return editor.commit()
    }

    private fun readValue(parser: XmlPullParser): Pair<String, Any>? {
        val tag = parser.name
        val name = parser.getAttributeValue(null, "name") ?: return null
        val attribute = parser.getAttributeValue(null, "value")
        val value: Any? = when (tag) {
            "string" -> parser.nextText()
            "boolean" -> attribute?.toBooleanStrictOrNull()
            "int" -> attribute?.toIntOrNull()
            "long" -> attribute?.toLongOrNull()
            "float" -> attribute?.toFloatOrNull()
            "set" -> readSet(parser)
            else -> null
        }
        return value?.let { name to it }
    }

    private fun readSet(parser: XmlPullParser): Set<String> {
        val depth = parser.depth
        val set = LinkedHashSet<String>()
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT || (event == XmlPullParser.END_TAG && parser.depth == depth)) break
            if (event == XmlPullParser.START_TAG && parser.name == "string") {
                set.add(parser.nextText())
            }
        }
        return set
    }
}
