package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.PlayerLayoutBinding
import com.github.andreyasadchy.xtra.ui.UiTestRender
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerLiveRewindControlsTest {
    private fun controls(fontScale: Float = 1f, busy: Boolean = false): PlayerLayoutBinding {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { this.fontScale = fontScale }
        val context = ContextThemeWrapper(app.createConfigurationContext(config), R.style.BaseDarkTheme)
        return PlayerLayoutBinding.inflate(LayoutInflater.from(context)).apply {
            root.visibility = View.VISIBLE
            root.alpha = 1f
            listOf(playPause, restart, seekLive, liveRewind, createClipButton, volume, audioCompressor,
                audioOnly, toggleChatInput, fullscreen, uptimeIcon, viewersIcon).forEach {
                it.imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            listOf(restart, volume, audioCompressor, fullscreen,
                uptimeLayout, uptimeIcon, viewersIcon).forEach { it.visibility = View.VISIBLE }
            listOf(seekLive, audioOnly, toggleChatInput, createClipButton).forEach { it.isVisible = busy }
            uptimeTimer.text = "7:19:43"
            viewersText.text = "2.9K"
            listOf(position, duration, progressBar, rewind, fastForward).forEach { it.visibility = View.GONE }
        }
    }

    private fun layout(controls: PlayerLayoutBinding, width: Int, height: Int) {
        controls.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        controls.root.layout(0, 0, width, height)
    }

    private fun bounds(controls: PlayerLayoutBinding, view: View) = Rect().also {
        view.getDrawingRect(it)
        controls.root.offsetDescendantRectToMyCoords(view, it)
    }

    private fun viewport(controls: PlayerLayoutBinding) = controls.bottomLeftLayout.parent.parent as HorizontalScrollView

    private fun assertSingleRow(controls: PlayerLayoutBinding) {
        val left = bounds(controls, controls.bottomLeftLayout)
        val right = bounds(controls, controls.bottomRightLayout)
        val info = bounds(controls, controls.streamInfoLayout)
        val bar = bounds(controls, viewport(controls))
        assertTrue(abs(left.centerY() - right.centerY()) <= 1)
        assertTrue(abs(info.centerY() - left.centerY()) <= 1)
        assertEquals(controls.root.height - 5, bar.bottom)
        assertEquals(0f, controls.playPause.translationY)
        assertEquals(1, controls.uptimeTimer.lineCount)
        assertEquals(1, controls.viewersText.lineCount)
    }

    @Test fun `fresh default controls and stats fit one bottom row without scrolling in RTL too`() {
        for (width in listOf(360, 412)) for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            val controls = controls()
            controls.root.layoutDirection = direction
            controls.bindLiveRewindButton(true, true) {}
            layout(controls, width, width * 9 / 16)
            assertSingleRow(controls)
            assertFalse(viewport(controls).canScrollHorizontally(-1))
            assertFalse(viewport(controls).canScrollHorizontally(1))
            assertEquals(5, controls.root.height - bounds(controls, controls.bottomLeftLayout).bottom)
            val info = bounds(controls, controls.streamInfoLayout)
            val bar = bounds(controls, viewport(controls))
            assertTrue(info.left >= bar.left && info.right <= bar.right)
            UiTestRender.save(controls.root, "live-rewind-default-$width-$direction")
        }
    }

    @Test fun `all optional actions and large text stay on one scrollable row with fullscreen pinned`() {
        for (width in listOf(320, 360)) for (scale in listOf(1f, 2f)) for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            val controls = controls(scale, busy = true)
            controls.root.layoutDirection = direction
            controls.uptimeTimer.text = "12:34:56"
            controls.viewersText.text = "123.4K"
            var clicks = 0
            controls.bindLiveRewindButton(true, true) { clicks++ }
            layout(controls, width, width * 9 / 16)
            assertSingleRow(controls)
            val bar = viewport(controls)
            assertTrue(bar.canScrollHorizontally(-1) || bar.canScrollHorizontally(1))
            assertEquals(40, controls.createClipButton.width)
            assertTrue(controls.liveRewind.performClick())
            assertEquals(1, clicks)
            val pinned = bounds(controls, controls.bottomRightLayout)
            bar.scrollTo(if (direction == View.LAYOUT_DIRECTION_RTL) 0 else bar.getChildAt(0).width, 0)
            assertEquals(pinned, bounds(controls, controls.bottomRightLayout))
            val text = bounds(controls, controls.viewersText)
            val area = bounds(controls, bar)
            assertTrue("viewer label remains reachable: $width/$scale/$direction, $text in $area, scroll=${bar.scrollX}", text.left >= area.left && text.right <= area.right)
            UiTestRender.save(controls.root, "live-rewind-overflow-$width-$scale-$direction")
        }
    }

    @Test fun `later stats changes grow horizontal overflow without lifting controls or rebinding`() {
        val controls = controls()
        controls.bindLiveRewindButton(true, true) {}
        layout(controls, 360, 203)
        val y = bounds(controls, controls.bottomLeftLayout).top
        controls.viewersText.text = "123,456,789"
        layout(controls, 360, 203)
        assertSingleRow(controls)
        assertEquals(y, bounds(controls, controls.bottomLeftLayout).top)
        assertTrue(viewport(controls).canScrollHorizontally(1))
        controls.viewersText.text = "527"
        layout(controls, 360, 203)
        assertSingleRow(controls)
        assertFalse(viewport(controls).canScrollHorizontally(1))
    }

    @Test fun `orientation restores original parents dimensions and rules without accumulating bars`() {
        val controls = controls()
        val count = controls.root.childCount
        for (attempt in 0..2) {
            controls.bindLiveRewindButton(true, true) {}
            controls.bindLiveRewindButton(true, true) {}
            layout(controls, 360, 203)
            assertEquals(count - 1, controls.root.childCount)
            assertEquals(18, controls.viewersIcon.width)
            controls.bindLiveRewindButton(true, false) {}
            layout(controls, 800, 450)
            assertEquals(count, controls.root.childCount)
            assertSame(controls.root, controls.bottomLeftLayout.parent)
            assertSame(controls.root, controls.streamInfoLayout.parent)
            assertEquals(30, controls.viewersIcon.width)
            assertEquals(0f, controls.playPause.translationY)
            assertEquals(controls.bottomLayout.id, (controls.bottomLeftLayout.layoutParams as RelativeLayout.LayoutParams).getRule(RelativeLayout.ABOVE))
        }
        UiTestRender.save(controls.root, "live-rewind-landscape")
        controls.bindLiveRewindButton(false, true) { fail("Hidden action must not execute") }
        layout(controls, 360, 203)
        assertEquals(View.GONE, controls.liveRewind.visibility)
        assertFalse(controls.liveRewind.hasOnClickListeners())
        assertSame(controls.root, controls.bottomLeftLayout.parent)
    }

    @Test fun `new orientation wins before the first portrait layout`() {
        val controls = controls()
        controls.bindLiveRewindButton(true, true) {}
        controls.bindLiveRewindButton(true, false) {}
        layout(controls, 800, 450)
        assertEquals(0f, controls.playPause.translationY)
        assertSame(controls.root, controls.bottomLeftLayout.parent)
    }

    @Test fun `missing stats leave one bottom row without an empty second row`() {
        val controls = controls()
        controls.uptimeLayout.isVisible = false
        controls.viewersIcon.isVisible = false
        controls.viewersText.text = null
        controls.createClipButton.isVisible = true
        controls.bindLiveRewindButton(true, true) {}
        layout(controls, 320, 180)
        assertEquals(0f, controls.playPause.translationY)
        assertEquals(175, bounds(controls, controls.bottomLeftLayout).bottom)
        assertEquals(40, controls.createClipButton.width)
    }

    @Test
    @Config(sdk = [23])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun `Android 6 attaches and restores the row without using newer gravity accessors`() {
        val controls = controls()
        var clicks = 0
        controls.bindLiveRewindButton(true, true) { clicks++ }
        layout(controls, 360, 203)
        assertTrue(controls.liveRewind.performClick())
        assertEquals(1, clicks)
        assertEquals(0f, controls.playPause.translationY)
        controls.bindLiveRewindButton(true, false) {}
        layout(controls, 800, 450)
        assertSame(controls.root, controls.bottomLeftLayout.parent)
        assertSame(controls.root, controls.streamInfoLayout.parent)
        assertEquals(30, controls.viewersIcon.width)
    }
}
