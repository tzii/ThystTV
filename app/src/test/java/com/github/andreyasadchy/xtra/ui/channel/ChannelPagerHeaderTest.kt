package com.github.andreyasadchy.xtra.ui.channel

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentChannelBinding
import com.google.android.material.appbar.AppBarLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [28], qualifiers = "w960dp-h540dp-land-mdpi")
class ChannelPagerHeaderTest {
    private lateinit var activity: ActivityController<Activity>

    @Before fun setup() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
    }

    @Test fun `TV channel header stays visible through Videos Chat and Clips transitions`() {
        val (fragment, binding) = fixture(television = true)
        val params = binding.collapsingToolbar.layoutParams as AppBarLayout.LayoutParams
        val originalFlags = params.scrollFlags
        assertTrue(binding.appBar.totalScrollRange > 0)
        binding.appBar.setExpanded(false, false)
        settle(binding.root)
        assertTrue("The fixture must reproduce a collapsed channel header", binding.appBar.top < 0)

        for (tabIndex in listOf(0, 1, 2, 1, 0)) {
            // Exercise the same production transition invoked by onPageSelected.
            fragment.updateTabHeader(isChat = tabIndex == 1, originalScrollFlags = originalFlags)
            binding.tabLayout.getTabAt(tabIndex)!!.select()
            settle(binding.root)
            val tab = (binding.tabLayout.getChildAt(0) as ViewGroup).getChildAt(tabIndex)
            binding.viewPager.requestRectangleOnScreen(Rect(0, 0, binding.viewPager.width, binding.viewPager.height), false)
            settle(binding.root)
            assertEquals(0, params.scrollFlags)
            assertEquals(0, binding.appBar.totalScrollRange)
            assertFullyVisible(binding.appBar)
            assertFullyVisible(tab)
        }
        fragment.onDestroyView()
    }

    @Test fun `phone channel tabs preserve original scrolling and Chat collapse behavior`() {
        val (fragment, binding) = fixture(television = false)
        val params = binding.collapsingToolbar.layoutParams as AppBarLayout.LayoutParams
        val originalFlags = params.scrollFlags
        fragment.updateTabHeader(isChat = false, originalScrollFlags = originalFlags)
        settle(binding.root)
        assertEquals(originalFlags, params.scrollFlags)
        assertTrue(binding.appBar.totalScrollRange > 0)

        fragment.updateTabHeader(isChat = true, originalScrollFlags = originalFlags)
        settle(binding.root)
        assertEquals(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL, params.scrollFlags)
        assertTrue(binding.appBar.top < 0)
        assertNull(binding.appBar.background)

        fragment.updateTabHeader(isChat = false, originalScrollFlags = originalFlags)
        settle(binding.root)
        assertEquals(originalFlags, params.scrollFlags)
        assertTrue(binding.appBar.totalScrollRange > 0)
        fragment.onDestroyView()
    }

    private fun fixture(television: Boolean): Pair<ChannelPagerFragment, FragmentChannelBinding> {
        val configuration = Configuration(activity.get().resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                if (television) Configuration.UI_MODE_TYPE_TELEVISION else Configuration.UI_MODE_TYPE_NORMAL
        }
        val context = ContextThemeWrapper(activity.get().createConfigurationContext(configuration), R.style.BaseDarkTheme)
        val fragment = ChannelPagerFragment()
        val binding = FragmentChannelBinding.bind(fragment.onCreateView(LayoutInflater.from(context), null, null))
        binding.userLayout.visibility = View.VISIBLE
        binding.userImage.visibility = View.VISIBLE
        binding.userName.apply { visibility = View.VISIBLE; text = "Channel" }
        listOf("Videos", "Chat", "Clips").forEach { binding.tabLayout.addTab(binding.tabLayout.newTab().setText(it)) }
        activity.get().setContentView(binding.root)
        settle(binding.root)
        return fragment to binding
    }

    private fun settle(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 960, 540)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
    }

    private fun assertFullyVisible(view: View) {
        val rect = Rect()
        assertTrue(view.getGlobalVisibleRect(rect))
        assertEquals(view.height, rect.height())
        assertEquals(view.width, rect.width())
    }
}
