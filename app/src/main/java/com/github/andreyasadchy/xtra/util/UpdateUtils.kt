package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.model.ui.UpdateInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object UpdateUtils {

    const val DEFAULT_RELEASE_API_URL = "https://api.github.com/repos/tzii/ThystTV/releases/latest"
    private const val LEGACY_XTRA_RELEASE_API_URL = "https://api.github.com/repos/crackededed/xtra/releases/tags/latest"
    private val markdownLink = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    private val headingPrefix = Regex("""^#{1,6}\s+""")
    private val unorderedListPrefix = Regex("""^\s*[-*+]\s+""")
    private val orderedListPrefix = Regex("""^\s*\d+[.)]\s+""")
    private val emphasisMarkers = Regex("""(\*\*|__|\*|_|`)""")

    fun resolveReleaseApiUrl(preferredUrl: String?): String {
        return preferredUrl
            ?.takeIf { it.isNotBlank() && it != LEGACY_XTRA_RELEASE_API_URL }
            ?: DEFAULT_RELEASE_API_URL
    }

    fun getAvailableUpdate(release: JsonObject, installedVersion: String): UpdateInfo? {
        val tagName = release["tag_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        if (!isVersionNewer(tagName, installedVersion)) return null
        val downloadUrl = release["assets"]?.jsonArray
            ?.firstNotNullOfOrNull { asset ->
                val obj = asset.jsonObject
                val isApk = obj["content_type"]?.jsonPrimitive?.contentOrNull == "application/vnd.android.package-archive" ||
                        obj["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true
                if (isApk) obj["browser_download_url"]?.jsonPrimitive?.contentOrNull else null
            }
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return UpdateInfo(
            versionName = normalizeVersionName(tagName),
            tagName = tagName,
            title = release["name"]?.jsonPrimitive?.contentOrNull,
            publishedAt = release["published_at"]?.jsonPrimitive?.contentOrNull,
            releaseNotes = release["body"]?.jsonPrimitive?.contentOrNull,
            releaseUrl = release["html_url"]?.jsonPrimitive?.contentOrNull,
            downloadUrl = downloadUrl
        )
    }

    fun isVersionNewer(candidateVersion: String, installedVersion: String): Boolean {
        val candidate = versionSegments(candidateVersion)
        val installed = versionSegments(installedVersion)
        val length = maxOf(candidate.size, installed.size)
        for (index in 0 until length) {
            val candidatePart = candidate.getOrElse(index) { 0 }
            val installedPart = installed.getOrElse(index) { 0 }
            if (candidatePart != installedPart) {
                return candidatePart > installedPart
            }
        }
        return false
    }

    fun formatReleaseNotes(releaseNotes: String): String {
        return releaseNotes
            .lineSequence()
            .map { line ->
                var formatted = line.trimEnd()
                    .replace(markdownLink) { match -> match.groupValues[1] }
                formatted = headingPrefix.replace(formatted, "")
                formatted = if (unorderedListPrefix.containsMatchIn(formatted)) {
                    unorderedListPrefix.replace(formatted, "\u2022 ")
                } else {
                    orderedListPrefix.replace(formatted, "")
                }
                formatted = formatted.replace(emphasisMarkers, "")
                formatted.trimEnd()
            }
            .joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private fun normalizeVersionName(version: String): String {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
    }

    private fun versionSegments(version: String): List<Int> {
        return normalizeVersionName(version)
            .split(".")
            .map { segment ->
                segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
    }
}
