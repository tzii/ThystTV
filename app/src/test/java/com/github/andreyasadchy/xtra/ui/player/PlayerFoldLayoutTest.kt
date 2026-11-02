package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28], qualifiers = "land")
class PlayerFoldLayoutTest {
    // A 2076 x 2152 inner screen, hinge across the middle (tabletop) or down it (book).
    private val tabletop = FoldHinge(horizontal = true, bounds = FoldBounds(0, 1076, 2076, 1076))
    private val book = FoldHinge(horizontal = false, bounds = FoldBounds(1038, 0, 1038, 2152))

    @Test fun `tabletop puts video above the hinge and chat below it`() {
        val harness = Harness(chatOpen = true)
        harness.fold(tabletop)
        with(harness.binding) {
            assertEquals(2076, playerLayout.width)
            assertEquals(1076, playerLayout.height)
            assertEquals(View.VISIBLE, chatLayout.visibility)
            assertEquals(1076, chatLayout.top)
            assertEquals(2076, chatLayout.width)
            assertEquals(1076, chatLayout.height)
        }
    }

    @Test fun `tabletop keeps the video above the hinge when chat is hidden`() {
        val harness = Harness(chatOpen = false)
        harness.fold(tabletop)
        assertEquals(1076, harness.binding.playerLayout.height)
        assertEquals(View.GONE, harness.binding.chatLayout.visibility)
    }

    @Test fun `tabletop chat follows keyboard height changes and restores on close`() {
        val harness = Harness(chatOpen = true)
        harness.fold(tabletop)
        val minChat = (PlayerFoldLayoutPolicy.MIN_CHAT_DP * harness.binding.root.resources.displayMetrics.density).toInt()

        harness.insets(1200, imeVisible = true)
        assertEquals(1076, harness.binding.playerLayout.height)
        assertEquals(minChat, harness.binding.chatLinearLayout.height)
        assertEquals(2152 - 1200, harness.binding.chatLinearLayout.bottom + harness.binding.chatLayout.top)

        // A taller keyboard, e.g. its emoji panel, does not change the container or visibility.
        harness.insets(1500, imeVisible = true)
        assertEquals(minChat, harness.binding.chatLinearLayout.height)
        assertEquals(2152 - 1500, harness.binding.chatLinearLayout.bottom + harness.binding.chatLayout.top)

        harness.insets(0, imeVisible = false)
        assertEquals(1076, harness.binding.chatLayout.top)
        assertEquals(1076, harness.binding.chatLayout.height)
        assertEquals(1076, harness.binding.playerLayout.height)
    }

    @Test fun `a resized keyboard window keeps tabletop even with a small lower pane`() {
        val harness = Harness(chatOpen = true)
        harness.fold(tabletop)
        harness.insets(0, imeVisible = true)
        harness.containerHeight = 1120
        harness.settle()
        harness.fold(tabletop)
        assertEquals(1076, harness.binding.playerLayout.height)
        val minChat = (PlayerFoldLayoutPolicy.MIN_CHAT_DP * harness.binding.root.resources.displayMetrics.density).toInt()
        assertEquals(minChat, harness.binding.chatLinearLayout.height)
        assertEquals(1120, harness.binding.chatLinearLayout.bottom + harness.binding.chatLayout.top)
    }

    @Test fun `keyboard does not reveal hidden tabletop chat or change book widths`() {
        val hidden = Harness(chatOpen = false)
        hidden.fold(tabletop)
        hidden.insets(1200, imeVisible = true)
        assertEquals(View.GONE, hidden.binding.chatLayout.visibility)
        assertEquals(1076, hidden.binding.playerLayout.height)

        val sidebar = Harness(chatOpen = true)
        sidebar.fold(book)
        sidebar.insets(1200, imeVisible = true)
        assertEquals(1038, sidebar.binding.playerLayout.width)
        assertEquals(1038, sidebar.binding.chatLayout.width)
    }

