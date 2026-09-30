package com.github.andreyasadchy.xtra.ui.player

/** Apply only to automatic resume, never an explicit timestamp or an active session. */
internal fun videoResumePosition(savedPositionMs: Long?, durationMs: Long?): Long {
    val position = savedPositionMs ?: 0L
    return if (durationMs != null && durationMs > 0L && position >= durationMs) 0L else position
}

/** Processing VoDs can grow beyond their advertised duration. Cached bookmark art
 * and absent art cannot establish completion either, so preserve their position. */
internal fun networkVideoResumePosition(savedPositionMs: Long?, durationMs: Long?, thumbnailUrl: String?): Long {
    val hasFinalThumbnail = thumbnailUrl != null &&
            (thumbnailUrl.startsWith("https://") || thumbnailUrl.startsWith("http://")) &&
            !thumbnailUrl.startsWith("https://vod-secure.twitch.tv/_404/404_processing")
    return videoResumePosition(savedPositionMs, durationMs.takeIf { hasFinalThumbnail })
}
