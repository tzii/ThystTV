package com.github.andreyasadchy.xtra.ui.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.UiTestRender
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [28], qualifiers = "w1280dp-h720dp-mdpi")
class PlayerGestureFeedbackRenderTest {
    private lateinit var activity: ActivityController<Activity>
    private val context: Context get() = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
    private val hide = Runnable {}

    @Before fun attachWindow() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
    }

    @After fun closeWindow() {
        activity.pause().stop().destroy()
    }

    @Test fun `tablet edge sliders mirror their margins and carry icon and level`() {
        // Back-gesture bands of different widths still give one shared distance.
        val edges = PlayerFeedbackEdges(unsafeLeftPx = 24, unsafeRightPx = 40)
        for (kind in listOf(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackKind.DEVICE_VOLUME)) {
            val (surface, feedback) = createSurface()
            present(surface, feedback, kind, 1280, 720, edges)
            val slider = feedback.findViewById<PlayerGestureLevelView>(R.id.feedbackLevel)
            assertOnlyShown(feedback, R.id.feedbackLevel)
            // 44 x 176 dp plus 4 px of pad on each side.
            assertEquals(52, slider.width)
            assertEquals(184, slider.height)
            assertEquals(65f, slider.level, 0f)
            assertEquals(PlayerGestureLevelView.IconPlace.IN_FILL, slider.iconPlace())
            assertTrue(slider.stopDotVisible())
            assertEquals("${kind.name} 65%", feedback.contentDescription)
            assertEquals((720 - slider.height) / 2, feedback.top + slider.top)
            // 40 px band + 16 px margin from each physical side, to the drawn track.
            if (kind == PlayerGestureFeedbackKind.BRIGHTNESS) {
                assertEquals(56f, slider.left + slider.trackBounds().left, 0f)
            } else {
                assertEquals(56f, 1280 - (slider.left + slider.trackBounds().right), 0f)
            }
            assertSettled(slider)
            UiTestRender.save(surface, "gesture-tablet-${kind.name.lowercase()}")
        }
    }

    @Test fun `landscape phone player beside the side chat keeps vertical sliders`() {
        // 560 x 360 dp player: the side chat takes the rest of a landscape phone.
        for (kind in listOf(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackKind.DEVICE_VOLUME)) {
            val (surface, feedback) = createSurface()
            present(surface, feedback, kind, 560, 360, PlayerFeedbackEdges(surfaceRightPx = 360, unsafeLeftPx = 24, unsafeRightPx = 24))
            val slider = feedback.findViewById<PlayerGestureLevelView>(R.id.feedbackLevel)
            assertOnlyShown(feedback, R.id.feedbackLevel)
            assertEquals(PlayerSurfacePolicy.edgeSliderHeightPx(1f, 360).toFloat(), slider.trackBounds().height(), 0f)
            // The chat edge is inside the window, so both mirror around the player.
            if (kind == PlayerGestureFeedbackKind.BRIGHTNESS) {
                assertEquals(40f, slider.left + slider.trackBounds().left, 0f)
            } else {
                assertEquals(40f, 560 - (slider.left + slider.trackBounds().right), 0f)
            }
            UiTestRender.save(surface, "gesture-side-chat-${kind.name.lowercase()}")
        }
    }

    @Test fun `cutout padding leaves both sliders the same distance from the screen edge`() {
        // Landscape window 1280 px wide; a 110 px cutout on the left pads the
        // player, so the player surface is 1170 px wide starting at x = 110.
        val window = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val (surface, feedback) = createSurface(attach = false)
        window.addView(surface, FrameLayout.LayoutParams(1170, 720).apply { leftMargin = 110 })
        activity.get().setContentView(window)
        val edges = PlayerFeedbackEdges(surfaceLeftPx = 110, surfaceRightPx = 0, unsafeLeftPx = 110, unsafeRightPx = 48)
        val fromScreenEdge = mutableListOf<Float>()
        for (kind in listOf(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackKind.DEVICE_VOLUME)) {
            showIn(window, feedback, kind, 1170, 720, edges, windowWidth = 1280)
            val slider = feedback.findViewById<PlayerGestureLevelView>(R.id.feedbackLevel)
            fromScreenEdge += if (kind == PlayerGestureFeedbackKind.BRIGHTNESS) {
                110 + slider.left + slider.trackBounds().left
            } else {
                1280 - (110 + slider.left + slider.trackBounds().right)
            }
            UiTestRender.save(window, "gesture-cutout-${kind.name.lowercase()}")
        }
        assertEquals("both sliders the same distance from their screen edge: $fromScreenEdge", 1, fromScreenEdge.toSet().size)
        assertEquals(126f, fromScreenEdge.first(), 0f)
    }

    @Test fun `very short resized players keep the slider and drop the icon last`() {
        val (surface, feedback) = createSurface()
        for ((width, height) in listOf(720 to 96, 360 to 160, 720 to 160, 720 to 240, 560 to 315, 720 to 360, 720 to 720)) {
            present(surface, feedback, PlayerGestureFeedbackKind.DEVICE_VOLUME, width, height)
            val slider = feedback.findViewById<PlayerGestureLevelView>(R.id.feedbackLevel)
            assertOnlyShown(feedback, R.id.feedbackLevel)
            assertTrue(slider.trackBounds().height() <= height * PlayerSurfacePolicy.EDGE_SLIDER_MAX_HEIGHT_FRACTION)
            assertTrue("track remains visible in a $height px player", slider.trackBounds().height() > 0f)
            // The icon needs a 40 dp block; only the tiniest player loses it.
            assertEquals("icon in a $height px player", height >= 160, slider.iconPlace() != PlayerGestureLevelView.IconPlace.NONE)
            assertTrue(feedback.top + slider.top >= 0)
            assertTrue(feedback.top + slider.bottom <= surface.height)
            if (width == 360) UiTestRender.save(surface, "gesture-short-player")
        }
    }

    @Test fun `the icon rides in the fill and moves above the handle when the fill is short`() {
        val (surface, feedback) = createSurface()
        for ((level, place) in listOf(
            0 to PlayerGestureLevelView.IconPlace.ABOVE_HANDLE,
            15 to PlayerGestureLevelView.IconPlace.ABOVE_HANDLE,
            40 to PlayerGestureLevelView.IconPlace.IN_FILL,
            100 to PlayerGestureLevelView.IconPlace.IN_FILL,
        )) {
            present(surface, feedback, PlayerGestureFeedbackKind.DEVICE_VOLUME, 1280, 720, level = level, text = "$level%")
            val slider = feedback.findViewById<PlayerGestureLevelView>(R.id.feedbackLevel)
            val track = slider.trackBounds()
            assertEquals("icon at $level%", place, slider.iconPlace())
            // The stop dot marks the top until the handle reaches it.
            assertEquals("stop dot at $level%", level < 100, slider.stopDotVisible())
            if (level == 0) assertEquals(track.bottom - 2f, slider.handleCenterY(), 0.5f)
            if (level == 100) assertEquals(track.top + 2f, slider.handleCenterY(), 0.5f)
            UiTestRender.save(surface, "gesture-volume-$level")
        }
    }

    @Test fun `a visible slider follows each update at once without a layout pass`() {
        val (surface, feedback) = createSurface()
        present(surface, feedback, PlayerGestureFeedbackKind.DEVICE_VOLUME, 1280, 720, level = 20, text = "20")
        val slider = feedback.findViewById<PlayerGestureLevelView>(R.id.feedbackLevel)
        assertEquals(20f, slider.level, 0f)
        assertFalse(surface.isLayoutRequested)
        // A swipe update between whole percents: the handle follows the
        // reading's fraction, not the rounded level.
        PlayerSurfacePolicy.presentFeedback(
            context, feedback, PlayerGestureFeedbackKind.DEVICE_VOLUME, 1280, 720,
            PlayerGestureFeedbackState.presentation(
                PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.LARGE, 80, "80%",
                reading = PlayerGestureReading(0.803f, "80%", valueIcon = true),
            ),
            R.drawable.baseline_volume_up_black_24, "volume 80", hide,
            edges = PlayerFeedbackEdges.NONE,
        )
        assertEquals(80.3f, slider.level, 0.001f)
        // Same placement while visible: only the drawing changes, so the
        // player window is not laid out again on every touch update.
        assertFalse("an update requests no layout", surface.isLayoutRequested)
        // The entrance does not replay for an update while visible.
        assertEquals(1f, slider.alpha, 0f)
        // Another kind moves the overlay, which does lay it out.
        PlayerSurfacePolicy.presentFeedback(
            context, feedback, PlayerGestureFeedbackKind.BRIGHTNESS, 1280, 720,
            PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 40, "40%"),
            R.drawable.ic_gesture_brightness_half, "brightness 40", hide,
            edges = PlayerFeedbackEdges.NONE,
        )
        assertTrue(surface.isLayoutRequested)
    }

    @Test fun `seek updates at the same place redraw without a layout pass`() {
        val (surface, feedback) = createSurface()
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720, seekForward)
        assertFalse(surface.isLayoutRequested)
        PlayerSurfacePolicy.presentFeedback(
            context, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720,
            PlayerGestureFeedbackState.presentation(
                PlayerGestureFeedbackKind.SEEK, PlayerSurfaceClass.LARGE,
                reading = PlayerGestureReading(
                    0.7f, "+12:00", startFraction = 0.5f, detail = "27:00 / 3:12:45", caption = "27:00 / 3:12:45",
                ),
            ),
            R.drawable.ic_gesture_seek_forward, "seek +12:00", hide,
            edges = PlayerFeedbackEdges.NONE,
        )
        val slider = feedback.findViewById<PlayerGestureSliderView>(R.id.feedbackSlider)
        assertEquals(0.7f, slider.fraction, 0f)
        assertEquals("+12:00", slider.label)
        // A new caption text redraws; only a caption appearing or going lays out.
        assertEquals("27:00 / 3:12:45", slider.caption)
        assertFalse("an update requests no layout", surface.isLayoutRequested)
    }

    @Test fun `a caption appearing or going lays the slider out again`() {
        val slider = PlayerGestureSliderView(context)
        slider.setReading(0.5f, 0.5f, "1.00x")
        slider.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        slider.layout(0, 0, slider.measuredWidth, slider.measuredHeight)
        val plain = slider.measuredHeight
        assertFalse(slider.isLayoutRequested)
        slider.setReading(0.5f, 0.5f, "+0:00", "15:00 / 3:12:45")
        assertTrue("caption appears", slider.isLayoutRequested)
        slider.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        slider.layout(0, 0, slider.measuredWidth, slider.measuredHeight)
        assertEquals(plain + PlayerGestureSliderView.CAPTION_BAND_DP.toInt(), slider.measuredHeight)
        slider.setReading(0.6f, 0.5f, "+1:00", "16:00 / 3:12:45")
        assertFalse("new caption text", slider.isLayoutRequested)
        slider.setReading(0.6f, 0.5f, "1.25x")
        assertTrue("caption goes", slider.isLayoutRequested)
    }

    @Test fun `fresh edge slider springs out of its side and lands on its final state`() {
        val (surface, feedback) = createSurface()
        PlayerSurfacePolicy.presentFeedback(
            context, feedback, PlayerGestureFeedbackKind.BRIGHTNESS, 1280, 720,
            PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 50, "50%"),
            R.drawable.ic_gesture_brightness_half, "brightness 50%", hide,
            edges = PlayerFeedbackEdges.NONE,
        )
        measure(surface, 1280, 720)
        val slider = feedback.findViewById<PlayerGestureLevelView>(R.id.feedbackLevel)
        assertEquals(0f, slider.alpha, 0f)
        assertEquals(PlayerSurfacePolicy.ENTRANCE_SCALE, slider.scaleX, 0f)
        // Brightness grows out of the left edge.
        assertEquals(0f, slider.pivotX, 0f)
        settle()
        assertSettled(slider)
    }

    @Test fun `the number style shows brightness and volume in the center with their icon`() {
        for (kind in listOf(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackKind.DEVICE_VOLUME)) {
            val (surface, feedback) = createSurface()
            presentReading(
                surface, feedback, kind, 1280, 720,
                PlayerGestureReading(0.65f, "65%", valueIcon = true),
                PlayerGestureFeedbackStyle.NUMBER,
                icon = if (kind == PlayerGestureFeedbackKind.BRIGHTNESS) R.drawable.ic_gesture_brightness_half else R.drawable.baseline_volume_up_black_24,
            )
            val value = feedback.findViewById<PlayerGestureValueView>(R.id.feedbackValue)
            assertOnlyShown(feedback, R.id.feedbackValue)
            assertEquals("65%", value.value)
            assertEquals(null, value.detail)
            assertTrue(value.hasIcon)
            assertCentered(value, feedback, 1280, 720)
            assertContentInside(value)
            UiTestRender.save(surface, "gesture-number-${kind.name.lowercase()}")
        }
    }

    @Test fun `pinch hint pill hands the label back to the next pill`() {
        val (surface, feedback) = createSurface()
        // The pinch hint is a plain message, so it keeps the pill and its fixed label slot.
        present(surface, feedback, PlayerGestureFeedbackKind.PINCH, 1280, 720, text = "Fit")
        val text = feedback.findViewById<TextView>(R.id.feedbackText)
        assertEquals(View.VISIBLE, feedback.findViewById<View>(R.id.feedbackContainer).visibility)
        assertEquals(Gravity.CENTER, text.gravity)
        assertEquals(PlayerSurfacePolicy.pinchLabelSlotWidth(context, text), text.width)

        present(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720)
        assertEquals(0, text.minWidth)
        assertEquals("tnum", text.fontFeatureSettings)
        assertEquals(Gravity.END, text.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
        assertEquals(text.paint.measureText("65%"), text.width.toFloat(), 2f)
        UiTestRender.save(surface, "gesture-message-pill")
    }

    @Test fun `same overlay restores pill geometry after resize and other gestures`() {
        val (surface, feedback) = createSurface()
        present(surface, feedback, PlayerGestureFeedbackKind.BRIGHTNESS, 1280, 720)
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720, seekForward)
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720, seekForward, PlayerGestureFeedbackStyle.NUMBER)
        for ((width, height, kind) in listOf(
            Triple(360, 203, PlayerGestureFeedbackKind.PINCH),
            Triple(560, 315, PlayerGestureFeedbackKind.PINCH),
            Triple(1280, 720, PlayerGestureFeedbackKind.SEEK),
        )) {
            present(surface, feedback, kind, width, height)
            val pill = feedback.findViewById<LinearLayout>(R.id.feedbackContainer)
            val badge = feedback.findViewById<View>(R.id.feedbackIconBadge)
            val icon = feedback.findViewById<ImageView>(R.id.feedbackIcon)
            val level = feedback.findViewById<LinearProgressIndicator>(R.id.feedbackProgress)
            assertOnlyShown(feedback, R.id.feedbackContainer)
            assertEquals(LinearLayout.HORIZONTAL, pill.orientation)
            assertEquals(32, badge.width)
            assertEquals(32, badge.height)
            assertEquals(20, icon.width)
            assertEquals(24, feedback.paddingTop)
            assertEquals("65%", feedback.findViewById<TextView>(R.id.feedbackText).text.toString())
            assertSettled(pill)
            if (kind != PlayerGestureFeedbackKind.SEEK) {
                assertEquals(96, level.width)
                assertEquals(6, level.trackThickness)
                assertEquals(View.VISIBLE, level.visibility)
                assertTrue("attached compact indicator draws its track", level.progressDrawable?.isVisible == true)
                assertEquals(48, pill.height)
            } else {
                assertEquals(View.GONE, level.visibility)
            }
            assertContentsFit(pill)
            if (width == 360) UiTestRender.save(surface, "gesture-compact-phone")
            if (width == 560) UiTestRender.save(surface, "gesture-short-narrow-player")
        }
    }

    @Test fun `overlay colors stay readable on bright video in every app theme`() {
        for (theme in listOf(R.style.BaseDarkTheme, R.style.BaseLightTheme, R.style.BaseAmoledTheme, R.style.BaseBlueTheme)) {
            val colors = PlayerPanelTheme.gestureFeedback(ContextThemeWrapper(activity.get(), theme))
            // Worst case for light content: the container over white video.
            val overWhite = ColorUtils.compositeColors(colors.container, Color.WHITE)
            assertTrue("content on container ($theme)", ColorUtils.calculateContrast(colors.content, overWhite) >= 7.0)
            assertTrue("icon on accent ($theme)", ColorUtils.calculateContrast(colors.onAccent, colors.accent) >= 4.5)
            assertTrue("accent against container ($theme)", ColorUtils.calculateContrast(colors.accent, overWhite) >= 3.0)
            // The centered number and its dimmer small line on the glow's
            // center, over white video.
            val glowOverWhite = ColorUtils.compositeColors(PlayerGestureValueView.GLOW_CENTER, Color.WHITE)
            assertTrue("number on glow ($theme)", ColorUtils.calculateContrast(colors.content, glowOverWhite) >= 4.5)
            val detail = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(colors.content, PlayerGestureValueView.DETAIL_ALPHA), glowOverWhite)
            assertTrue("small line on glow ($theme)", ColorUtils.calculateContrast(detail, glowOverWhite) >= 4.5)
        }
    }

    @Test fun `pill uses the overlay palette`() {
        val (surface, feedback) = createSurface()
        present(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720)
        val colors = PlayerPanelTheme.gestureFeedback(context)
        val icon = feedback.findViewById<ImageView>(R.id.feedbackIcon)
        assertEquals(colors.onAccent, ImageViewCompat.getImageTintList(icon)?.defaultColor)
        val badge = feedback.findViewById<View>(R.id.feedbackIconBadge).background as GradientDrawable
        assertEquals(colors.accent, badge.color?.defaultColor)
        val pill = feedback.findViewById<View>(R.id.feedbackContainer).background as GradientDrawable
        assertEquals(colors.container, pill.color?.defaultColor)
        assertFalse(colors.content == colors.container)
    }

    @Test fun `seek slider sits at the top center with the change and the start dot`() {
        val (surface, feedback) = createSurface()
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720, seekForward)
        val slider = feedback.findViewById<PlayerGestureSliderView>(R.id.feedbackSlider)
        assertOnlyShown(feedback, R.id.feedbackSlider)
        // 300 x 44 dp plus 4 px of pad, and the 30 dp caption band; the
        // track starts 16 px below the top.
        assertEquals(308, slider.width)
        assertEquals(82, slider.height)
        assertEquals(16f, feedback.top + slider.top + 4f, 0f)
        assertEquals(26f, slider.trackCenterY(), 0f)
        // The target time and the length, in a pill centered under the track.
        assertEquals("24:00 / 3:12:45", slider.caption)
        val caption = slider.captionBounds()!!
        assertEquals((slider.trackLeft() + slider.trackRight()) / 2f, caption.centerX(), 0.5f)
        assertEquals(4f + 44f + 6f, caption.top, 0.5f)
        assertTrue("caption inside the view", caption.bottom <= slider.height - 4f + 0.5f)
        assertTrue("caption within the track's width", caption.left >= slider.trackLeft() && caption.right <= slider.trackRight())
        assertEquals(640f, feedback.left + slider.left + slider.width / 2f, 0.5f)
        assertEquals("+9:00", slider.label)
        val track = slider.trackRight() - slider.trackLeft()
        assertEquals(slider.trackLeft() + (track - 4f) * 0.62f + 2f, slider.handleCenterX(), 0.5f)
        val dot = slider.startDotX()
        assertTrue("start dot shown", dot != null)
        assertEquals(slider.trackLeft() + (track - 4f) * 0.5f + 2f, dot!!, 0.5f)
        assertTrue("start dot left of a forward handle", dot < slider.handleCenterX())
        // Icon at the start of the fill, the change at the end of the track.
        assertEquals(PlayerGestureSliderView.Side.ACTIVE, slider.iconSide())
        assertEquals(PlayerGestureSliderView.Side.INACTIVE, slider.labelSide())
        assertEquals(slider.trackRight() - 12f, slider.labelBounds()!!.second, 0.5f)
        assertSettled(slider)
        UiTestRender.save(surface, "gesture-seek-slider")

        // Dragging back: the handle crosses to the left of the start dot.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720,
            PlayerGestureReading(0.36f, "−6:00", startFraction = 0.5f, detail = "18:00"), icon = R.drawable.ic_gesture_seek_back)
        assertTrue(slider.handleCenterX() < slider.startDotX()!!)
        assertEquals(308, slider.width)
        UiTestRender.save(surface, "gesture-seek-slider-back")

        // Back at the start: the handle covers the dot and the slider says
        // Cancel; the caption still shows where that is.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720,
            PlayerGestureReading(0.5f, null, startFraction = 0.5f, detail = "Cancel", valueIcon = true, caption = "15:00 / 3:12:45"),
            icon = R.drawable.ic_gesture_undo)
        assertEquals(null, slider.startDotX())
        assertEquals("Cancel", slider.label)
        assertEquals("15:00 / 3:12:45", slider.caption)
        UiTestRender.save(surface, "gesture-seek-slider-cancel")
    }

    @Test fun `the value moves into the fill near the end and never meets the icon`() {
        val (surface, feedback) = createSurface()
        val slider = feedback.findViewById<PlayerGestureSliderView>(R.id.feedbackSlider)
        for (fraction in listOf(0f, 0.05f, 0.3f, 0.5f, 0.8f, 0.95f, 1f)) {
            presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720,
                PlayerGestureReading(fraction, "+1:02:10", startFraction = 0.5f, detail = "3:02:10"))
            val label = slider.labelBounds()!!
            assertTrue("value inside the track at $fraction", label.first >= slider.trackLeft() && label.second <= slider.trackRight())
            // The value never sits under the handle.
            val handle = slider.handleCenterX()
            assertTrue("value clear of the handle at $fraction", label.second < handle - 2f || label.first > handle + 2f)
            slider.iconBounds()?.let { icon ->
                assertTrue("icon clear of the value at $fraction", icon.second <= label.first || icon.first >= label.second)
                assertTrue("icon clear of the handle at $fraction", icon.second < handle - 2f || icon.first > handle + 2f)
            }
            if (fraction >= 0.95f) assertEquals(PlayerGestureSliderView.Side.ACTIVE, slider.labelSide())
            if (fraction == 0f) assertEquals(PlayerGestureSliderView.Side.INACTIVE, slider.iconSide())
            if (fraction == 0.95f) UiTestRender.save(surface, "gesture-seek-slider-end")
        }
    }

    @Test fun `speed and pinch share the seek slider's place and size`() {
        val (surface, feedback) = createSurface()
        val slider = feedback.findViewById<PlayerGestureSliderView>(R.id.feedbackSlider)
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720, seekForward)
        // Seek is taller only by its caption: the track stays in one place.
        val seekBounds = listOf(slider.left, slider.top, slider.width, slider.height - 30)
        presentReading(surface, feedback, PlayerGestureFeedbackKind.PLAYBACK_SPEED, 1280, 720,
            PlayerGestureReading(PlayerScrubPolicy.speedFraction(1.5f), "1.50x", startFraction = PlayerScrubPolicy.speedFraction(1f)),
            icon = R.drawable.baseline_speed_black_24)
        assertOnlyShown(feedback, R.id.feedbackSlider)
        assertEquals("1.50x", slider.label)
        assertEquals(null, slider.caption)
        assertEquals(null, slider.captionBounds())
        assertTrue(slider.handleCenterX() > slider.startDotX()!!)
        assertEquals(seekBounds, listOf(slider.left, slider.top, slider.width, slider.height))
        UiTestRender.save(surface, "gesture-speed-slider")

        for ((fraction, label) in listOf(0f to "Fit", 0.31f to "134%", 0.5f to "150%", 1f to "Fill")) {
            PlayerSurfacePolicy.presentFeedback(
                context, feedback, PlayerGestureFeedbackKind.PINCH, 1280, 720,
                PlayerGestureFeedbackState.pinchZoomPresentation(PlayerSurfaceClass.LARGE, fraction, label, startFraction = 0.31f),
                R.drawable.baseline_aspect_ratio_black_24, "Pinch · $label", hide,
                edges = PlayerFeedbackEdges.NONE,
            )
            measure(surface, 1280, 720)
            settle()
            measure(surface, 1280, 720)
            assertOnlyShown(feedback, R.id.feedbackSlider)
            assertEquals(label, slider.label)
            assertEquals(seekBounds, listOf(slider.left, slider.top, slider.width, slider.height))
            if (label == "134%") UiTestRender.save(surface, "gesture-pinch-slider")
        }
    }

    @Test fun `short players get the compact slider and narrow ones keep the margins`() {
        val (surface, feedback) = createSurface()
        val slider = feedback.findViewById<PlayerGestureSliderView>(R.id.feedbackSlider)
        // A short split-screen player.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 720, 240, seekForward)
        assertEquals(PlayerGestureSliderView.COMPACT_WIDTH_DP.toInt() + 8, slider.width)
        val compactCaption = slider.captionBounds()!!
        assertTrue(compactCaption.left >= slider.trackLeft() && compactCaption.right <= slider.trackRight())
        assertTrue("caption above the player's bottom", feedback.top + slider.top + compactCaption.bottom <= 240f)
        UiTestRender.save(surface, "gesture-seek-slider-compact")

        // A window narrower than the slider plus its margins narrows it further,
        // and a long value shrinks to fit its part.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 160, 200,
            PlayerGestureReading(0.7f, "+10:02:10", startFraction = 0.5f, detail = "10:12:10 / 12:00:00", caption = "10:12:10 / 12:00:00"))
        assertEquals(160 - 32f, slider.trackRight() - slider.trackLeft(), 0f)
        assertTrue(slider.left + slider.trackLeft() >= 16f)
        assertTrue(slider.left + slider.trackRight() <= 160f - 16f)
        val label = slider.labelBounds()!!
        assertTrue(label.first >= slider.trackLeft() && label.second <= slider.trackRight())
        // A caption wider than the track shrinks to fit it.
        val narrowCaption = slider.captionBounds()!!
        assertTrue(narrowCaption.left >= slider.trackLeft() - 0.5f && narrowCaption.right <= slider.trackRight() + 0.5f)
        UiTestRender.save(surface, "gesture-seek-slider-narrow")

        // A large player again restores the regular slider.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720, seekForward)
        assertEquals(308, slider.width)
    }

    @Test fun `top slider drops in from above and clears after hiding`() {
        val (surface, feedback) = createSurface()
        PlayerSurfacePolicy.presentFeedback(
            context, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720,
            PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.SEEK, PlayerSurfaceClass.LARGE, reading = seekForward),
            R.drawable.ic_gesture_seek_forward, "seek", hide,
            edges = PlayerFeedbackEdges.NONE,
        )
        measure(surface, 1280, 720)
        val slider = feedback.findViewById<PlayerGestureSliderView>(R.id.feedbackSlider)
        assertEquals(0f, slider.alpha, 0f)
        assertEquals(PlayerSurfacePolicy.ENTRANCE_SCALE, slider.scaleX, 0f)
        assertEquals(-PlayerSurfacePolicy.ENTRANCE_SLIDE_DP.toFloat(), slider.translationY, 0f)
        settle()
        assertSettled(slider)
        assertEquals(0f, slider.translationY, 0f)
        // A later update while visible moves the handle without replaying it.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720,
            PlayerGestureReading(0.7f, "+12:00", startFraction = 0.5f, detail = "36:00"))
        assertSettled(slider)
        assertEquals(0.7f, slider.fraction, 0f)
        // After hiding, nothing carries into the next gesture.
        PlayerSurfacePolicy.resetFeedback(feedback)
        assertEquals(null, slider.label)
        assertEquals(null, slider.startFraction)
        assertEquals(null, slider.caption)
    }

    @Test fun `the number style centers the seek change with the target below it`() {
        val (surface, feedback) = createSurface()
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720, seekForward, PlayerGestureFeedbackStyle.NUMBER)
        val value = feedback.findViewById<PlayerGestureValueView>(R.id.feedbackValue)
        assertOnlyShown(feedback, R.id.feedbackValue)
        assertFalse(value.compact)
        assertEquals(300, value.width)
        assertEquals(180, value.height)
        assertCentered(value, feedback, 1280, 720)
        assertEquals("+9:00", value.value)
        assertEquals("24:00 / 3:12:45", value.detail)
        assertFalse("seek shows no icon", value.hasIcon)
        assertContentInside(value)
        assertSettled(value)
        UiTestRender.save(surface, "gesture-number-seek")

        // Back at the start: the undo icon over Cancel.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720,
            PlayerGestureReading(0.5f, null, startFraction = 0.5f, detail = "Cancel", valueIcon = true),
            PlayerGestureFeedbackStyle.NUMBER, icon = R.drawable.ic_gesture_undo)
        assertEquals(null, value.value)
        assertEquals("Cancel", value.detail)
        assertTrue(value.hasIcon)
        assertContentInside(value)
        UiTestRender.save(surface, "gesture-number-cancel")

        // Speed: just the speed.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.PLAYBACK_SPEED, 1280, 720,
            PlayerGestureReading(PlayerScrubPolicy.speedFraction(1.5f), "1.50x", startFraction = PlayerScrubPolicy.speedFraction(1f)),
            PlayerGestureFeedbackStyle.NUMBER, icon = R.drawable.baseline_speed_black_24)
        assertEquals("1.50x", value.value)
        assertEquals(null, value.detail)
        assertFalse(value.hasIcon)
        assertCentered(value, feedback, 1280, 720)
        UiTestRender.save(surface, "gesture-number-speed")
    }

    @Test fun `the number shrinks on small players and long values fit`() {
        val (surface, feedback) = createSurface()
        val value = feedback.findViewById<PlayerGestureValueView>(R.id.feedbackValue)
        // Portrait-sized player: the compact number.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 412, 232, seekForward, PlayerGestureFeedbackStyle.NUMBER)
        assertTrue(value.compact)
        assertEquals(220, value.width)
        assertEquals(132, value.height)
        assertCentered(value, feedback, 412, 232)
        assertContentInside(value)
        UiTestRender.save(surface, "gesture-number-compact")

        // A tiny window: the glow stays inside the player and the value shrinks.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 160, 120,
            PlayerGestureReading(0.7f, "+10:02:10", startFraction = 0.5f, detail = "10:12:10"), PlayerGestureFeedbackStyle.NUMBER)
        assertEquals(160, value.width)
        assertEquals(120, value.height)
        assertTrue(value.valueDrawnWidth() <= value.textRoom() + 0.5f)
        assertTrue(value.detailDrawnWidth() <= value.textRoom() + 0.5f)

        // A large player again restores the regular number.
        presentReading(surface, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720, seekForward, PlayerGestureFeedbackStyle.NUMBER)
        assertFalse(value.compact)
        assertEquals(300, value.width)
    }

    @Test fun `the number grows from its center and clears after hiding`() {
        val (surface, feedback) = createSurface()
        PlayerSurfacePolicy.presentFeedback(
            context, feedback, PlayerGestureFeedbackKind.SEEK, 1280, 720,
            PlayerGestureFeedbackState.presentation(
                PlayerGestureFeedbackKind.SEEK, PlayerSurfaceClass.LARGE, reading = seekForward, style = PlayerGestureFeedbackStyle.NUMBER,
            ),
            R.drawable.ic_gesture_seek_forward, "seek", hide,
            edges = PlayerFeedbackEdges.NONE,
        )
        measure(surface, 1280, 720)
        val value = feedback.findViewById<PlayerGestureValueView>(R.id.feedbackValue)
        assertEquals(0f, value.alpha, 0f)
        assertEquals(PlayerSurfacePolicy.ENTRANCE_SCALE, value.scaleX, 0f)
        assertEquals(0f, value.translationY, 0f)
        assertEquals(value.width / 2f, value.pivotX, 0.5f)
        assertEquals(value.height / 2f, value.pivotY, 0.5f)
        settle()
        assertSettled(value)
        PlayerSurfacePolicy.resetFeedback(feedback)
        assertEquals(null, value.value)
        assertEquals(null, value.detail)
        assertFalse(value.hasIcon)
    }

    private val seekForward = PlayerGestureReading(
        0.62f, "+9:00", startFraction = 0.5f, detail = "24:00 / 3:12:45", caption = "24:00 / 3:12:45",
    )

    private fun presentReading(
        surface: FrameLayout,
        feedback: View,
        kind: PlayerGestureFeedbackKind,
        width: Int,
        height: Int,
        reading: PlayerGestureReading,
        style: PlayerGestureFeedbackStyle = PlayerGestureFeedbackStyle.SLIDERS,
        icon: Int = R.drawable.ic_gesture_seek_forward,
    ) {
        PlayerSurfacePolicy.presentFeedback(
            context, feedback, kind, width, height,
            PlayerGestureFeedbackState.presentation(kind, PlayerSurfacePolicy.classify(width, 1f), reading = reading, style = style),
            icon, "${kind.name} ${reading.value}", hide,
            edges = PlayerFeedbackEdges.NONE,
        )
        measure(surface, width, height)
        settle()
        measure(surface, width, height)
    }

    /** Exactly one of the overlay's indicators is shown. */
    private fun assertOnlyShown(feedback: View, shown: Int) {
        for (id in listOf(R.id.feedbackContainer, R.id.feedbackLevel, R.id.feedbackSlider, R.id.feedbackValue)) {
            assertEquals(
                feedback.resources.getResourceEntryName(id),
                if (id == shown) View.VISIBLE else View.GONE,
                feedback.findViewById<View>(id).visibility,
            )
        }
    }

    /** The icon, value and small line fit inside the glow, side insets included. */
    private fun assertContentInside(value: PlayerGestureValueView) {
        val (top, bottom) = value.contentBounds()
        assertTrue("content top $top", top >= 0f)
        assertTrue("content bottom $bottom", bottom <= value.height)
        assertTrue(value.valueDrawnWidth() <= value.textRoom() + 0.5f)
        assertTrue(value.detailDrawnWidth() <= value.textRoom() + 0.5f)
    }

    /** The view's center is the video's center, within a pixel. */
    private fun assertCentered(view: View, feedback: View, width: Int, height: Int) {
        assertEquals("horizontal center", width / 2f, feedback.left + view.left + view.width / 2f, 1f)
        assertEquals("vertical center", height / 2f, feedback.top + view.top + view.height / 2f, 1f)
    }

    private fun createSurface(attach: Boolean = true): Pair<FrameLayout, View> {
        val surface = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(Color.rgb(31, 49, 62), Color.rgb(186, 198, 169)))
        }
        val feedback = LayoutInflater.from(context).inflate(R.layout.layout_player_gesture_feedback, surface, false)
        surface.addView(feedback)
        // Material progress drawables follow real attachment/window visibility.
        // A detached preview otherwise hides the horizontal track despite valid bounds.
        if (attach) activity.get().setContentView(surface)
        return surface to feedback
    }

    private fun present(
        surface: FrameLayout,
        feedback: View,
        kind: PlayerGestureFeedbackKind,
        width: Int,
        height: Int,
        edges: PlayerFeedbackEdges = PlayerFeedbackEdges.NONE,
        level: Int = 65,
        text: String = "65%",
    ) {
        PlayerSurfacePolicy.presentFeedback(
            context, feedback, kind, width, height,
            PlayerGestureFeedbackState.presentation(kind, PlayerSurfacePolicy.classify(width, 1f), level, text),
            if (kind == PlayerGestureFeedbackKind.BRIGHTNESS) PlayerGestureFeedbackState.brightnessIcon(level, false) else PlayerGestureFeedbackState.volumeIcon(level),
            "${kind.name} $text", hide,
            edges = edges,
        )
        measure(surface, width, height)
        settle()
        measure(surface, width, height)
        feedback.findViewById<LinearProgressIndicator>(R.id.feedbackProgress).jumpDrawablesToCurrentState()
    }

    private fun showIn(window: FrameLayout, feedback: View, kind: PlayerGestureFeedbackKind, width: Int, height: Int, edges: PlayerFeedbackEdges, windowWidth: Int) {
        PlayerSurfacePolicy.presentFeedback(
            context, feedback, kind, width, height,
            PlayerGestureFeedbackState.presentation(kind, PlayerSurfacePolicy.classify(width, 1f), 65, "65%"),
            if (kind == PlayerGestureFeedbackKind.BRIGHTNESS) R.drawable.ic_gesture_brightness_half else R.drawable.baseline_volume_up_black_24,
            "${kind.name} 65%", hide,
            edges = edges,
        )
        measure(window, windowWidth, height)
        settle()
        measure(window, windowWidth, height)
    }

    /** Runs the entrance spring and level glides to their end. */
    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PlayerSpringInterpolator.ENTRANCE_MS + 100))
    }

    private fun measure(surface: View, width: Int, height: Int) {
        surface.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        surface.layout(0, 0, width, height)
    }

    private fun assertSettled(view: View) {
        assertEquals(1f, view.alpha, 0f)
        assertEquals(1f, view.scaleX, 0f)
        assertEquals(1f, view.scaleY, 0f)
    }

    private fun assertContentsFit(pill: LinearLayout) {
        for (index in 0 until pill.childCount) {
            val child = pill.getChildAt(index)
            if (child.visibility == View.GONE) continue
            assertTrue("child fits left edge", child.left >= pill.paddingLeft)
            assertTrue("child fits right edge", child.right <= pill.width - pill.paddingRight)
            assertTrue("child fits top edge", child.top >= pill.paddingTop)
            assertTrue("child fits bottom edge", child.bottom <= pill.height - pill.paddingBottom)
        }
    }
}
