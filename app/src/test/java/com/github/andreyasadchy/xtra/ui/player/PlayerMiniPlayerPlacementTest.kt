package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.graphics.RectF
import android.os.Bundle
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/** Checks transformed video bounds through production fragment placement paths. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28], qualifiers = "land-mdpi")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerMiniPlayerPlacementTest {
    @Test fun `minimize keeps the measured RTL viewport inside an extra wide window`() {
        for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            Harness(direction).use { harness ->
                harness.fragment.minimize()
                harness.settle()
                assertEquals(1920, harness.binding.playerLayout.width)
                harness.assertCorner()
            }
        }
    }

    @Test fun `reinitializing a minimized RTL player preserves its visible corner`() {
        for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            Harness(direction).use { harness ->
                ReflectionHelpers.setField(harness.fragment, "isMaximized", false)
                ReflectionHelpers.callInstanceMethod<Unit>(harness.fragment, "initLayout")
                harness.settle()
                harness.assertCorner()
            }
        }
    }

    @Test fun `drag release snaps the measured RTL player back without clipping`() {
        Harness(View.LAYOUT_DIRECTION_RTL).use { harness ->
            harness.fragment.minimize()
            harness.settle()
            harness.binding.slidingLayout.translationX -= 400f
            harness.binding.slidingLayout.translationY -= 100f
            harness.release(moveFreely = false)
            harness.assertCorner()
        }
    }

    @Test fun `free drag release constrains the actual RTL player at every window edge`() {
        Harness(View.LAYOUT_DIRECTION_RTL, padded = true).use { harness ->
            harness.fragment.minimize()
            harness.settle()
            for (offscreen in listOf(-5000f, 5000f)) {
                harness.binding.slidingLayout.translationX = offscreen
                harness.binding.slidingLayout.translationY = offscreen
                harness.release(moveFreely = true)
                val bounds = harness.visibleBounds()
                assertTrue("left $bounds", bounds.left >= -0.5f)
                assertTrue("top $bounds", bounds.top >= -0.5f)
                assertTrue("right $bounds", bounds.right <= harness.binding.slidingLayout.width + 0.5f)
                assertTrue("bottom $bounds", bounds.bottom <= harness.binding.slidingLayout.height + 0.5f)
            }
        }
    }

    @Test fun `minimized placement uses the padded child origin in both orientations and directions`() {
        for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            for ((width, height) in listOf(2400 to 1080, 1080 to 2400)) {
                Harness(direction, width, height, padded = true).use { harness ->
                    harness.fragment.minimize()
                    harness.settle()
                    harness.assertCorner()
                    ReflectionHelpers.callInstanceMethod<Unit>(harness.fragment, "initLayout")
                    harness.settle()
                    harness.assertCorner()
                }
            }
        }
    }

    private class Harness(
        direction: Int,
        private val width: Int = 2400,
        private val height: Int = 1080,
        padded: Boolean = false,
    ) : AutoCloseable {
        private val activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.BaseDarkTheme)
        }.setup()
        private val context = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
        val binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        val fragment: PlayerFragment = Mockito.mock(PlayerFragment::class.java,
            Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS))

        init {
            context.prefs().edit {
                clear()
                // Unrelated media-backed quick controls are outside this geometry harness.
                putBoolean(C.PLAYER_SETTINGS, false)
                putBoolean(C.PLAYER_SPEEDBUTTON, false)
                putBoolean(C.PLAYER_CHATTOGGLE, false)
                putBoolean(C.PLAYER_FULLSCREEN, false)
            }
            Mockito.doReturn(context).`when`(fragment).context
            Mockito.doReturn(activity.get()).`when`(fragment).activity
            Mockito.doReturn(activity.get()).`when`(fragment).viewLifecycleOwner
            Mockito.doReturn(binding.root).`when`(fragment).view
            Mockito.doReturn(Bundle()).`when`(fragment).arguments
            ReflectionHelpers.setField(fragment, "_binding", binding)
            ReflectionHelpers.setField(fragment, "isPortrait", width < height)
            binding.root.layoutDirection = direction
            binding.slidingLayout.layoutDirection = direction
            binding.playerLayout.layoutDirection = direction
            binding.playerLayout.isPortrait = width < height
            binding.playerLayout.layoutParams = FrameLayout.LayoutParams(-1, -1, Gravity.TOP or Gravity.START)
            binding.chatLayout.visibility = View.GONE
            binding.playerControls.root.visibility = View.GONE
            if (padded) binding.slidingLayout.setPadding(40, 30, 60, 20)
            measureAndLayout()
        }

        fun release(moveFreely: Boolean) {
            ReflectionHelpers.callInstanceMethod<Unit>(fragment, "settleMinimizedPlayerPosition",
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, moveFreely))
            settle()
        }

        fun settle() {
            measureAndLayout()
            binding.slidingLayout.viewTreeObserver.dispatchOnPreDraw()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        }

        private fun measureAndLayout() {
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            binding.root.layout(0, 0, width, height)
        }

        fun visibleBounds(): RectF = with(binding) {
            RectF(playerLayout.left.toFloat(), playerLayout.top.toFloat(),
                playerLayout.right.toFloat(), playerLayout.bottom.toFloat()).also(slidingLayout.matrix::mapRect)
        }

        fun assertCorner() = with(binding) {
            val bounds = visibleBounds()
            val density = root.resources.displayMetrics.density
            assertEquals("right $bounds", slidingLayout.width - 20f * density * slidingLayout.scaleX,
                bounds.right, 0.5f)
            assertEquals("bottom $bounds", slidingLayout.height - 30f * density * slidingLayout.scaleY,
                bounds.bottom, 0.5f)
            assertTrue("left $bounds", bounds.left >= 0f)
            assertTrue("top $bounds", bounds.top >= 0f)
        }

        override fun close() {
            activity.pause().stop().destroy()
        }
    }
}
