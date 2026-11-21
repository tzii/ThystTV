package com.github.andreyasadchy.xtra.ui.download

import android.app.Application
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.ui.saved.downloads.DownloadsAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ClipDownloadDurationTest {
    private fun arguments(duration: Int?) = DownloadDialog.newClipInstance(
        id = "clip", channelId = null, channelLogin = null, channelName = null,
        channelImage = null, gameId = null, gameSlug = null, gameName = null,
        title = null, thumbnail = null, createdAt = null, videoId = "vod",
        videoOffsetSeconds = 120, durationSeconds = duration,
    ).requireArguments()

    @Test fun `download arguments retain the clip duration independently of its VOD offset`() {
        val restored = DownloadDialog().apply { arguments = Bundle(arguments(37)) }
        assertEquals(37, restored.requireArguments().getInt("durationSeconds", -1))
        assertEquals(120, restored.requireArguments().getInt("videoOffsetSeconds", -1))
    }

    @Test fun `missing and invalid durations stay unknown`() {
        for (duration in listOf(null, 0, -1)) {
            assertEquals(-1, arguments(duration).getInt("durationSeconds", -1))
        }
    }

    @Test fun `reused download cards retain known lengths and hide unknown lengths or offsets`() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
        val fragment = mock<Fragment> { on { requireContext() } doReturn context }
        val adapter = DownloadsAdapter(fragment, {}, {}, {}, {}, {}, {}, {}, {})
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        val row = holder.itemView
        val item = OfflineVideo(clipId = "clip", duration = 22_000L, sourceStartPosition = 0L,
            status = OfflineVideo.STATUS_DOWNLOADED)
        holder.bind(item)
        assertEquals("00:22", row.findViewById<TextView>(R.id.duration).text.toString())
        assertEquals(View.VISIBLE, row.findViewById<View>(R.id.sourceStart).visibility)
        item.sourceStartPosition = -1_000L
        holder.bind(item)
        assertEquals(View.GONE, row.findViewById<View>(R.id.sourceStart).visibility)
        assertEquals(View.GONE, row.findViewById<View>(R.id.sourceEnd).visibility)
        for (unknown in listOf(null, 0L, -1L)) {
            item.duration = unknown
            holder.bind(item)
            assertEquals(View.GONE, row.findViewById<View>(R.id.duration).visibility)
        }
    }
}
