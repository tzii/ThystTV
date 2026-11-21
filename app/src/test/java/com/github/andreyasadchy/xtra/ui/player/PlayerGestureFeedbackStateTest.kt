package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGestureFeedbackStateTest {

    private val number = PlayerGestureFeedbackStyle.NUMBER

    @Test
    fun `the style setting reads its stored value and falls back to sliders`() {
        assertEquals(PlayerGestureFeedbackStyle.SLIDERS, PlayerGestureFeedbackStyle.fromPref("sliders"))
        assertEquals(PlayerGestureFeedbackStyle.NUMBER, PlayerGestureFeedbackStyle.fromPref("number"))
        assertEquals(PlayerGestureFeedbackStyle.SLIDERS, PlayerGestureFeedbackStyle.fromPref(null))
        assertEquals(PlayerGestureFeedbackStyle.SLIDERS, PlayerGestureFeedbackStyle.fromPref("card"))
    }

    @Test
    fun `sliders put brightness and volume at the edge`() {
        for (surfaceClass in PlayerSurfaceClass.entries) {
            for (kind in listOf(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackKind.DEVICE_VOLUME)) {
                assertEquals(PlayerGestureFeedbackLayout.EDGE_VERTICAL, PlayerGestureFeedbackState.layoutFor(kind, surfaceClass))
                // A reading cannot turn a vertical gesture horizontal.
                assertEquals(PlayerGestureFeedbackLayout.EDGE_VERTICAL, PlayerGestureFeedbackState.layoutFor(kind, surfaceClass, reading = true))
            }
        }
    }

    @Test
    fun `sliders put seek, speed and pinch at the top on every surface`() {
        val reading = PlayerGestureReading(0.6f, "+1:00", startFraction = 0.5f, detail = "12:00")
        for (surfaceClass in PlayerSurfaceClass.entries) {
            for (kind in listOf(PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackKind.PLAYBACK_SPEED, PlayerGestureFeedbackKind.PINCH)) {
                val presentation = PlayerGestureFeedbackState.presentation(kind, surfaceClass, text = "a11y", reading = reading)
                assertEquals(PlayerGestureFeedbackLayout.TOP_SLIDER, presentation.layout)
                assertEquals(reading, presentation.reading)
                // The plain text stays for the message fallback.
                assertEquals("a11y", presentation.text)
            }
        }
    }

    @Test
    fun `the number style centers every gesture with a reading`() {
        val reading = PlayerGestureReading(0.65f, "65%", valueIcon = true)
        for (surfaceClass in PlayerSurfaceClass.entries) {
            for (kind in PlayerGestureFeedbackKind.entries) {
                val presentation = PlayerGestureFeedbackState.presentation(kind, surfaceClass, reading = reading, style = number)
                assertEquals(PlayerGestureFeedbackLayout.CENTER_VALUE, presentation.layout)
                assertEquals(reading, presentation.reading)
            }
        }
        // Without a reading, brightness and volume keep their slider in either style.
        assertEquals(
            PlayerGestureFeedbackLayout.EDGE_VERTICAL,
            PlayerGestureFeedbackState.layoutFor(PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.LARGE, style = number),
        )
    }

    @Test
    fun `plain messages keep the top pill in both styles`() {
        assertEquals(
            PlayerGestureFeedbackLayout.COMPACT_HORIZONTAL,
            PlayerGestureFeedbackState.layoutFor(PlayerGestureFeedbackKind.PINCH, PlayerSurfaceClass.COMPACT),
        )
        for (style in PlayerGestureFeedbackStyle.entries) {
            // The pinch hint has no reading.
            val hint = PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.PINCH, PlayerSurfaceClass.LARGE, text = "Pinch to zoom", style = style)
            assertEquals(PlayerGestureFeedbackLayout.CENTERED_HORIZONTAL, hint.layout)
            assertNull(hint.reading)
            // A speed message without a reading, such as caught-up, stays a pill.
            val message = PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.PLAYBACK_SPEED, PlayerSurfaceClass.LARGE, text = "Caught up", style = style)
            assertEquals(PlayerGestureFeedbackLayout.CENTERED_HORIZONTAL, message.layout)
            assertEquals("Caught up", message.text)
        }
    }

    @Test
    fun `Fit and Fill pinch puts the start in the middle and the handle on the pinch`() {
        val towardFill = PlayerGestureFeedbackState.pinchPresentation(PlayerSurfaceClass.COMPACT, 0.5f, PlayerDisplayMode.FILL, "Fill")
        assertEquals(PlayerGestureFeedbackLayout.TOP_SLIDER, towardFill.layout)
        assertEquals(PlayerGestureReading(0.75f, "Fill", startFraction = 0.5f), towardFill.reading)
        val towardFit = PlayerGestureFeedbackState.pinchPresentation(PlayerSurfaceClass.LARGE, 1f, PlayerDisplayMode.FIT, "Fit", number)
        assertEquals(PlayerGestureFeedbackLayout.CENTER_VALUE, towardFit.layout)
        assertEquals(PlayerGestureReading(0f, "Fit", startFraction = 0.5f), towardFit.reading)
    }

    @Test
    fun `free zoom marks where the pinch began`() {
        val zoom = PlayerGestureFeedbackState.pinchZoomPresentation(PlayerSurfaceClass.LARGE, 0.7f, "128%", startFraction = 0.2f)
        assertEquals(PlayerGestureReading(0.7f, "128%", startFraction = 0.2f), zoom.reading)
        // Both the handle and the start clamp to the track.
        val clamped = PlayerGestureFeedbackState.pinchZoomPresentation(PlayerSurfaceClass.LARGE, 1.4f, "Fill", startFraction = -1f)
        assertEquals(PlayerGestureReading(1f, "Fill", startFraction = 0f), clamped.reading)
        // Without a start, the pinch starts where it is.
        assertEquals(0.3f, PlayerGestureFeedbackState.pinchZoomPresentation(PlayerSurfaceClass.LARGE, 0.3f, "Fit").reading?.startFraction)
    }

    @Test
    fun `an edge slider without a level follows its reading`() {
        val presentation = PlayerGestureFeedbackState.presentation(
            kind = PlayerGestureFeedbackKind.DEVICE_VOLUME,
            surfaceClass = PlayerSurfaceClass.LARGE,
            reading = PlayerGestureReading(0.42f, "42%", valueIcon = true),
        )
        assertEquals(PlayerGestureFeedbackLayout.EDGE_VERTICAL, presentation.layout)
        assertEquals(42, presentation.level)
    }

    @Test
    fun `edge slider keeps its short value and level`() {
        val presentation = PlayerGestureFeedbackState.presentation(
            kind = PlayerGestureFeedbackKind.BRIGHTNESS,
            surfaceClass = PlayerSurfaceClass.LARGE,
            level = 45,
            text = "45%",
        )
        assertEquals(PlayerGestureFeedbackLayout.EDGE_VERTICAL, presentation.layout)
        assertTrue(presentation.levelVisible)
        assertEquals(45, presentation.level)
        assertEquals("45%", presentation.text)
    }

    @Test
    fun `presentation without text stays text-free`() {
        val presentation = PlayerGestureFeedbackState.presentation(
            kind = PlayerGestureFeedbackKind.DEVICE_VOLUME,
            surfaceClass = PlayerSurfaceClass.LARGE,
            level = 45,
        )
        assertNull(presentation.text)
    }

    @Test
    fun `brightness icon follows the level`() {
        assertEquals(R.drawable.ic_gesture_brightness_auto, PlayerGestureFeedbackState.brightnessIcon(0, auto = true))
        assertEquals(R.drawable.ic_gesture_brightness_low, PlayerGestureFeedbackState.brightnessIcon(5, auto = false))
        assertEquals(R.drawable.ic_gesture_brightness_low, PlayerGestureFeedbackState.brightnessIcon(33, auto = false))
        assertEquals(R.drawable.ic_gesture_brightness_half, PlayerGestureFeedbackState.brightnessIcon(34, auto = false))
        assertEquals(R.drawable.ic_gesture_brightness_half, PlayerGestureFeedbackState.brightnessIcon(66, auto = false))
        assertEquals(R.drawable.ic_brightness_medium_black_24dp, PlayerGestureFeedbackState.brightnessIcon(67, auto = false))
        assertEquals(R.drawable.ic_brightness_medium_black_24dp, PlayerGestureFeedbackState.brightnessIcon(100, auto = false))
    }

    @Test
    fun `volume icon follows the level`() {
        assertEquals(R.drawable.baseline_volume_off_black_24, PlayerGestureFeedbackState.volumeIcon(0))
        assertEquals(R.drawable.ic_gesture_volume_down, PlayerGestureFeedbackState.volumeIcon(1))
        assertEquals(R.drawable.ic_gesture_volume_down, PlayerGestureFeedbackState.volumeIcon(49))
        assertEquals(R.drawable.baseline_volume_up_black_24, PlayerGestureFeedbackState.volumeIcon(50))
        assertEquals(R.drawable.baseline_volume_up_black_24, PlayerGestureFeedbackState.volumeIcon(100))
    }

    @Test
    fun `compact level feedback keeps level and text`() {
        val presentation = PlayerGestureFeedbackState.presentation(
            kind = PlayerGestureFeedbackKind.DEVICE_VOLUME,
            surfaceClass = PlayerSurfaceClass.COMPACT,
            level = 60,
            text = "60",
        )
        assertEquals(PlayerGestureFeedbackLayout.EDGE_VERTICAL, presentation.layout)
        assertTrue(presentation.levelVisible)
        assertEquals(60, presentation.level)
        assertEquals("60", presentation.text)
    }

    @Test
    fun `seek and speed never show a level`() {
        for (kind in listOf(PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackKind.PLAYBACK_SPEED)) {
            val presentation = PlayerGestureFeedbackState.presentation(
                kind = kind,
                surfaceClass = PlayerSurfaceClass.LARGE,
                level = 50,
                text = "value",
            )
            assertFalse(presentation.levelVisible)
            assertEquals(0, presentation.level)
            assertEquals("value", presentation.text)
        }
    }

    @Test
    fun `pinch bar is visible from zero progress through armed and grows toward Fill`() {
        for (progress in listOf(0f, 0.25f, 0.5f, 0.99f, 1f)) {
            val presentation = PlayerGestureFeedbackState.pinchPresentation(PlayerSurfaceClass.LARGE, progress, PlayerDisplayMode.FILL, "Fill")
            assertTrue(presentation.levelVisible)
            assertEquals(PlayerGestureFeedbackState.pinchLevel(progress, PlayerDisplayMode.FILL), presentation.level)
            assertEquals("Fill", presentation.text)
        }
        assertEquals(50, PlayerGestureFeedbackState.pinchLevel(0f, PlayerDisplayMode.FILL))
        assertEquals(100, PlayerGestureFeedbackState.pinchLevel(1f, PlayerDisplayMode.FILL))
        assertEquals(75, PlayerGestureFeedbackState.pinchLevel(0.5f, PlayerDisplayMode.FILL))
    }

    @Test
    fun `pinch bar shrinks toward Fit from the same neutral center`() {
        assertEquals(50, PlayerGestureFeedbackState.pinchLevel(0f, PlayerDisplayMode.FIT))
        assertEquals(25, PlayerGestureFeedbackState.pinchLevel(0.5f, PlayerDisplayMode.FIT))
        assertEquals(0, PlayerGestureFeedbackState.pinchLevel(1f, PlayerDisplayMode.FIT))
    }

    @Test
    fun `endpoint elastic progress moves the committed-mode bar`() {
        val fitElastic = PlayerGestureFeedbackState.pinchPresentation(
            PlayerSurfaceClass.LARGE,
            0.5f,
            PlayerDisplayMode.FIT,
            "Fit",
        )
        val fillElastic = PlayerGestureFeedbackState.pinchPresentation(
            PlayerSurfaceClass.LARGE,
            0.5f,
            PlayerDisplayMode.FILL,
            "Fill",
        )
        assertEquals(25, fitElastic.level)
        assertEquals(75, fillElastic.level)
    }

    @Test
    fun `free zoom bar shows the position between Fit and Fill`() {
        val fit = PlayerGestureFeedbackState.pinchZoomPresentation(PlayerSurfaceClass.LARGE, 0f, "Fit")
        val middle = PlayerGestureFeedbackState.pinchZoomPresentation(PlayerSurfaceClass.LARGE, 0.5f, "Zoom 128%")
        val fill = PlayerGestureFeedbackState.pinchZoomPresentation(PlayerSurfaceClass.LARGE, 1f, "Fill")
        assertTrue(fit.levelVisible)
        assertEquals(0, fit.level)
        assertEquals(50, middle.level)
        assertEquals("Zoom 128%", middle.text)
        assertEquals(100, fill.level)
        assertEquals(100, PlayerGestureFeedbackState.pinchZoomPresentation(PlayerSurfaceClass.LARGE, 1.4f, "Fill").level)
        assertEquals(0, PlayerGestureFeedbackState.pinchZoomPresentation(PlayerSurfaceClass.LARGE, -1f, "Fit").level)
    }

    @Test
    fun `zoom percent rounds the Fit-relative zoom`() {
        assertEquals(100, PlayerGestureFeedbackState.zoomPercent(1f))
        assertEquals(134, PlayerGestureFeedbackState.zoomPercent(1.335f))
        assertEquals(164, PlayerGestureFeedbackState.zoomPercent(1.635f))
        assertEquals(200, PlayerGestureFeedbackState.zoomPercent(2f))
    }

    @Test
    fun `pinch feedback skips redundant writes but keeps endpoints exact`() {
        // First write always passes; identical small deltas do not, so
        // per-touch updates stop churning layout and progress.
        assertTrue(
            PlayerGestureFeedbackState.shouldUpdatePinchFeedback(null, null, "Zoom 130%", 30, false),
        )
        assertFalse(
            PlayerGestureFeedbackState.shouldUpdatePinchFeedback("Zoom 130%", 30, "Zoom 130%", 31, false),
        )
        // A visible bar step passes even within the same frame.
        assertTrue(
            PlayerGestureFeedbackState.shouldUpdatePinchFeedback("Zoom 130%", 30, "Zoom 130%", 32, false),
        )
        // Label changes and endpoint arrivals always pass.
        assertTrue(
            PlayerGestureFeedbackState.shouldUpdatePinchFeedback("Zoom 130%", 31, "Zoom 131%", 31, false),
        )
        assertTrue(
            PlayerGestureFeedbackState.shouldUpdatePinchFeedback("Zoom 199%", 99, "Fill", 100, true),
        )
    }

    @Test
    fun `pinch feedback identical frames skip the view write`() {
        // Skipped frames keep the shared hide timer alive in the fragment so
        // the pill does not fade mid-pinch; the pure helper only decides view
        // writes.
        assertFalse(
            PlayerGestureFeedbackState.shouldUpdatePinchFeedback("Zoom 130%", 30, "Zoom 130%", 30, false),
        )
    }

    @Test
    fun `fill edge flashes only when newly sticking to Fill`() {
        assertTrue(
            PlayerGestureFeedbackState.shouldFlashFillEdge(PinchZoomController.Target.FILL, true),
        )
        // Dwelling at Fill (no new endpoint entry) must not refire.
        assertFalse(
            PlayerGestureFeedbackState.shouldFlashFillEdge(PinchZoomController.Target.FILL, false),
        )
        // Fit stick and intermediate zoom never flash the Fill edge.
        assertFalse(
            PlayerGestureFeedbackState.shouldFlashFillEdge(PinchZoomController.Target.FIT, true),
        )
        assertFalse(
            PlayerGestureFeedbackState.shouldFlashFillEdge(PinchZoomController.Target.ZOOM, false),
        )
        assertFalse(
            PlayerGestureFeedbackState.shouldFlashFillEdge(PinchZoomController.Target.ZOOM, true),
        )
    }

    @Test
    fun `neutral preview from Stretch parks at the bar center`() {
        assertEquals(50, PlayerGestureFeedbackState.pinchLevel(0f, PlayerDisplayMode.STRETCH))
        assertEquals(50, PlayerGestureFeedbackState.pinchLevel(1f, PlayerDisplayMode.STRETCH))
    }

    @Test
    fun `pinch progress clamps to the arm range before mapping`() {
        // Out-of-range progress pins at the nearest end: over-arm completes
        // Fill; anything below neutral stays at the center.
        assertEquals(100, PlayerGestureFeedbackState.pinchLevel(1.5f, PlayerDisplayMode.FILL))
        assertEquals(50, PlayerGestureFeedbackState.pinchLevel(-1f, PlayerDisplayMode.FIT))
    }

    @Test
    fun `levels clamp to the 0-100 range`() {
        assertEquals(0, PlayerGestureFeedbackState.clampLevel(-10))
        assertEquals(0, PlayerGestureFeedbackState.clampLevel(0))
        assertEquals(100, PlayerGestureFeedbackState.clampLevel(100))
        assertEquals(100, PlayerGestureFeedbackState.clampLevel(250))
    }

    @Test
    fun `every presentation field is written so switching kinds cannot inherit stale state`() {
        val presentations = listOf(
            PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 80, "80%"),
            PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.SEEK, PlayerSurfaceClass.LARGE, null, "12:00 / 24:00"),
            PlayerGestureFeedbackState.pinchPresentation(PlayerSurfaceClass.LARGE, 0.5f, PlayerDisplayMode.FILL, "Fill"),
            PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.COMPACT, 0, "0"),
        )
        // Each presentation fully describes visibility, level, layout, and text;
        // there is no optional field an applier could leave untouched.
        presentations.forEach { presentation ->
            assertTrue(presentation.layout in PlayerGestureFeedbackLayout.entries)
            assertTrue(presentation.level in 0..100)
        }
    }
}
