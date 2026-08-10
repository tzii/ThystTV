package com.github.andreyasadchy.xtra.ui.download

import com.github.andreyasadchy.xtra.model.ui.OfflineVideo

internal data class VideoSegmentSelection(
    val startIndex: Int?,
    val endIndex: Int?,
    val indexes: IntRange?,
    val sourceStartPositionMs: Long?,
    val selectedDurationMs: Long,
    val segmentCount: Int,
) {
    val isEmpty: Boolean get() = indexes == null

    companion object {
        val Empty = VideoSegmentSelection(null, null, null, null, 0L, 0)
    }
}

internal fun selectVideoSegments(
    durationsMs: List<Long>,
    fromMs: Long,
    toMs: Long,
): VideoSegmentSelection {
    if (durationsMs.isEmpty() || fromMs >= toMs) return VideoSegmentSelection.Empty

    var segmentStart = 0L
    var firstIndex: Int? = null
    var lastIndex: Int? = null
    var sourceStart: Long? = null
    var selectedDuration = 0L

    durationsMs.forEachIndexed { index, duration ->
        val segmentEnd = segmentStart + duration
        if (segmentEnd > fromMs && segmentStart < toMs) {
            if (firstIndex == null) {
                firstIndex = index
                sourceStart = segmentStart
            }
            lastIndex = index
            selectedDuration += duration
        }
        segmentStart = segmentEnd
    }

    val start = firstIndex ?: return VideoSegmentSelection.Empty
    val end = requireNotNull(lastIndex)
    return VideoSegmentSelection(
        startIndex = start,
        endIndex = end,
        indexes = start..end,
        sourceStartPositionMs = sourceStart,
        selectedDurationMs = selectedDuration,
        segmentCount = end - start + 1,
    )
}

internal fun OfflineVideo.applyVideoSegmentSelection(selection: VideoSegmentSelection) {
    check(!selection.isEmpty)
    duration = selection.selectedDurationMs - 1_000L
    sourceStartPosition = requireNotNull(selection.sourceStartPositionMs)
    maxProgress = selection.segmentCount
}
