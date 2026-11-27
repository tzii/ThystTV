package com.github.andreyasadchy.xtra.ui.chat

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentChatBinding
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** #129: a tap on the poll, prediction or room status card must not open the menu of the message behind it. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class ChatPollCardTouchTest {

    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)

    private fun tapReachesMessages(
        setUp: (FragmentChatBinding) -> Unit = {},
        card: (FragmentChatBinding) -> View,
    ): Boolean {
        val binding = FragmentChatBinding.inflate(LayoutInflater.from(context))
        setUp(binding)
        val reached = mutableListOf<Int>()
        binding.recyclerView.setOnTouchListener { _, event ->
            reached += event.actionMasked
            true
        }
        val view = card(binding)
        view.visibility = View.VISIBLE
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
        )
        binding.root.layout(0, 0, 400, 600)
        assertTrue(view.height > 0)
        val x = view.left + view.width / 2f
        val y = view.top + view.height / 2f
        val time = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { i, action ->
            val event = MotionEvent.obtain(time, time + i * 50L, action, x, y, 0)
            binding.root.dispatchTouchEvent(event)
            event.recycle()
        }
        return reached.isNotEmpty()
    }

    @Test fun `a tap on the poll card stays on the card`() {
        assertTrue(!tapReachesMessages { it.pollLayout })
    }

    @Test fun `a tap on the prediction card stays on the card`() {
        assertTrue(!tapReachesMessages { it.predictionLayout })
    }

    @Test fun `a tap on the room status banner stays on the banner`() {
        val shown = { binding: FragmentChatBinding ->
            binding.textSlow.text = "Slow mode"
            binding.textSlow.visibility = View.VISIBLE
        }
        assertTrue(!tapReachesMessages(shown) { it.chatStatus })
    }

    @Test fun `TalkBack offers no click on the cards`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.BaseDarkTheme)
        val binding = FragmentChatBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        listOf(binding.chatStatus, binding.pollLayout, binding.predictionLayout).forEach { view ->
            view.visibility = View.VISIBLE
            ViewCompat.setAccessibilityDelegate(view, TapShieldAccessibility)
            val info = AccessibilityNodeInfoCompat.obtain()
            view.onInitializeAccessibilityNodeInfo(info.unwrap())
            assertFalse(info.isClickable)
            assertFalse(info.actionList.any { it.id == AccessibilityNodeInfoCompat.ACTION_CLICK })
        }
    }
}
