package com.github.andreyasadchy.xtra.ui.player

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDialogSizingTest {

    @Test
    fun `large surface classification`() {
        assertTrue(PlayerDialogSizing.isLargeSurface(1200, 2f))
        assertFalse(PlayerDialogSizing.isLargeSurface(1199, 2f))
    }

    @Test
    fun `large surface panel is capped at 420dp`() {
        assertEquals(840, PlayerDialogSizing.panelWidthPx(2560, 2f))
    }

    @Test
    fun `narrow large surface uses the 420dp cap`() {
        // 1220px at 2dp = 610dp (large); cap 840px wins over 1220 - 64 = 1156
        assertEquals(840, PlayerDialogSizing.panelWidthPx(1220, 2f))
        // at density 1, 600px is exactly large; 420dp cap still applies
        assertEquals(420, PlayerDialogSizing.panelWidthPx(700, 1f))
    }

    @Test
    fun `compact surface keeps phone sheet sizing`() {
        // 700px at 2dp = 350dp (compact); 700 - 80 = 620 < 1000 cap
        assertEquals(620, PlayerDialogSizing.panelWidthPx(700, 2f))
        // 800px at 2dp = 400dp (compact); 800 - 80 = 720
        assertEquals(720, PlayerDialogSizing.panelWidthPx(800, 2f))
    }

    @Test
    fun `window gravity centers on large surfaces and bottom-anchors on compact`() {
        assertEquals(Gravity.CENTER, PlayerDialogSizing.windowGravity(1200, 2f))
        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, PlayerDialogSizing.windowGravity(1000, 2f))
    }
}