    @Test fun `tabletop docks floating chat below the hinge and cycles without floating`() {
        val harness = Harness(chatOpen = true, floating = true)
        harness.fold(tabletop)
        with(harness) {
            assertFalse(ReflectionHelpers.getField<Boolean>(fragment, "isFloatingChatEnabled"))
            assertTrue(ReflectionHelpers.getField<Boolean>(fragment, "isChatOpen"))
            assertSame(binding.chatFragmentContainer, chatView.parent)
            assertEquals(View.GONE, binding.floatingChatRoot.visibility)
            assertEquals(1076, binding.chatLayout.top)

            fragment.cycleChatMode()
            settle()
            assertFalse(ReflectionHelpers.getField<Boolean>(fragment, "isChatOpen"))
            assertFalse(ReflectionHelpers.getField<Boolean>(fragment, "isFloatingChatEnabled"))
            assertEquals(View.GONE, binding.chatLayout.visibility)
            assertEquals(1076, binding.playerLayout.height)

            fragment.cycleChatMode()
            settle()
            assertTrue(ReflectionHelpers.getField<Boolean>(fragment, "isChatOpen"))
            assertEquals(1076, binding.chatLayout.top)
            assertEquals(1076, binding.playerLayout.height)
        }
    }

    @Test fun `unfolding floats chat again after tabletop docked it`() {
        val harness = Harness(chatOpen = true, floating = true)
        harness.fold(tabletop)
        harness.fold(null)
        with(harness) {
            assertTrue(ReflectionHelpers.getField<Boolean>(fragment, "isFloatingChatEnabled"))
            assertSame(binding.floatingChatContainer, chatView.parent)
            assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
            assertEquals(View.GONE, binding.chatLayout.visibility)
            assertEquals(0, (binding.playerLayout.layoutParams as FrameLayout.LayoutParams).marginEnd)
            assertEquals(2152, binding.playerLayout.height)
        }
    }

    @Test fun `a rotation while docked keeps chat docked until the hinge goes away`() {
        val harness = Harness(chatOpen = true, floating = true)
        harness.fold(tabletop)
        // A rotation can report the hinge in the new orientation before the configuration changes.
        harness.fold(book)
        assertFalse(ReflectionHelpers.getField<Boolean>(harness.fragment, "isFloatingChatEnabled"))
        harness.fold(tabletop)
        harness.fold(null)
        assertTrue(ReflectionHelpers.getField<Boolean>(harness.fragment, "isFloatingChatEnabled"))
        assertEquals(View.VISIBLE, harness.binding.floatingChatRoot.visibility)
    }

    @Test fun `a chat change while docked keeps the sidebar after unfolding`() {
        val harness = Harness(chatOpen = true, floating = true)
        harness.fold(tabletop)
        harness.fragment.cycleChatMode()
        harness.fragment.cycleChatMode()
        harness.fold(null)
        with(harness) {
            assertFalse(ReflectionHelpers.getField<Boolean>(fragment, "isFloatingChatEnabled"))
            assertSame(binding.chatFragmentContainer, chatView.parent)
            assertEquals(View.VISIBLE, binding.chatLayout.visibility)
            assertEquals(240, binding.chatLayout.width)
        }
    }

    @Test fun `book posture splits video and chat at the hinge`() {
        val harness = Harness(chatOpen = true)
        harness.fold(book)
        with(harness.binding) {
            assertEquals(0, playerLayout.left)
            assertEquals(1038, playerLayout.width)
            assertEquals(2152, playerLayout.height)
            assertEquals(1038, chatLayout.left)
            assertEquals(1038, chatLayout.width)
        }
    }

    @Test fun `book posture gives floating chat the whole screen and splits again for the sidebar`() {
        val harness = Harness(chatOpen = true)
        harness.fold(book)
        harness.fragment.cycleChatMode()
        harness.settle()
        with(harness) {
            assertTrue(ReflectionHelpers.getField<Boolean>(fragment, "isFloatingChatEnabled"))
            assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
            assertEquals(View.GONE, binding.chatLayout.visibility)
            assertEquals(2076, binding.playerLayout.width)

            fragment.cycleChatMode()
            fragment.cycleChatMode()
            settle()
            assertEquals(1038, binding.playerLayout.width)
            assertEquals(1038, binding.chatLayout.left)
        }
    }

    @Test fun `book posture leaves hidden chat with the whole screen`() {
        val harness = Harness(chatOpen = false)
        harness.fold(book)
        assertEquals(2076, harness.binding.playerLayout.width)
        assertEquals(View.GONE, harness.binding.chatLayout.visibility)
    }

