package com.github.andreyasadchy.xtra.ui.player

import androidx.media3.common.Format
import java.util.Locale
import kotlin.math.roundToInt

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

private fun String?.isReadableQualityLabel(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) {
        return false
    }
    val normalized = value.lowercase(Locale.US)
    if (normalized.all { it.isDigit() }) {
        return false
    }
    return normalized == "source" ||
        normalized == "chunked" ||
        normalized == "audio_only" ||
        Regex("""\d{3,4}p(\d{2})?""").matches(normalized)
}
