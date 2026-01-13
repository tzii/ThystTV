package com.github.andreyasadchy.xtra.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil3.request.transformations
import coil3.load
import coil3.transform.CircleCropTransformation
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogStreamPickerBinding
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Dialog for selecting a stream to add as secondary in multi-stream mode.
 * Shows followed live streams with search functionality.
 */
@AndroidEntryPoint
class StreamPickerDialog : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_EXCLUDE_CHANNEL_ID = "exclude_channel_id"

        fun newInstance(excludeChannelId: String? = null): StreamPickerDialog {
            return StreamPickerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_EXCLUDE_CHANNEL_ID, excludeChannelId)
                }
            }
        }
    }

    private var _binding: DialogStreamPickerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StreamPickerViewModel by viewModels()

    private var onStreamSelected: ((Stream) -> Unit)? = null

    private val adapter = StreamPickerAdapter { stream ->
        onStreamSelected?.invoke(stream)
        dismiss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogStreamPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expand bottom sheet
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        // Set up RecyclerView
        binding.streamsList.adapter = adapter

        // Set up search
        binding.searchEditText.addTextChangedListener { text ->
            viewModel.setSearchQuery(text?.toString() ?: "")
        }

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.setSearchQuery(binding.searchEditText.text?.toString() ?: "")
                true
            } else false
        }

        // Get exclude channel ID
        val excludeChannelId = arguments?.getString(ARG_EXCLUDE_CHANNEL_ID)

        // Load followed streams
        viewModel.loadFollowedStreams(excludeChannelId)

        // Observe states
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.streams.collectLatest { streams ->
                        adapter.submitList(streams)
                        if (streams.isEmpty() && !viewModel.isLoading.value) {
                            binding.emptyMessage.visible()
                            binding.streamsList.gone()
                        } else {
                            binding.emptyMessage.gone()
                            binding.streamsList.visible()
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collectLatest { isLoading ->
                        binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setOnStreamSelectedListener(listener: (Stream) -> Unit) {
        onStreamSelected = listener
    }

    // ==================== Adapter ====================

    private class StreamPickerAdapter(
        private val onStreamClick: (Stream) -> Unit
    ) : ListAdapter<Stream, StreamPickerAdapter.ViewHolder>(StreamDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_stream_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position), onStreamClick)
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val channelLogo: ImageView = itemView.findViewById(R.id.channelLogo)
            private val channelName: TextView = itemView.findViewById(R.id.channelName)
            private val streamTitle: TextView = itemView.findViewById(R.id.streamTitle)
            private val gameName: TextView = itemView.findViewById(R.id.gameName)
            private val viewerCount: TextView = itemView.findViewById(R.id.viewerCount)
            private val streamThumbnail: ImageView = itemView.findViewById(R.id.streamThumbnail)

            fun bind(stream: Stream, onStreamClick: (Stream) -> Unit) {
                channelName.text = stream.channelName ?: stream.channelLogin
                streamTitle.text = stream.title
                gameName.text = stream.gameName ?: ""
                viewerCount.text = TwitchApiHelper.formatCount(stream.viewerCount ?: 0, true)

                // Load channel logo
                stream.channelLogo?.let { url ->
                    channelLogo.load(url) {
                        transformations(CircleCropTransformation())
                        // Placeholder disabled due to Coil 3 type mismatch (requires Image, not Int)
                    }
                }

                // Load stream thumbnail
                stream.thumbnail?.let { url ->
                    streamThumbnail.load(url) {
                        // Placeholder disabled due to Coil 3 type mismatch (requires Image, not Int)
                    }
                }

                itemView.setOnClickListener {
                    onStreamClick(stream)
                }
            }
        }

        class StreamDiffCallback : DiffUtil.ItemCallback<Stream>() {
            override fun areItemsTheSame(oldItem: Stream, newItem: Stream): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Stream, newItem: Stream): Boolean {
                return oldItem.id == newItem.id &&
                        oldItem.title == newItem.title &&
                        oldItem.viewerCount == newItem.viewerCount
            }
        }
    }
}
