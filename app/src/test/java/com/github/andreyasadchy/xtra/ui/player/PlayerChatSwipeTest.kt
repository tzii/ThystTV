package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/**
 * Drives the real gesture listener, arbiter and fragment chat transitions on a
 * 1000x500 landscape player. Without system gesture insets the chat-side edge
 * zone is the 30px fallback, so the seekable strip is 30-86px from that edge.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28], qualifiers = "w1000dp-h500dp-land-mdpi")
class PlayerChatSwipeTest {
    private lateinit var activity: ActivityController<AppCompatActivity>
    private lateinit var binding: FragmentPlayerBinding
    private lateinit var fragment: PlayerFragment
    private lateinit var listener: PlayerGestureListener
    private val arbiter = PlayerGestureArbiter(0.02f)
    private val prefs get() = fragment.requireContext().prefs()
    private val chatView by lazy { View(fragment.requireContext()) }

    @Before fun setup() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.BaseDarkTheme)
        }.setup()
        val context = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
        context.prefs().edit { clear(); putBoolean(C.FLOATING_CHAT_ENABLED, true) }
        binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        activity.get().setContentView(binding.root)
        binding.playerLayout.layoutParams = FrameLayout.LayoutParams(1000, 500)
        fragment = Mockito.mock(PlayerFragment::class.java,
            Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS))
        Mockito.doReturn(context).`when`(fragment).context
        Mockito.doReturn(binding.root).`when`(fragment).view
        Mockito.doReturn(android.os.Bundle()).`when`(fragment).arguments
        ReflectionHelpers.setField(fragment, "_binding", binding)
        ReflectionHelpers.setField(fragment, "gestureArbiter", arbiter)
        ReflectionHelpers.setField(fragment, "chatWidthLandscape", 240)
        val chat = Mockito.mock(ChatFragment::class.java)
        Mockito.doReturn(chatView).`when`(chat).view
        ReflectionHelpers.setField(fragment, "chatFragment", chat)
        binding.chatFragmentContainer.addView(chatView)
        fragment.hideChat()
        listener = PlayerGestureListener(context, fragment, doubleTapEnabled = true)
        setVideoType(PlayerFragment.STREAM)
        idle()
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
    }

    @Test fun `live swipe anywhere shows the sidebar and the reverse hides it`() {
        swipe(fromX = 600f, toX = 400f)
        assertEquals(PlayerGestureArbiter.Owner.CHAT_SWIPE, arbiter.owner)
        assertSidebar()

        swipe(fromX = 300f, toX = 500f)
        assertHidden()
    }

    @Test fun `swipes never cycle chat already in the requested state`() {
        swipe(fromX = 300f, toX = 500f)
        assertHidden()
        fragment.showChat()
        idle()
        swipe(fromX = 600f, toX = 400f)
        assertSidebar()
    }

    @Test fun `a short swipe does nothing`() {
        swipe(fromX = 600f, toX = 560f)
        assertEquals(PlayerGestureArbiter.Owner.CHAT_SWIPE, arbiter.owner)
        assertHidden()
    }

    @Test fun `vod swipe outside the chat side strip keeps seeking`() {
        setVideoType(PlayerFragment.VIDEO)
        swipe(fromX = 600f, toX = 400f, y = 400f)
        assertEquals(PlayerGestureArbiter.Owner.SEEK, arbiter.owner)
        assertHidden()
    }

    @Test fun `vod swipe from the chat side strip shows chat`() {
        setVideoType(PlayerFragment.VIDEO)
        swipe(fromX = 940f, toX = 760f, y = 400f)
        assertEquals(PlayerGestureArbiter.Owner.CHAT_SWIPE, arbiter.owner)
        assertSidebar()
    }

    @Test fun `swipe toward the chat side hides floating chat`() {
        fragment.showChat()
        fragment.cycleChatMode()
        idle()
        assertSame(binding.floatingChatContainer, chatView.parent)

        swipe(fromX = 300f, toX = 500f)
        assertHidden()
        assertSame(binding.chatFragmentContainer, chatView.parent)
        assertEquals(View.GONE, binding.floatingChatRoot.visibility)
        assertFalse(ReflectionHelpers.getField<Boolean>(fragment, "isFloatingChatEnabled"))
    }

    @Test fun `showing chat by swipe ends a fold-docked floating restore`() {
        ReflectionHelpers.setField(fragment, "foldDockedFloatingChat", true)
        swipe(fromX = 600f, toX = 400f)
        assertFalse(ReflectionHelpers.getField<Boolean>(fragment, "foldDockedFloatingChat"))
    }

    @Test fun `rtl mirrors the swipe directions`() {
        binding.playerLayout.layoutDirection = View.LAYOUT_DIRECTION_RTL
        idle()
        swipe(fromX = 600f, toX = 400f)
        assertHidden()
        swipe(fromX = 400f, toX = 600f)
        assertSidebar()
    }

    @Test fun `setting off or chat disabled leaves live horizontal swipes inert`() {
        prefs.edit { putBoolean(C.PLAYER_GESTURES_SWIPE_CHAT, false) }
        swipe(fromX = 600f, toX = 400f)
        assertEquals(PlayerGestureArbiter.Owner.IDLE, arbiter.owner)
        assertHidden()

        prefs.edit { putBoolean(C.PLAYER_GESTURES_SWIPE_CHAT, true); putBoolean(C.CHAT_DISABLE, true) }
        swipe(fromX = 600f, toX = 400f)
        assertEquals(PlayerGestureArbiter.Owner.IDLE, arbiter.owner)
        assertHidden()
    }

    @Test fun `vertical swipes keep brightness and volume`() {
        swipe(fromX = 900f, toX = 900f, y = 400f, toY = 200f)
        assertEquals(PlayerGestureArbiter.Owner.DEVICE_VOLUME, arbiter.owner)
        assertHidden()
    }

    private fun setVideoType(type: String) {
        ReflectionHelpers.setField(fragment, "videoType", type)
    }

    /** One pointer sequence through the detector callbacks the touch listener feeds. */
    private fun swipe(fromX: Float, toX: Float, y: Float = 250f, toY: Float = y) {
        arbiter.onSequenceStarted()
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, fromX, y, 0)
        try {
            listener.onDown(down)
            var lastX = fromX
            var lastY = y
            for (step in 1..10) {
                val x = fromX + (toX - fromX) * step / 10f
                val stepY = y + (toY - y) * step / 10f
                val move = MotionEvent.obtain(0, step * 16L, MotionEvent.ACTION_MOVE, x, stepY, 0)
                try {
                    listener.onScroll(down, move, lastX - x, lastY - stepY)
                } finally {
                    move.recycle()
                }
                lastX = x
                lastY = stepY
            }
        } finally {
            down.recycle()
        }
        idle()
    }

    private fun assertSidebar() {
        assertTrue(ReflectionHelpers.getField<Boolean>(fragment, "isChatOpen"))
        assertFalse(ReflectionHelpers.getField<Boolean>(fragment, "isFloatingChatEnabled"))
        assertEquals(View.VISIBLE, binding.chatLayout.visibility)
        assertEquals(240, (binding.playerLayout.layoutParams as FrameLayout.LayoutParams).marginEnd)
        assertTrue(prefs.getBoolean(C.KEY_CHAT_OPENED, false))
    }

    private fun assertHidden() {
        assertFalse(ReflectionHelpers.getField<Boolean>(fragment, "isChatOpen"))
        assertEquals(View.GONE, binding.chatLayout.visibility)
        assertEquals(0, (binding.playerLayout.layoutParams as FrameLayout.LayoutParams).marginEnd)
        assertFalse(prefs.getBoolean(C.KEY_CHAT_OPENED, true))
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }
}
