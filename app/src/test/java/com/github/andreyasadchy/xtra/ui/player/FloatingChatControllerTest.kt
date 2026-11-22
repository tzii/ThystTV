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
    private var channel = "c1"
    private var closeRequests = 0

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

    @Test fun `controls bring the header and push the card below the top band`() {
        controller.show(animate = false)
        idle()
        controller.onControlsVisibilityChanged(true)
        idle()
        assertEquals(View.VISIBLE, binding.floatingChatHeader.visibility)
        assertEquals(1f, binding.floatingChatHeader.alpha, 0f)
        assertEquals(View.INVISIBLE, binding.floatingChatPill.visibility)
        assertEquals(View.VISIBLE, binding.floatingChatGrip.visibility)
        assertEquals(TOP_BAND + MARGIN, binding.floatingChatRoot.y, 0.5f)

        controller.onControlsVisibilityChanged(false)
        idle()
        // An invisible header must not take touches.
        assertEquals(View.INVISIBLE, binding.floatingChatHeader.visibility)
        assertEquals(View.VISIBLE, binding.floatingChatPill.visibility)
        assertEquals(MARGIN, binding.floatingChatRoot.y, 0.5f)
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
        val hostWidth = binding.floatingChatHost.width
        assertEquals(hostWidth.toFloat(), binding.floatingChatTab.x + binding.floatingChatTab.measuredWidth, 1f)

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

    @Test fun `dragging across the middle moves the card to the left and remembers it`() {
        controller.show(animate = false)
        idle()
        val pill = binding.floatingChatPill
        drag(pill, dx = -600f, dy = 120f)
        idle()
        assertEquals(listOf(true, false), holds)
        assertEquals(MARGIN, binding.floatingChatRoot.x, 0.5f)
        val stored = FloatingChatLayoutStore.read(prefs.getString(FloatingChatLayoutStore.PREF_KEY, null), "c1")!!
        assertEquals(FloatingChatSide.LEFT, stored.side)

        // Another channel starts from the last used place, then keeps its own.
        controller.hide(animate = false)
        channel = "c2"
        val second = createController()
        second.show(animate = false)
        idle()
        assertEquals(MARGIN, binding.floatingChatRoot.x, 0.5f)
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

    @Test fun `a drag that starts on a header button moves the card instead of pressing it`() {
        controller.show(animate = false)
        controller.onControlsVisibilityChanged(true)
        idle()
        val header = binding.floatingChatHeader
        val close = binding.floatingChatClose
        // On the close button, which fills the end of the header.
        val startX = close.left + close.width / 2f
        val startY = close.top + close.height / 2f
        drag(header, dx = -600f, dy = 0f, startX = startX, startY = startY)
        idle()
        assertEquals(0, closeRequests)
        assertEquals(listOf(true, false), holds)
        assertEquals(MARGIN, binding.floatingChatRoot.x, 0.5f)

        // A tap on the button still closes.
        holds.clear()
        close.performClick()
        assertEquals(1, closeRequests)
    }

    @Test fun `a tap on the grip keeps the stored size`() {
        controller.show(animate = false)
        controller.onControlsVisibilityChanged(true)
        idle()
        drag(binding.floatingChatGrip, dx = 0f, dy = 0f)
        idle()
        assertEquals(listOf(true, false), holds)
        assertNull(prefs.getString(FloatingChatLayoutStore.PREF_KEY, null))
        assertEquals(224, binding.floatingChatRoot.width)
    }

    private fun createController() = FloatingChatController(
        host = binding.floatingChatHost,
        card = binding.floatingChatRoot,
        header = binding.floatingChatHeader,
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
            override fun controlBands() = TOP_BAND.toInt() to BOTTOM_BAND
            override fun canCompose() = false
            override fun onAppearanceRequested(trigger: View) = Unit
            override fun onComposeRequested() = Unit
            override fun onCloseRequested() {
                closeRequests++
            }
            override fun onCardOnScreenChanged(onScreen: Boolean) = Unit
        },
    )

    /** Raw coordinates equal view coordinates here, which is all the handler reads. */
    private fun drag(view: View, dx: Float, dy: Float, startX: Float = 10f, startY: Float = 10f) {
        val time = SystemClock.uptimeMillis()
        val events = buildList {
            add(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, startX, startY, 0))
            for (step in 1..10) {
                add(MotionEvent.obtain(time, time + step * 16L, MotionEvent.ACTION_MOVE, startX + dx * step / 10, startY + dy * step / 10, 0))
            }
            add(MotionEvent.obtain(time, time + 400, MotionEvent.ACTION_UP, startX + dx, startY + dy, 0))
        }
        try {
            events.forEach(view::dispatchTouchEvent)
        } finally {
            events.forEach(MotionEvent::recycle)
        }
    }

    private fun rightX() = binding.floatingChatHost.width - MARGIN - 224f

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
    }

    private companion object {
        const val MARGIN = 12f
        const val TOP_BAND = 56f
        const val BOTTOM_BAND = 96
    }
}
