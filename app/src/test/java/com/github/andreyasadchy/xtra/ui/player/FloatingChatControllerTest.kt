package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
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
import java.time.Duration

/** Real card, handles and pinch detector on a 1000x500 landscape mdpi screen (1 dp = 1 px). */
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

    @Before fun setup() {
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
        idle()
        activity.pause().stop().destroy()
    }

    @Test fun `card opens top right with pill and resize grip visible while controls are hidden`() {
        controller.show(animate = false)
        idle()
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        assertEquals(224, binding.floatingChatRoot.width)
        assertEquals(240, binding.floatingChatRoot.height)
        assertEquals(rightX(), binding.floatingChatRoot.x, 0.5f)
        assertEquals(MARGIN, binding.floatingChatRoot.y, 0.5f)
        assertEquals(View.VISIBLE, binding.floatingChatPill.visibility)
        assertEquals(View.VISIBLE, binding.floatingChatGrip.visibility)
        assertEquals(View.INVISIBLE, binding.floatingChatHeader.visibility)
    }

    @Test fun `controls show header buttons without moving, fading or hiding card or grip`() {
        controller.show(animate = false)
        idle()
        val card = binding.floatingChatRoot
        controller.onControlsVisibilityChanged(true)
        idle()
        assertEquals(rightX(), card.x, 0f)
        assertEquals(MARGIN, card.y, 0f)
        assertEquals(1f, card.alpha, 0f)
        assertEquals(View.VISIBLE, binding.floatingChatHeader.visibility)
        assertEquals(1f, binding.floatingChatHeader.alpha, 0f)
        assertEquals(View.INVISIBLE, binding.floatingChatPill.visibility)
        assertEquals(View.VISIBLE, binding.floatingChatGrip.visibility)
        assertEquals(48, binding.floatingChatContainer.paddingTop)

        controller.onControlsVisibilityChanged(false)
        idle()
        // An invisible header never takes touches; pill and grip stay ready.
        assertEquals(View.INVISIBLE, binding.floatingChatHeader.visibility)
        assertEquals(View.VISIBLE, binding.floatingChatPill.visibility)
        assertEquals(View.VISIBLE, binding.floatingChatGrip.visibility)
        assertEquals(0, binding.floatingChatContainer.paddingTop)
        assertEquals(1f, card.alpha, 0f)
        assertEquals(rightX(), card.x, 0f)
        assertEquals(MARGIN, card.y, 0f)
    }

    @Test fun `drag clamps on all four sides including insets and stays exactly where released`() {
        controller.setInsets(FloatingChatInsets(left = 30, right = 20, top = 16, bottom = 40))
        controller.show(animate = false)
        idle()
        val card = binding.floatingChatRoot
        val minX = 30f + MARGIN
        val minY = 16f + MARGIN
        val maxX = hostWidth() - 20f - MARGIN - card.width
        val maxY = hostHeight() - 40f - MARGIN - card.height

        // Left & top clamp
        drag(binding.floatingChatPill, dx = -2000f, dy = -2000f)
        assertEquals(minX, card.x, 0.5f)
        assertEquals(minY, card.y, 0.5f)
        // Grip moved to bottom-right on the left half of the screen.
        assertEquals(Gravity.BOTTOM or Gravity.RIGHT, (binding.floatingChatGrip.layoutParams as FrameLayout.LayoutParams).gravity)

        // Right & bottom clamp
        drag(binding.floatingChatPill, dx = 2000f, dy = 2000f)
        assertEquals(maxX, card.x, 0.5f)
        assertEquals(maxY, card.y, 0.5f)
        assertEquals(Gravity.BOTTOM or Gravity.LEFT, (binding.floatingChatGrip.layoutParams as FrameLayout.LayoutParams).gravity)

        // Mid-screen drop (including 4 px from an edge): stays at the exact release pixel with no animation or magnet.
        val dropX = minX + 4f
        val dropY = minY + 75f
        drag(binding.floatingChatPill, dx = dropX - card.x, dy = dropY - card.y)
        assertEquals(dropX, card.x, 0.5f)
        assertEquals(dropY, card.y, 0.5f)
        idle()
        assertEquals(dropX, card.x, 0.5f)
        assertEquals(dropY, card.y, 0.5f)
    }

    @Test fun `drag from header starting on a button moves the card without firing the button`() {
        controller.show(animate = false)
        controller.onControlsVisibilityChanged(true)
        idle()
        val close = binding.floatingChatClose
        drag(
            binding.floatingChatHeader,
            dx = -300f,
            dy = 80f,
            startX = close.left + close.width / 2f,
            startY = close.top + close.height / 2f,
        )
        assertEquals(0, closeRequests)
        assertEquals(listOf(true, false), holds)
        assertEquals(rightX() - 300f, binding.floatingChatRoot.x, 0.5f)
        assertEquals(MARGIN + 80f, binding.floatingChatRoot.y, 0.5f)

        close.performClick()
        assertEquals(1, closeRequests)
    }

    @Test fun `resize from corner grip works with controls hidden and with controls shown`() {
        controller.show(animate = false)
        idle()
        // Controls hidden: bottom-left grip grows card leftward & downward, keeping right edge fixed.
        drag(binding.floatingChatGrip, dx = -50f, dy = 60f)
        idle()
        assertEquals(274, binding.floatingChatRoot.width)
        assertEquals(300, binding.floatingChatRoot.height)
        assertEquals(hostWidth() - MARGIN, binding.floatingChatRoot.x + 274f, 0.5f)
        assertEquals(MARGIN, binding.floatingChatRoot.y, 0.5f)

        // Controls shown: resize continues to work identically and persists.
        controller.onControlsVisibilityChanged(true)
        idle()
        drag(binding.floatingChatGrip, dx = 30f, dy = -40f)
        idle()
        assertEquals(244, binding.floatingChatRoot.width)
        assertEquals(260, binding.floatingChatRoot.height)
        val stored = FloatingChatLayoutStore.read(prefs.getString(FloatingChatLayoutStore.PREF_KEY, null), "c1")!!
        assertEquals(244f, stored.widthDp, 0.5f)
        assertEquals(260f, stored.heightDp, 0.5f)
    }

    @Test fun `resize clamps to min and max bounds and handles ACTION_CANCEL`() {
        controller.show(animate = false)
        idle()
        // Try to grow way past usable bounds: clamped to max width share and bottom margin.
        drag(binding.floatingChatGrip, dx = -1200f, dy = 1200f)
        idle()
        assertEquals((hostWidth() * FloatingChatPlacement.MAX_WIDTH_SHARE).toInt(), binding.floatingChatRoot.width)
        assertEquals((hostHeight() - 2 * MARGIN).toInt(), binding.floatingChatRoot.height)
        assertTrue(binding.floatingChatRoot.x >= MARGIN)
        assertTrue(binding.floatingChatRoot.y + binding.floatingChatRoot.height <= hostHeight() - MARGIN)

        // Shrink below minimum: clamped to 170x140 dp.
        drag(binding.floatingChatGrip, dx = 1000f, dy = -1000f)
        idle()
        assertEquals(170, binding.floatingChatRoot.width)
        assertEquals(140, binding.floatingChatRoot.height)

        // ACTION_CANCEL during resize restores stored size and releases holdControls.
        holds.clear()
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val move = MotionEvent.obtain(time, time + 16, MotionEvent.ACTION_MOVE, -90f, 100f, 0)
        val cancel = MotionEvent.obtain(time, time + 32, MotionEvent.ACTION_CANCEL, -90f, 100f, 0)
        try {
            binding.floatingChatGrip.dispatchTouchEvent(down)
            binding.floatingChatGrip.dispatchTouchEvent(move)
            idle()
            binding.floatingChatGrip.dispatchTouchEvent(cancel)
        } finally {
            down.recycle()
            move.recycle()
            cancel.recycle()
        }
        assertEquals(listOf(true, false), holds)
        assertEquals(170, binding.floatingChatRoot.width)
        assertEquals(140, binding.floatingChatRoot.height)
    }

    @Test fun `pinch resizes card while controls are hidden and saves on release`() {
        controller.show(animate = false)
        idle()
        assertEquals(View.INVISIBLE, binding.floatingChatHeader.visibility)
        val beforeWidth = binding.floatingChatRoot.width
        val beforeHeight = binding.floatingChatRoot.height

        // Android ScaleGestureDetector requires span >= config_minScalingSpan (~170px at mdpi).
        pinch(binding.floatingChatRoot, startSpan = 190f, endSpan = 285f)
        idle()
        assertTrue("expected width > $beforeWidth, got ${binding.floatingChatRoot.width}", binding.floatingChatRoot.width > beforeWidth)
        assertTrue("expected height > $beforeHeight, got ${binding.floatingChatRoot.height}", binding.floatingChatRoot.height > beforeHeight)
        val stored = FloatingChatLayoutStore.read(prefs.getString(FloatingChatLayoutStore.PREF_KEY, null), "c1")!!
        assertEquals(binding.floatingChatRoot.width.toFloat(), stored.widthDp, 1f)
        assertEquals(binding.floatingChatRoot.height.toFloat(), stored.heightDp, 1f)
        assertEquals(listOf(true, false), holds)
    }

    @Test fun `drag and resize still finish cleanly when controls toggle mid-gesture`() {
        controller.show(animate = false)
        idle()

        // Drag on pill while controls turn on mid-drag.
        drag(binding.floatingChatPill, dx = -250f, dy = 90f) {
            controller.onControlsVisibilityChanged(true)
            idle()
        }
        assertEquals(rightX() - 250f, binding.floatingChatRoot.x, 0.5f)
        assertEquals(MARGIN + 90f, binding.floatingChatRoot.y, 0.5f)
        assertEquals(listOf(true, false), holds)

        // Drag on header across into the left half while controls turn off mid-drag.
        holds.clear()
        val afterFirstX = binding.floatingChatRoot.x
        val afterFirstY = binding.floatingChatRoot.y
        drag(binding.floatingChatHeader, dx = -200f, dy = 40f) {
            controller.onControlsVisibilityChanged(false)
            idle()
        }
        assertEquals(afterFirstX - 200f, binding.floatingChatRoot.x, 0.5f)
        assertEquals(afterFirstY + 40f, binding.floatingChatRoot.y, 0.5f)
        assertEquals(listOf(true, false), holds)

        // Now on the left half (bottom-right grip): resize rightward while controls toggle on and off mid-resize.
        holds.clear()
        drag(binding.floatingChatGrip, dx = 50f, dy = 40f) {
            controller.onControlsVisibilityChanged(true)
            idle()
            controller.onControlsVisibilityChanged(false)
            idle()
        }
        idle()
        assertEquals(274, binding.floatingChatRoot.width)
        assertEquals(280, binding.floatingChatRoot.height)
        assertEquals(listOf(true, false), holds)
    }

    @Test fun `minimize hides at once and maximize restores saved position with fade`() {
        controller.show(animate = false)
        idle()
        drag(binding.floatingChatPill, dx = -260f, dy = 110f)
        val savedX = binding.floatingChatRoot.x
        val savedY = binding.floatingChatRoot.y
        onScreen.clear()

        // Minimize: immediate hide.
        controller.hide(animate = false)
        assertFalse(controller.isShown)
        assertEquals(View.GONE, binding.floatingChatRoot.visibility)
        assertEquals(listOf(false), onScreen)
        assertNull(controller.restingBounds())

        // Maximize: short fade back at saved place.
        controller.show(animate = true)
        assertTrue(controller.isShown)
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        assertEquals(0f, binding.floatingChatRoot.alpha, 0.01f)
        assertEquals(savedX, binding.floatingChatRoot.x, 0.5f)
        assertEquals(savedY, binding.floatingChatRoot.y, 0.5f)
        idle()
        assertEquals(1f, binding.floatingChatRoot.alpha, 0.01f)
        assertEquals(listOf(false, true), onScreen)
    }

    @Test fun `hide fade runs callback once gone and show during fade keeps the card`() {
        controller.show(animate = false)
        idle()
        var hidden = false
        controller.hide(animate = true) { hidden = true }
        assertFalse(hidden)
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        idle()
        assertTrue(hidden)
        assertEquals(View.GONE, binding.floatingChatRoot.visibility)

        // Show interrupts running hide fade: callback still runs and card stays visible.
        controller.show(animate = false)
        hidden = false
        controller.hide(animate = true) { hidden = true }
        controller.show(animate = false)
        assertTrue(hidden)
        idle()
        assertEquals(View.VISIBLE, binding.floatingChatRoot.visibility)
        assertEquals(1f, binding.floatingChatRoot.alpha, 0f)
    }

    @Test fun `rotation or window resize re-places from saved fractions and clamps`() {
        controller.show(animate = false)
        idle()
        drag(binding.floatingChatPill, dx = -100f, dy = 200f)

        // Shrink host to simulate rotation or split-screen window resize.
        binding.floatingChatHost.measure(
            View.MeasureSpec.makeMeasureSpec(520, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
        )
        binding.floatingChatHost.layout(0, 0, 520, 300)
        val card = binding.floatingChatRoot
        assertTrue(card.x >= MARGIN)
        assertTrue(card.x + card.width <= 520f - MARGIN + 0.5f)
        assertTrue(card.y >= MARGIN)
        assertTrue(card.y + card.height <= 300f - MARGIN + 0.5f)
    }

    @Test fun `keyboard inset clamps card above keyboard and restores position when closed`() {
        controller.show(animate = false)
        idle()
        drag(binding.floatingChatPill, dx = 0f, dy = 220f)
        val bottomY = binding.floatingChatRoot.y

        controller.setInsets(FloatingChatInsets(bottom = 300, ime = true))
        val card = binding.floatingChatRoot
        assertTrue(card.y + card.height <= hostHeight() - 300f - MARGIN + 0.5f)

        controller.setInsets(FloatingChatInsets())
        assertEquals(bottomY, card.y, 0.5f)
        assertEquals(240, card.height)
    }

    @Test fun `legacy 1_7_0 prefs migrate once and per-channel layout falls back to last used`() {
        prefs.edit {
            putFloat("floating_chat_x_legacy", 12f)
            putFloat("floating_chat_y_legacy", 200f)
            putInt("floating_chat_w_legacy", 260)
            putInt("floating_chat_h_legacy", 220)
        }
        channel = "legacy"
        val migrated = createController()
        migrated.show(animate = false)
        idle()
        assertFalse(prefs.contains("floating_chat_x_legacy"))
        assertEquals(MARGIN, binding.floatingChatRoot.x, 0.5f)
        assertEquals(200f, binding.floatingChatRoot.y, 0.5f)
        assertEquals(260, binding.floatingChatRoot.width)
        assertEquals(220, binding.floatingChatRoot.height)
        migrated.hide(animate = false)

        // Channel c1 moves to a distinct spot; channel c2 inherits last-used, while legacy keeps its own.
        channel = "c1"
        val first = createController()
        first.show(animate = false)
        idle()
        drag(binding.floatingChatPill, dx = -320f, dy = 100f)
        val c1X = binding.floatingChatRoot.x
        val c1Y = binding.floatingChatRoot.y
        first.hide(animate = false)

        channel = "c2"
        val second = createController()
        second.show(animate = false)
        idle()
        assertEquals(c1X, binding.floatingChatRoot.x, 0.5f)
        assertEquals(c1Y, binding.floatingChatRoot.y, 0.5f)
        second.hide(animate = false)

        channel = "legacy"
        val backToLegacy = createController()
        backToLegacy.show(animate = false)
        idle()
        assertEquals(MARGIN, binding.floatingChatRoot.x, 0.5f)
        assertEquals(260, binding.floatingChatRoot.width)
    }

    @Test fun `gesture slider ducking fades card to 25 percent and clears on hide`() {
        controller.show(animate = false)
        idle()
        controller.setDucked(true)
        idle()
        assertEquals(0.25f, binding.floatingChatRoot.alpha, 0.01f)
        controller.setDucked(false)
        idle()
        assertEquals(1f, binding.floatingChatRoot.alpha, 0.01f)

        controller.setDucked(true)
        idle()
        controller.hide(animate = false)
        controller.show(animate = false)
        assertEquals(1f, binding.floatingChatRoot.alpha, 0.01f)
    }

    @Test fun `composer open hides corner grip and closing composer restores it`() {
        controller.show(animate = false)
        idle()
        assertEquals(View.VISIBLE, binding.floatingChatGrip.visibility)
        controller.setComposerOpen(true)
        assertEquals(View.INVISIBLE, binding.floatingChatGrip.visibility)
        controller.setComposerOpen(false)
        assertEquals(View.VISIBLE, binding.floatingChatGrip.visibility)
    }

    private fun createController() = FloatingChatController(
        host = binding.floatingChatHost,
        card = binding.floatingChatRoot,
        header = binding.floatingChatHeader,
        content = binding.floatingChatContainer,
        pill = binding.floatingChatPill,
        grip = binding.floatingChatGrip,
        composeButton = binding.floatingChatCompose,
        appearanceButton = binding.floatingChatAppearance,
        closeButton = binding.floatingChatClose,
        prefs = prefs,
        callbacks = object : FloatingChatController.Callbacks {
            override fun channelId() = channel
            override fun holdControls(hold: Boolean) {
                holds += hold
            }
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
            add(event(MotionEvent.ACTION_UP, 176, startX + dx, startY + dy))
        }
        try {
            first.forEach(view::dispatchTouchEvent)
            midway()
            rest.forEach(view::dispatchTouchEvent)
        } finally {
            (first + rest).forEach(MotionEvent::recycle)
        }
    }

    /** Dispatches a real two-finger pinch through [view]'s ScaleGestureDetector. */
    private fun pinch(view: View, startSpan: Float, endSpan: Float) {
        val cx = view.width / 2f
        val cy = view.height / 2f
        val time = SystemClock.uptimeMillis()
        val p0 = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val p1 = MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val c0 = MotionEvent.PointerCoords()
        val c1 = MotionEvent.PointerCoords()
        fun twoFinger(action: Int, offset: Long, span: Float): MotionEvent {
            c0.x = cx - span / 2f
            c0.y = cy
            c1.x = cx + span / 2f
            c1.y = cy
            return MotionEvent.obtain(
                time, time + offset, action, 2,
                arrayOf(p0, p1), arrayOf(c0, c1),
                0, 0, 1f, 1f, 0, 0, 0, 0,
            )
        }
        val events = buildList {
            add(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, cx - startSpan / 2f, cy, 0))
            add(twoFinger(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 16, startSpan))
            for (step in 1..6) {
                val span = startSpan + (endSpan - startSpan) * step / 6f
                add(twoFinger(MotionEvent.ACTION_MOVE, 16L + step * 16L, span))
            }
            add(twoFinger(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 128, endSpan))
            add(MotionEvent.obtain(time, time + 144, MotionEvent.ACTION_UP, cx - endSpan / 2f, cy, 0))
        }
        try {
            events.forEach(view::dispatchTouchEvent)
        } finally {
            events.forEach(MotionEvent::recycle)
        }
    }

    private fun hostWidth() = binding.floatingChatHost.width.toFloat()
    private fun hostHeight() = binding.floatingChatHost.height.toFloat()
    private fun rightX() = hostWidth() - MARGIN - 224f

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
    }

    private companion object {
        const val MARGIN = 12f
    }
}
