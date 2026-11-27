package com.github.andreyasadchy.xtra.ui.common

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class PagedListFragment : BaseNetworkFragment(), IntegrityDialog.CallbackListener {

    /**
     * A reload the app started by itself: no refresh spinner, and the list
     * only jumps to a new first item when it was already at the top.
     */
    private var quietRefresh = false
    private var quietRefreshStarted = false

    /** Reloads [adapter] in the background: like a pull to refresh, without the spinner. */
    protected fun refreshQuietly(adapter: PagingDataAdapter<*, *>) {
        quietRefresh = true
        quietRefreshStarted = false
        adapter.refresh()
    }

    /** Reloads [adapter] like a pull to refresh, ending any quiet reload. */
    protected fun refresh(adapter: PagingDataAdapter<*, *>) {
        quietRefresh = false
        quietRefreshStarted = false
        adapter.refresh()
    }

    fun <T : Any, VH : RecyclerView.ViewHolder> setAdapter(recyclerView: RecyclerView, adapter: PagingDataAdapter<T, VH>) {
        var firstInsertion = true
        val observer = object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (firstInsertion) {
                    firstInsertion = false
                } else {
                    try {
                        if (positionStart == 0 && (!quietRefresh || !recyclerView.canScrollVertically(-1))) {
                            recyclerView.scrollToPosition(0)
                        }
                    } catch (e: Exception) {

                    }
                }
            }
        }
        adapter.registerAdapterDataObserver(observer)
        recyclerView.adapter = adapter
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                adapter.unregisterAdapterDataObserver(observer)
                if (recyclerView.adapter === adapter) {
                    recyclerView.adapter = null
                }
                owner.lifecycle.removeObserver(this)
            }
        })
    }

    fun shouldShowButton(recyclerView: RecyclerView): Boolean {
        val offset = recyclerView.computeVerticalScrollOffset()
        if (offset < 0) {
            return false
        }
        val extent = recyclerView.computeVerticalScrollExtent()
        val range = recyclerView.computeVerticalScrollRange()
        val percentage = (100f * offset / (range - extent).toFloat())
        return percentage > 3f
    }

    fun <T : Any, VH : RecyclerView.ViewHolder> initializeAdapter(binding: CommonRecyclerViewLayoutBinding, pagingAdapter: PagingDataAdapter<T, VH>, enableSwipeRefresh: Boolean = true, enableScrollTopButton: Boolean = true) {
        with(binding) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        pagingAdapter.loadStateFlow.collectLatest { loadState ->
                            if (quietRefresh) {
                                if (loadState.refresh is LoadState.Loading) {
                                    quietRefreshStarted = true
                                } else if (quietRefreshStarted) {
                                    quietRefresh = false
                                    quietRefreshStarted = false
                                }
                            }
                            progressBar.isVisible = loadState.refresh is LoadState.Loading && pagingAdapter.itemCount == 0 && !quietRefresh
                            if (enableSwipeRefresh) {
                                swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading && pagingAdapter.itemCount != 0 && !quietRefresh
                            }
                            // A quiet reload of an empty list keeps "Nothing here" instead of flashing a spinner.
                            nothingHere.isVisible = (loadState.refresh !is LoadState.Loading || quietRefresh) && pagingAdapter.itemCount == 0
                            if ((loadState.refresh as? LoadState.Error ?:
                                loadState.append as? LoadState.Error ?:
                                loadState.prepend as? LoadState.Error)?.error?.message == "failed integrity check" &&
                                requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) &&
                                requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
                            ) {
                                IntegrityDialog.show(childFragmentManager, "refresh")
                            }
                        }
                    } finally {
                        // Stopped before the quiet reload was seen to start or end: later reloads are not quiet.
                        quietRefresh = false
                        quietRefreshStarted = false
                    }
                }
            }
            if (enableSwipeRefresh) {
                swipeRefresh.isEnabled = true
                swipeRefresh.setOnRefreshListener {
                    refresh(pagingAdapter)
                }
            }
            if (enableScrollTopButton && requireContext().prefs().getBoolean(C.UI_SCROLLTOP, true)) {
                recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        scrollTop.isVisible = shouldShowButton(recyclerView)
                    }
                })
                scrollTop.setOnClickListener {
                    (parentFragment as? Scrollable)?.scrollToTop()
                    it.visibility = View.GONE
                }
            }
        }
        if (requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) &&
            requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true) &&
            TwitchApiHelper.isIntegrityTokenExpired(requireContext())
        ) {
            IntegrityDialog.show(childFragmentManager, "refresh")
        }
    }
}
