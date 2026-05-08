package com.github.andreyasadchy.xtra.model.ui

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val title: String?,
    val publishedAt: String?,
    val releaseNotes: String?,
    val releaseUrl: String?,
    val downloadUrl: String
)
