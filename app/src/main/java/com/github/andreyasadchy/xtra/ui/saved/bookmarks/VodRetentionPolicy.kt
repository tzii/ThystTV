package com.github.andreyasadchy.xtra.ui.saved.bookmarks

internal fun vodRetentionDays(
    userType: String?,
    userBroadcasterType: String?,
): Int {
    val effectiveType = userType ?: userBroadcasterType
    return when {
        effectiveType.isNullOrBlank() -> 7
        effectiveType.equals("affiliate", ignoreCase = true) -> 14
        else -> 60
    }
}
