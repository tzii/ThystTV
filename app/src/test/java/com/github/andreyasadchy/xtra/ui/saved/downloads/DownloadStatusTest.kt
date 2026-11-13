package com.github.andreyasadchy.xtra.ui.saved.downloads

import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadStatusTest {
    private fun video(
        status: Int,
        live: Boolean = false,
        progress: Int = 0,
        maxProgress: Int = 100,
        downloadChat: Boolean = false,
        chatProgress: Int = 0,
        maxChatProgress: Int = 100,
    ) = OfflineVideo(
        status = status,
        live = live,
        progress = progress,
        maxProgress = maxProgress,
        downloadChat = downloadChat,
        chatProgress = chatProgress,
        maxChatProgress = maxChatProgress,
    )

    @Test fun `a finished download shows nothing`() {
        assertNull(DownloadStatus.of(video(OfflineVideo.STATUS_DOWNLOADED)))
    }

    @Test fun `a VoD download shows its percent and a filling bar`() {
        assertEquals(
            DownloadStatus(R.string.downloading_progress, 24, null, DownloadStatus.Bar.DETERMINATE),
            DownloadStatus.of(video(OfflineVideo.STATUS_DOWNLOADING, progress = 24, maxProgress = 100)),
        )
    }

    @Test fun `chat progress is shown only when chat is being saved`() {
        assertEquals(40, DownloadStatus.of(video(OfflineVideo.STATUS_DOWNLOADING, downloadChat = true, chatProgress = 2, maxChatProgress = 5))?.chatPercent)
        assertNull(DownloadStatus.of(video(OfflineVideo.STATUS_DOWNLOADING, chatProgress = 2, maxChatProgress = 5))?.chatPercent)
    }

    @Test fun `a live recording has no end, so its bar runs without a percent`() {
        assertEquals(
            DownloadStatus(R.string.downloading, null, null, DownloadStatus.Bar.INDETERMINATE),
            DownloadStatus.of(video(OfflineVideo.STATUS_DOWNLOADING, live = true, progress = 7)),
        )
    }

    @Test fun `waiting states show a label and no bar`() {
        listOf(
            OfflineVideo.STATUS_PENDING to R.string.download_pending,
            OfflineVideo.STATUS_BLOCKED to R.string.download_queued,
            OfflineVideo.STATUS_QUEUED to R.string.download_blocked,
            OfflineVideo.STATUS_QUEUED_WIFI to R.string.download_blocked_wifi,
            OfflineVideo.STATUS_WAITING_FOR_STREAM to R.string.download_waiting_for_stream,
        ).forEach { (status, label) ->
            assertEquals(DownloadStatus(label, null, null, DownloadStatus.Bar.NONE), DownloadStatus.of(video(status)))
        }
    }

    @Test fun `moving, deleting and converting report their percent`() {
        listOf(
            OfflineVideo.STATUS_MOVING to R.string.download_moving,
            OfflineVideo.STATUS_DELETING to R.string.download_deleting,
            OfflineVideo.STATUS_CONVERTING to R.string.download_converting,
        ).forEach { (status, label) ->
            assertEquals(
                DownloadStatus(label, 50, null, DownloadStatus.Bar.DETERMINATE),
                DownloadStatus.of(video(status, progress = 3, maxProgress = 6)),
            )
        }
    }

    @Test fun `percent survives an unknown total and overshoot`() {
        assertEquals(0, DownloadStatus.percent(10, 0))
        assertEquals(100, DownloadStatus.percent(130, 100))
        assertEquals(0, DownloadStatus.percent(-4, 100))
        assertEquals(50, DownloadStatus.percent(Int.MAX_VALUE / 2, Int.MAX_VALUE - 1))
    }
}
