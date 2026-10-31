package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerMiniPlayerLayoutTest {
    @Test fun `a 16 by 9 mini player fills its viewport on phone foldable and tablet windows`() {
        for ((width, height) in listOf(1080 to 2400, 2400 to 1080, 1280 to 1182, 2560 to 1600)) {
            val harness = Harness(width, height)
            harness.minimized(true)
            with(harness.binding) {
                assertTrue("base width fits $width x $height", playerLayout.width <= width)
                assertTrue("base height fits $width x $height", playerLayout.height <= height)
                assertEquals("viewport width at $width x $height", playerLayout.width, aspectRatioFrameLayout.width)
                assertEquals("viewport height at $width x $height", playerLayout.height, aspectRatioFrameLayout.height)
                val visibleRatio = playerLayout.width * slidingLayout.scaleX / (playerLayout.height * slidingLayout.scaleY)
                assertEquals("visible ratio at $width x $height", 16f / 9f, visibleRatio, 0.005f)
            }
        }
    }

    @Test fun `restoring a near square mini player gives the full player its window back`() {
        val harness = Harness(1280, 1182)
        harness.minimized(true)
        assertEquals(720, harness.binding.playerLayout.height)
        harness.minimized(false)
        assertEquals(1280, harness.binding.playerLayout.width)
        assertEquals(1182, harness.binding.playerLayout.height)
        harness.minimized(true)
        assertEquals(720, harness.binding.playerLayout.height)
    }

    @Test fun `a resized minimized viewport remains 16 by 9 without clipping in a wide window`() {
        val harness = Harness(1280, 1182)
        harness.minimized(true)
        harness.width = 2400
        harness.height = 1080
        harness.settle()
        with(harness.binding) {
            assertEquals(1920, playerLayout.width)
            assertEquals(1080, playerLayout.height)
            assertEquals(playerLayout.width, aspectRatioFrameLayout.width)
            assertEquals(playerLayout.height, aspectRatioFrameLayout.height)
        }
    }

    @Test fun `a four by three source keeps legitimate fit bars inside the mini viewport`() {
        val harness = Harness(1280, 1182)
        harness.binding.aspectRatioFrameLayout.setAspectRatio(4f / 3f)
        harness.minimized(true)
        with(harness.binding) {
            assertEquals(1280, playerLayout.width)
            assertEquals(720, playerLayout.height)
            assertEquals(960, aspectRatioFrameLayout.width)
            assertEquals(720, aspectRatioFrameLayout.height)
        }
    }

    private class Harness(var width: Int, var height: Int) {
        private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
        val binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        private val fragment = Mockito.mock(PlayerFragment::class.java,
            Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS))

        init {
            context.prefs().edit().clear().commit()
            Mockito.doReturn(context).`when`(fragment).context
            Mockito.doReturn(binding.root).`when`(fragment).view
            Mockito.doReturn(android.os.Bundle()).`when`(fragment).arguments
            ReflectionHelpers.setField(fragment, "_binding", binding)
            ReflectionHelpers.setField(fragment, "isPortrait", width < height)
            binding.playerLayout.isPortrait = width < height
            binding.playerLayout.layoutParams = FrameLayout.LayoutParams(-1, -1)
            binding.chatLayout.visibility = View.GONE
            binding.aspectRatioFrameLayout.setAspectRatio(16f / 9f)
            settle()
        }

        fun minimized(minimized: Boolean) {
            ReflectionHelpers.setField(fragment, "isMaximized", !minimized)
            ReflectionHelpers.callInstanceMethod<Unit>(fragment, "updateFoldLayout")
            val scale = if (minimized) {
                ReflectionHelpers.callInstanceMethod<Pair<Float, Float>>(fragment, "getScaleValues")
            } else 1f to 1f
            binding.slidingLayout.scaleX = scale.first
            binding.slidingLayout.scaleY = scale.second
            settle()
        }

        fun settle() {
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            binding.root.layout(0, 0, width, height)
        }
    }
}
