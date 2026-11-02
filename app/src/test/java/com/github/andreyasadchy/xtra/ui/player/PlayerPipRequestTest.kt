package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.util.Rational
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class PlayerPipRequestTest {
    @Test fun `next request follows settings while an entered window retains its shape`() {
        val harness = Harness()
        harness.context.prefs().edit { putBoolean(C.PLAYER_PIP_CHAT, true) }
        harness.refresh()
        assertEquals(Rational(9, 16), harness.lastAspectRatio())

        whenever(harness.activity.isInPictureInPictureMode).thenReturn(true)
        ReflectionHelpers.setField(harness.fragment, "isPortrait", false)
        harness.context.prefs().edit { putBoolean(C.PLAYER_PIP_CHAT, false) }
        harness.refresh()
        assertTrue(ReflectionHelpers.getField(harness.fragment, "pipChatRequested"))
        assertEquals(Rational(9, 16), harness.lastAspectRatio())

        whenever(harness.activity.isInPictureInPictureMode).thenReturn(false)
        harness.refresh()
        assertFalse(ReflectionHelpers.getField(harness.fragment, "pipChatRequested"))
        assertEquals(16f / 9f, harness.lastAspectRatio().toFloat(), 0.01f)
    }

    @Test fun `remote playback and missing chat fall back without a portrait request`() {
        val harness = Harness()
        harness.context.prefs().edit { putBoolean(C.PLAYER_PIP_CHAT, true) }
        whenever(harness.chat.supportsPipMode()).thenReturn(false)
        harness.refresh()
        assertFalse(ReflectionHelpers.getField(harness.fragment, "pipChatRequested"))
        whenever(harness.chat.supportsPipMode()).thenReturn(true)
        whenever(harness.fragment.isRemotePlayback()).thenReturn(true)
        harness.refresh()
        assertFalse(ReflectionHelpers.getField(harness.fragment, "pipChatRequested"))
    }

    @Test fun `PiP window listener is detached before fragment view destruction`() {
        val harness = Harness()
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val container = FrameLayout(host).also(host::setContentView)
        container.addView(harness.binding.root)
        ReflectionHelpers.callInstanceMethod<Unit>(harness.fragment, "watchPlayerPresentation",
            ReflectionHelpers.ClassParameter.from(View::class.java, harness.binding.root))
        container.viewTreeObserver.dispatchOnGlobalLayout()
        verify(harness.fragment, atLeastOnce()).isAdded
        container.removeView(harness.binding.root)
        clearInvocations(harness.fragment)
        container.viewTreeObserver.dispatchOnGlobalLayout()
        verify(harness.fragment, never()).isAdded
    }

    private class Harness {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
        val activity = mock<FragmentActivity>()
        val chat = mock<ChatFragment>()
        val binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        val fragment: PlayerFragment = mock(defaultAnswer = org.mockito.Mockito.CALLS_REAL_METHODS,
            useConstructor = org.mockito.kotlin.UseConstructor.parameterless())

        init {
            context.prefs().edit { clear() }
            shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
            whenever(activity.packageManager).thenReturn(context.packageManager)
            doReturn(context).whenever(fragment).context
            doReturn(activity).whenever(fragment).activity
            doReturn(true).whenever(fragment).isAdded
            doReturn(true).whenever(fragment).canEnterPictureInPicture()
            whenever(chat.supportsPipMode()).thenReturn(true)
            ReflectionHelpers.setField(fragment, "_binding", binding)
            ReflectionHelpers.setField(fragment, "pipSupported", true)
            ReflectionHelpers.setField(fragment, "chatFragment", chat)
            ReflectionHelpers.setField(fragment, "videoType", PlayerFragment.STREAM)
            ReflectionHelpers.setField(fragment, "isPortrait", true)
            binding.playerLayout.isPortrait = true
            binding.playerLayout.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            binding.chatLayout.visibility = View.VISIBLE
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, 1080, 2400)
            assertTrue(binding.playerLayout.width > 0 && binding.playerLayout.height > 0)
        }

        fun refresh() = ReflectionHelpers.callInstanceMethod<Unit>(fragment, "refreshPipPresentation")
        fun lastRequest(): PictureInPictureParams = argumentCaptor<PictureInPictureParams>().run {
            verify(activity, atLeastOnce()).setPictureInPictureParams(capture())
            lastValue
        }
        // The public Rational getter is newer than API 28; inspect the real built
        // parameter's stored value rather than calling a newer framework method.
        fun lastAspectRatio(): Rational = ReflectionHelpers.getField(lastRequest(), "mAspectRatio")
    }
}
