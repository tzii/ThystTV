package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import androidx.appcompat.app.AppCompatActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerSpeedPopupBinding
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerVolumeOverlayBinding
import com.github.andreyasadchy.xtra.ui.UiTestRender
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.After
import org.junit.Assert.*
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [28], qualifiers = "w960dp-h540dp-land-television-mdpi")
class PlayerTvRemoteTest {
    private lateinit var activity: ActivityController<AppCompatActivity>
    private val context: Context get() = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)

    @Before fun setup() {
        // Native Robolectric windows default to touch mode. Start in remote mode
        // before attachment so ordinary (non-touch-focusable) controls can focus.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.BaseDarkTheme)
        }.setup().visible()
        context.prefs().edit { clear() }
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
    }

    @Test fun `first center reveals controls without playing and next center invokes focused action`() {
        val (fragment, binding) = player()
        var clicks = 0
        binding.playerControls.playPause.setOnClickListener { clicks++ }
        assertTrue(fragment.dispatchTvKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
        measure(binding.root, 960, 540)
        assertTrue(binding.playerControls.playPause.hasFocus())
        assertTrue(fragment.dispatchTvKeyEvent(KeyEvent(0, 100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1)))
        assertTrue(fragment.dispatchTvKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)))
        assertEquals(0, clicks)
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val event = key(action, KeyEvent.KEYCODE_DPAD_CENTER)
            assertFalse(fragment.dispatchTvKeyEvent(event))
            assertTrue(binding.root.dispatchKeyEvent(event))
        }
        assertEquals(1, clicks)
        UiTestRender.save(binding.playerControls.root, "tv-player-focused-controls")
    }

    @Test fun `live controls use a visible action when play pause is hidden`() {
        val (fragment, binding) = player()
        binding.playerControls.playPause.visibility = View.GONE
        binding.playerControls.quality.visibility = View.VISIBLE
        var clicks = 0
        binding.playerControls.quality.setOnClickListener { clicks++ }
        reveal(fragment)
        measure(binding.root, 960, 540)
        assertTrue(binding.playerControls.quality.isFocused)
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val event = key(action, KeyEvent.KEYCODE_DPAD_CENTER)
            assertFalse(fragment.dispatchTvKeyEvent(event))
            assertTrue(binding.root.dispatchKeyEvent(event))
        }
        assertEquals(1, clicks)
    }

    @Test fun `closing a popup restores its visible quick control`() {
        assertVolumePopupFocusRestored(quickControlVisible = true)
    }

    @Test fun `closing a More popup with its quick control hidden restores visible player focus`() {
        assertVolumePopupFocusRestored(quickControlVisible = false)
    }

    @Test fun `remote input refreshes idle timeout and Back hides controls before minimize`() {
        val (fragment, binding) = player()
        reveal(fragment)
        idle(2500)
        assertFalse(fragment.dispatchTvKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        idle(1000)
        assertEquals(View.VISIBLE, binding.playerControls.root.visibility)
        val back = ReflectionHelpers.getField<OnBackPressedCallback>(fragment, "backPressedCallback")
        Mockito.doNothing().`when`(fragment).minimize()
        back.handleOnBackPressed()
        assertEquals(View.GONE, binding.playerControls.root.visibility)
        Mockito.verify(fragment, Mockito.never()).minimize()
        back.handleOnBackPressed()
        Mockito.verify(fragment).minimize()
        reveal(fragment)
        idle(3400)
        assertEquals(View.GONE, binding.playerControls.root.visibility)
    }

    @Test fun `inactive minimized controller-disabled and phone players leave key handling unchanged`() {
        val (fragment, binding) = player()
        val event = key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        fragment.isMaximized = false
        assertFalse(fragment.dispatchTvKeyEvent(event))
        fragment.isMaximized = true
        ReflectionHelpers.setField(fragment, "useController", false)
        assertFalse(fragment.dispatchTvKeyEvent(event))
        ReflectionHelpers.setField(fragment, "useController", true)
        binding.root.visibility = View.GONE
        assertFalse(fragment.dispatchTvKeyEvent(event))
        binding.root.visibility = View.VISIBLE
        val phone = ContextThemeWrapper(context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = Configuration.UI_MODE_TYPE_NORMAL
        }), R.style.BaseDarkTheme)
        Mockito.doReturn(phone).`when`(fragment).context
        assertFalse(fragment.dispatchTvKeyEvent(event))
        assertEquals(View.GONE, binding.playerControls.root.visibility)
        binding.playerControls.root.visibility = View.VISIBLE
        Mockito.doNothing().`when`(fragment).minimize()
        ReflectionHelpers.getField<OnBackPressedCallback>(fragment, "backPressedCallback").handleOnBackPressed()
        Mockito.verify(fragment).minimize()
        Mockito.doReturn(context).`when`(fragment).context
        assertFalse(fragment.dispatchTvKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)))
        ReflectionHelpers.setField(fragment, "_binding", null)
        assertFalse(fragment.dispatchTvKeyEvent(event))
    }

    @Test fun `TV close delegates full player removal and mini-player center restores once`() {
        val (fragment, binding) = player()
        val mainActivity = Mockito.mock(MainActivity::class.java)
        Mockito.doReturn(mainActivity).`when`(fragment).activity
        invoke(fragment, "configureTvPlayerControls")
        assertEquals(View.VISIBLE, binding.playerControls.closePlayer.visibility)
        binding.playerControls.closePlayer.performClick()
        Mockito.verify(mainActivity).closePlayer(fragment)
        Mockito.verify(fragment, Mockito.never()).close()

        fragment.isMaximized = false
        Mockito.doNothing().`when`(fragment).maximize()
        invoke(fragment, "focusTvMiniPlayer")
        assertTrue(binding.slidingLayout.hasFocus())
        press(binding.slidingLayout, KeyEvent.KEYCODE_DPAD_CENTER)
        Mockito.verify(fragment).maximize()
    }

    @Test fun `TV popup focus stays inside while phone focus search is unchanged`() {
        for (television in listOf(true, false)) {
            val themed = ContextThemeWrapper(context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                uiMode = if (television) Configuration.UI_MODE_TYPE_TELEVISION else Configuration.UI_MODE_TYPE_NORMAL
            }), R.style.BaseDarkTheme)
            val root = FrameLayout(themed)
            val outside = Button(themed).apply { text = "Behind player" }
            root.addView(outside, FrameLayout.LayoutParams(140, 60).apply { leftMargin = 0; topMargin = 180 })
            val popup = LayoutInflater.from(themed).inflate(R.layout.layout_player_popup_host, root, false) as ViewGroup
            root.addView(popup)
            val panel = popup.findViewById<FrameLayout>(R.id.playerPopupPanelContainer)
            panel.layoutParams = FrameLayout.LayoutParams(300, 150).apply { leftMargin = 200; topMargin = 150 }
            val inside = Button(themed).apply { text = "Popup choice" }
            panel.addView(inside, FrameLayout.LayoutParams(140, 60).apply { topMargin = 30 })
            activity.get().setContentView(root)
            measure(root, 960, 540)
            assertTrue(inside.requestFocus())
            assertSame(if (television) inside else outside, inside.focusSearch(View.FOCUS_LEFT))
        }
    }

    @Test fun `remote sliders update playback and saved settings without touch callbacks`() {
        val speedBinding = LayoutPlayerSpeedPopupBinding.inflate(LayoutInflater.from(context))
        var speed = 1f
        val speedBinder = PlayerSpeedPopupBinder(context, speedBinding, 1f, 384, { speed = it }, {})
        speedBinder.bind()
        speedBinding.root.prepareTvPlayerFocus()
        activity.get().setContentView(speedBinding.root)
        measure(speedBinding.root, 384, 400)
        assertTrue(speedBinding.speedSlider.requestFocus())
        press(speedBinding.speedSlider, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(speed > 1f)
        assertEquals(speed, context.prefs().getFloat(C.PLAYER_SPEED, 1f), 0.001f)
        UiTestRender.save(speedBinding.root, "tv-speed-focused-slider")
        speedBinder.dispose()

        val volumeBinding = LayoutPlayerVolumeOverlayBinding.inflate(LayoutInflater.from(context))
        var volume = 0.6f
        var dismissals = 0
        val volumeBinder = PlayerVolumePopupBinder(context, volumeBinding, PlayerVolumeOverlayState(), volume, 1500, { volume = it }, { dismissals++ })
        volumeBinder.bind()
        volumeBinding.root.prepareTvPlayerFocus()
        activity.get().setContentView(volumeBinding.root)
        measure(volumeBinding.root, 384, 150)
        assertTrue(volumeBinding.volumeOverlaySlider.requestFocus())
        press(volumeBinding.volumeOverlaySlider, KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue(volume < 0.6f)
        assertEquals(volume, context.prefs().getInt(C.PLAYER_VOLUME, 100) / 100f, 0.001f)
        idle(1000)
        volumeBinder.onRemoteInteraction()
        idle(1000)
        assertEquals(0, dismissals)
        idle(600)
        assertEquals(1, dismissals)
        volumeBinder.dispose()
    }

    @Test fun `toolbar speed rounds the remote slider value and keeps compact labels`() {
        val (fragment, playerBinding) = player()
        val updateSpeed = PlayerFragment::class.java.getDeclaredMethod("updatePlaybackSpeedUi", java.lang.Float::class.java).apply {
            isAccessible = true
        }
        for ((speed, label) in listOf(1f to "1x", 1.5f to "1.5x", 1.25f to "1.25x")) {
            updateSpeed.invoke(fragment, speed)
            assertEquals(label, playerBinding.playerControls.speed.text.toString())
        }
        val popup = LayoutPlayerSpeedPopupBinding.inflate(LayoutInflater.from(context))
        val binder = PlayerSpeedPopupBinder(context, popup, 1f, 384, { updateSpeed.invoke(fragment, it) }, {})
        binder.bind()
        popup.root.prepareTvPlayerFocus()
        activity.get().setContentView(popup.root)
        measure(popup.root, 384, 400)
        assertTrue(popup.speedSlider.requestFocus())
        press(popup.speedSlider, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(1.05f, popup.speedSlider.value, 0.0001f)
        assertEquals("1.05x", popup.currentSpeedText.text.toString())
        assertEquals("1.05x", playerBinding.playerControls.speed.text.toString())
        binder.dispose()
    }

    private fun player(): Pair<PlayerFragment, FragmentPlayerBinding> {
        val binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        val fragment = Mockito.mock(PlayerFragment::class.java, Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS))
        Mockito.doReturn(context).`when`(fragment).context
        Mockito.doReturn(activity.get()).`when`(fragment).activity
        Mockito.doReturn(binding.root).`when`(fragment).view
        Mockito.doReturn(Bundle()).`when`(fragment).arguments
        ReflectionHelpers.setField(fragment, "_binding", binding)
        binding.playerLayout.layoutParams = FrameLayout.LayoutParams(960, 540)
        binding.playerControls.root.prepareTvPlayerFocus()
        activity.get().setContentView(binding.root)
        measure(binding.root, 960, 540)
        return fragment to binding
    }

    private fun assertVolumePopupFocusRestored(quickControlVisible: Boolean) {
        val (fragment, binding) = player()
        Mockito.doReturn(LayoutInflater.from(context)).`when`(fragment).layoutInflater
        binding.playerControls.playPause.visibility = View.GONE
        binding.playerControls.quality.visibility = View.VISIBLE
        // More can open Volume even when its quick control is disabled in preferences.
        binding.playerControls.volume.visibility = if (quickControlVisible) View.VISIBLE else View.GONE
        reveal(fragment)
        fragment.showVolumeOverlay()
        measure(binding.root, 960, 540)
        idle(250)
        assertTrue("Opening a popup must move remote focus into its content", binding.playerPopupHost.playerPopupPanelContainer.hasFocus())

        ReflectionHelpers.getField<OnBackPressedCallback>(fragment, "backPressedCallback").handleOnBackPressed()
        idle(250)

        assertEquals(View.GONE, binding.playerPopupHost.root.visibility)
        val expected = if (quickControlVisible) binding.playerControls.volume else binding.playerControls.quality
        assertTrue("Dismissal must restore a visible control immediately", expected.isFocused)
        assertFalse("The next remote key must continue native control navigation", fragment.dispatchTvKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
    }

    private fun reveal(fragment: PlayerFragment) {
        assertTrue(fragment.dispatchTvKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
        assertTrue(fragment.dispatchTvKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)))
    }

    private fun press(view: View, code: Int) {
        view.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, code))
        view.dispatchKeyEvent(key(KeyEvent.ACTION_UP, code))
    }

    private fun key(action: Int, code: Int) = KeyEvent(action, code)
    private fun invoke(fragment: PlayerFragment, name: String) =
        PlayerFragment::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(fragment)
    private fun idle(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    private fun measure(view: View, width: Int, height: Int) {
        view.layoutDirection = View.LAYOUT_DIRECTION_LTR
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
}
