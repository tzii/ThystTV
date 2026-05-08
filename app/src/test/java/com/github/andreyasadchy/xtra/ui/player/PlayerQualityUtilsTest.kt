package com.github.andreyasadchy.xtra.ui.player

import androidx.media3.common.Format
import com.github.andreyasadchy.xtra.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerQualityUtilsTest {

    @Test
    fun `audio only hls variant is not shown as numeric fallback`() {
        val format = Format.Builder()
            .setCodecs("mp4a.40.2")
            .build()

        val name = format.toReadableQualityName(
            videoName = null,
            url = "https://example.com/playlist/0/index.m3u8",
            fallback = "0"
        )

        assertEquals("audio_only", name)
    }

    @Test
    fun `video hls variant uses height and high frame rate`() {
        val format = Format.Builder()
            .setCodecs("avc1.64002a")
            .setHeight(720)
            .setFrameRate(60f)
            .build()

        val name = format.toReadableQualityName(
            videoName = null,
            url = null,
            fallback = "0"
        )

        assertEquals("720p60", name)
    }

    @Test
    fun `numeric quality names are treated as fallback labels`() {
        assertEquals(true, "0".isNumericQualityFallback())
        assertEquals(true, "12".isNumericQualityFallback())
        assertEquals(false, "720p60".isNumericQualityFallback())
        assertEquals(false, "audio_only".isNumericQualityFallback())
    }

    @Test
    fun `collapsed quality refresh does not replace existing video options`() {
        val current = listOf(
            VideoQuality("auto"),
            VideoQuality("1080p60", "avc1.64002a", "https://example.com/1080.m3u8"),
            VideoQuality("720p60", "avc1.64002a", "https://example.com/720.m3u8"),
            VideoQuality("audio_only"),
            VideoQuality("chat_only")
        )
        val collapsed = listOf(VideoQuality("auto"), VideoQuality("audio_only"), VideoQuality("chat_only"))

        assertEquals(false, collapsed.shouldReplaceCurrentQualities(current))
    }

    @Test
    fun `full quality refresh can replace existing video options`() {
        val current = listOf(
            VideoQuality("auto"),
            VideoQuality("720p60", "avc1.64002a", "https://example.com/720.m3u8"),
            VideoQuality("audio_only")
        )
        val full = listOf(
            VideoQuality("1080p60", "avc1.64002a", "https://example.com/1080.m3u8"),
            VideoQuality("720p60", "avc1.64002a", "https://example.com/720.m3u8")
        ).toSelectableQualities(includeChatOnly = false)

        assertEquals(true, full.shouldReplaceCurrentQualities(current))
    }
}
