package com.github.andreyasadchy.xtra.ui.main

private val embedClipParameter = Regex("[?&]clip=([^&#]+)")

/**
 * The clip slug in a `twitch.tv/<channel>/clip/<slug>` or `clips.twitch.tv/<slug>` link,
 * including a `clips.twitch.tv/embed?clip=<slug>` player link, or null when there is none.
 */
internal fun clipLinkId(url: String): String? {
    val path = when {
        url.contains("/clip/") -> url.substringAfter("/clip/")
        url.contains("clips.twitch.tv/") -> url.substringAfter("clips.twitch.tv/").let { path ->
            if (path.startsWith("embed")) embedClipParameter.find(path)?.groupValues?.get(1) ?: return null else path
        }
        else -> return null
    }
    return path.substringBefore('?').substringBefore('/').substringBefore('#').takeIf { it.isNotBlank() }
}
