package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSpeedControlsTest {

    @Test
    fun `menu speed is available for videos when speed button is enabled`() {
        assertTrue(
            PlayerSpeedControls.shouldShowSpeedMenu(
                videoType = PlayerFragment.VIDEO,
                speedButtonEnabled = true,
                menuSpeedEnabled = false
            )
        )
    }

    @Test
    fun `menu speed is available for videos when explicit menu setting is enabled`() {
        assertTrue(
            PlayerSpeedControls.shouldShowSpeedMenu(
                videoType = PlayerFragment.VIDEO,
                speedButtonEnabled = false,
                menuSpeedEnabled = true
            )
        )
    }

    @Test
    fun `menu speed is hidden for live streams`() {
        assertFalse(
            PlayerSpeedControls.shouldShowSpeedMenu(
                videoType = PlayerFragment.STREAM,
                speedButtonEnabled = true,
                menuSpeedEnabled = true
            )
        )
    }
}
