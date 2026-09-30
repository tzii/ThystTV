package com.github.andreyasadchy.xtra.ui.stats

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.stats.CategoryWatchTime
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
@Config(application = Application::class, sdk = [28], qualifiers = "w800dp-h450dp-land-television-mdpi")
class StatsTvFocusTest {
    private lateinit var activity: ActivityController<Activity>
    private val context get() = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)

    @Before fun attachWindow() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
    }

    @After fun closeWindow() {
        activity.pause().stop().destroy()
    }

    @Test fun `static and empty cards provide a focus stop without a fake action`() {
        for (item in emptyCards()) {
            val host = FrameLayout(context)
            val card = createCard(host, item)
            host.addView(card)
            activity.get().setContentView(host)
            measure(host)

            assertTrue("${item.cardType} is reachable", card.requestFocus())
            assertTrue(card.isFocused)
            assertFalse("display-only cards have no click action", card.isClickable)
            assertEquals(listOf(card), card.getFocusables(View.FOCUS_FORWARD))
        }
    }

    @Test fun `populated nested lists focus rows and expose their overflow`() {
        val categories = StatsDashboardItem.Categories((1..8).map {
            CategoryWatchTime("category-$it", "Category $it", 60L * it, it)
        })
        val favorites = StatsDashboardItem.FavoriteChannels((1..20).map {
            FavoriteChannelRow("channel-$it", "Channel $it", 60L * it, it, 50, 0.5f)
        })
        for ((item, nestedId, count) in listOf(
            Triple(categories, R.id.categoryLegendRecyclerView, 8),
            Triple(favorites, R.id.favoriteChannelsRecyclerView, 20),
        )) {
            val dashboard = RecyclerView(context).apply {
                layoutManager = GridLayoutManager(context, 1)
                isFocusable = false
                isFocusableInTouchMode = false
                adapter = StatsDashboardAdapter().apply { submitList(listOf(item)) }
            }
            activity.get().setContentView(dashboard)
            measure(dashboard)
            settle()
            measure(dashboard)
            val card = dashboard.findViewHolderForAdapterPosition(0)!!.itemView
            val nested = card.findViewById<RecyclerView>(nestedId)
            assertFalse("the nested list is not a remote stop", nested.isFocusable)
            var focused = nested.findViewHolderForAdapterPosition(0)!!.itemView
            assertTrue(focused.requestFocus())
            settle()

            for (position in 1 until count) {
                // Use the framework/RecyclerView focus search and request path,
                // including its request to scroll the newly focused row into view.
                val candidate = focused.focusSearch(View.FOCUS_DOWN)
                assertNotNull("row $position remains reachable", candidate)
                val next = requireNotNull(candidate)
                assertFalse("focus must not stop on a list container", next is RecyclerView)
                assertTrue("row $position accepts focus", next.requestFocus(View.FOCUS_DOWN))
                settle()
                assertEquals("one remote move advances one row", position, nested.getChildAdapterPosition(next))
                assertTrue(next.isFocused)
                focused = next
            }
            assertTrue("last row is visible", focused.getGlobalVisibleRect(Rect()))
            if (nestedId == R.id.categoryLegendRecyclerView) {
                assertTrue("bounded landscape legend scrolls to later categories", nested.computeVerticalScrollOffset() > 0)
            } else {
                assertTrue("the dashboard scrolls through a tall Favorites card", dashboard.computeVerticalScrollOffset() > 0)
            }
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h450dp-land-mdpi")
    fun `phone cards and display rows retain their existing focus behavior`() {
        val host = FrameLayout(context)
        for (item in emptyCards()) {
            assertFalse("${item.cardType} does not gain remote focus on a phone", createCard(host, item).isFocusable)
        }
        val legendRow = CategoryLegendAdapter().createViewHolder(host, 0).itemView
        val favoriteRow = FavoriteChannelsAdapter().createViewHolder(host, 0).itemView
        for (row in listOf(legendRow, favoriteRow)) {
            assertFalse(row.isFocusable)
            assertNotEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, (row as ViewGroup).descendantFocusability)
        }
    }

    private fun createCard(parent: ViewGroup, item: StatsDashboardItem): View {
        val adapter = StatsDashboardAdapter().apply { submitList(listOf(item)) }
        return adapter.createViewHolder(parent, item.cardType.ordinal).also {
            adapter.bindViewHolder(it, 0)
        }.itemView
    }

    private fun emptyCards(): List<StatsDashboardItem> = listOf(
        StatsDashboardItem.ScreenTime(emptyList(), "0m", "", "0m", "Last 7 days", "0m"),
        StatsDashboardItem.Streak("0", "0"),
        StatsDashboardItem.Categories(emptyList()),
        StatsDashboardItem.Heatmap(emptyList()),
        StatsDashboardItem.FavoriteChannels(emptyList()),
    )

    private fun measure(view: View) {
        view.layoutDirection = View.LAYOUT_DIRECTION_LTR
        view.measure(
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
    }
}
