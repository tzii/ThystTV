package com.github.andreyasadchy.xtra.ui.saved.downloads

import androidx.annotation.StringRes
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo

/**
 * What an unfinished download's card shows over its picture: a short label, a percent where
 * one is known, and a progress bar only while work is actually moving.
 */
internal data class DownloadStatus(
    @StringRes val label: Int,
    val percent: Int?,
    val chatPercent: Int?,
    val bar: Bar,
) {
    enum class Bar { NONE, DETERMINATE, INDETERMINATE }

    companion object {
        /** Null once the download is finished, when the card shows nothing extra. */
        fun of(video: OfflineVideo): DownloadStatus? {
            val percent = percent(video.progress, video.maxProgress)
            return when (video.status) {
                OfflineVideo.STATUS_DOWNLOADED -> null
                OfflineVideo.STATUS_DOWNLOADING -> if (video.live) {
                    DownloadStatus(R.string.downloading, null, null, Bar.INDETERMINATE)
                } else {
                    DownloadStatus(
                        label = R.string.downloading_progress,
                        percent = percent,
                        chatPercent = if (video.downloadChat) percent(video.chatProgress, video.maxChatProgress) else null,
                        bar = Bar.DETERMINATE,
                    )
                }
                OfflineVideo.STATUS_MOVING -> DownloadStatus(R.string.download_moving, percent, null, Bar.DETERMINATE)
                OfflineVideo.STATUS_DELETING -> DownloadStatus(R.string.download_deleting, percent, null, Bar.DETERMINATE)
                OfflineVideo.STATUS_CONVERTING -> DownloadStatus(R.string.download_converting, percent, null, Bar.DETERMINATE)
                // WorkManager's BLOCKED means waiting behind other work; ENQUEUED (QUEUED here) waits on the network.
                OfflineVideo.STATUS_BLOCKED -> DownloadStatus(R.string.download_queued, null, null, Bar.NONE)
                OfflineVideo.STATUS_QUEUED -> DownloadStatus(R.string.download_blocked, null, null, Bar.NONE)
                OfflineVideo.STATUS_QUEUED_WIFI -> DownloadStatus(R.string.download_blocked_wifi, null, null, Bar.NONE)
                OfflineVideo.STATUS_WAITING_FOR_STREAM -> DownloadStatus(R.string.download_waiting_for_stream, null, null, Bar.NONE)
                else -> DownloadStatus(R.string.download_pending, null, null, Bar.NONE)
            }
        }

        /** Workers report progress before they know the total, so guard the division. */
        fun percent(progress: Int, max: Int): Int =
            if (max <= 0) 0 else (progress.toLong() * 100 / max).toInt().coerceIn(0, 100)
    }
}
