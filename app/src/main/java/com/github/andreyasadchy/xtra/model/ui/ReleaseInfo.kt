package com.github.andreyasadchy.xtra.model.ui

data class ReleaseInfo(
    val versionName: String,
    val tagName: String,
    val title: String?,
    val publishedAt: String?,
    val releaseNotes: String?,
    val releaseUrl: String?,
    val downloadUrl: String?,
) {
    fun toUpdateInfo(): UpdateInfo? {
        val url = downloadUrl?.takeIf { it.isNotBlank() } ?: return null
        return UpdateInfo(
            versionName = versionName,
            tagName = tagName,
            title = title,
            publishedAt = publishedAt,
            releaseNotes = releaseNotes,
            releaseUrl = releaseUrl,
            downloadUrl = url,
        )
    }
}
