package com.github.andreyasadchy.xtra.ui.player

/** Public playback links only; never share a tokenized playlist or local download URI. */
internal object PlayerShareLink {
    fun create(type: String?, channelLogin: String?, videoId: String?, clipId: String?, positionMs: Long?): String? = when (type) {
        PlayerFragment.STREAM -> channelLogin?.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,100}")) }
            ?.let { "https://www.twitch.tv/$it" }
        PlayerFragment.VIDEO -> videoId?.takeIf { it.matches(Regex("[0-9]{1,32}")) }?.let { id ->
            val timestamp = positionMs?.takeIf { it >= 0 }?.let {
                val seconds = it / 1000L
                "?t=${seconds / 3600}h${seconds % 3600 / 60}m${seconds % 60}s"
            }.orEmpty()
            "https://www.twitch.tv/videos/$id$timestamp"
        }
        PlayerFragment.CLIP -> clipId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,256}")) }
            ?.let { "https://clips.twitch.tv/$it" }
        else -> null
    }
}
