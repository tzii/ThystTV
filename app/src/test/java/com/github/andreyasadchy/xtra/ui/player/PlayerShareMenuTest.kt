package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerMorePopupBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class PlayerShareMenuTest {
    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
    private val fragment = mock(PlayerFragment::class.java).also {
        `when`(it.requireContext()).thenReturn(context)
        `when`(it.getIsPortrait()).thenReturn(true)
        `when`(it.playerShareUrl()).thenReturn("https://www.twitch.tv/videos/123?t=0h0m7s")
    }
    private val binding = LayoutPlayerMorePopupBinding.inflate(LayoutInflater.from(context))
    private val dismiss = mock(Runnable::class.java)
    private fun binder() = PlayerMorePopupBinder(fragment, binding, PlayerFragment.VIDEO, null, null, false, dismiss::run)

    @Test fun `timestamp share is reachable and dismisses More before opening the chooser`() {
        context.prefs().edit().putBoolean(C.PLAYER_MENU_SHARE, true).commit()
        val binder = binder()
        binder.bind()
        val action = binding.morePopupSettings.menuShare
        assertEquals(View.VISIBLE, action.visibility)
        assertEquals(context.getString(R.string.player_share_position), action.text)
        assertTrue(action.minimumHeight >= (48 * context.resources.displayMetrics.density).toInt())
        action.performClick()
        inOrder(dismiss, fragment).apply {
            verify(dismiss).run()
            verify(fragment).sharePlayback()
        }
        binder.dispose()
        action.performClick()
        verify(fragment, times(1)).sharePlayback()
    }

    @Test fun `setting and unavailable identity hide the share action`() {
        context.prefs().edit().putBoolean(C.PLAYER_MENU_SHARE, false).commit()
        binder().bind()
        assertEquals(View.GONE, binding.morePopupSettings.menuShare.visibility)
        context.prefs().edit().putBoolean(C.PLAYER_MENU_SHARE, true).commit()
        `when`(fragment.playerShareUrl()).thenReturn(null)
        binder().bind()
        assertEquals(View.GONE, binding.morePopupSettings.menuShare.visibility)
    }

    @Test fun `share action launches a text chooser with public URL and title`() {
        `when`(fragment.arguments).thenReturn(Bundle().apply { putString("title", "Example VoD") })
        `when`(fragment.getString(R.string.share)).thenReturn("Share")
        doCallRealMethod().`when`(fragment).sharePlayback()
        fragment.sharePlayback()
        val captor = ArgumentCaptor.forClass(Intent::class.java)
        verify(fragment).startActivity(captor.capture())
        assertEquals(Intent.ACTION_CHOOSER, captor.value.action)
        @Suppress("DEPRECATION")
        val send = captor.value.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("https://www.twitch.tv/videos/123?t=0h0m7s", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Example VoD", send.getStringExtra(Intent.EXTRA_TITLE))
        assertNull(send.data)
    }
}
