package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/** The real card, tab and springs on a landscape mdpi screen, so dp and px match. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28], qualifiers = "w1000dp-h500dp-land-mdpi")
class FloatingChatControllerTest {
    private lateinit var activity: ActivityController<AppCompatActivity>
    private lateinit var binding: FragmentPlayerBinding
    private lateinit var controller: FloatingChatController
    private val prefs get() = binding.root.context.prefs()
    private val holds = mutableListOf<Boolean>()
    private val onScreen = mutableListOf<Boolean>()
    private var channel = "c1"
    private var closeRequests = 0
    private var dismissals = 0
    private var controls = FloatingChatControls()

    @Before fun setup() {
        // DynamicAnimation keeps one frame handler per thread, and Robolectric reuses
        // the thread across test classes: start from a fresh handler every time.
        ReflectionHelpers.getStaticField<ThreadLocal<*>>(
            Class.forName("androidx.dynamicanimation.animation.AnimationHandler"),
            "sAnimatorHandler",
        ).remove()
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.BaseDarkTheme)
        }.setup()
        val context = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
        context.prefs().edit { clear() }
        binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        activity.get().setContentView(binding.root)
        controller = createController()
        idle()
        // The player's top right buttons.
        controls = FloatingChatControls(listOf(FloatingChatBounds(hostWidth() - 150f, 0f, hostWidth(), TOP_BAND)))
    }

    @After fun teardown() {
        // Let springs finish so none outlive this test's looper.
        idle()
        activity.pause().stop().destroy()
    }

    @Test fun `the card opens in the top corner of the right side`() {
        controller.show(animate = false)
        idle()
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        assertEquals(224, binding.floatingChatRoot.width)
        assertEquals(240, binding.floatingChatRoot.height)
        assertEquals(rightX(), binding.floatingChatRoot.x, 0.5f)
        assertEquals(MARGIN, binding.floatingChatRoot.y, 0.5f)
    }

    @Test fun `hiding fades the card before it reports gone`() {
        controller.show(animate = false)
        idle()
        var hidden = false
        controller.hide(animate = true) { hidden = true }
        assertFalse(hidden)
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        idle()
        assertTrue(hidden)
        assertEquals(View.GONE, binding.floatingChatRoot.visibility)
        assertEquals(1f, binding.floatingChatRoot.alpha, 0f)
    }

    @Test fun `showing again during the fade keeps the card and still reports`() {
        controller.show(animate = false)
        idle()
        var hidden = false
        controller.hide(animate = true) { hidden = true }
        controller.show(animate = false)
        assertTrue(hidden)
        idle()
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        assertEquals(1f, binding.floatingChatRoot.alpha, 0f)
    }

    @Test fun `controls bring the header and move the card just off the buttons it covers`() {
        controller.show(animate = false)
        idle()
        controller.onControlsVisibilityChanged(true)
        idle()
        assertEquals(View.VISIBLE, binding.floatingChatHeader.visibility)
        assertEquals(1f, binding.floatingChatHeader.alpha, 0f)
        assertEquals(View.INVISIBLE, binding.floatingChatPill.visibility)
        assertEquals(View.VISIBLE, binding.floatingChatGrip.visibility)
        // Below the buttons, the shortest way clear; sideways would be 146 px.
        assertEquals(TOP_BAND + GAP, binding.floatingChatRoot.y, 0.5f)
        assertEquals(rightX(), binding.floatingChatRoot.x, 0.5f)
        // The header's buttons never sit on a chat line.
        assertEquals(48, binding.floatingChatContainer.paddingTop)

        controller.onControlsVisibilityChanged(false)
        idle()
        // An invisible header must not take touches.
        assertEquals(View.INVISIBLE, binding.floatingChatHeader.visibility)
        assertEquals(View.VISIBLE, binding.floatingChatPill.visibility)
        assertEquals(MARGIN, binding.floatingChatRoot.y, 0.5f)
        assertEquals(0, binding.floatingChatContainer.paddingTop)
    }

    @Test fun `a card clear of the controls stays put when they show`() {
        store(FloatingChatLayout(0.5f, 0.5f, 224f, 240f))
        controller.show(animate = false)
        idle()
        val x = binding.floatingChatRoot.x
        val y = binding.floatingChatRoot.y
        controller.onControlsVisibilityChanged(true)
        idle()
        assertEquals(x, binding.floatingChatRoot.x, 0f)
        assertEquals(y, binding.floatingChatRoot.y, 0f)
    }

    @Test fun `resting bounds report the moved card and nothing while tucked`() {
        assertNull(controller.restingBounds())
        controller.show(animate = false)
        controller.onControlsVisibilityChanged(true)
        val bounds = controller.restingBounds()!!
        idle()
        assertEquals(binding.floatingChatRoot.x, bounds.left, 0.5f)
        assertEquals(binding.floatingChatRoot.y, bounds.top, 0.5f)
        assertEquals(binding.floatingChatRoot.width.toFloat(), bounds.width(), 0.5f)
        assertEquals(binding.floatingChatRoot.height.toFloat(), bounds.height(), 0.5f)

        binding.floatingChatTuck.performClick()
        idle()
        assertNull(controller.restingBounds())
    }

    @Test fun `with the controls up the card steps aside from the seek button and comes back`() {
        // The +10 button: 60dp, 50dp in from the right edge, centred.
        val buttonLeft = hostWidth() - 110f
        val buttonTop = hostHeight() / 2f - 30f
        controls = FloatingChatControls(listOf(FloatingChatBounds(buttonLeft, buttonTop, buttonLeft + 60f, buttonTop + 60f)))
        store(FloatingChatLayout(1f, 0.5f, 224f, 240f))
        val stored = prefs.getString(FloatingChatLayoutStore.PREF_KEY, null)
        controller.show(animate = false)
        idle()
        val middleY = MARGIN + (hostHeight() - 2 * MARGIN - 240f) / 2f
        assertEquals(rightX(), binding.floatingChatRoot.x, 0.5f)
        assertEquals(middleY, binding.floatingChatRoot.y, 0.5f)

        controller.onControlsVisibilityChanged(true)
        idle()
        assertEquals(buttonLeft - GAP - 224f, binding.floatingChatRoot.x, 0.5f)
        assertEquals(middleY, binding.floatingChatRoot.y, 0.5f)
        assertEquals(224, binding.floatingChatRoot.width)

        controller.onControlsVisibilityChanged(false)
        idle()
        assertEquals(rightX(), binding.floatingChatRoot.x, 0.5f)
        assertEquals(middleY, binding.floatingChatRoot.y, 0.5f)
        // Stepping aside is not a move: nothing new is stored.
        assertEquals(stored, prefs.getString(FloatingChatLayoutStore.PREF_KEY, null))
    }

    @Test fun `a gesture slider fades the card back until it goes`() {
        controller.show(animate = false)
        idle()
        controller.setDucked(true)
        idle()
        assertEquals(0.25f, binding.floatingChatRoot.alpha, 0.01f)
        controller.setDucked(false)
        idle()
        assertEquals(1f, binding.floatingChatRoot.alpha, 0.01f)

        // Hiding while faded clears it, so the next show is fully visible.
        controller.setDucked(true)
        idle()
        controller.hide(animate = true)
        idle()
        controller.show(animate = true)
        idle()
        assertEquals(1f, binding.floatingChatRoot.alpha, 0.01f)
    }

    @Test fun `pass through only applies while the controls are hidden`() {
        prefs.edit { putBoolean(C.FLOATING_CHAT_PASS_THROUGH, true) }
        controller.applyAppearance()
        controller.show(animate = false)
        assertTrue(binding.floatingChatRoot.passThrough)
        assertEquals(View.INVISIBLE, binding.floatingChatPill.visibility)
        controller.onControlsVisibilityChanged(true)
        assertFalse(binding.floatingChatRoot.passThrough)
    }

    @Test fun `tucking leaves a tab that counts new lines and brings the card back`() {
        controller.show(animate = false)
        controller.onControlsVisibilityChanged(true)
        idle()
        binding.floatingChatTuck.performClick()
        idle()
        assertTrue(controller.isTucked)
        assertEquals(View.INVISIBLE, binding.floatingChatRoot.visibility)
        assertEquals(View.VISIBLE, binding.floatingChatTab.visibility)
        assertEquals(hostWidth(), binding.floatingChatTab.x + binding.floatingChatTab.measuredWidth, 1f)

        controller.onMessagesAppended(3)
        assertEquals(View.VISIBLE, binding.floatingChatTabCount.visibility)
        assertEquals("3", binding.floatingChatTabCount.text.toString())

        binding.floatingChatTab.performClick()
        idle()
        assertFalse(controller.isTucked)
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        assertEquals(View.GONE, binding.floatingChatTab.visibility)
        assertEquals(rightX(), binding.floatingChatRoot.x, 0.5f)
    }

    @Test fun `a dragged card stays where it is let go and is remembered`() {
        controller.show(animate = false)
        idle()
        drag(binding.floatingChatPill, dx = -400f, dy = 120f)
        idle()
        assertEquals(listOf(true, false), holds)
        assertEquals(rightX() - 400f, binding.floatingChatRoot.x, 0.5f)
        assertEquals(MARGIN + 120f, binding.floatingChatRoot.y, 0.5f)
        val stored = FloatingChatLayoutStore.read(prefs.getString(FloatingChatLayoutStore.PREF_KEY, null), "c1")!!
        assertEquals((rightX() - 400f - MARGIN) / (hostWidth() - 2 * MARGIN - 224f), stored.xFraction, 0.001f)

        // Another channel starts from the last used place.
        controller.hide(animate = false)
        channel = "c2"
        val second = createController()
        second.show(animate = false)
        idle()
        assertEquals(rightX() - 400f, binding.floatingChatRoot.x, 0.5f)
        assertEquals(MARGIN + 120f, binding.floatingChatRoot.y, 0.5f)
    }

    @Test fun `a cancelled drag settles and releases the controls`() {
        controller.show(animate = false)
        idle()
        val pill = binding.floatingChatPill
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val move = MotionEvent.obtain(time, time + 16, MotionEvent.ACTION_MOVE, 10f, 200f, 0)
        val cancel = MotionEvent.obtain(time, time + 32, MotionEvent.ACTION_CANCEL, 10f, 200f, 0)
        try {
            pill.dispatchTouchEvent(down)
            pill.dispatchTouchEvent(move)
            pill.dispatchTouchEvent(cancel)
        } finally {
            down.recycle()
            move.recycle()
            cancel.recycle()
        }
        idle()
        assertEquals(listOf(true, false), holds)
        assertEquals(MARGIN, binding.floatingChatRoot.y, 0.5f)
        assertEquals(rightX(), binding.floatingChatRoot.x, 0.5f)
    }

    @Test fun `a header drag fades the controls, keeps the header and drops the card where it is let go`() {
        controller.show(animate = false)
        controller.onControlsVisibilityChanged(true)
        idle()
        val header = binding.floatingChatHeader
        val close = binding.floatingChatClose
        val startY = TOP_BAND + GAP
        assertEquals(startY, binding.floatingChatRoot.y, 0.5f)
        // On the close button, which fills the end of the header.
        drag(
            header, dx = -400f, dy = 0f,
            startX = close.left + close.width / 2f, startY = close.top + close.height / 2f,
        ) {
            // Past the touch slop: the controls fade, the header and grip stay under the finger.
            assertEquals(1, dismissals)
            assertEquals(View.VISIBLE, binding.floatingChatHeader.visibility)
            assertEquals(1f, binding.floatingChatHeader.alpha, 0f)
            assertEquals(View.VISIBLE, binding.floatingChatGrip.visibility)
        }
        idle()
        assertEquals(0, closeRequests)
        assertEquals(listOf(true, false), holds)
        // No jump back up once the controls are gone.
        assertEquals(rightX() - 400f, binding.floatingChatRoot.x, 0.5f)
        assertEquals(startY, binding.floatingChatRoot.y, 0.5f)
        assertEquals(View.INVISIBLE, binding.floatingChatHeader.visibility)
        assertEquals(View.VISIBLE, binding.floatingChatPill.visibility)

        // A tap on the button still closes.
        holds.clear()
        close.performClick()
        assertEquals(1, closeRequests)
    }

    @Test fun `a tap on the header or grip leaves the controls and the stored size`() {
        controller.show(animate = false)
        controller.onControlsVisibilityChanged(true)
        idle()
        drag(binding.floatingChatHeader, dx = 0f, dy = 0f)
        drag(binding.floatingChatGrip, dx = 0f, dy = 0f)
        idle()
        assertEquals(0, dismissals)
        assertEquals(listOf(true, false, true, false), holds)
        assertNull(prefs.getString(FloatingChatLayoutStore.PREF_KEY, null))
        assertEquals(224, binding.floatingChatRoot.width)
    }

    @Test fun `resizing from the grip fades the controls and keeps the outer edge`() {
        controller.show(animate = false)
        controller.onControlsVisibilityChanged(true)
        idle()
        drag(binding.floatingChatGrip, dx = -50f, dy = 50f) {
            assertEquals(1, dismissals)
            assertEquals(View.VISIBLE, binding.floatingChatGrip.visibility)
            assertEquals(View.VISIBLE, binding.floatingChatHeader.visibility)
        }
        idle()
        assertEquals(274, binding.floatingChatRoot.width)
        assertEquals(290, binding.floatingChatRoot.height)
        assertEquals(hostWidth() - MARGIN, binding.floatingChatRoot.x + 274f, 0.5f)
        assertEquals(TOP_BAND + GAP, binding.floatingChatRoot.y, 0.5f)
        assertEquals(View.INVISIBLE, binding.floatingChatGrip.visibility)
        val stored = FloatingChatLayoutStore.read(prefs.getString(FloatingChatLayoutStore.PREF_KEY, null), "c1")!!
        assertEquals(274f, stored.widthDp, 0f)
        assertEquals(290f, stored.heightDp, 0f)
    }

    @Test fun `the card follows a swipe down and goes back up with the player`() {
        controller.show(animate = false)
        idle()
        controller.followPlayer(translationY = 100f, progress = 0.5f)
        assertEquals(100f, binding.floatingChatHost.translationY, 0f)
        assertEquals(1f - 0.45f * 0.5f, binding.floatingChatHost.alpha, 0.001f)

        // The controls showing mid-swipe wait until the card is back.
        controller.onControlsVisibilityChanged(true)
        idle()
        assertEquals(MARGIN, binding.floatingChatRoot.y, 0.5f)

        controller.returnWithPlayer(250L)
        idle()
        assertEquals(FloatingChatHostPose.IDENTITY, FloatingChatHostPose.of(binding.floatingChatHost))
        assertEquals(1f, binding.floatingChatHost.alpha, 0f)
        assertEquals(TOP_BAND + GAP, binding.floatingChatRoot.y, 0.5f)
    }

    @Test fun `minimizing carries the card into the mini player, then hides it`() {
        controller.show(animate = false)
        idle()
        onScreen.clear()
        assertTrue(controller.holdForPlayer())
        assertFalse(controller.isShown)
        assertEquals(listOf(false), onScreen)
        // The controls hiding as the player leaves must not move the card.
        controller.onControlsVisibilityChanged(true)

        val host = binding.floatingChatHost
        // Robolectric runs a whole animation in one idle, so only its ends can be seen.
        controller.hideWithPlayer(FloatingChatHostPose(200f, 150f, 0.4f, 0.4f), 250L)
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        assertEquals(rightX(), binding.floatingChatRoot.x, 0.5f)
        assertEquals(MARGIN, binding.floatingChatRoot.y, 0.5f)

        idle()
        assertEquals(View.GONE, binding.floatingChatRoot.visibility)
        assertEquals(FloatingChatHostPose.IDENTITY, FloatingChatHostPose.of(host))
        assertEquals(1f, host.alpha, 0f)
        // Reported off screen once.
        assertEquals(listOf(false), onScreen)
    }

    @Test fun `maximizing during the minimize brings the card back from where it got to`() {
        controller.show(animate = false)
        idle()
        assertTrue(controller.holdForPlayer())
        controller.hideWithPlayer(FloatingChatHostPose(200f, 150f, 0.4f, 0.4f), 250L)
        val host = binding.floatingChatHost
        // Where the player is when the viewer taps it to maximize again.
        val midway = FloatingChatHostPose(120f, 90f, 0.64f, 0.64f)

        controller.showWithPlayer(midway, 250L)
        assertTrue(controller.isShown)
        assertEquals(midway, FloatingChatHostPose.of(host))
        assertEquals(0f, host.alpha, 0f)
        idle()
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        assertEquals(FloatingChatHostPose.IDENTITY, FloatingChatHostPose.of(host))
        assertEquals(1f, host.alpha, 0f)
        assertEquals(rightX(), binding.floatingChatRoot.x, 0.5f)
        assertEquals(MARGIN, binding.floatingChatRoot.y, 0.5f)
    }

    @Test fun `a minimize that starts after a quick maximize leaves the card shown`() {
        controller.show(animate = false)
        idle()
        assertTrue(controller.holdForPlayer())
        // Maximized again before the minimize animation got its first frame.
        controller.show(animate = false)
        controller.hideWithPlayer(FloatingChatHostPose(200f, 150f, 0.4f, 0.4f), 250L)
        idle()
        assertTrue(controller.isShown)
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        assertEquals(FloatingChatHostPose.IDENTITY, FloatingChatHostPose.of(binding.floatingChatHost))
    }

    @Test fun `a hidden card has nothing to carry into the mini player`() {
        assertFalse(controller.holdForPlayer())
        controller.hideWithPlayer(FloatingChatHostPose(200f, 150f, 0.4f, 0.4f), 250L)
        idle()
        assertEquals(View.GONE, binding.floatingChatRoot.visibility)
        assertEquals(FloatingChatHostPose.IDENTITY, FloatingChatHostPose.of(binding.floatingChatHost))
    }

    private fun createController() = FloatingChatController(
        host = binding.floatingChatHost,
        card = binding.floatingChatRoot,
        header = binding.floatingChatHeader,
        content = binding.floatingChatContainer,
        pill = binding.floatingChatPill,
        grip = binding.floatingChatGrip,
        tab = binding.floatingChatTab,
        tabCount = binding.floatingChatTabCount,
        composeButton = binding.floatingChatCompose,
        appearanceButton = binding.floatingChatAppearance,
        tuckButton = binding.floatingChatTuck,
        closeButton = binding.floatingChatClose,
        prefs = prefs,
        callbacks = object : FloatingChatController.Callbacks {
            override fun channelId() = channel
            override fun holdControls(hold: Boolean) {
                holds += hold
            }
            override fun dismissControls() {
                // PlayerFragment starts the controls' fade and reports them hidden at once.
                dismissals++
                controller.onControlsVisibilityChanged(false)
            }
            override fun controlBands() = controls
            override fun canCompose() = false
            override fun onAppearanceRequested(trigger: View) = Unit
            override fun onComposeRequested() = Unit
            override fun onCloseRequested() {
                closeRequests++
            }
            override fun onCardOnScreenChanged(onScreen: Boolean) {
                this@FloatingChatControllerTest.onScreen += onScreen
            }
        },
    )

    private fun store(layout: FloatingChatLayout) {
        prefs.edit { putString(FloatingChatLayoutStore.PREF_KEY, FloatingChatLayoutStore.write(null, channel, layout)) }
    }

    /**
     * Raw coordinates equal view coordinates here, which is all the handlers
     * read. The finger rests at the end before it lifts, so the card is let go
     * with no fling. [midway] runs halfway, with the finger still down.
     */
    private fun drag(
        view: View,
        dx: Float,
        dy: Float,
        startX: Float = 10f,
        startY: Float = 10f,
        midway: () -> Unit = {},
    ) {
        val time = SystemClock.uptimeMillis()
        fun event(action: Int, offset: Long, x: Float, y: Float) = MotionEvent.obtain(time, time + offset, action, x, y, 0)
        val first = buildList {
            add(event(MotionEvent.ACTION_DOWN, 0, startX, startY))
            for (step in 1..5) {
                add(event(MotionEvent.ACTION_MOVE, step * 16L, startX + dx * step / 10, startY + dy * step / 10))
            }
        }
        val rest = buildList {
            for (step in 6..10) {
                add(event(MotionEvent.ACTION_MOVE, step * 16L, startX + dx * step / 10, startY + dy * step / 10))
            }
            // Robolectric's velocity tracker looks back 200 ms from the newest sample.
            add(event(MotionEvent.ACTION_MOVE, 400, startX + dx, startY + dy))
            add(event(MotionEvent.ACTION_MOVE, 500, startX + dx, startY + dy))
            add(event(MotionEvent.ACTION_UP, 600, startX + dx, startY + dy))
        }
        try {
            first.forEach(view::dispatchTouchEvent)
            midway()
            rest.forEach(view::dispatchTouchEvent)
        } finally {
            (first + rest).forEach(MotionEvent::recycle)
        }
    }

    private fun hostWidth() = binding.floatingChatHost.width.toFloat()

    private fun hostHeight() = binding.floatingChatHost.height.toFloat()

    private fun rightX() = hostWidth() - MARGIN - 224f

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
    }

    private companion object {
        const val MARGIN = 12f
        const val GAP = 8f
        const val TOP_BAND = 56f
    }
}
