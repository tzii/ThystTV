package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.ui.Video
import org.junit.Assert.*
import org.junit.Test

class LiveRewindTest {
    private val start = 1_700_000_000_000L
    private fun candidate(id: String = "1", created: Long = start, duration: Long = 600_000L) =
        LiveRewindCandidate(Video(id = id), created, duration)

    @Test fun `matching favors nearest broadcast start rather than newest unrelated recording`() {
        val expected = candidate()
        assertSame(expected, LiveRewind.selectRecording(listOf(candidate("old", start - 3_600_000L), expected, candidate("new", start + 90_000L)), start, start + 600_000L))
    }

    @Test fun `recordings from previous sessions and stale short archives are rejected`() {
        assertNull(LiveRewind.selectRecording(listOf(candidate(created = start - 181_000L)), start, start + 600_000L))
        assertNull(LiveRewind.selectRecording(listOf(candidate(duration = 1_000L)), start, start + 600_000L))
        assertNull(LiveRewind.selectRecording(listOf(candidate(created = start - 120_000L, duration = 90_000L)), start, start + 60_000L))
        assertNull(LiveRewind.selectRecording(listOf(candidate(created = start - 120_000L, duration = 120_000L)), start, start + 60_000L))
    }

    @Test fun `empty unknown future and malformed recording metadata fails closed`() {
        assertNull(LiveRewind.selectRecording(emptyList(), start, start + 600_000L))
        assertNull(LiveRewind.selectRecording(listOf(candidate()), 0, start))
        assertNull(LiveRewind.selectRecording(listOf(candidate()), start, start - 1))
        for (invalid in listOf(candidate(id = ""), candidate(created = 0), candidate(duration = 0), candidate(duration = -1))) {
            assertNull(LiveRewind.selectRecording(listOf(invalid), start, start + 600_000L))
        }
    }

    @Test fun `session requires actual id and start and respects known expected values`() {
        assertTrue(LiveRewind.sameSession(null, null, "1", "start"))
        assertTrue(LiveRewind.sameSession("1", "start", "1", "start"))
        assertFalse(LiveRewind.sameSession("1", "start", "2", "start"))
        assertFalse(LiveRewind.sameSession("1", "start", "1", "later"))
        assertFalse(LiveRewind.sameSession(null, null, null, "start"))
        assertFalse(LiveRewind.sameSession(null, null, "1", ""))
    }

    @Test fun `seek target rounds completed chunks and clamps at beginning`() {
        assertEquals(570_000L, LiveRewind.targetPosition(609_999L, 30_000L))
        assertEquals(540_000L, LiveRewind.targetPosition(609_999L, 60_000L))
        assertEquals(420_000L, LiveRewind.targetPosition(609_999L, 180_000L))
        assertEquals(0L, LiveRewind.targetPosition(120_000L, 180_000L))
        assertEquals(0L, LiveRewind.targetPosition(20_000L, 30_000L))
        assertEquals(0L, LiveRewind.targetPosition(600_000L, null))
        assertEquals(0L, LiveRewind.targetPosition(-1L, 30_000L))
        assertEquals(590_000L, LiveRewind.targetPosition(600_000L, -1L))
        assertEquals(0L, LiveRewind.targetPosition(600_000L, Long.MAX_VALUE))
    }

    @Test fun `preflight rejects permission failures empty playlists and non HLS documents`() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nhttps://example.test/video.m3u8"
        assertTrue(LiveRewind.isPlayablePlaylist(master, null))
        assertTrue(LiveRewind.isPlayablePlaylist("#EXTM3U\n#EXTINF:10,\nsegment.ts", null))
        assertFalse(LiveRewind.isPlayablePlaylist(master, 403))
        assertFalse(LiveRewind.isPlayablePlaylist(null, null))
        assertFalse(LiveRewind.isPlayablePlaylist("<html>Forbidden</html>", null))
        assertFalse(LiveRewind.isPlayablePlaylist("#EXTM3U\n#EXT-X-ENDLIST", null))
    }
}
