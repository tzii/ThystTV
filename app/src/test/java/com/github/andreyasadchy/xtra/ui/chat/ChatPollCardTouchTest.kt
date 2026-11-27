package com.github.andreyasadchy.xtra.ui.chat

import android.app.Application
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentChatBinding
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** #129: a tap on the poll or prediction card must not open the menu of the message behind it. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class ChatPollCardTouchTest {

    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)

    private fun tapReachesMessages(card: (FragmentChatBinding) -> View): Boolean {
        val binding = FragmentChatBinding.inflate(LayoutInflater.from(context))
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
}
