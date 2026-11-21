package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatLayoutStoreTest {
    private val left = FloatingChatLayout(FloatingChatSide.LEFT, FloatingChatAnchor.BOTTOM, 1f, 200f, 260f)
    private val right = FloatingChatLayout(FloatingChatSide.RIGHT, FloatingChatAnchor.FREE, 0.25f, 240f, 300f)
    private val fallback = FloatingChatPlacement.defaultLayout(tablet = false)

    @Test fun `a channel gets its own layout back`() {
        val document = FloatingChatLayoutStore.write(null, "a", left)
        assertEquals(left, FloatingChatLayoutStore.read(document, "a"))
        assertTrue(FloatingChatLayoutStore.hasChannel(document, "a"))
    }

    @Test fun `a new channel starts from the last used layout`() {
        var document = FloatingChatLayoutStore.write(null, "a", left)
        document = FloatingChatLayoutStore.write(document, "b", right)
        assertFalse(FloatingChatLayoutStore.hasChannel(document, "c"))
        assertEquals(right, FloatingChatLayoutStore.read(document, "c"))
        assertEquals(left, FloatingChatLayoutStore.read(document, "a"))
    }

    @Test fun `nothing stored or a broken document reads as nothing`() {
        assertNull(FloatingChatLayoutStore.read(null, "a"))
        assertNull(FloatingChatLayoutStore.read("not json", "a"))
        assertFalse(FloatingChatLayoutStore.hasChannel("not json", "a"))
        // A broken document is replaced on the next write.
        assertEquals(left, FloatingChatLayoutStore.read(FloatingChatLayoutStore.write("not json", "a", left), "a"))
    }

    @Test fun `only the most recent channels are kept`() {
        var document: String? = null
        for (index in 0..FloatingChatLayoutStore.MAX_CHANNELS) {
            document = FloatingChatLayoutStore.write(document, "c$index", left.copy(widthDp = 200f + index))
        }
        assertFalse(FloatingChatLayoutStore.hasChannel(document, "c0"))
        assertTrue(FloatingChatLayoutStore.hasChannel(document, "c1"))
        // Writing a channel again moves it to the recent end.
        document = FloatingChatLayoutStore.write(document, "c1", right)
        document = FloatingChatLayoutStore.write(document, "new", left)
        assertTrue(FloatingChatLayoutStore.hasChannel(document, "c1"))
        assertFalse(FloatingChatLayoutStore.hasChannel(document, "c2"))
    }

    @Test fun `a layout without a channel only updates the last used one`() {
        val document = FloatingChatLayoutStore.write(FloatingChatLayoutStore.write(null, "a", left), null, right)
        assertEquals(left, FloatingChatLayoutStore.read(document, "a"))
        assertEquals(right, FloatingChatLayoutStore.read(document, null))
    }

    @Test fun `stored values out of range are repaired or dropped`() {
        val document = """{"last":{"side":"RIGHT","anchor":"FREE","y":4.0,"w":220.0,"h":250.0},""" +
            """"channels":[{"id":"bad","layout":{"side":"UP","anchor":"TOP","y":0.0,"w":220.0,"h":250.0}}]}"""
        assertEquals(1f, FloatingChatLayoutStore.read(document, null)!!.yFraction, 0f)
        assertNull(FloatingChatLayoutStore.read(document, "bad"))
    }

    @Test fun `a 1_7_0 pixel position converts to an anchored layout`() {
        // 1000x500 px at density 2: the old card sat near the top right.
        val top = FloatingChatLayoutStore.fromLegacy(x = 600f, y = 30f, widthPx = 400, heightPx = 480, parentWidth = 1000, parentHeight = 500, density = 2f, fallback = fallback)!!
        assertEquals(FloatingChatSide.RIGHT, top.side)
        assertEquals(FloatingChatAnchor.TOP, top.anchor)
        assertEquals(200f, top.widthDp, 0f)
        assertEquals(240f, top.heightDp, 0f)

        val bottomLeft = FloatingChatLayoutStore.fromLegacy(x = 0f, y = 400f, widthPx = 300, heightPx = 100, parentWidth = 1000, parentHeight = 500, density = 1f, fallback = fallback)!!
        assertEquals(FloatingChatSide.LEFT, bottomLeft.side)
        assertEquals(FloatingChatAnchor.BOTTOM, bottomLeft.anchor)
    }

    @Test fun `a missing 1_7_0 position or size falls back`() {
        assertNull(FloatingChatLayoutStore.fromLegacy(-1f, -1f, -1, -1, 1000, 500, 1f, fallback))
        val sizeless = FloatingChatLayoutStore.fromLegacy(10f, 10f, -1, -1, 1000, 500, 1f, fallback)!!
        assertEquals(fallback.widthDp, sizeless.widthDp, 0f)
        assertEquals(fallback.heightDp, sizeless.heightDp, 0f)
    }
}
