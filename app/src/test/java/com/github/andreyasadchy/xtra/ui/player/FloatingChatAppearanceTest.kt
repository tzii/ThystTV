package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.ui.chat.ChatOverlayStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatAppearanceTest {

    @Test fun `opacity maps to clear, shade and solid`() {
        assertEquals(FloatingChatBackground.CLEAR, FloatingChatBackground.fromOpacity(0))
        assertEquals(FloatingChatBackground.CLEAR, FloatingChatBackground.fromOpacity(19))
        assertEquals(FloatingChatBackground.SHADE, FloatingChatBackground.fromOpacity(20))
        assertEquals(FloatingChatBackground.SHADE, FloatingChatBackground.fromOpacity(FloatingChatBackground.DEFAULT_OPACITY))
        assertEquals(FloatingChatBackground.SOLID, FloatingChatBackground.fromOpacity(80))
        assertEquals(FloatingChatBackground.SOLID, FloatingChatBackground.fromOpacity(100))
    }

    @Test fun `each preset lands back on itself`() {
        for (background in FloatingChatBackground.entries) {
            assertEquals(background, FloatingChatBackground.fromOpacity(background.opacity))
        }
    }

    @Test fun `clear draws only outlined text with no surface`() {
        val clear = appearance(opacity = 0)
        assertEquals(0, clear.surfaceAlpha)
        assertFalse(clear.hasSurface)
        assertEquals(ChatOverlayStyle.Treatment.HALO, clear.overlayStyle().treatment)
    }

    @Test fun `shade and solid get a surface and lighter text treatment`() {
        val shade = appearance(opacity = 55)
        assertEquals(140, shade.surfaceAlpha)
        assertTrue(shade.hasSurface)
        assertEquals(ChatOverlayStyle.Treatment.SOFT_SHADOW, shade.overlayStyle().treatment)

        val solid = appearance(opacity = 92)
        assertEquals(235, solid.surfaceAlpha)
        assertEquals(ChatOverlayStyle.Treatment.NONE, solid.overlayStyle().treatment)
    }

    @Test fun `text options carry into the chat style`() {
        val style = appearance(boldText = true, textSize = FloatingChatTextSize.LARGE, fadeOld = true).overlayStyle()
        assertTrue(style.boldText)
        assertEquals(1.15f, style.textScale, 0f)
        assertTrue(style.fadeOldMessages)
    }

    @Test fun `unknown text sizes read as medium`() {
        assertEquals(FloatingChatTextSize.MEDIUM, FloatingChatTextSize.fromValue(null))
        assertEquals(FloatingChatTextSize.MEDIUM, FloatingChatTextSize.fromValue("xl"))
        assertEquals(FloatingChatTextSize.SMALL, FloatingChatTextSize.fromValue("s"))
    }

    private fun appearance(
        opacity: Int = FloatingChatBackground.DEFAULT_OPACITY,
        boldText: Boolean = false,
        textSize: FloatingChatTextSize = FloatingChatTextSize.MEDIUM,
        fadeOld: Boolean = false,
    ) = FloatingChatAppearance(
        opacity = opacity,
        boldText = boldText,
        textSize = textSize,
        fadeOldMessages = fadeOld,
        passThrough = false,
        replyBar = false,
    )
}
