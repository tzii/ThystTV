package com.github.andreyasadchy.xtra.ui.channel

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.InputEvent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.marginTop
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentChannelBinding
import com.github.andreyasadchy.xtra.ui.main.TvNavigationFocus
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
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
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [28], qualifiers = "w960dp-h540dp-land-mdpi")
class ChannelPagerHeaderTest {
    private lateinit var activity: ActivityController<Activity>

    @Before fun setup() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().windowFocusChanged(true)
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
    }

    @Test fun `TV channel tabs stay focused above usable content through Chat and Clips transitions`() {
        val (fragment, binding) = fixture(television = true)
        val params = binding.collapsingToolbar.layoutParams as AppBarLayout.LayoutParams
        val originalFlags = params.scrollFlags
        assertTrue(binding.appBar.totalScrollRange > 0)
        shadowOf(binding.root.context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false)
        assertTrue("Channel entry can initially focus Watch live", binding.watchLive.requestFocus())
        val navigation = TvNavigationFocus(BottomNavigationView(binding.root.context), { binding.viewPager }, { binding.appBar })
        navigation.prepareContent()
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                // The production pager callback delegates to this same transition.
                fragment.updateTabHeader(isChat = tab.position == 1, originalScrollFlags = originalFlags)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        fragment.updateTabHeader(isChat = false, originalScrollFlags = originalFlags)
        settle(binding.root)
        val tabRow = binding.tabLayout.getChildAt(0) as ViewGroup
        assertTrue("Collapse transfers an obscured profile action to the selected visible tab", tabRow.getChildAt(0).isFocused)
        assertFullyVisible(tabRow.getChildAt(0))
        assertFullyVisible(binding.toolbar)
        for ((keyCode, tabIndex) in listOf(KeyEvent.KEYCODE_DPAD_RIGHT to 1, KeyEvent.KEYCODE_DPAD_RIGHT to 2,
            KeyEvent.KEYCODE_DPAD_LEFT to 1, KeyEvent.KEYCODE_DPAD_LEFT to 0)) {
            sendRemoteKey(binding.root, keyCode)
            sendRemoteKey(binding.root, KeyEvent.KEYCODE_DPAD_CENTER)
            val tab = (binding.tabLayout.getChildAt(0) as ViewGroup).getChildAt(tabIndex)
            binding.viewPager.requestRectangleOnScreen(Rect(0, 0, binding.viewPager.width, binding.viewPager.height), false)
            settle(binding.root)
            assertEquals(tabIndex, binding.tabLayout.selectedTabPosition)
            assertTrue("Remote tab focus survives the header transition", tab.isFocused)
            assertEquals(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED, params.scrollFlags)
            assertTrue(binding.appBar.top < 0)
            assertFullyVisible(binding.toolbar)
            assertFullyVisible(tab)
            val content = Rect()
            assertTrue(binding.viewPager.getGlobalVisibleRect(content))
            assertTrue("The compact header leaves most of the browse region for video cards", content.height() >= binding.root.height / 2)
        }
        binding.appBar.setExpanded(true, false)
        settle(binding.root)
        assertTrue(binding.watchLive.requestFocus())
        navigation.prepareContent()
        binding.viewPager.isFocusable = true
        assertTrue(binding.viewPager.requestFocus())
        settle(binding.root)
        assertTrue("The deferred correction must not steal focus moved into content", binding.viewPager.hasFocus())
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
        binding.userCreated.apply { visibility = View.VISIBLE; text = "Created January 2015" }
        binding.userFollowers.apply { visibility = View.VISIBLE; text = "100,000 followers" }
        binding.streamLayout.visibility = View.VISIBLE
        binding.title.apply { visibility = View.VISIBLE; text = "A live stream with a detailed title and featured category" }
        binding.gameName.apply { visibility = View.VISIBLE; text = "Featured category" }
        binding.watchLive.visibility = View.VISIBLE
        listOf("Videos", "Chat", "Clips").forEach { binding.tabLayout.addTab(binding.tabLayout.newTab().setText(it)) }
        activity.get().setContentView(binding.root, ViewGroup.LayoutParams(960, 460))
        settle(binding.root)
        // Match the existing channel callback's toolbar/tab minimum-height geometry.
        binding.toolbarContainer.layoutParams = (binding.toolbarContainer.layoutParams as CollapsingToolbarLayout.LayoutParams).apply {
            bottomMargin = binding.toolbarContainer2.height
        }
        binding.toolbar.layoutParams = binding.toolbar.layoutParams.apply {
            height = binding.toolbarContainer.marginTop + binding.toolbarContainer2.height
        }
        settle(binding.root)
        return fragment to binding
    }

    private fun settle(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(460, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 960, 460)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
    }

    private fun sendRemoteKey(view: View, keyCode: Int) {
        val viewRoot = ReflectionHelpers.callInstanceMethod<Any>(view, "getViewRootImpl")
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            ReflectionHelpers.callInstanceMethod<Unit>(viewRoot, "dispatchInputEvent",
                ReflectionHelpers.ClassParameter.from(InputEvent::class.java, KeyEvent(action, keyCode)))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        }
    }

    private fun assertFullyVisible(view: View) {
        val rect = Rect()
        assertTrue(view.getGlobalVisibleRect(rect))
        assertEquals(view.height, rect.height())
        assertEquals(view.width, rect.width())
    }
}
