package com.github.andreyasadchy.xtra.ui.player

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSurfacePolicyTest {

    private val pillLayouts = listOf(
        PlayerGestureFeedbackLayout.COMPACT_HORIZONTAL,
        PlayerGestureFeedbackLayout.CENTERED_HORIZONTAL,
    )

    @Test
    fun `compact below 600dp`() {
        assertEquals(PlayerSurfaceClass.COMPACT, PlayerSurfacePolicy.classify(599, 1f))
        assertEquals(PlayerSurfaceClass.COMPACT, PlayerSurfacePolicy.classify(1198, 2f))
    }

    @Test
    fun `large at or above 600dp`() {
        assertEquals(PlayerSurfaceClass.LARGE, PlayerSurfacePolicy.classify(600, 1f))
        assertEquals(PlayerSurfaceClass.LARGE, PlayerSurfacePolicy.classify(1200, 2f))
        assertEquals(PlayerSurfaceClass.LARGE, PlayerSurfacePolicy.classify(1920, 2f))
    }

    @Test
    fun `invalid dimensions fall back to compact`() {
        assertEquals(PlayerSurfaceClass.COMPACT, PlayerSurfacePolicy.classify(0, 2f))
        assertEquals(PlayerSurfaceClass.COMPACT, PlayerSurfacePolicy.classify(1200, 0f))
        assertEquals(PlayerSurfaceClass.COMPACT, PlayerSurfacePolicy.classify(-5, 2f))
    }

    @Test
    fun `brightness and volume are edge sliders on every surface`() {
        // Beside the side chat, in split screen or on a tablet: the vertical
        // swipe always shows the vertical slider.
        for (surfaceClass in PlayerSurfaceClass.entries) {
            for (kind in listOf(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackKind.DEVICE_VOLUME)) {
                val presentation = PlayerGestureFeedbackState.presentation(kind, surfaceClass, level = 40, text = "40")
                assertEquals(PlayerGestureFeedbackLayout.EDGE_VERTICAL, presentation.layout)
                val placement = PlayerSurfacePolicy.placementFor(kind, presentation.layout, 2f)
                assertEquals(PlayerFeedbackIndicator.EDGE_SLIDER, placement.indicator)
                assertTrue(placement.verticalPill)
            }
        }
    }

    @Test
    fun `pill layouts stay top-centered and horizontal for every kind`() {
        for (layout in pillLayouts) {
            for (kind in PlayerGestureFeedbackKind.entries) {
                val placement = PlayerSurfacePolicy.placementFor(kind, layout, 2f)
                assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, placement.gravity)
                assertEquals((PlayerSurfacePolicy.CENTER_FEEDBACK_MAX_WIDTH_DP * 2f).toInt(), placement.maxWidthPx)
                assertEquals((16 * 2f).toInt(), placement.marginLeftPx)
                assertEquals((16 * 2f).toInt(), placement.marginRightPx)
                assertTrue(placement.topPaddingPx > 0)
                assertNull(placement.fixedContainerWidthPx)
                assertNull(placement.fixedContainerHeightPx)
                assertEquals(PlayerFeedbackIndicator.PILL, placement.indicator)
                assertFalse(placement.verticalPill)
            }
        }
    }

    @Test
    fun `top slider sits at the top center at a fixed width`() {
        // Density 2: the 300 dp slider is 600 px, plus 8 px of shadow room on
        // each side; the 16 dp top gap is measured to the drawn track.
        val wide = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackLayout.TOP_SLIDER, 2f,
            surfaceHeightPx = 1350, surfaceWidthPx = 2400,
        )
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, wide.gravity)
        assertEquals(PlayerFeedbackIndicator.TOP_SLIDER, wide.indicator)
        assertEquals(616, wide.fixedContainerWidthPx)
        assertEquals(104, wide.fixedContainerHeightPx)
        assertEquals(24, wide.topPaddingPx)
        assertFalse(wide.verticalPill)
        // Speed and pinch share the same slider and place.
        for (kind in listOf(PlayerGestureFeedbackKind.PLAYBACK_SPEED, PlayerGestureFeedbackKind.PINCH)) {
            assertEquals(
                wide,
                PlayerSurfacePolicy.placementFor(kind, PlayerGestureFeedbackLayout.TOP_SLIDER, 2f, surfaceHeightPx = 1350, surfaceWidthPx = 2400),
            )
        }
    }

    @Test
    fun `small players get the compact slider and keep the side margins`() {
        // Portrait player: 412 x 232 dp is short, so the slider shrinks.
        assertTrue(PlayerSurfacePolicy.compactFeedback(2.625f, 1080, 608))
        assertFalse(PlayerSurfacePolicy.compactFeedback(2.625f, 2400, 1080))
        assertFalse(PlayerSurfacePolicy.compactFeedback(2f, 0, 0))
        val portrait = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackLayout.TOP_SLIDER, 1f,
            surfaceHeightPx = 232, surfaceWidthPx = 412,
        )
        assertEquals(PlayerGestureSliderView.COMPACT_WIDTH_DP.toInt() + 8, portrait.fixedContainerWidthPx)
        // A window narrower than the slider plus 16 dp margins narrows it further.
        assertEquals(100, PlayerSurfacePolicy.topSliderWidthPx(1f, 132, 16, compact = true))
        assertEquals(0, PlayerSurfacePolicy.topSliderWidthPx(1f, 20, 16))
        assertEquals(600, PlayerSurfacePolicy.topSliderWidthPx(2f, 0, 32))
    }

    @Test
    fun `number style centers every gesture`() {
        for (kind in PlayerGestureFeedbackKind.entries) {
            val placement = PlayerSurfacePolicy.placementFor(
                kind, PlayerGestureFeedbackLayout.CENTER_VALUE, 2f,
                surfaceHeightPx = 1350, surfaceWidthPx = 2400,
            )
            assertEquals(Gravity.CENTER, placement.gravity)
            assertEquals(PlayerFeedbackIndicator.CENTER_VALUE, placement.indicator)
            assertEquals(600, placement.fixedContainerWidthPx)
            assertEquals(360, placement.fixedContainerHeightPx)
            assertEquals(0, placement.topPaddingPx)
            assertEquals(0, placement.marginLeftPx)
            assertEquals(0, placement.marginRightPx)
        }
        val portrait = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackLayout.CENTER_VALUE, 1f,
            surfaceHeightPx = 232, surfaceWidthPx = 412,
        )
        assertEquals(220, portrait.fixedContainerWidthPx)
        assertEquals(132, portrait.fixedContainerHeightPx)
        // The glow never grows past the player.
        assertEquals(200 to 100, PlayerSurfacePolicy.valueSizePx(1f, 200, 100, compact = true))
        assertEquals(300 to 180, PlayerSurfacePolicy.valueSizePx(1f, 0, 0, compact = false))
    }

    @Test
    fun `brightness is a left-edge slider`() {
        // Density 2: the 44 dp handle and 176 dp track plus 8 px of shadow
        // room on each side; the 16 dp margin is measured to the drawn track.
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 2f,
            surfaceHeightPx = 1600,
        )
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.LEFT, placement.gravity)
        assertEquals(104, placement.maxWidthPx)
        assertEquals(104, placement.fixedContainerWidthPx)
        assertEquals(368, placement.fixedContainerHeightPx)
        assertEquals(24, placement.marginLeftPx)
        assertEquals(0, placement.topPaddingPx)
        assertTrue(placement.verticalPill)
    }

    @Test
    fun `device volume is a right-edge slider`() {
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 2f,
            surfaceHeightPx = 1600,
        )
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.RIGHT, placement.gravity)
        assertEquals(104, placement.fixedContainerWidthPx)
        assertEquals(368, placement.fixedContainerHeightPx)
        assertEquals(24, placement.marginRightPx)
        assertTrue(placement.verticalPill)
    }

    @Test
    fun `edge sliders use physical sides, not start and end`() {
        // Brightness and volume follow the physical screen halves, so RTL
        // layouts must not mirror them.
        val brightness = PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 1f)
        val volume = PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 1f)
        assertEquals(0, brightness.gravity and Gravity.RELATIVE_LAYOUT_DIRECTION)
        assertEquals(0, volume.gravity and Gravity.RELATIVE_LAYOUT_DIRECTION)
    }

    @Test
    fun `back-gesture zones push both sliders in by the same amount`() {
        val edges = PlayerFeedbackEdges(unsafeLeftPx = 60, unsafeRightPx = 60)
        assertEquals(92 to 92, PlayerSurfacePolicy.edgeMargins(edges, 32))
        val brightness = PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 2f, edges)
        val volume = PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 2f, edges)
        // The view's 8 px shadow room sits inside the margin, so the drawn
        // track still starts 92 px in.
        assertEquals(84, brightness.marginLeftPx)
        assertEquals(84, volume.marginRightPx)
    }

    @Test
    fun `cutout padding no longer pushes one slider further from its screen edge`() {
        // Landscape, camera cutout on the left: the player is padded 110 px
        // clear of it, back-gesture zones are 60 px on both sides. Before, both
        // sliders sat margin + 60 px inside the player, so the brightness one
        // ended up 110 px further from its screen edge than volume.
        val edges = PlayerFeedbackEdges(surfaceLeftPx = 110, surfaceRightPx = 0, unsafeLeftPx = 110, unsafeRightPx = 60)
        val (left, right) = PlayerSurfacePolicy.edgeMargins(edges, 32)
        assertEquals(edges.surfaceLeftPx + left, edges.surfaceRightPx + right)
        assertEquals(32, left)
        assertEquals(142, right)
    }

    @Test
    fun `cutout on the right mirrors the same way`() {
        val edges = PlayerFeedbackEdges(surfaceLeftPx = 0, surfaceRightPx = 110, unsafeLeftPx = 60, unsafeRightPx = 110)
        val (left, right) = PlayerSurfacePolicy.edgeMargins(edges, 32)
        assertEquals(edges.surfaceLeftPx + left, edges.surfaceRightPx + right)
        assertEquals(142 to 32, left to right)
    }

    @Test
    fun `drawing behind the cutout keeps the slider clear of the camera`() {
        // "Draw behind cutouts": the player spans the whole width.
        val edges = PlayerFeedbackEdges(surfaceLeftPx = 0, surfaceRightPx = 0, unsafeLeftPx = 110, unsafeRightPx = 60)
        assertEquals(142 to 142, PlayerSurfacePolicy.edgeMargins(edges, 32))
    }

    @Test
    fun `side chat edge mirrors the sliders around the player instead`() {
        // Cutout padding on the left, side chat filling the right 800 px.
        val edges = PlayerFeedbackEdges(surfaceLeftPx = 110, surfaceRightPx = 800, unsafeLeftPx = 110, unsafeRightPx = 60)
        assertEquals(32 to 32, PlayerSurfacePolicy.edgeMargins(edges, 32))
        // Without cutout padding the left back-gesture zone still reaches into
        // the player; the volume slider beside the chat matches its inset.
        val noCutout = PlayerFeedbackEdges(surfaceLeftPx = 0, surfaceRightPx = 800, unsafeLeftPx = 60, unsafeRightPx = 60)
        assertEquals(92 to 92, PlayerSurfacePolicy.edgeMargins(noCutout, 32))
    }

    @Test
    fun `negative edge values are ignored`() {
        val edges = PlayerFeedbackEdges(surfaceLeftPx = -20, surfaceRightPx = -5, unsafeLeftPx = -1, unsafeRightPx = 0)
        assertEquals(32 to 32, PlayerSurfacePolicy.edgeMargins(edges, 32))
    }

    @Test
    fun `pill, top slider and centered value ignore edges`() {
        val edges = PlayerFeedbackEdges(surfaceLeftPx = 110, unsafeLeftPx = 110, unsafeRightPx = 60)
        for (layout in pillLayouts) {
            val placement = PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.SEEK, layout, 2f, edges)
            assertEquals((16 * 2f).toInt(), placement.marginLeftPx)
            assertEquals((16 * 2f).toInt(), placement.marginRightPx)
        }
        for (layout in listOf(PlayerGestureFeedbackLayout.TOP_SLIDER, PlayerGestureFeedbackLayout.CENTER_VALUE)) {
            assertEquals(
                PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.SEEK, layout, 2f, surfaceHeightPx = 1350, surfaceWidthPx = 2400),
                PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.SEEK, layout, 2f, edges, surfaceHeightPx = 1350, surfaceWidthPx = 2400),
            )
        }
    }

    @Test
    fun `edge slider height caps to a share of short surfaces`() {
        val density = 2f
        val desired = (PlayerSurfacePolicy.EDGE_SLIDER_HEIGHT_DP * density).toInt()
        assertEquals(desired, PlayerSurfacePolicy.edgeSliderHeightPx(density, 0))
        assertEquals(desired, PlayerSurfacePolicy.edgeSliderHeightPx(density, desired * 10))
        val shortSurface = 400
        assertEquals(
            (shortSurface * PlayerSurfacePolicy.EDGE_SLIDER_MAX_HEIGHT_FRACTION).toInt(),
            PlayerSurfacePolicy.edgeSliderHeightPx(density, shortSurface),
        )
    }

    @Test
    fun `resized player uses its current bounds instead of device dimensions`() {
        val fullTablet = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 1f,
            surfaceHeightPx = 720,
        )
        val shortVideo = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 1f,
            surfaceHeightPx = 160,
        )
        // 176 dp, or 60% of a 160 px player, plus 4 px of shadow room each side.
        assertEquals(184, fullTablet.fixedContainerHeightPx)
        assertEquals(104, shortVideo.fixedContainerHeightPx)
        assertTrue(PlayerSurfacePolicy.requiresCleanReposition(fullTablet, shortVideo, isVisible = true))
    }

    @Test
    fun `unchanged placement refreshes content in place`() {
        val edges = PlayerFeedbackEdges(unsafeLeftPx = 20)
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 2f, edges, surfaceHeightPx = 1600,
        )
        val same = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 2f, edges, surfaceHeightPx = 1600,
        )
        assertFalse(PlayerSurfacePolicy.requiresCleanReposition(placement, same, isVisible = true))
    }

    @Test
    fun `placement change while visible requires a clean reposition`() {
        val brightness = PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 2f)
        val seek = PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackLayout.TOP_SLIDER, 2f)
        assertTrue(PlayerSurfacePolicy.requiresCleanReposition(brightness, seek, isVisible = true))
        // A pill already hidden (for example mid-fade-out) can move freely.
        assertFalse(PlayerSurfacePolicy.requiresCleanReposition(brightness, seek, isVisible = false))
        // A visible pill with no stored placement resets defensively.
        assertTrue(PlayerSurfacePolicy.requiresCleanReposition(null, seek, isVisible = true))
    }

    @Test
    fun `edge-only changes count as placement changes`() {
        val withoutInset = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 2f, surfaceHeightPx = 1600,
        )
        val withInset = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerGestureFeedbackLayout.EDGE_VERTICAL, 2f,
            PlayerFeedbackEdges(unsafeRightPx = 40), surfaceHeightPx = 1600,
        )
        assertTrue(PlayerSurfacePolicy.requiresCleanReposition(withoutInset, withInset, isVisible = true))
    }
}
