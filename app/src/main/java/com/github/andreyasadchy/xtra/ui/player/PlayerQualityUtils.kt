package com.github.andreyasadchy.xtra.ui.player

import androidx.media3.common.Format
import com.github.andreyasadchy.xtra.model.VideoQuality
import java.util.Locale
import kotlin.math.roundToInt

private const val AUTO_QUALITY_NAME = "auto"
private const val AUDIO_ONLY_QUALITY_NAME = "audio_only"
private const val CHAT_ONLY_QUALITY_NAME = "chat_only"

internal fun Format.toReadableQualityName(videoName: String?, url: String?, fallback: String): String {
    listOf(label, videoName)
        .firstOrNull { it.isReadableQualityLabel() }
        ?.let { return it }

    val heightLabel = height.takeIf { it > 0 }?.let { height ->
        val fps = frameRate.takeIf { it > 0f }?.roundToInt()
        buildString {
            append(height)
            append('p')
            if (fps != null && fps > 30) {
                append(fps)
            }
        }
    }
    if (heightLabel != null) {
        return heightLabel
    }

    if (isAudioOnlyVariant()) {
        return "audio_only"
    }

    val urlLabel = url
        ?.split('/')
        ?.asReversed()
        ?.drop(1)
        ?.firstOrNull { it.isReadableQualityLabel() }
    if (urlLabel != null) {
        return if (urlLabel.equals("chunked", ignoreCase = true)) "source" else urlLabel
    }

    return listOf(label, videoName)
        .firstOrNull { !it.isNullOrBlank() }
        ?: fallback
}

internal fun String?.isNumericQualityFallback(): Boolean {
    val value = this?.trim().orEmpty()
    return value.isNotEmpty() && value.all { it.isDigit() }
}

internal fun List<VideoQuality>.toSelectableQualities(includeChatOnly: Boolean): List<VideoQuality> {
    return asSequence()
        .filterNot { it.name.isNumericQualityFallback() }
        .sortedByDescending {
            it.name?.substringAfter("p", "")?.takeWhile { char -> char.isDigit() }?.toIntOrNull()
        }
        .sortedByDescending {
            it.name?.substringBefore("p", "")?.takeWhile { char -> char.isDigit() }?.toIntOrNull()
        }
        .sortedByDescending {
            it.name == "source"
        }
        .toMutableList()
        .apply {
            add(0, VideoQuality(AUTO_QUALITY_NAME))
            if (find { it.name == AUDIO_ONLY_QUALITY_NAME } == null) {
                add(VideoQuality(AUDIO_ONLY_QUALITY_NAME))
            }
            if (includeChatOnly && find { it.name == CHAT_ONLY_QUALITY_NAME } == null) {
                add(VideoQuality(CHAT_ONLY_QUALITY_NAME))
            }
        }
}

internal fun List<VideoQuality>.shouldReplaceCurrentQualities(currentQualities: List<VideoQuality>?): Boolean {
    val currentVideoCount = currentQualities?.videoQualityOptionCount() ?: 0
    if (currentQualities.isNullOrEmpty() || currentVideoCount == 0) {
        return true
    }
    return videoQualityOptionCount() >= currentVideoCount
}

private fun Format.isAudioOnlyVariant(): Boolean {
    if (height > 0) {
        return false
    }
    val codecFamilies = codecs
        ?.split(',')
        ?.mapNotNull { codec ->
            codec.trim()
                .takeIf { it.isNotBlank() }
                ?.substringBefore('.')
                ?.lowercase(Locale.US)
        }
        .orEmpty()
    return codecFamilies.isNotEmpty() && codecFamilies.all { it == "mp4a" || it == "opus" }
}

private fun List<VideoQuality>.videoQualityOptionCount(): Int {
    return filter { quality ->
        when (quality.name.normalizedQualityName()) {
            "", AUTO_QUALITY_NAME, AUDIO_ONLY_QUALITY_NAME, CHAT_ONLY_QUALITY_NAME -> false
            else -> !quality.name.isNumericQualityFallback()
        }
    }.distinctBy {
        it.name.normalizedQualityName() to it.codecs?.substringBefore('.')
    }.size
}

private fun String?.isReadableQualityLabel(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) {
        return false
    }
    val normalized = value.lowercase(Locale.US)
    if (value.isNumericQualityFallback()) {
        return false
    }
    return normalized == "source" ||
        normalized == "chunked" ||
        normalized == "audio_only" ||
        Regex("""\d{3,4}p(\d{2})?""").matches(normalized)
}

private fun String?.normalizedQualityName(): String {
    return this
        ?.trim()
        ?.lowercase(Locale.US)
        ?.replace(' ', '_')
        ?.replace('-', '_')
        .orEmpty()
}
