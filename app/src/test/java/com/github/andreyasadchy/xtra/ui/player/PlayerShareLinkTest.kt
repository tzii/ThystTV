package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlayerShareLinkTest {
    @Test fun `vod links use whole playback seconds including long vods`() {
        assertEquals("https://www.twitch.tv/videos/123?t=1h2m3s", link(position = 3_723_999))
        assertEquals("https://www.twitch.tv/videos/123?t=25h0m0s", link(position = 90_000_000))
    }

    @Test fun `zero is explicit and unknown or negative positions omit the timestamp`() {
        assertEquals("https://www.twitch.tv/videos/123?t=0h0m0s", link(position = 0))
        assertEquals("https://www.twitch.tv/videos/123", link(position = null))
        assertEquals(link(position = null), link(position = Long.MIN_VALUE))
    }

    @Test fun `live and clip links do not inherit vod timestamps`() {
        assertEquals("https://www.twitch.tv/example_channel", link(PlayerFragment.STREAM))
        assertEquals("https://clips.twitch.tv/Example-Clip_123", link(PlayerFragment.CLIP))
    }

    @Test fun `local and missing playback identity cannot be shared`() {
        assertNull(link(PlayerFragment.OFFLINE_VIDEO))
        assertNull(link(null))
        assertNull(PlayerShareLink.create(PlayerFragment.VIDEO, null, null, null, 0))
    }

    @Test fun `invalid ids cannot add query parameters or change the destination`() {
        for (id in listOf("", "../other", "123?token=private", "123#fragment", "https://example.com")) {
            assertNull(PlayerShareLink.create(PlayerFragment.VIDEO, null, id, null, 0))
            assertNull(PlayerShareLink.create(PlayerFragment.STREAM, id, null, null, 0))
            assertNull(PlayerShareLink.create(PlayerFragment.CLIP, null, null, id, 0))
        }
    }

    private fun link(type: String? = PlayerFragment.VIDEO, position: Long? = 20_000) =
        PlayerShareLink.create(type, "example_channel", "123", "Example-Clip_123", position)
}
