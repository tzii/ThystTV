package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.ui.player.PinchZoomController.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class PinchZoomControllerTest {

    /** 16:9 video on a near-square foldable inner screen in landscape. */
    private val foldable = PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 2352, 2172)

    private fun controller(mode: PlayerDisplayMode = PlayerDisplayMode.FIT, zoom: Float = 1f, max: Float = foldable) =
        PinchZoomController().apply { begin(mode, zoom, max) }

    /** Extended zoom: magnifying past Fill up to the usual cap. */
    private fun extended(mode: PlayerDisplayMode = PlayerDisplayMode.FIT, max: Float = foldable, magnification: Float = 1f) =
        PinchZoomController().apply {
            begin(mode, zoom = 1f, maxZoom = max, magnifyCap = PinchZoomController.magnifyCap(max), magnification = magnification)
        }

    /** Raw-to-drawn factor past Fill's saturated resistance. */
    private val magnifyScale = (1f + PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA) / 1.12f

    @Test
    fun `free zoom needs a band between the two detents`() {
        assertFalse(PinchZoomController.supports(1f))
        // 16:9 video on a 16:10 tablet: only a toggle.
        assertFalse(PinchZoomController.supports(PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 2560, 1600)))
        assertFalse(PinchZoomController.supports(Float.NaN))
        // 20:9 phone and the foldable inner screen.
        assertTrue(PinchZoomController.supports(PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 2400, 1080)))
        assertTrue(PinchZoomController.supports(foldable))
    }

    @Test
    fun `zoom follows the fingers and is kept on release`() {
        val pinch = controller()
        val frame = pinch.update(1.3f)
        assertEquals(Target.ZOOM, frame.target)
        assertEquals(1.3f, frame.zoom, 0.0001f)
        assertEquals(1.3f, frame.displayZoom, 0.0001f)
        assertEquals(ln(1.3f) / ln(foldable), frame.fraction, 0.0001f)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FIT, result.mode)
        assertEquals(1.3f, result.zoom, 0.0001f)
        assertEquals(Target.ZOOM, result.target)
        assertTrue(result.changed)
    }

    @Test
    fun `near Fit snaps to plain Fit and is unchanged`() {
        val pinch = controller()
        val frame = pinch.update(1.04f)
        assertEquals(Target.FIT, frame.target)
        assertEquals(1f, frame.displayZoom, 0f)
        assertFalse(frame.reachedEnd)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FIT, result.mode)
        assertEquals(1f, result.zoom, 0f)
        assertFalse(result.changed)
    }

    @Test
    fun `near Fill snaps to Fill once with a single end tick`() {
        val pinch = controller()
        val entered = pinch.update(foldable / 1.04f)
        assertEquals(Target.FILL, entered.target)
        assertEquals(foldable, entered.displayZoom, 0.0001f)
        assertEquals(1f, entered.fraction, 0f)
        assertTrue(entered.reachedEnd)
        assertFalse(pinch.update(foldable).reachedEnd)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FILL, result.mode)
        assertEquals(1f, result.zoom, 0f)
        assertTrue(result.changed)
    }

    @Test
    fun `returning to Fit ticks but starting there does not`() {
        val pinch = controller()
        assertFalse(pinch.update(0.98f).reachedEnd)
        assertFalse(pinch.update(1.3f).reachedEnd)
        assertTrue(pinch.update(1.02f).reachedEnd)
    }

    @Test
    fun `pinching past either end resists up to five percent`() {
        val belowFit = controller().update(0.5f)
        assertEquals(Target.FIT, belowFit.target)
        assertTrue(belowFit.displayZoom < 1f)
        assertEquals(1f - PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA, belowFit.displayZoom, 0.0001f)

        val pinch = controller()
        val slightlyPastFill = pinch.update(foldable * 1.03f)
        val farPastFill = pinch.update(foldable * 3f)
        assertEquals(Target.FILL, farPastFill.target)
        assertEquals(foldable, farPastFill.zoom, 0.0001f)
        assertTrue(slightlyPastFill.displayZoom > foldable)
        assertTrue(farPastFill.displayZoom > slightlyPastFill.displayZoom)
        assertEquals(foldable * (1f + PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA), farPastFill.displayZoom, 0.0001f)
        assertEquals(PlayerDisplayMode.FILL, pinch.release().mode)
    }

    @Test
    fun `pinching in from Fill keeps an intermediate zoom`() {
        val pinch = controller(mode = PlayerDisplayMode.FILL)
        val frame = pinch.update(0.8f)
        assertEquals(Target.ZOOM, frame.target)
        assertEquals(foldable * 0.8f, frame.zoom, 0.0001f)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FIT, result.mode)
        assertEquals(foldable * 0.8f, result.zoom, 0.0001f)
        assertTrue(result.changed)
    }

    @Test
    fun `a kept zoom resumes where it was`() {
        val pinch = controller(zoom = 1.3f)
        val frame = pinch.update(1f)
        assertEquals(Target.ZOOM, frame.target)
        assertEquals(1.3f, frame.displayZoom, 0.0001f)
        assertFalse(frame.reachedEnd)
        assertFalse(pinch.release().changed)
    }

    @Test
    fun `a kept zoom beyond a narrower screen's Fill starts at Fill`() {
        val phone = PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 2400, 1080)
        val pinch = controller(zoom = 1.5f, max = phone)
        val frame = pinch.update(1.01f)
        assertEquals(Target.FILL, frame.target)
        assertEquals(phone, frame.zoom, 0.0001f)
        assertFalse(frame.reachedEnd)
    }

    @Test
    fun `cancel restores the starting state`() {
        val pinch = controller(zoom = 1.3f)
        pinch.update(1.2f)
        val result = pinch.cancel()
        assertEquals(PlayerDisplayMode.FIT, result.mode)
        assertEquals(1.3f, result.zoom, 0f)
        assertEquals(Target.ZOOM, result.target)
        assertFalse(result.changed)
    }

    @Test
    fun `displayed zoom never moves against the fingers`() {
        val pinch = controller()
        var previous = 0f
        var scale = 0.5f
        while (scale <= 3f) {
            val displayed = pinch.update(scale).displayZoom
            assertTrue("$scale: $displayed < $previous", displayed >= previous)
            previous = displayed
            scale += 0.01f
        }
    }

    @Test
    fun `magnify cap is four times Fit or twice a larger Fill`() {
        assertEquals(4f, PinchZoomController.magnifyCap(foldable), 0f)
        assertEquals(5f, PinchZoomController.magnifyCap(2.5f), 0f)
    }

    @Test
    fun `extended zoom breaks through Fill's resistance and keeps the magnification`() {
        val pinch = extended()
        val resisted = pinch.update(foldable * 1.12f)
        assertEquals(Target.FILL, resisted.target)
        assertEquals(foldable * (1f + PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA), resisted.displayZoom, 0.0001f)

        val magnified = pinch.update(foldable * 1.3f)
        assertEquals(Target.MAGNIFIED, magnified.target)
        assertTrue(magnified.reachedEnd)
        assertEquals(foldable * 1.3f * magnifyScale, magnified.zoom, 0.0001f)
        assertEquals(magnified.zoom, magnified.displayZoom, 0f)
        assertEquals(1f, magnified.fraction, 0f)

        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FILL, result.mode)
        assertEquals(1f, result.zoom, 0f)
        assertEquals(Target.MAGNIFIED, result.target)
        assertEquals(1.3f * magnifyScale, result.magnification, 0.0001f)
        assertTrue(result.changed)
    }

    @Test
    fun `releasing just past Fill's resistance returns to plain Fill`() {
        val pinch = extended()
        val frame = pinch.update(foldable * 1.15f)
        assertEquals(Target.FILL, frame.target)
        assertEquals(foldable * 1.15f * magnifyScale, frame.displayZoom, 0.0001f)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FILL, result.mode)
        assertEquals(1f, result.magnification, 0f)
    }

    @Test
    fun `the magnify cap resists like the other ends and is kept`() {
        val cap = PinchZoomController.magnifyCap(foldable)
        val pinch = extended()
        val frame = pinch.update(20f)
        assertEquals(Target.MAGNIFIED, frame.target)
        assertEquals(cap, frame.zoom, 0f)
        assertEquals(cap * (1f + PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA), frame.displayZoom, 0.0001f)
        assertEquals(cap / foldable, pinch.release().magnification, 0.0001f)
    }

    @Test
    fun `a magnified Fill resumes and pinching back to Fill recenters with a tick`() {
        val pinch = extended(mode = PlayerDisplayMode.FILL, magnification = 2f)
        val resumed = pinch.update(1f)
        assertEquals(Target.MAGNIFIED, resumed.target)
        assertEquals(foldable * 2f, resumed.displayZoom, 0.0001f)
        assertFalse(resumed.reachedEnd)
        assertFalse(pinch.release().changed)

        val back = pinch.update(0.5f)
        assertEquals(Target.FILL, back.target)
        assertTrue(back.reachedEnd)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FILL, result.mode)
        assertEquals(1f, result.magnification, 0f)
        assertTrue(result.changed)
    }

    @Test
    fun `a magnified pinch can still reach a Fit zoom`() {
        val pinch = extended(mode = PlayerDisplayMode.FILL, magnification = 2f)
        val frame = pinch.update(0.35f)
        assertEquals(Target.ZOOM, frame.target)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FIT, result.mode)
        assertEquals(1f, result.magnification, 0f)
    }

    @Test
    fun `cancel restores the starting magnification`() {
        val pinch = extended(mode = PlayerDisplayMode.FILL, magnification = 2f)
        pinch.update(0.5f)
        val result = pinch.cancel()
        assertEquals(PlayerDisplayMode.FILL, result.mode)
        assertEquals(Target.MAGNIFIED, result.target)
        assertEquals(2f, result.magnification, 0f)
        assertFalse(result.changed)
    }

    @Test
    fun `without extended zoom a stored magnification is ignored`() {
        val pinch = PinchZoomController().apply { begin(PlayerDisplayMode.FILL, 1f, foldable, magnification = 2f) }
        val frame = pinch.update(1.5f)
        assertEquals(Target.FILL, frame.target)
        assertEquals(1f, pinch.release().magnification, 0f)
    }

    @Test
    fun `extended zoom on a shape without a band keeps Fit and Fill as neighbouring detents`() {
        val tablet = PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 2560, 1600)
        for (max in listOf(tablet, 1f)) {
            val fromFit = extended(max = max)
            var scale = 0.5f
            while (scale <= 6f) {
                assertTrue("$max at $scale", fromFit.update(scale).target != Target.ZOOM)
                scale += 0.01f
            }
            assertEquals(Target.FIT, extended(max = max).update(1.04f).target)
            assertEquals(Target.FILL, extended(max = max).update(1.08f).target)
            assertEquals(Target.MAGNIFIED, extended(max = max).update(2f).target)

            val fromFill = extended(mode = PlayerDisplayMode.FILL, max = max)
            assertEquals(Target.FILL, fromFill.update(1f).target)
            assertFalse(fromFill.release().changed)
            assertEquals(Target.FIT, fromFill.update(0.9f).target)
        }
    }

    @Test
    fun `a kept Fit zoom survives a pinch where the shape has no band`() {
        val tablet = PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 2560, 1600)
        val pinch = PinchZoomController().apply {
            begin(PlayerDisplayMode.FIT, 1.3f, tablet, magnifyCap = PinchZoomController.magnifyCap(tablet))
        }
        assertEquals(Target.FIT, pinch.update(1.03f).target)
        val result = pinch.release()
        assertEquals(PlayerDisplayMode.FIT, result.mode)
        assertEquals(1.3f, result.zoom, 0f)
        assertFalse(result.changed)
    }

    @Test
    fun `magnifying never jumps or moves against the fingers`() {
        val pinch = extended()
        var previous = 0f
        var scale = 0.5f
        while (scale <= 8f) {
            val displayed = pinch.update(scale).displayZoom
            assertTrue("$scale: $displayed < $previous", displayed >= previous)
            if (scale > foldable) {
                assertTrue("$scale: $displayed jumps from $previous", displayed / previous < 1.01f)
            }
            previous = displayed
            scale += 0.005f
        }
    }
}
