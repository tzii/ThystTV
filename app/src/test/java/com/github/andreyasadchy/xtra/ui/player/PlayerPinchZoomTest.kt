package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.media3.ui.AspectRatioFrameLayout
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/** Drives the real fragment pinch path on a near-square landscape player (Fill is about 1.64x Fit). */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28], qualifiers = "land")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerPinchZoomTest {
    private lateinit var activity: ActivityController<AppCompatActivity>
    private lateinit var binding: FragmentPlayerBinding
    private lateinit var fragment: PlayerFragment
    private lateinit var listener: PlayerGestureListener
    private val arbiter = PlayerGestureArbiter(0.02f)
    private val frame get() = binding.aspectRatioFrameLayout
    private val prefs get() = fragment.requireContext().prefs()
    private val ratio = PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 1083, 1000)
    /** Raw-to-drawn factor past Fill's saturated resistance. */
    private val magnifyScale = (1f + PlayerDisplayModePreviewer.ELASTIC_MAX_SCALE_DELTA) / 1.12f

    @Before fun setup() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.BaseDarkTheme)
        }.setup()
        val context = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
        context.prefs().edit { clear() }
        binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        activity.get().setContentView(binding.root)
        resizePlayer(1083, 1000)
        fragment = Mockito.mock(PlayerFragment::class.java,
            Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS))
        Mockito.doReturn(context).`when`(fragment).context
        Mockito.doReturn(android.os.Bundle()).`when`(fragment).arguments
        ReflectionHelpers.setField(fragment, "_binding", binding)
        ReflectionHelpers.setField(fragment, "displayModeStore",
            PlayerDisplayModeStore(SharedPreferencesDisplayModeStorage(context.prefs())))
        ReflectionHelpers.setField(fragment, "videoAspectRatio", 16f / 9f)
        ReflectionHelpers.setField(fragment, "gestureArbiter", arbiter)
        listener = PlayerGestureListener(context, fragment, doubleTapEnabled = true)
        call("finalizePinchSurface")
        idle()
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
    }

    @Test fun `released zoom stays centered and is saved with fit`() {
        pinch(1.3f)
        assertEquals(PlayerDisplayMode.FIT, fragment.getCurrentDisplayMode())
        assertEquals(1.3f, fragment.getDisplayZoom(), 0.001f)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, frame.resizeMode)
        assertEquals(1.3f, frame.scaleX, 0.001f)
        assertEquals(1.3f, frame.scaleY, 0.001f)
        // No explicit pivot: views scale about their center.
        assertFalse(frame.isPivotSet)
        val prefs = fragment.requireContext().prefs()
        assertEquals("fit", prefs.getString(C.PLAYER_DISPLAY_MODE_LANDSCAPE, null))
        assertEquals(1.3f, prefs.getFloat(C.PLAYER_DISPLAY_ZOOM_LANDSCAPE, 0f), 0.001f)
    }

    @Test fun `pinching to fill swaps to the fill renderer at unit scale`() {
        pinch(1.7f)
        assertEquals(PlayerDisplayMode.FILL, fragment.getCurrentDisplayMode())
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, frame.resizeMode)
        assertEquals(1f, frame.scaleX, 0.001f)
        assertFalse(fragment.requireContext().prefs().contains(C.PLAYER_DISPLAY_ZOOM_LANDSCAPE))
    }

    @Test fun `pinching in from fill keeps a zoom in the fit renderer`() {
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(0.8f)
        val expected = PlayerDisplayModePreviewer.fillToFitRatio(16f / 9f, 1083, 1000) * 0.8f
        assertEquals(PlayerDisplayMode.FIT, fragment.getCurrentDisplayMode())
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, frame.resizeMode)
        assertEquals(expected, frame.scaleX, 0.001f)
    }

    @Test fun `kept zoom is clamped to fill and dropped where the shape leaves no band`() {
        pinch(1.5f)
        // A 16:9.6 area: Fill is only about 1.11x Fit, so the zoom draws plain Fit.
        resizePlayer(1600, 1000)
        call("refreshDisplayZoom")
        assertEquals(1f, frame.scaleX, 0.001f)
        // A 20:9 phone area: Fill is 1.25x Fit, below the kept 1.5x.
        resizePlayer(2222, 1000)
        call("refreshDisplayZoom")
        assertEquals(1.25f, frame.scaleX, 0.01f)
        resizePlayer(1083, 1000)
        call("refreshDisplayZoom")
        assertEquals(1.5f, frame.scaleX, 0.001f)
        assertEquals(1.5f, fragment.getDisplayZoom(), 0.001f)
    }

    @Test fun `choosing a mode clears the zoom and portrait draws plain fit`() {
        pinch(1.3f)
        fragment.selectDisplayMode(PlayerDisplayMode.FIT)
        idle()
        assertEquals(1f, fragment.getDisplayZoom(), 0f)
        assertEquals(1f, frame.scaleX, 0.001f)
        assertFalse(fragment.requireContext().prefs().contains(C.PLAYER_DISPLAY_ZOOM_LANDSCAPE))

        pinch(1.3f)
        ReflectionHelpers.setField(fragment, "isPortrait", true)
        call("finalizePinchSurface")
        assertEquals(1f, frame.scaleX, 0.001f)
        assertEquals(1.3f, fragment.getDisplayZoom(), 0.001f)
    }

    @Test fun `cancelled pinch returns to the kept zoom`() {
        pinch(1.3f)
        pinch(1.2f, cancelled = true)
        assertEquals(1.3f, frame.scaleX, 0.001f)
        assertEquals(1.3f, fragment.getDisplayZoom(), 0.001f)
    }

    @Test fun `without extended zoom pinching past fill stays at fill`() {
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(2f, around = 800f to 700f)
        assertEquals(PlayerDisplayMode.FILL, fragment.getCurrentDisplayMode())
        assertSurface(scale = 1f, x = 0f, y = 0f)
        assertEquals(1f, fragment.getMagnifiedZoom(), 0f)
    }

    @Test fun `extended zoom magnifies fill around the fingers`() {
        enableExtendedZoom()
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(2f, around = 800f to 700f)
        val magnification = 2f * magnifyScale
        assertEquals(PlayerDisplayMode.FILL, fragment.getCurrentDisplayMode())
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, frame.resizeMode)
        // The picture point under the fingers stays there: t = focus * (1 - magnification).
        assertSurface(
            scale = magnification,
            x = (800f - 541.5f) * (1f - magnification),
            y = (700f - 500f) * (1f - magnification),
        )
        assertEquals(ratio * magnification, fragment.getMagnifiedZoom(), 0.001f)
        // The magnification is not saved; the next session starts at plain Fill.
        assertEquals("fill", prefs.getString(C.PLAYER_DISPLAY_MODE_LANDSCAPE, null))
        assertFalse(prefs.contains(C.PLAYER_DISPLAY_ZOOM_LANDSCAPE))
    }

    @Test fun `pinching from fit through fill ends magnified in the fill renderer`() {
        enableExtendedZoom()
        pinch(4f, around = 541.5f to 500f)
        assertEquals(PlayerDisplayMode.FILL, fragment.getCurrentDisplayMode())
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, frame.resizeMode)
        assertSurface(scale = 4f * magnifyScale / ratio, x = 0f, y = 0f)
    }

    @Test fun `a magnified pan stops at the edge of the picture`() {
        enableExtendedZoom()
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        // The fingers spread around the center, then drift toward the lower right.
        pinch(2f, around = 541.5f to 500f, to = 1041.5f to 980f)
        val zoom = fragment.getMagnifiedZoom()
        val fitHeight = PlayerMagnifyGeometry.fitSize(16f / 9f, 1083, 1000).second
        assertEquals(500f, frame.translationX, 0.5f)
        assertEquals(PlayerMagnifyGeometry.panLimit(fitHeight, 1000f, zoom), frame.translationY, 0.5f)
    }

    @Test fun `pinching back to fill recenters and leaving full screen clears the magnification`() {
        enableExtendedZoom()
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(2f, around = 800f to 700f)
        pinch(0.5f, around = 800f to 700f)
        assertEquals(PlayerDisplayMode.FILL, fragment.getCurrentDisplayMode())
        assertSurface(scale = 1f, x = 0f, y = 0f)
        assertEquals(1f, fragment.getMagnifiedZoom(), 0f)

        pinch(2f, around = 800f to 700f)
        call("applyMinimizedPlayerVisualState")
        assertSurface(scale = 1f, x = 0f, y = 0f)
        call("applyMaximizedPlayerVisualState")
        assertSurface(scale = 1f, x = 0f, y = 0f)
        assertEquals(1f, fragment.getMagnifiedZoom(), 0f)
    }

    @Test fun `choosing fill or turning extended zoom off draws plain fill`() {
        enableExtendedZoom()
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(2f, around = 800f to 700f)
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        assertSurface(scale = 1f, x = 0f, y = 0f)
        assertEquals(1f, fragment.getMagnifiedZoom(), 0f)

        pinch(2f, around = 800f to 700f)
        prefs.edit { putBoolean(C.PLAYER_EXTENDED_ZOOM, false) }
        call("refreshDisplayZoom")
        assertSurface(scale = 1f, x = 0f, y = 0f)
        assertEquals(1f, fragment.getMagnifiedZoom(), 0f)
    }

    @Test fun `a cancelled pinch returns to the magnified pan`() {
        enableExtendedZoom()
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(2f, around = 800f to 700f)
        val scale = frame.scaleX
        val x = frame.translationX
        val y = frame.translationY
        pinch(1.5f, around = 300f to 300f, cancelled = true)
        assertSurface(scale, x, y)
    }

    @Test fun `a magnification follows the player area while chat changes it`() {
        enableExtendedZoom()
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(2f, around = 800f to 700f)
        val x = frame.translationX
        val y = frame.translationY
        // Side chat narrows the player. Fill keeps its height, so the same picture point stays centered.
        resizePlayer(800, 1000)
        call("refreshDisplayZoom")
        assertSurface(scale = 2f * magnifyScale, x = x, y = y)
    }

    @Test fun `only a magnified picture takes one-finger drags`() {
        enableExtendedZoom()
        pinch(1.3f)
        assertFalse(fragment.canPanMagnified())
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        assertFalse(fragment.canPanMagnified())
        pinch(2f, around = 541.5f to 500f)
        assertTrue(fragment.canPanMagnified())
        ReflectionHelpers.setField(fragment, "pinchInProgress", true)
        assertFalse(fragment.canPanMagnified())
    }

    @Test fun `a one-finger drag pans a magnified picture up to its edges`() {
        enableExtendedZoom()
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(2f, around = 541.5f to 500f)
        val scale = frame.scaleX
        drag(listOf(300f to 300f), listOf(240f to 320f), listOf(180f to 340f))
        assertEquals(PlayerGestureArbiter.Owner.MAGNIFIED_PAN, arbiter.owner)
        assertSurface(scale, x = -120f, y = 40f)

        drag(listOf(300f to 300f), listOf(-4000f to 4000f))
        val zoom = fragment.getMagnifiedZoom()
        val (fitWidth, fitHeight) = PlayerMagnifyGeometry.fitSize(16f / 9f, 1083, 1000)
        assertSurface(
            scale,
            x = -PlayerMagnifyGeometry.panLimit(fitWidth, 1083f, zoom),
            y = PlayerMagnifyGeometry.panLimit(fitHeight, 1000f, zoom),
        )
    }

    @Test fun `a second finger landing or lifting does not jump the pan`() {
        enableExtendedZoom()
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(2f, around = 541.5f to 500f)
        val scale = frame.scaleX
        drag(
            listOf(300f to 300f),
            listOf(250f to 300f),
            listOf(250f to 300f, 450f to 300f),
            listOf(250f to 320f, 450f to 320f),
            listOf(250f to 320f),
            listOf(230f to 320f),
        )
        assertEquals(PlayerGestureArbiter.Owner.MAGNIFIED_PAN, arbiter.owner)
        assertSurface(scale, x = -70f, y = 20f)
    }

    @Test fun `the next pinch starts from the panned picture`() {
        enableExtendedZoom()
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(2f, around = 541.5f to 500f)
        drag(listOf(300f to 300f), listOf(180f to 340f))
        val scale = frame.scaleX
        pinch(1f, around = 541.5f to 500f)
        assertSurface(scale, x = -120f, y = 40f)
    }

    @Test fun `a pinch cancelled after entering picture-in-picture keeps the magnification cleared`() {
        enableExtendedZoom()
        fragment.selectDisplayMode(PlayerDisplayMode.FILL)
        idle()
        pinch(2f, around = 800f to 700f)
        beginPinch(100f, center = 800f to 700f)
        updatePinch(120f, center = 800f to 700f)
        // Entering PiP clears the magnification while the pinch still owns the surface.
        val pip = Mockito.mock(AppCompatActivity::class.java)
        Mockito.doReturn(true).`when`(pip).isInPictureInPictureMode
        Mockito.doReturn(pip).`when`(fragment).activity
        call("clearMagnification")
        call("refreshDisplayZoom")
        finishPinch(cancelled = true)
        assertSurface(scale = 1f, x = 0f, y = 0f)
        // Back in full screen, plain Fill stays.
        Mockito.doReturn(false).`when`(pip).isInPictureInPictureMode
        call("refreshDisplayZoom")
        assertSurface(scale = 1f, x = 0f, y = 0f)
        assertEquals(1f, fragment.getMagnifiedZoom(), 0f)
    }

    private fun enableExtendedZoom() {
        prefs.edit { putBoolean(C.PLAYER_EXTENDED_ZOOM, true) }
    }

    private fun assertSurface(scale: Float, x: Float, y: Float) {
        assertEquals(scale, frame.scaleX, 0.001f)
        assertEquals(scale, frame.scaleY, 0.001f)
        assertEquals(x, frame.translationX, 0.5f)
        assertEquals(y, frame.translationY, 0.5f)
    }

    /**
     * One pinch from a 100px span to [scale] times it. Without [around] the
     * first finger stays at (500, 500); otherwise the fingers are centered on
     * [around] and then on [to].
     */
    private fun pinch(
        scale: Float,
        cancelled: Boolean = false,
        around: Pair<Float, Float>? = null,
        to: Pair<Float, Float>? = around,
    ) {
        beginPinch(100f, around)
        updatePinch(100f * scale, to)
        finishPinch(cancelled)
    }

    private fun beginPinch(span: Float, center: Pair<Float, Float>?) {
        ReflectionHelpers.setField(fragment, "pinchPointerId1", 0)
        ReflectionHelpers.setField(fragment, "pinchPointerId2", 1)
        ReflectionHelpers.setField(fragment, "pinchAnchorSpan", span)
        twoFingers(span, center) { event ->
            PlayerFragment::class.java.getDeclaredMethod(
                "beginPinch", Boolean::class.javaPrimitiveType, MotionEvent::class.java,
            ).apply { isAccessible = true }.invoke(fragment, false, event)
        }
    }

    private fun updatePinch(span: Float, center: Pair<Float, Float>?) {
        twoFingers(span, center) { event ->
            PlayerFragment::class.java.getDeclaredMethod("updatePinch", MotionEvent::class.java)
                .apply { isAccessible = true }.invoke(fragment, event)
        }
    }

    private fun finishPinch(cancelled: Boolean) {
        PlayerFragment::class.java.getDeclaredMethod("finishPinch", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(fragment, cancelled)
        idle()
    }

    private fun twoFingers(span: Float, center: Pair<Float, Float>?, block: (MotionEvent) -> Unit) {
        val properties = Array(2) { id -> MotionEvent.PointerProperties().apply { this.id = id } }
        val left = center?.first?.minus(span / 2f) ?: 500f
        val top = center?.second ?: 500f
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = left; y = top },
            MotionEvent.PointerCoords().apply { x = left + span; y = top },
        )
        val event = MotionEvent.obtain(0, 10, MotionEvent.ACTION_MOVE, 2, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        try {
            block(event)
        } finally {
            event.recycle()
        }
    }

    /**
     * One sequence through the detector callbacks the touch listener feeds.
     * Each step lists the fingers down. Like the real detector, which never
     * sees a finger land or lift, the scroll distance follows the fingers'
     * midpoint from the previous step.
     */
    private fun drag(vararg steps: List<Pair<Float, Float>>) {
        arbiter.onSequenceStarted()
        val (startX, startY) = steps.first().first()
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, startX, startY, 0)
        try {
            listener.onDown(down)
            var lastX = startX
            var lastY = startY
            var lastCount = 1
            steps.forEachIndexed { index, fingers ->
                if (fingers.size > lastCount) arbiter.onPointerAdded(fingers.size)
                if (fingers.size < lastCount) arbiter.onPointerRemoved(fingers.size)
                lastCount = fingers.size
                val focusX = fingers.map { it.first }.average().toFloat()
                val focusY = fingers.map { it.second }.average().toFloat()
                val properties = Array(fingers.size) { id -> MotionEvent.PointerProperties().apply { this.id = id } }
                val coords = Array(fingers.size) { i ->
                    MotionEvent.PointerCoords().apply { x = fingers[i].first; y = fingers[i].second }
                }
                val move = MotionEvent.obtain(0, (index + 1) * 16L, MotionEvent.ACTION_MOVE, fingers.size, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
                try {
                    listener.onScroll(down, move, lastX - focusX, lastY - focusY)
                } finally {
                    move.recycle()
                }
                lastX = focusX
                lastY = focusY
            }
        } finally {
            down.recycle()
        }
        idle()
    }

    private fun call(name: String) {
        PlayerFragment::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(fragment)
    }

    private fun resizePlayer(width: Int, height: Int) {
        binding.playerLayout.layoutParams = FrameLayout.LayoutParams(width, height)
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }
}
