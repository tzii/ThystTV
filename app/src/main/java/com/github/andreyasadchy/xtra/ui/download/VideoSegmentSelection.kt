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

internal fun selectVideoSegmentsLegacy(
    durationsMs: List<Long>,
    targetDurationMs: Long,
    fromMs: Long,
    toMs: Long,
): VideoSegmentSelection {
    require(durationsMs.isNotEmpty())
    val relativeStartTimes = ArrayList<Long>(durationsMs.size)
    var totalDuration = 0L
    durationsMs.forEach { duration ->
        relativeStartTimes += totalDuration
        totalDuration += duration
    }
    val fromIndex = if (fromMs == 0L) 0 else {
        val min = fromMs - targetDurationMs
        relativeStartTimes.binarySearch { time ->
            when {
                time > fromMs -> 1
                time < min -> -1
                else -> 0
            }
        }.let { if (it < 0) -it else it }
    }
    val toIndex = if (toMs in relativeStartTimes.last()..totalDuration) {
        relativeStartTimes.lastIndex
    } else {
        val max = toMs + targetDurationMs
        relativeStartTimes.binarySearch { time ->
            when {
                time > max -> 1
                time < toMs -> -1
                else -> 0
            }
        }.let { if (it < 0) -it else it }
    }
    val indexes = fromIndex..toIndex
    return VideoSegmentSelection(
        startIndex = fromIndex,
        endIndex = toIndex,
        indexes = indexes,
        sourceStartPositionMs = relativeStartTimes[fromIndex],
        selectedDurationMs = indexes.sumOf { durationsMs[it] },
        segmentCount = indexes.count(),
    )
}

internal fun OfflineVideo.applyVideoSegmentSelection(selection: VideoSegmentSelection) {
    check(!selection.isEmpty)
    duration = selection.selectedDurationMs - 1_000L
    sourceStartPosition = requireNotNull(selection.sourceStartPositionMs)
    maxProgress = selection.segmentCount
}
