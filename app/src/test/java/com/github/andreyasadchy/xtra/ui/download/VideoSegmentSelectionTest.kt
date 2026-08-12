package com.github.andreyasadchy.xtra.ui.download

import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSegmentSelectionTest {

    @Test
    fun `selects complete playlist`() {
        val result = selectVideoSegments(
            durationsMs = listOf(1_000L, 1_000L, 1_000L, 1_000L),
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
    fun `excludes segment starting exactly at requested end`() {
        val result = selectVideoSegments(
            durationsMs = listOf(1_000L, 1_000L, 1_000L, 1_000L),
            fromMs = 1_000L,
            toMs = 3_000L,
        )

        assertEquals(1, result.startIndex)
        assertEquals(2, result.endIndex)
        assertEquals(1_000L, result.sourceStartPositionMs)
        assertEquals(2_000L, result.selectedDurationMs)
        assertEquals(2, result.segmentCount)
    }

    @Test
    fun `includes segment containing requested start`() {
        val result = selectVideoSegments(
            durationsMs = listOf(2_000L, 1_000L, 1_000L),
            fromMs = 1_500L,
            toMs = 2_500L,
        )
        assertEquals(0..1, result.indexes)
        assertEquals(0L, result.sourceStartPositionMs)
        assertEquals(3_000L, result.selectedDurationMs)
        assertEquals(2, result.segmentCount)
    }

    @Test
    fun `excludes segment ending exactly at requested start`() {
        val result = selectVideoSegments(listOf(2_000L, 1_000L), 2_000L, 3_000L)
        assertEquals(1..1, result.indexes)
    }

    @Test
    fun `includes segment containing requested end but excludes next segment`() {
        val result = selectVideoSegments(listOf(1_000L, 1_000L, 1_000L), 0L, 1_500L)
        assertEquals(0..1, result.indexes)
    }

    @Test
    fun `returns explicit empty selection for invalid or disjoint ranges`() {
        assertEquals(VideoSegmentSelection.Empty, selectVideoSegments(listOf(1_000L), 1_000L, 1_000L))
        assertEquals(VideoSegmentSelection.Empty, selectVideoSegments(listOf(1_000L), 2_000L, 1_000L))
        assertEquals(VideoSegmentSelection.Empty, selectVideoSegments(emptyList(), 0L, 1_000L))
        assertEquals(VideoSegmentSelection.Empty, selectVideoSegments(listOf(1_000L), 2_000L, 3_000L))
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
