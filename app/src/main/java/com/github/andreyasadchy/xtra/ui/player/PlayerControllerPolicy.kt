package com.github.andreyasadchy.xtra.ui.player

internal object PlayerControllerPolicy {
    /**
     * Whether a playback state change brings up the controls. A VoD that stalls and
     * resumes passes through BUFFERING and READY without anyone touching it, so those
     * alone must not open the controls (and step the floating chat card aside) in the
     * middle of playback. Paused, ended and idle need the play button, so they still
     * show them. Controls that are already up get their hide timer refreshed. Media3's
     * own PlayerView auto-shows by the same rule.
     */
    fun showOnPlaybackStateChange(
        isStream: Boolean,
        useController: Boolean,
        showPlayButton: Boolean,
        controlsVisible: Boolean,
    ): Boolean = !isStream && useController && (showPlayButton || controlsVisible)
}
