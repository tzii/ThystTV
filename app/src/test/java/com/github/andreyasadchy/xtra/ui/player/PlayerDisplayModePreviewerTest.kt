package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDisplayModePreviewerTest {

    @Test
    fun `matching aspect ratio collapses to one`() {
        assertEquals(1f, PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 1920, 1080), 0.0001f)
    }

    @Test
    fun `four by three source on sixteen by nine viewport crops by width ratio`() {
        // fitted width = min(1920, 1080 * 4/3 = 1440) = 1440; filled = 1920
        assertEquals(1920f / 1440f, PlayerDisplayModePreviewer.fillToFitRatio(4f / 3f, 1920, 1080), 0.0001f)
    }

    @Test
    fun `portrait source on landscape viewport`() {
        // fitted width = min(1920, 1080 * 9/16 = 607.5); filled = 1920
        assertEquals(1920f / 607.5f, PlayerDisplayModePreviewer.fillToFitRatio(9f / 16f, 1920, 1080), 0.0001f)
    }

    @Test
    fun `invalid viewport falls back to one`() {
        assertEquals(1f, PlayerDisplayModePreviewer.fillToFitRatio(4f / 3f, 0, 1080), 0.0001f)
        assertEquals(1f, PlayerDisplayModePreviewer.fillToFitRatio(4f / 3f, 1920, 0), 0.0001f)
    }

    @Test
    fun `invalid video aspect falls back to sixteen by nine`() {
        assertEquals(1f, PlayerDisplayModePreviewer.fillToFitRatio(0f, 1920, 1080), 0.0001f)
        assertEquals(1f, PlayerDisplayModePreviewer.fillToFitRatio(-1f, 1920, 1080), 0.0001f)
    }

    @Test
    fun `fit to fill preview interpolates from one to ratio`() {
        val ratio = 4f / 3f
        assertEquals(1f, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.FIT, PlayerDisplayMode.FILL, 0f, ratio), 0.0001f)
        assertEquals(ratio, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.FIT, PlayerDisplayMode.FILL, 1f, ratio), 0.0001f)
        assertEquals((1f + ratio) / 2f, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.FIT, PlayerDisplayMode.FILL, 0.5f, ratio), 0.0001f)
    }

    @Test
    fun `fill to fit preview interpolates from ratio to one`() {
        val ratio = 4f / 3f
        assertEquals(ratio, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.FILL, PlayerDisplayMode.FIT, 0f, ratio), 0.0001f)
        assertEquals(1f, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.FILL, PlayerDisplayMode.FIT, 1f, ratio), 0.0001f)
    }

    @Test
    fun `stretch previews anchor at the fill scale in both directions`() {
        val ratio = 4f / 3f
        assertEquals(ratio, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.STRETCH, PlayerDisplayMode.FILL, 0f, ratio), 0.0001f)
        assertEquals(ratio, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.STRETCH, PlayerDisplayMode.FILL, 1f, ratio), 0.0001f)
        assertEquals(ratio, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.STRETCH, PlayerDisplayMode.FIT, 0f, ratio), 0.0001f)
        assertEquals(1f, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.STRETCH, PlayerDisplayMode.FIT, 1f, ratio), 0.0001f)
    }

    @Test
    fun `progress is clamped`() {
        val ratio = 2f
        assertEquals(ratio, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.FIT, PlayerDisplayMode.FILL, 1.5f, ratio), 0.0001f)
        assertEquals(1f, PlayerDisplayModePreviewer.previewScale(PlayerDisplayMode.FIT, PlayerDisplayMode.FILL, -0.5f, ratio), 0.0001f)
    }
}
