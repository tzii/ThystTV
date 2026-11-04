package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.ui.common.ProtectedCategory
import com.github.andreyasadchy.xtra.ui.common.ProtectedChannel
import com.github.andreyasadchy.xtra.ui.common.SpoilerShield
import com.github.andreyasadchy.xtra.ui.common.SpoilerShieldRules
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class PlayerTitleProtectionTest {
    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
    private val hiddenLabel get() = context.getString(R.string.spoiler_shield_title_hidden)
    private val model = PlayerViewModel(mock(), mock(), mock(), mock(), mock(), mock(), null, null, mock(), mock(), mock(), mock(), mock())
    private val binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
    private val fragment: PlayerFragment = mock(defaultAnswer = Mockito.CALLS_REAL_METHODS)
    private val shownTitle get() = binding.playerControls.title.text.toString()

    @Before fun setup() {
        context.prefs().edit { clear() }
        doReturn(context).whenever(fragment).context
        doReturn(Bundle().apply {
            putString("channelId", "c1")
            putString("gameId", "g1")
            putString("gameName", "Chess")
            putString("title", "Secret")
        }).whenever(fragment).arguments
        ReflectionHelpers.setField(fragment, "viewModel\$delegate", lazyOf(model))
        ReflectionHelpers.setField(fragment, "_binding", binding)
        fragment.updateStreamInfo("Secret", "g1", "chess", "Chess")
    }

    private fun protectChannel() = SpoilerShield.saveRules(context.prefs(), SpoilerShieldRules(channels = listOf(ProtectedChannel(id = "c1"))))

    private fun playbackTitle() = ReflectionHelpers.callInstanceMethod<String?>(fragment, "playbackTitle")

    @Test fun `a rule added while the player stays open hides its title`() {
        assertEquals("Secret", shownTitle)
        protectChannel()
        fragment.refreshPlayerTitleProtection()
        assertEquals(hiddenLabel, shownTitle)
    }

    @Test fun `removing the rule brings the title back`() {
        protectChannel()
        fragment.refreshPlayerTitleProtection()
        SpoilerShield.saveRules(context.prefs(), SpoilerShieldRules())
        fragment.refreshPlayerTitleProtection()
        assertEquals("Secret", shownTitle)
    }

    @Test fun `a rule change ends an earlier reveal, as on the channel page`() {
        protectChannel()
        fragment.refreshPlayerTitleProtection()
        binding.playerControls.title.performClick()
        assertEquals("Secret", shownTitle)
        fragment.refreshPlayerTitleProtection()
        assertFalse(model.spoilerTitleRevealed)
        assertEquals(hiddenLabel, shownTitle)
    }

    @Test fun `the media title follows a live category change`() {
        SpoilerShield.saveRules(context.prefs(), SpoilerShieldRules(categories = listOf(ProtectedCategory(id = "g2"))))
        assertEquals("Secret", playbackTitle())
        fragment.updateStreamInfo("Secret", "g2", "go", "Go")
        assertEquals(hiddenLabel, playbackTitle())
    }
}
