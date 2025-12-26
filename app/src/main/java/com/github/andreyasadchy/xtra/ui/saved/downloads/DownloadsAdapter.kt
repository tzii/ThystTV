package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.content.ContentResolver
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentDownloadsListItemBinding
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.convertDpToPixels
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible
import kotlin.math.min

class DownloadsAdapter(
    private val fragment: Fragment,
    private val checkDownloadStatus: (OfflineVideo) -> Unit,
    private val stopDownload: (OfflineVideo) -> Unit,
    private val resumeDownload: (OfflineVideo) -> Unit,
    private val convertVideo: (OfflineVideo) -> Unit,
    private val moveVideo: (OfflineVideo) -> Unit,
    private val updateChatUrl: (OfflineVideo) -> Unit,
    private val shareVideo: (OfflineVideo) -> Unit,
    private val deleteVideo: (OfflineVideo) -> Unit,
) : PagingDataAdapter<OfflineVideo, DownloadsAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<OfflineVideo>() {
        override fun areItemsTheSame(oldItem: OfflineVideo, newItem: OfflineVideo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: OfflineVideo, newItem: OfflineVideo): Boolean {
            return false //bug, oldItem and newItem are sometimes the same
        }
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagingViewHolder {
        val binding = FragmentDownloadsListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PagingViewHolder(binding, fragment)
    }

    override fun onBindViewHolder(holder: PagingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PagingViewHolder(
        private val binding: FragmentDownloadsListItemBinding,
        private val fragment: Fragment,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OfflineVideo?) {
            with(binding) {
                if (item != null) {
                    val context = fragment.requireContext()
                    val channelListener: (View) -> Unit = {
                        fragment.findNavController().navigate(
                            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                                channelId = item.channelId,
                                channelLogin = item.channelLogin,
                                channelName = item.channelName,
                                channelLogo = item.channelLogo,
                                updateLocal = true
                            )
                        )
                    }
                    val gameListener: (View) -> Unit = {
                        fragment.findNavController().navigate(
                            if (context.prefs().getBoolean(C.UI_GAMEPAGER, true)) {
                                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                    gameId = item.gameId,
                                    gameSlug = item.gameSlug,
                                    gameName = item.gameName
                                )
                            } else {
                                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                    gameId = item.gameId,
                                    gameSlug = item.gameSlug,
                                    gameName = item.gameName
                                )
                            }
                        )
                    }
                    root.setOnClickListener {
                        (fragment.activity as MainActivity).startOfflineVideo(item)
                    }
                    root.setOnLongClickListener { deleteVideo(item); true }
                    if (item.thumbnail?.toUri()?.scheme == ContentResolver.SCHEME_CONTENT) {
                    fragment.requireContext().imageLoader.enqueue(
                        ImageRequest.Builder(fragment.requireContext()).apply {
                            data(item.thumbnail)
                            diskCachePolicy(CachePolicy.DISABLED)
                            crossfade(true)
                            target(thumbnail)
                        }.build()
                    )
                        date.visible()
                        date.text = context.getString(R.string.uploaded_date, TwitchApiHelper.formatTime(context, it))
                    } ?: {
                        date.gone()
                    }
                    if (item.downloadDate != null) {
                        downloadDate.visible()
                        downloadDate.text = context.getString(R.string.downloaded_date, TwitchApiHelper.formatTime(context, item.downloadDate))
                    } else {
                        downloadDate.gone()
                    }
                    if (item.type != null) {
                        val text = TwitchApiHelper.getType(context, item.type)
                        if (text != null) {
                            type.visible()
                            type.text = text
                        } else {
                            type.gone()
                        }
                    } else {
                        type.gone()
                    }
                    if (item.channelLogo != null) {
                        userImage.visible()
                        fragment.requireContext().imageLoader.enqueue(
                            ImageRequest.Builder(fragment.requireContext()).apply {
                                data(item.channelLogo)
                                diskCachePolicy(CachePolicy.DISABLED)
                                if (context.prefs().getBoolean(C.UI_ROUNDUSERIMAGE, true)) {
                                    transformations(CircleCropTransformation())
                                }
                                crossfade(true)
                                target(userImage)
                            }.build()
                        )
                        userImage.setOnClickListener(channelListener)
                    } else {
                        userImage.gone()
                    }
                    if (item.channelName != null) {
                        username.visible()
                        username.text = if (item.channelLogin != null && !item.channelLogin.equals(item.channelName, true)) {
                            when (context.prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                                "0" -> "${item.channelName}(${item.channelLogin})"
                                "1" -> item.channelName
                                else -> item.channelLogin
                            }
                        } else {
                            item.channelName
                        }
                        username.setOnClickListener(channelListener)
                    } else {
                        username.gone()
                    }
                    item.name?.let {
                        title.visible()
                        title.text = it.trim()
                    } ?: {
                        title.gone()
                    }
                    if (item.gameName != null) {
                        gameName.visible()
                        gameName.text = item.gameName
                        gameName.setOnClickListener(gameListener)
                    } else {
                        gameName.gone()
                    }
                    item.duration?.let { itemDuration ->
                        duration.visible()
                        duration.text = DateUtils.formatElapsedTime(itemDuration / 1000L)
                        item.sourceStartPosition?.let { sourceStartPosition ->
                            sourceStart.visible()
                            sourceStart.text = context.getString(R.string.source_vod_start, DateUtils.formatElapsedTime(sourceStartPosition / 1000L))
                            sourceEnd.visible()
                            sourceEnd.text = context.getString(R.string.source_vod_end, DateUtils.formatElapsedTime((sourceStartPosition + itemDuration) / 1000L))
                        } ?: {
                            sourceStart.gone()
                            sourceEnd.gone()
                        }
                        if (context.prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true) && item.lastWatchPosition != null && itemDuration > 0L) {
                            progressBar.progress = (item.lastWatchPosition!!.toFloat() / itemDuration * 100).toInt()
                            progressBar.visible()
                        } else {
                            progressBar.gone()
                        }
                    } ?: {
                        duration.gone()
                        sourceStart.gone()
                        sourceEnd.gone()
                        progressBar.gone()
                    }
                    if (sourceEnd.isVisible && sourceStart.isVisible) {
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, context.convertDpToPixels(5F), 0, 0)
                        sourceEnd.layoutParams = params
                    }
                    if (type.isVisible && (sourceStart.isVisible || sourceEnd.isVisible)) {
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, context.convertDpToPixels(5F), 0, 0)
                        type.layoutParams = params
                    }
                    options.setOnClickListener { it ->
                        PopupMenu(context, it).apply {
                            inflate(R.menu.offline_item)
                            when (item.status) {
                                OfflineVideo.STATUS_DOWNLOADING, OfflineVideo.STATUS_BLOCKED, OfflineVideo.STATUS_QUEUED, OfflineVideo.STATUS_QUEUED_WIFI, OfflineVideo.STATUS_WAITING_FOR_STREAM -> {
                                    menu.findItem(R.id.stopDownload).isVisible = true
                                }
                                OfflineVideo.STATUS_PENDING -> {
                                    if (item.live) {
                                        menu.findItem(R.id.stopDownload).isVisible = true
                                    }
                                    menu.findItem(R.id.resumeDownload).isVisible = true
                                }
                                else -> {
                                    menu.findItem(R.id.moveVideo).apply {
                                        isVisible = true
                                        title = context.getString(
                                            if (item.url?.toUri()?.scheme == ContentResolver.SCHEME_CONTENT) {
                                                R.string.move_to_app_storage
                                            } else {
                                                R.string.move_to_shared_storage
                                            }
                                        )
                                    }
                                    if (item.url?.endsWith(".m3u8") == true) {
                                        menu.findItem(R.id.convertVideo).isVisible = true
                                    }
                                    menu.findItem(R.id.updateChatUrl).isVisible = true
                                    if (item.url?.toUri()?.scheme == ContentResolver.SCHEME_CONTENT) {
                                        menu.findItem(R.id.shareVideo).isVisible = true
                                    }
                                }
                            }
                            setOnMenuItemClickListener {
                                when (it.itemId) {
                                    R.id.stopDownload -> stopDownload(item)
                                    R.id.resumeDownload -> resumeDownload(item)
                                    R.id.convertVideo -> convertVideo(item)
                                    R.id.moveVideo -> moveVideo(item)
                                    R.id.updateChatUrl -> updateChatUrl(item)
                                    R.id.shareVideo -> shareVideo(item)
                                    R.id.delete -> deleteVideo(item)
                                    else -> menu.close()
                                }
                                true
                            }
                            show()
                        }
                    }
                    if (item.status == OfflineVideo.STATUS_DOWNLOADED) {
                        status.gone()
                    } else {
                        downloadProgress.text = when (item.status) {
                            OfflineVideo.STATUS_DOWNLOADING -> {
                                if (item.live) {
                                    context.getString(R.string.downloading)
                                } else {
                                    context.getString(R.string.downloading_progress, ((item.progress.toFloat() / item.maxProgress) * 100f).toInt())
                                }
                            }
                            OfflineVideo.STATUS_MOVING -> context.getString(R.string.download_moving, ((item.progress.toFloat() / item.maxProgress) * 100f).toInt())
                            OfflineVideo.STATUS_DELETING -> context.getString(R.string.download_deleting, ((item.progress.toFloat() / item.maxProgress) * 100f).toInt())
                            OfflineVideo.STATUS_CONVERTING -> context.getString(R.string.download_converting, ((item.progress.toFloat() / item.maxProgress) * 100f).toInt())
                            OfflineVideo.STATUS_BLOCKED -> context.getString(R.string.download_queued)
                            OfflineVideo.STATUS_QUEUED -> context.getString(R.string.download_blocked)
                            OfflineVideo.STATUS_QUEUED_WIFI -> context.getString(R.string.download_blocked_wifi)
                            OfflineVideo.STATUS_WAITING_FOR_STREAM -> context.getString(R.string.download_waiting_for_stream)
                            else -> context.getString(R.string.download_pending)
                        }
                        if (item.downloadChat && item.status == OfflineVideo.STATUS_DOWNLOADING && !item.live) {
                            chatDownloadProgress.visible()
                            chatDownloadProgress.text = context.getString(R.string.chat_downloading_progress, min(((item.chatProgress.toFloat() / item.maxChatProgress) * 100f).toInt(), 100))
                        } else {
                            chatDownloadProgress.gone()
                        }
                        status.visible()
                        if (item.status == OfflineVideo.STATUS_DOWNLOADING ||
                            item.status == OfflineVideo.STATUS_BLOCKED ||
                            item.status == OfflineVideo.STATUS_QUEUED ||
                            item.status == OfflineVideo.STATUS_QUEUED_WIFI ||
                            item.status == OfflineVideo.STATUS_WAITING_FOR_STREAM
                        ) {
                            checkDownloadStatus(item)
                        }
                    }
                }
            }
        }
    }
}