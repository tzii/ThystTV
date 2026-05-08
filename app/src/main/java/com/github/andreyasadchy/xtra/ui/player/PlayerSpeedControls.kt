package com.github.andreyasadchy.xtra.ui.player

object PlayerSpeedControls {

    fun shouldShowSpeedMenu(
        videoType: String?,
        speedButtonEnabled: Boolean,
        menuSpeedEnabled: Boolean
    ): Boolean {
        return videoType != PlayerFragment.STREAM && (speedButtonEnabled || menuSpeedEnabled)
    }

}
