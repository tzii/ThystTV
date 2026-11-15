package com.github.andreyasadchy.xtra.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipLinkTest {
    @Test fun channelClipLink() {
        assertEquals("FunnySlug-AbC_12", clipLinkId("https://www.twitch.tv/channel/clip/FunnySlug-AbC_12"))
        assertEquals("FunnySlug", clipLinkId("https://m.twitch.tv/channel/clip/FunnySlug?filter=clips&range=7d"))
        assertEquals("FunnySlug", clipLinkId("https://www.twitch.tv/channel/clip/FunnySlug/"))
    }

    @Test fun clipsDomainLink() {
        assertEquals("FunnySlug", clipLinkId("https://clips.twitch.tv/FunnySlug"))
        assertEquals("FunnySlug", clipLinkId("https://clips.twitch.tv/FunnySlug?tt_medium=share"))
        assertEquals("FunnySlug", clipLinkId("https://clips.twitch.tv/FunnySlug/?x=1"))
        assertEquals("FunnySlug", clipLinkId("https://clips.twitch.tv/FunnySlug#t"))
    }

    @Test fun embedLinkUsesTheClipParameter() {
        assertEquals("FunnySlug", clipLinkId("https://clips.twitch.tv/embed?clip=FunnySlug&parent=example.com"))
        assertEquals("FunnySlug", clipLinkId("https://clips.twitch.tv/embed?parent=example.com&clip=FunnySlug"))
        assertNull(clipLinkId("https://clips.twitch.tv/embed?parent=example.com"))
    }

    @Test fun linksWithoutASlug() {
        assertNull(clipLinkId("https://clips.twitch.tv/"))
        assertNull(clipLinkId("https://www.twitch.tv/channel/clip/"))
        assertNull(clipLinkId("https://www.twitch.tv/channel"))
    }
}
