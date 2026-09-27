package com.github.andreyasadchy.xtra.ui.main

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Looper
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.FocusFinder
import android.view.InputEvent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.UiTestRender
import com.github.andreyasadchy.xtra.ui.stats.StatsCardType
import com.github.andreyasadchy.xtra.ui.stats.StatsDashboardAdapter
import com.github.andreyasadchy.xtra.ui.stats.StatsDashboardItem
import com.github.andreyasadchy.xtra.ui.view.GridRecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
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
@Config(application = Application::class, sdk = [28], qualifiers = "w1280dp-h720dp-mdpi")
class TvNavigationFocusTest {
    private lateinit var activity: ActivityController<Activity>

    @Before fun attachWindow() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().windowFocusChanged(true)
        assertTrue("remote input requires a focused window", activity.get().window.decorView.hasWindowFocus())
    }

    @After fun closeWindow() {
        activity.pause().stop().destroy()
    }

    @Test fun `selected destination receives focus in a reordered menu`() {
        val fixture = fixture()
        fixture.navBar.selectedItemId = R.id.rootTopFragment
        assertTrue(fixture.helper.focusNavigation())
        assertTrue(fixture.item(R.id.rootTopFragment).isFocused)
        assertTrue(fixture.helper.isNavigationFocused())
        assertFalse(fixture.item(R.id.savedPagerFragment).isFocused)
        assertFalse("empty full-screen player host cannot trap remote focus", fixture.root.findViewById<View>(R.id.playerContainer).isFocusable)
    }

    @Test fun `disabled and hidden destinations are skipped`() {
        val fixture = fixture()
        fixture.navBar.selectedItemId = R.id.rootTopFragment
        fixture.navBar.menu.findItem(R.id.rootTopFragment).isVisible = false
        fixture.navBar.menu.findItem(R.id.savedPagerFragment).isEnabled = false
        fixture.helper.install()
        fixture.measure()
        assertTrue(fixture.helper.focusNavigation())
        assertTrue(fixture.item(R.id.followPagerFragment).isFocused)
    }

    @Test fun `all hidden destinations leave content focus intact`() {
        val fixture = fixture()
        assertTrue(fixture.first.requestFocus())
        for (index in 0 until fixture.navBar.menu.size()) {
            fixture.navBar.menu.getItem(index).isVisible = false
        }
        fixture.helper.install()
        fixture.measure()
        assertFalse(fixture.helper.focusNavigation())
        assertFalse(fixture.helper.isNavigationFocused())
        assertTrue(fixture.first.isFocused)
    }

    @Test fun `focus does not select a destination until center activation`() {
        val fixture = fixture()
        fixture.navBar.selectedItemId = R.id.rootTopFragment
        var selected: Int? = null
        fixture.navBar.setOnItemSelectedListener { selected = it.itemId; true }
        val following = fixture.item(R.id.followPagerFragment)
        assertTrue(following.requestFocus())
        assertNull(selected)
        assertEquals(R.id.rootTopFragment, fixture.navBar.selectedItemId)
        following.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        following.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(R.id.followPagerFragment, selected)
        assertEquals(R.id.followPagerFragment, fixture.navBar.selectedItemId)
    }

    @Test fun `horizontal and down keys stay in navigation despite a clipped competing feed control`() {
        val fixture = fixture()
        fixture.navBar.selectedItemId = R.id.rootTopFragment
        val popular = fixture.item(R.id.rootTopFragment)
        val itemBounds = Rect(0, 0, popular.width, popular.height)
        fixture.root.offsetDescendantRectToMyCoords(popular, itemBounds)
        val hostBounds = Rect(0, 0, fixture.host.width, fixture.host.height)
        fixture.root.offsetDescendantRectToMyCoords(fixture.host, hostBounds)
        val clippedTag = button(fixture.root.context, "Clipped feed tag")
        fixture.host.addView(clippedTag, FrameLayout.LayoutParams(32, 32).apply {
            leftMargin = itemBounds.right - hostBounds.left - 16
            topMargin = itemBounds.centerY() - hostBounds.top - 16
        })
        fixture.measure()
        assertTrue(clippedTag.isAttachedToWindow)
        assertTrue(clippedTag.isShown)
        assertFalse("feed tag lies below its clipped content host", clippedTag.getGlobalVisibleRect(Rect()))
        assertTrue(fixture.helper.focusNavigation())
        assertSame("ordinary geometric navigation would enter the clipped feed", clippedTag,
            FocusFinder.getInstance().findNextFocus(fixture.root, popular, View.FOCUS_RIGHT))

        fixture.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(fixture.item(R.id.followPagerFragment).isFocused)
        fixture.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        fixture.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue("right and bottom boundaries retain the final menu item", fixture.item(R.id.followPagerFragment).isFocused)
        fixture.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue(popular.isFocused)
        fixture.press(KeyEvent.KEYCODE_DPAD_LEFT)
        fixture.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue("menu traversal follows its reordered items and clamps at the start", fixture.item(R.id.savedPagerFragment).isFocused)
        assertFalse(clippedTag.hasFocus())
        assertEquals("moving focus does not navigate", R.id.rootTopFragment, fixture.navBar.selectedItemId)
    }

    @Test fun `horizontal navigation skips both disabled and hidden destinations`() {
        for (hidden in listOf(false, true)) {
            val fixture = fixture()
            fixture.navBar.selectedItemId = R.id.savedPagerFragment
            fixture.navBar.menu.findItem(R.id.rootTopFragment).apply {
                isVisible = !hidden
                isEnabled = hidden
            }
            fixture.helper.install()
            fixture.measure()
            assertTrue(fixture.helper.focusNavigation())
            fixture.press(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertTrue(fixture.item(R.id.followPagerFragment).isFocused)
            fixture.press(KeyEvent.KEYCODE_DPAD_LEFT)
            assertTrue(fixture.item(R.id.savedPagerFragment).isFocused)
            assertEquals(R.id.savedPagerFragment, fixture.navBar.selectedItemId)
        }
    }

    @Test fun `horizontal navigation follows the visible direction in RTL`() {
        val fixture = fixture()
        fixture.root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        fixture.navBar.layoutDirection = View.LAYOUT_DIRECTION_RTL
        fixture.measure()
        fixture.navBar.selectedItemId = R.id.rootTopFragment
        assertTrue(fixture.item(R.id.savedPagerFragment).left > fixture.item(R.id.rootTopFragment).left)
        assertTrue(fixture.helper.focusNavigation())
        fixture.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue(fixture.item(R.id.followPagerFragment).isFocused)
        fixture.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue(fixture.item(R.id.followPagerFragment).isFocused)
        fixture.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(fixture.item(R.id.rootTopFragment).isFocused)
        fixture.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        fixture.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(fixture.item(R.id.savedPagerFragment).isFocused)
        assertEquals(R.id.rootTopFragment, fixture.navBar.selectedItemId)
    }

    @Test fun `up from navigation restores the remembered content control`() {
        val fixture = fixture()
        assertTrue(fixture.second.requestFocus())
        fixture.helper.rememberContentFocus(fixture.second)
        assertTrue(fixture.helper.focusNavigation())
        val navItem = fixture.navBar.findFocus()!!
        fixture.helper.rememberContentFocus(navItem)
        fixture.helper.rememberContentFocus(null)
        assertTrue(navItem.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
        assertTrue(fixture.second.isFocused)
        assertFalse(fixture.helper.isNavigationFocused())
    }

    @Test fun `removed content control cannot be restored`() {
        val fixture = fixture()
        fixture.helper.rememberContentFocus(fixture.second)
        fixture.content.removeView(fixture.second)
        fixture.helper.focusNavigation()
        assertTrue(fixture.helper.focusContent())
        assertTrue(fixture.first.isFocused)
        assertFalse(fixture.second.isAttachedToWindow)
    }

    @Test fun `retained offscreen page does not receive current page focus`() {
        val fixture = fixture()
        fixture.helper.rememberContentFocus(fixture.second)
        val currentPage = LinearLayout(fixture.root.context).apply { orientation = LinearLayout.VERTICAL }
        val currentButton = button(fixture.root.context, "Current page")
        currentPage.addView(currentButton, LinearLayout.LayoutParams(300, 64))
        fixture.host.addView(currentPage, FrameLayout.LayoutParams(-1, -1))
        fixture.contentRoot = currentPage
        fixture.measure()
        assertTrue("old page remains attached, as retained ViewPager pages can", fixture.second.isAttachedToWindow)
        assertTrue(fixture.second.isShown)
        fixture.helper.focusNavigation()
        assertTrue(fixture.helper.focusContent())
        assertTrue(currentButton.isFocused)
        assertFalse(fixture.second.isFocused)
    }

    @Test fun `hidden remembered control falls back to visible content`() {
        val fixture = fixture()
        fixture.helper.rememberContentFocus(fixture.second)
        fixture.second.visibility = View.GONE
        fixture.helper.focusNavigation()
        assertTrue(fixture.helper.focusContent())
        assertTrue(fixture.first.isFocused)
    }

    @Test fun `empty or detached active content keeps navigation reachable`() {
        val fixture = fixture()
        fixture.content.removeAllViews()
        assertTrue(fixture.helper.focusNavigation())
        assertFalse(fixture.helper.focusContent())
        assertTrue(fixture.helper.isNavigationFocused())
        fixture.host.removeView(fixture.content)
        assertFalse(fixture.helper.focusContent())
        assertTrue(fixture.helper.isNavigationFocused())
    }

    @Test fun `empty pager content can return to its header controls`() {
        val fixture = fixture()
        fixture.content.removeAllViews()
        val header = LinearLayout(fixture.root.context)
        val headerButton = button(fixture.root.context, "Page tab")
        header.addView(headerButton, LinearLayout.LayoutParams(300, 64))
        fixture.host.addView(header, FrameLayout.LayoutParams(-1, 64))
        fixture.fallbackRoot = header
        fixture.measure()
        fixture.helper.focusNavigation()
        assertTrue(fixture.helper.focusContent())
        assertTrue(headerButton.isFocused)
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-television-mdpi")
    fun `empty inflated TV list returns to the real pager header`() {
        val fixture = fixture()
        shadowOf(fixture.root.context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        val inflater = LayoutInflater.from(fixture.root.context)
        val page = inflater.inflate(R.layout.common_recycler_view_layout, null).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -1)
            findViewById<View>(R.id.nothingHere).visibility = View.VISIBLE
        }
        val pagerRoot = inflater.inflate(R.layout.fragment_media_pager, fixture.host, false)
        val tabs = pagerRoot.findViewById<TabLayout>(R.id.tabLayout)
        tabs.addTab(tabs.newTab().setText("Channels"))
        tabs.addTab(tabs.newTab().setText("Videos"))
        pagerRoot.findViewById<ViewPager2>(R.id.viewPager).adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 1
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(page) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        }
        fixture.host.removeAllViews()
        fixture.host.addView(pagerRoot)
        fixture.contentRoot = page
        val header = pagerRoot.findViewById<ViewGroup>(R.id.appBar)
        fixture.fallbackRoot = header
        fixture.measure()
        // The newly attached pager schedules its RecyclerView's first adapter
        // layout on a frame; complete that traversal before testing page focus.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        fixture.measure()
        val list = page.findViewById<GridRecyclerView>(R.id.recyclerView)
        assertTrue(page.isAttachedToWindow)
        assertFalse("the empty TV list is not a remote stop", list.isFocusable)
        assertTrue(fixture.helper.focusNavigation())
        assertTrue("Material's app bar starts with touch focus blocking", header.touchscreenBlocksFocus)
        assertTrue("touchscreen-equipped TV cannot enter the unfocused header cluster", header.getFocusables(View.FOCUS_FORWARD).isEmpty())
        assertTrue(fixture.helper.focusContent())
        assertFalse(header.touchscreenBlocksFocus)
        assertTrue("UP reaches a visible pager header control", fixture.root.findFocus()?.isWithin(header) == true)
        assertFalse(list.hasFocus())
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-television-mdpi")
    fun `intentional static stats card remains an eligible content target`() {
        val fixture = fixture()
        fixture.content.removeAllViews()
        val card = StatsDashboardAdapter().onCreateViewHolder(fixture.content, StatsCardType.SCREEN_TIME.ordinal).itemView
        fixture.content.addView(card, LinearLayout.LayoutParams(-1, -2))
        fixture.measure()
        assertTrue(card.isFocusable)
        assertFalse("reading a chart does not require a fake click action", card.isClickable)
        assertTrue(fixture.helper.focusNavigation())
        assertTrue(fixture.helper.focusContent())
        assertTrue(card.isFocused)
        fixture.helper.rememberContentFocus(card)
        assertTrue(fixture.helper.focusNavigation())
        assertTrue(fixture.helper.focusContent())
        assertTrue(card.isFocused)
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-television-mdpi")
    fun `native up from a stats card reaches ranges on a TV with touchscreen support`() {
        val fixture = fixture()
        shadowOf(fixture.root.context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        val stats = LayoutInflater.from(fixture.root.context).inflate(R.layout.fragment_stats, fixture.host, false)
        val list = stats.findViewById<RecyclerView>(R.id.statsRecyclerView).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            layoutManager = LinearLayoutManager(context)
            adapter = StatsDashboardAdapter().apply {
                submitList(listOf(StatsDashboardItem.ScreenTime(emptyList(), "0m", "", "0m", "Last 7 days", "0m")))
            }
        }
        fixture.host.removeAllViews()
        fixture.host.addView(stats)
        fixture.contentRoot = stats
        fixture.fallbackRoot = stats.findViewById(R.id.appBar)
        fixture.measure()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        fixture.measure()
        assertTrue(fixture.helper.focusNavigation())
        assertTrue(fixture.helper.focusContent())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        fixture.measure()
        val card = list.findViewHolderForAdapterPosition(0)!!.itemView
        assertTrue(card.requestFocus())
        sendRemoteKey(fixture.root, KeyEvent.KEYCODE_DPAD_UP)
        val above = fixture.root.findFocus()!!
        assertTrue(above.id in listOf(R.id.range7Days, R.id.range30Days, R.id.rangeAllTime))
        assertFullyVisible(above)
        sendRemoteKey(fixture.root, KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(card.isFocused)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertFullyVisible(stats.findViewById(R.id.range7Days))
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-television-mdpi")
    fun `remote tab activation keeps the entire pager header visible`() {
        val fixture = fixture()
        val inflater = LayoutInflater.from(fixture.root.context)
        val pagerRoot = inflater.inflate(R.layout.fragment_media_pager, fixture.host, false)
        val pager = pagerRoot.findViewById<ViewPager2>(R.id.viewPager)
        val tabs = pagerRoot.findViewById<TabLayout>(R.id.tabLayout)
        val header = pagerRoot.findViewById<AppBarLayout>(R.id.appBar)
        pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 3
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val page = inflater.inflate(R.layout.common_recycler_view_layout, parent, false)
                return object : RecyclerView.ViewHolder(page) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        }
        val mediator = TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = listOf("Games", "Live", "Channels")[position]
        }.also { it.attach() }
        try {
            fixture.host.removeAllViews()
            fixture.host.addView(pagerRoot)
            fixture.contentRoot = pager
            fixture.fallbackRoot = header
            // Match the destination-change preparation before the first remote key.
            fixture.helper.prepareContent()
            fixture.measure()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            fixture.measure()
            assertTrue(fixture.helper.focusNavigation())
            sendRemoteKey(fixture.root, KeyEvent.KEYCODE_DPAD_UP)
            assertEquals(R.id.search, fixture.root.findFocus()!!.id)
            sendRemoteKey(fixture.root, KeyEvent.KEYCODE_DPAD_DOWN)
            val channels = (tabs.getChildAt(0) as ViewGroup).getChildAt(2)
            assertTrue(channels.isFocused)
            sendRemoteKey(fixture.root, KeyEvent.KEYCODE_DPAD_CENTER)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            assertEquals(2, pager.currentItem)
            assertTrue(channels.isFocused)
            pager.requestRectangleOnScreen(Rect(0, 0, pager.width, pager.height), false)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            assertFullyVisible(channels)
            assertFullyVisible(header)
        } finally {
            mediator.detach()
        }
    }

    @Test fun `focused destination remains distinct from checked destination in both themes`() {
        for ((theme, name) in listOf(R.style.BaseDarkTheme to "dark", R.style.BaseLightTheme to "light")) {
            val fixture = fixture(theme)
            fixture.navBar.selectedItemId = R.id.rootTopFragment
            val following = fixture.item(R.id.followPagerFragment)
            assertTrue(following.requestFocus())
            assertTrue(following.hasFocus())
            assertFalse(fixture.navBar.menu.findItem(R.id.followPagerFragment).isChecked)
            assertTrue(fixture.navBar.menu.findItem(R.id.rootTopFragment).isChecked)
            assertNotNull(following.foreground)
            assertTrue(following.foreground.state.contains(android.R.attr.state_focused))
            assertFalse(fixture.item(R.id.rootTopFragment).foreground.state.contains(android.R.attr.state_focused))
            UiTestRender.save(fixture.root, "tv-navigation-focus-$name")
        }
    }

    private fun fixture(theme: Int = R.style.BaseDarkTheme): Fixture {
        val context = ContextThemeWrapper(activity.get(), theme)
        val inflater = LayoutInflater.from(context).cloneInContext(context)
        // Keep the production navigation and overlay layout, without starting the
        // real Hilt/network-backed navigation fragments in this focus-only fixture.
        inflater.factory2 = object : LayoutInflater.Factory2 {
            override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? =
                if (name == "androidx.fragment.app.FragmentContainerView") FrameLayout(context, attrs) else null

            override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
                onCreateView(null, name, context, attrs)
        }
        val root = inflater.inflate(R.layout.activity_main, null) as ViewGroup
        val host = root.findViewById<FrameLayout>(R.id.navHostFragment)
        val navBar = root.findViewById<BottomNavigationView>(R.id.navBar)
        listOf(
            R.id.savedPagerFragment to R.string.saved,
            R.id.rootTopFragment to R.string.popular,
            R.id.followPagerFragment to R.string.following,
        ).forEachIndexed { index, (id, label) ->
            navBar.menu.add(Menu.NONE, id, index, label).setIcon(R.drawable.ic_games_black_24dp)
        }
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val first = button(context, "First item")
        val second = button(context, "Last focused item")
        content.addView(first, LinearLayout.LayoutParams(300, 64))
        content.addView(second, LinearLayout.LayoutParams(300, 64))
        host.addView(content, FrameLayout.LayoutParams(-1, -1))
        activity.get().setContentView(root)
        return Fixture(root, host, navBar, content, first, second).also {
            it.helper.install()
            it.measure()
        }
    }

    private fun button(context: Context, label: String) = Button(context).apply {
        id = View.generateViewId()
        text = label
        isFocusable = true
    }

    private fun sendRemoteKey(view: View, keyCode: Int) {
        val viewRoot = ReflectionHelpers.callInstanceMethod<Any>(view, "getViewRootImpl")
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            // Enter the Android input pipeline, including native directional focus search.
            ReflectionHelpers.callInstanceMethod<Unit>(viewRoot, "dispatchInputEvent",
                ReflectionHelpers.ClassParameter.from(InputEvent::class.java, KeyEvent(action, keyCode)))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        }
    }

    private fun assertFullyVisible(view: View) {
        val visible = Rect()
        assertTrue(view.getGlobalVisibleRect(visible))
        assertEquals("focused control must not be clipped vertically", view.height, visible.height())
        assertEquals("focused control must not be clipped horizontally", view.width, visible.width())
    }

    private class Fixture(
        val root: ViewGroup,
        val host: FrameLayout,
        val navBar: BottomNavigationView,
        val content: LinearLayout,
        val first: Button,
        val second: Button,
    ) {
        var contentRoot: View? = content
        var fallbackRoot: View? = null
        val helper = TvNavigationFocus(navBar, contentRoot = { contentRoot }, fallbackRoot = { fallbackRoot })
        fun item(id: Int): View = navBar.findViewById(id)
        fun press(keyCode: Int) {
            assertTrue(root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)))
            assertTrue(root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode)))
        }
        fun measure() {
            root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1280, 720)
        }
    }
}
