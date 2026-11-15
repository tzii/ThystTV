package com.github.andreyasadchy.xtra.model.ui

import androidx.navigation.NavDirections

class SettingsSearchItem(
    val navDirections: NavDirections,
    val location: String? = null,
    /** Title of the section the setting sits in on its screen. */
    val section: CharSequence? = null,
    val key: String? = null,
    val title: CharSequence? = null,
    val summary: CharSequence? = null,
    val value: String? = null,
)