    @Test fun `unfolding restores the sidebar layout`() {
        val harness = Harness(chatOpen = true)
        harness.fold(tabletop)
        harness.fold(null)
        with(harness.binding) {
            val params = playerLayout.layoutParams as FrameLayout.LayoutParams
            assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, params.height)
            assertEquals(240, params.marginEnd)
            assertEquals(2152, playerLayout.height)
            assertEquals(240, chatLayout.width)
            assertEquals(2152, chatLayout.height)
        }
    }

    @Test fun `minimizing leaves the fold split for a 16 by 9 viewport`() {
        val harness = Harness(chatOpen = true)
        harness.fold(tabletop)
        ReflectionHelpers.setField(harness.fragment, "isMaximized", false)
        harness.fold(tabletop)
        assertEquals((2076 / (16f / 9f)).toInt(), harness.binding.playerLayout.height)
    }

    @Test fun `the setting turns the split off`() {
        val harness = Harness(chatOpen = true)
        harness.prefs.edit { putBoolean(C.PLAYER_FOLD_LAYOUT, false) }
        harness.fold(tabletop)
        assertEquals(2152, harness.binding.playerLayout.height)
        assertEquals(240, harness.binding.chatLayout.width)
    }

    @Test fun `portrait tabletop replaces the 16 by 9 split and unfolding restores it`() {
        val harness = Harness(chatOpen = true, portrait = true)
        harness.fold(tabletop)
        with(harness.binding) {
            assertFalse(playerLayout.isPortrait)
            assertEquals(1076, playerLayout.height)
            assertEquals(1076, chatLayout.top)
        }
        harness.fold(null)
        with(harness.binding) {
            assertTrue(playerLayout.isPortrait)
            assertTrue(chatLayout.isPortrait)
            assertEquals((2076 / (16f / 9f)).toInt(), playerLayout.height)
        }
    }

    /**
     * Drives the real fragment chat transitions and fold layout on inflated views; only
     * fragment attachment and the network chat fragment are mocked, as in [PlayerPinchChatTest].
     */
    private class Harness(chatOpen: Boolean, floating: Boolean = false, portrait: Boolean = false) {
        private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
        val prefs = context.prefs()
        val binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        var containerHeight = 2152
        val chatView = View(context)
        val fragment: PlayerFragment = Mockito.mock(PlayerFragment::class.java,
            Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS))

        init {
            prefs.edit { clear() }
            Mockito.doReturn(context).`when`(fragment).context
            Mockito.doReturn(binding.root).`when`(fragment).view
            Mockito.doReturn(android.os.Bundle()).`when`(fragment).arguments
            ReflectionHelpers.setField(fragment, "_binding", binding)
            ReflectionHelpers.setField(fragment, "chatWidthLandscape", 240)
            val chat = Mockito.mock(ChatFragment::class.java)
            Mockito.doReturn(chatView).`when`(chat).view
            ReflectionHelpers.setField(fragment, "chatFragment", chat)
            binding.chatFragmentContainer.addView(chatView)
            if (portrait) {
                ReflectionHelpers.setField(fragment, "isPortrait", true)
                binding.playerLayout.isPortrait = true
                binding.chatLayout.isPortrait = true
                binding.playerLayout.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                )
                binding.chatLayout.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, android.view.Gravity.BOTTOM,
                )
                binding.chatLayout.visibility = View.VISIBLE
            } else {
                fragment.hideChat()
                if (chatOpen) fragment.showChat()
                if (floating) fragment.cycleChatMode()
            }
            settle()
        }

        fun fold(hinge: FoldHinge?) {
            ReflectionHelpers.setField(fragment, "foldHinge", hinge)
            ReflectionHelpers.callInstanceMethod<Unit>(fragment, "updateFoldLayout")
            settle()
        }

        fun insets(bottom: Int, imeVisible: Boolean) {
            ReflectionHelpers.callInstanceMethod<Unit>(fragment, "applyChatInsets",
                ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, bottom),
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, imeVisible),
            )
            settle()
        }

        fun settle() {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(2076, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(containerHeight, View.MeasureSpec.EXACTLY),
            )
            binding.root.layout(0, 0, 2076, containerHeight)
        }
    }
}
