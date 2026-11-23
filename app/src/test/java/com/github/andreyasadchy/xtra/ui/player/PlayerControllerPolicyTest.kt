package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControllerPolicyTest {
    @Test fun `a VoD rebuffer while playing does not open hidden controls`() {
        assertFalse(PlayerControllerPolicy.showOnPlaybackStateChange(isStream = false, useController = true, showPlayButton = false, controlsVisible = false))
    }

    @Test fun `controls already up are refreshed`() {
        assertTrue(PlayerControllerPolicy.showOnPlaybackStateChange(isStream = false, useController = true, showPlayButton = false, controlsVisible = true))
    }

    @Test fun `paused ended or idle VoDs show the play button`() {
        assertTrue(PlayerControllerPolicy.showOnPlaybackStateChange(isStream = false, useController = true, showPlayButton = true, controlsVisible = false))
    }

    @Test fun `streams and minimized players never auto show`() {
        assertFalse(PlayerControllerPolicy.showOnPlaybackStateChange(isStream = true, useController = true, showPlayButton = true, controlsVisible = true))
        assertFalse(PlayerControllerPolicy.showOnPlaybackStateChange(isStream = false, useController = false, showPlayButton = true, controlsVisible = true))
    }
}
