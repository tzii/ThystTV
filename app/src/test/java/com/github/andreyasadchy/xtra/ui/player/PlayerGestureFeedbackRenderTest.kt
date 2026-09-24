package com.github.andreyasadchy.xtra.ui.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.Insets
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.UiTestRender
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

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

    @Test fun `tablet edge pills enlarge icon and level while respecting insets`() {
        for (kind in listOf(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerGestureFeedbackKind.DEVICE_VOLUME)) {
            val (surface, feedback) = createSurface()
            present(surface, feedback, kind, 1280, 720, Insets.of(24, 0, 40, 0))
            val pill = feedback.findViewById<LinearLayout>(R.id.feedbackContainer)
            val icon = feedback.findViewById<ImageView>(R.id.feedbackIcon)
            val level = feedback.findViewById<ProgressBar>(R.id.feedbackVerticalLevel)
            assertEquals(64, pill.width)
            assertEquals(216, pill.height)
            assertEquals(32, icon.width)
            assertEquals(32, icon.height)
            assertEquals(Color.WHITE, icon.imageTintList?.defaultColor)
            assertEquals(8, level.width)
            assertEquals(148, level.height)
            assertEquals(65, level.progress)
            assertEquals("${kind.name} 65%", feedback.contentDescription)
            assertEquals(View.GONE, feedback.findViewById<View>(R.id.feedbackProgress).visibility)
            assertEquals(View.GONE, feedback.findViewById<View>(R.id.feedbackText).visibility)
            assertEquals((720 - pill.height) / 2, feedback.top + pill.top)
            assertEquals(if (kind == PlayerGestureFeedbackKind.BRIGHTNESS) 40 else 1160, pill.left)
            assertContentsFit(pill)
            UiTestRender.save(surface, "gesture-tablet-${kind.name.lowercase()}-after")

            // Reproduce the prior fixed dimensions for a same-surface comparison.
            // This reference image is presentation-only, not old player execution.
            pill.layoutParams = pill.layoutParams.apply { width = 48; height = 144 }
            pill.setPadding(6, 10, 6, 10)
            (pill.background as GradientDrawable).cornerRadius = 24f
            icon.layoutParams = icon.layoutParams.apply { width = 24; height = 24 }
            level.layoutParams = level.layoutParams.apply { width = 4 }
            measure(surface, 1280, 720)
            UiTestRender.save(surface, "gesture-tablet-${kind.name.lowercase()}-before-reference")
        }
    }

    @Test fun `very short resized players keep icon and level inside the pill`() {
        val (surface, feedback) = createSurface()
        for (height in listOf(96, 160, 240, 360, 720)) {
            present(surface, feedback, PlayerGestureFeedbackKind.DEVICE_VOLUME, 720, height)
            val pill = feedback.findViewById<LinearLayout>(R.id.feedbackContainer)
            val level = feedback.findViewById<ProgressBar>(R.id.feedbackVerticalLevel)
            assertTrue(pill.height <= height * 0.45f)
            assertTrue("level remains visible in a $height px player", level.height > 0)
            assertContentsFit(pill)
            assertTrue(feedback.top >= 0)
            assertTrue(feedback.bottom <= surface.height)
            if (height == 160) UiTestRender.save(surface, "gesture-short-player")
        }
    }

    @Test fun `same overlay restores compact geometry after resize and other gestures`() {
        val (surface, feedback) = createSurface()
        present(surface, feedback, PlayerGestureFeedbackKind.BRIGHTNESS, 1280, 720)
        for ((width, height, kind) in listOf(
            Triple(360, 203, PlayerGestureFeedbackKind.DEVICE_VOLUME),
            Triple(560, 315, PlayerGestureFeedbackKind.BRIGHTNESS),
            Triple(1280, 720, PlayerGestureFeedbackKind.SEEK),
        )) {
            present(surface, feedback, kind, width, height)
            val pill = feedback.findViewById<LinearLayout>(R.id.feedbackContainer)
            val icon = feedback.findViewById<ImageView>(R.id.feedbackIcon)
            val level = feedback.findViewById<LinearProgressIndicator>(R.id.feedbackProgress)
            assertEquals(LinearLayout.HORIZONTAL, pill.orientation)
            assertEquals(24, icon.width)
            assertEquals(24, icon.height)
            assertEquals(0, (icon.layoutParams as LinearLayout.LayoutParams).bottomMargin)
            assertEquals(12, (icon.layoutParams as LinearLayout.LayoutParams).marginEnd)
            assertEquals(24f, (pill.background as GradientDrawable).cornerRadius, 0f)
            assertEquals(View.GONE, feedback.findViewById<View>(R.id.feedbackVerticalLevel).visibility)
            assertEquals(24, feedback.paddingTop)
            assertEquals("65%", feedback.findViewById<TextView>(R.id.feedbackText).text.toString())
            if (kind != PlayerGestureFeedbackKind.SEEK) {
                assertEquals(88, level.width)
                assertEquals(4, level.trackThickness)
                assertEquals(View.VISIBLE, level.visibility)
                assertTrue("attached compact indicator draws its track", level.progressDrawable?.isVisible == true)
                assertEquals(40, pill.height)
            } else {
                assertEquals(View.GONE, level.visibility)
            }
            assertContentsFit(pill)
            if (width == 360) UiTestRender.save(surface, "gesture-compact-phone")
            if (width == 560) UiTestRender.save(surface, "gesture-narrow-video-with-side-chat")
        }
    }

    private fun createSurface(): Pair<FrameLayout, View> {
        val surface = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(Color.rgb(31, 49, 62), Color.rgb(186, 198, 169)))
        }
        val feedback = LayoutInflater.from(context).inflate(R.layout.layout_player_gesture_feedback, surface, false)
        surface.addView(feedback)
        // Material progress drawables follow real attachment/window visibility.
        // A detached preview otherwise hides the horizontal track despite valid bounds.
        activity.get().setContentView(surface)
        return surface to feedback
    }

    private fun present(surface: FrameLayout, feedback: View, kind: PlayerGestureFeedbackKind, width: Int, height: Int, insets: Insets = Insets.NONE) {
        PlayerSurfacePolicy.presentFeedback(
            context, feedback, kind, width, height, insets,
            PlayerGestureFeedbackState.presentation(kind, PlayerSurfacePolicy.classify(width, 1f), 65, "65%"),
            if (kind == PlayerGestureFeedbackKind.BRIGHTNESS) R.drawable.ic_brightness_medium_black_24dp else R.drawable.baseline_volume_up_black_24,
            "${kind.name} 65%", hide,
        )
        measure(surface, width, height)
        feedback.findViewById<LinearProgressIndicator>(R.id.feedbackProgress).jumpDrawablesToCurrentState()
    }

    private fun measure(surface: View, width: Int, height: Int) {
        surface.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        surface.layout(0, 0, width, height)
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
