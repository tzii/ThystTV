package com.github.andreyasadchy.xtra.ui.download

/**
 * The stretch of a VoD whose replay chat a download saves, in whole seconds of that VoD.
 *
 * A clip carries its VoD id and its place in that VoD separately. Twitch can report the id
 * while the place is still unknown, as for a clip made moments ago during the broadcast, and
 * older downloads stored an unknown place as a negative offset. Neither has chat to fetch.
 */
internal data class ChatDownloadWindow(val startSeconds: Int, val durationSeconds: Int) {
    val endSeconds: Int get() = startSeconds + durationSeconds

    companion object {
        /** The window for a download, or null when its VoD, start or length is unknown. */
        fun of(videoId: String?, sourceStartPositionMs: Long?, durationMs: Long?): ChatDownloadWindow? {
            if (videoId.isNullOrBlank()) return null
            if (sourceStartPositionMs == null || sourceStartPositionMs < 0L) return null
            if (durationMs == null || durationMs <= 0L) return null
            return ChatDownloadWindow((sourceStartPositionMs / 1000L).toInt(), (durationMs / 1000L).toInt())
        }
    }
}
