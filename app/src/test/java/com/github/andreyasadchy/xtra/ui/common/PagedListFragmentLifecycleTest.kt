package com.github.andreyasadchy.xtra.ui.common

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class PagedListFragmentLifecycleTest {
    private lateinit var activity: ActivityController<FragmentActivity>
    private val containerId = View.generateViewId()

    @Before fun setup() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        activity.get().setContentView(FrameLayout(activity.get()).apply { id = containerId })
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
    }

    @Test fun `initial insertion preserves position and later top insertions scroll to top`() {
        val fragment = addFragment()
        val recycler = fragment.requireView() as TrackingRecyclerView
        fragment.adapter.notifyItemRangeInserted(0, 1)
        assertTrue(recycler.scrolls.isEmpty())
        fragment.adapter.notifyItemRangeInserted(3, 1)
        assertTrue(recycler.scrolls.isEmpty())
        fragment.adapter.notifyItemRangeInserted(0, 1)
        assertEquals(listOf(0), recycler.scrolls)
    }

    @Test fun `destroy before initial insertion detaches adapter and removes scroll observer`() {
        assertDestroyedViewReleased(insertBeforeDestroy = false)
    }

    @Test fun `destroy after initial insertion releases old view and recreated view still scrolls`() {
        assertDestroyedViewReleased(insertBeforeDestroy = true)
    }

    private fun assertDestroyedViewReleased(insertBeforeDestroy: Boolean) {
        val fragment = addFragment()
        val oldRecycler = fragment.requireView() as TrackingRecyclerView
        val oldOwner = fragment.viewLifecycleOwner
        if (insertBeforeDestroy) fragment.adapter.notifyItemRangeInserted(0, 1)
        activity.get().supportFragmentManager.beginTransaction().detach(fragment).commitNow()
        assertEquals(Lifecycle.State.DESTROYED, oldOwner.lifecycle.currentState)
        assertEquals(Lifecycle.State.CREATED, fragment.lifecycle.currentState)
        assertNull(oldRecycler.adapter)
        repeat(2) { fragment.adapter.notifyItemRangeInserted(0, 1) }
        assertTrue("Destroyed views must not receive retained adapter callbacks", oldRecycler.scrolls.isEmpty())

        activity.get().supportFragmentManager.beginTransaction().attach(fragment).commitNow()
        val newRecycler = fragment.requireView() as TrackingRecyclerView
        assertNotSame(oldRecycler, newRecycler)
        assertSame(fragment.adapter, newRecycler.adapter)
        fragment.adapter.notifyItemRangeInserted(0, 1)
        assertTrue(newRecycler.scrolls.isEmpty())
        fragment.adapter.notifyItemRangeInserted(0, 1)
        assertEquals(listOf(0), newRecycler.scrolls)
        assertTrue(oldRecycler.scrolls.isEmpty())
    }

    private fun addFragment() = TestPagedListFragment().also {
        activity.get().supportFragmentManager.beginTransaction().add(containerId, it).commitNow()
    }

    class TestPagedListFragment : PagedListFragment() {
        override var enableNetworkCheck = false
        val adapter = TestPagingAdapter()

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            TrackingRecyclerView(requireContext())

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setAdapter(view as RecyclerView, adapter)
        }

        override fun initialize() {}
        override fun onNetworkRestored() {}
        override fun onIntegrityDialogCallback(callback: String?) {}
    }

    class TrackingRecyclerView(context: Context) : RecyclerView(context) {
        val scrolls = mutableListOf<Int>()
        override fun scrollToPosition(position: Int) { scrolls += position }
    }

    class TestPagingAdapter : PagingDataAdapter<String, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(View(parent.context)) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
    }
}
