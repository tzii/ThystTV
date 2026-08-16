package com.github.andreyasadchy.xtra.ui.player

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSurfacePolicyTest {

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
    fun `compact placement keeps top-centered pill for every kind`() {
        for (kind in PlayerGestureFeedbackKind.entries) {
            val placement = PlayerSurfacePolicy.placementFor(kind, PlayerSurfaceClass.COMPACT, 2f)
            assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, placement.gravity)
            assertEquals((PlayerSurfacePolicy.CENTER_FEEDBACK_MAX_WIDTH_DP * 2f).toInt(), placement.maxWidthPx)
            assertEquals((16 * 2f).toInt(), placement.marginStartPx)
            assertEquals((16 * 2f).toInt(), placement.marginEndPx)
            assertTrue(placement.topPaddingPx > 0)
            assertEquals(null, placement.fixedContainerWidthPx)
        }
    }

    @Test
    fun `large brightness is vertically centered at start edge with capped width`() {
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 2f,
        )
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.START, placement.gravity)
        assertEquals((PlayerSurfacePolicy.EDGE_FEEDBACK_MAX_WIDTH_DP * 2f).toInt(), placement.maxWidthPx)
        assertEquals(0, placement.topPaddingPx)
        assertEquals((PlayerSurfacePolicy.EDGE_FEEDBACK_MAX_WIDTH_DP * 2f).toInt(), placement.fixedContainerWidthPx)
    }

    @Test
    fun `large device volume is vertically centered at end edge`() {
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.LARGE, 2f,
        )
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.END, placement.gravity)
        assertEquals((PlayerSurfacePolicy.EDGE_FEEDBACK_MAX_WIDTH_DP * 2f).toInt(), placement.maxWidthPx)
        assertEquals(0, placement.topPaddingPx)
        assertEquals((PlayerSurfacePolicy.EDGE_FEEDBACK_MAX_WIDTH_DP * 2f).toInt(), placement.fixedContainerWidthPx)
    }

    @Test
    fun `large seek and speed stay centered with center max width`() {
        for (kind in listOf(PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackKind.PLAYBACK_SPEED)) {
            val placement = PlayerSurfacePolicy.placementFor(kind, PlayerSurfaceClass.LARGE, 2f)
            assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, placement.gravity)
            assertEquals((PlayerSurfacePolicy.CENTER_FEEDBACK_MAX_WIDTH_DP * 2f).toInt(), placement.maxWidthPx)
        }
    }

    @Test
    fun `edge placement is inset aware`() {
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 2f,
            insetStartPx = 20, insetEndPx = 30,
        )
        assertEquals((16 * 2f).toInt() + 20, placement.marginStartPx)
        assertEquals((16 * 2f).toInt(), placement.marginEndPx)

        val volumePlacement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.LARGE, 2f,
            insetStartPx = 20, insetEndPx = 30,
        )
        assertEquals((16 * 2f).toInt() + 30, volumePlacement.marginEndPx)
    }

    @Test
    fun `compact placement ignores insets`() {
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.COMPACT, 2f,
            insetStartPx = 20, insetEndPx = 30,
        )
        assertEquals((16 * 2f).toInt(), placement.marginStartPx)
        assertEquals((16 * 2f).toInt(), placement.marginEndPx)
    }
}
