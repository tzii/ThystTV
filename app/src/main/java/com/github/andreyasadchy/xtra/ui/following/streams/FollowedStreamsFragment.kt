package com.github.andreyasadchy.xtra.ui.following.streams

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.StreamsAdapter
import com.github.andreyasadchy.xtra.ui.common.StreamsCompactAdapter
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.isNetworkAvailable
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FollowedStreamsFragment : PagedListFragment(), Scrollable {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FollowedStreamsViewModel by viewModels()
    private lateinit var pagingAdapter: PagingDataAdapter<Stream, out RecyclerView.ViewHolder>
    /** When the list last finished loading (elapsed realtime). */
    private var loadedAt = SystemClock.elapsedRealtime()
    /** When the timer last fired, so a skipped or failed reload waits a whole interval too. */
    private var attemptedAt = 0L
    private var loading = false
    /** The last load failed (offline, or an integrity check): wait for a pull or a retry instead. */
    private var failed = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = CommonRecyclerViewLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pagingAdapter = if (requireContext().prefs().getString(C.COMPACT_STREAMS, "disabled") != "disabled") {
            StreamsCompactAdapter(this, {
                findNavController().navigate(
                    TopStreamsFragmentDirections.actionGlobalTopFragment(
                        tags = arrayOf(it)
                    )
                )
            })
        } else {
            StreamsAdapter(this, {
                findNavController().navigate(
                    TopStreamsFragmentDirections.actionGlobalTopFragment(
                        tags = arrayOf(it)
                    )
                )
            })
        }
        setAdapter(binding.recyclerView, pagingAdapter)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            if (activity?.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == false) {
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                binding.recyclerView.updatePadding(bottom = insets.bottom)
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.flow.collectLatest { pagingData ->
                    pagingAdapter.submitData(pagingData)
                }
            }
        }
        initializeAdapter(binding, pagingAdapter, enableScrollTopButton = false)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                pagingAdapter.loadStateFlow.distinctUntilChangedBy { it.refresh }.collect { loadState ->
                    val wasLoading = loading
                    loading = loadState.refresh is LoadState.Loading
                    when (loadState.refresh) {
                        is LoadState.Error -> failed = true
                        is LoadState.NotLoading -> {
                            failed = false
                            // Only a load that just finished: the flow repeats its last state to every
                            // new collector, so coming back on screen must not count as a fresh load.
                            if (wasLoading) loadedAt = SystemClock.elapsedRealtime()
                        }
                        is LoadState.Loading -> Unit
                    }
                }
            }
        }
        // Streams that ended leave the list by themselves while it is on screen (#128).
        // RESUMED: the Following pager keeps its other tabs only STARTED.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(FollowedStreamsAutoRefresh.delayUntilDue(maxOf(loadedAt, attemptedAt), SystemClock.elapsedRealtime()))
                    attemptedAt = SystemClock.elapsedRealtime()
                    // Offline, a reload comes back empty and would wipe the list: wait for the network.
                    if (!loading && !failed && requireContext().isNetworkAvailable) refreshQuietly(pagingAdapter)
                }
            }
        }
    }

    override fun scrollToTop() {
        binding.recyclerView.scrollToPosition(0)
    }

    override fun onNetworkRestored() {
        when {
            failed -> pagingAdapter.retry()
            // A load while offline came back empty rather than failing, so retry() does nothing.
            !loading -> refreshQuietly(pagingAdapter)
        }
    }

    override fun onIntegrityDialogCallback(callback: String?) {
        if (callback == "refresh") {
            refresh(pagingAdapter)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
