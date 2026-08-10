package com.github.andreyasadchy.xtra.ui.download

import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSegmentSelectionTest {

    @Test
    fun `selects complete playlist`() {
        val result = selectVideoSegmentsLegacy(
            durationsMs = listOf(1_000L, 1_000L, 1_000L, 1_000L),
            targetDurationMs = 1_000L,
            fromMs = 0L,
            toMs = 4_000L,
        )

        assertEquals(0, result.startIndex)
        assertEquals(3, result.endIndex)
        assertEquals(0..3, result.indexes)
        assertEquals(0L, result.sourceStartPositionMs)
        assertEquals(4_000L, result.selectedDurationMs)
        assertEquals(4, result.segmentCount)
    }

    @Test
    fun `characterizes exact current boundary selection`() {
        val result = selectVideoSegmentsLegacy(
            durationsMs = listOf(1_000L, 1_000L, 1_000L, 1_000L),
            targetDurationMs = 1_000L,
            fromMs = 1_000L,
            toMs = 3_000L,
        )

        assertEquals(1, result.startIndex)
        assertEquals(3, result.endIndex)
        assertEquals(1_000L, result.sourceStartPositionMs)
        assertEquals(3_000L, result.selectedDurationMs)
        assertEquals(3, result.segmentCount)
    }

    @Test
    fun `maps one selection to metadata shared by every output path`() {
        val video = OfflineVideo()
        val result = VideoSegmentSelection(1, 3, 1..3, 1_000L, 3_000L, 3)

        video.applyVideoSegmentSelection(result)

        assertEquals(2_000L, video.duration)
        assertEquals(1_000L, video.sourceStartPosition)
        assertEquals(3, video.maxProgress)
    }
}
