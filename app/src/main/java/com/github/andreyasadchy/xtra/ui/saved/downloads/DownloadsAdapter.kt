package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.content.ContentResolver
import android.content.Context
import android.text.format.DateUtils
import android.util.TypedValue
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
import com.github.andreyasadchy.xtra.ui.common.SpoilerShieldCards
import com.github.andreyasadchy.xtra.ui.common.showSpoilerPlaceholder
import com.github.andreyasadchy.xtra.ui.common.spoilerSubject
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.SpoilerFreeVideos
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs

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

    private val spoilerShield = SpoilerShieldCards(this)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        spoilerShield.attach(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        spoilerShield.detach()
        super.onDetachedFromRecyclerView(recyclerView)
    }

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
        private var boundId: Int? = null

        private fun bindStatus(item: OfflineVideo, context: Context) {
            with(binding) {
                val status = DownloadStatus.of(item)
                val sameItem = boundId == item.id
                boundId = item.id
                statusScrim.isVisible = status != null
                downloadStatus.isVisible = status != null
                if (status == null) {
                    downloadProgressBar.isVisible = false
                    return
                }
                // The pill takes the bottom of the picture, where the dates would sit.
                date.isVisible = false
                downloadDate.isVisible = false
                val label = if (status.percent != null) context.getString(status.label, status.percent) else context.getString(status.label)
                downloadStatus.text = if (status.chatPercent != null) {
                    context.getString(R.string.download_status_with_chat, label, context.getString(R.string.chat_downloading_progress, status.chatPercent))
                } else {
                    label
                }
                when (status.bar) {
                    DownloadStatus.Bar.NONE -> downloadProgressBar.isVisible = false
                    DownloadStatus.Bar.INDETERMINATE -> {
                        if (!downloadProgressBar.isIndeterminate) {
                            // Material won't switch modes on a shown indicator.
                            downloadProgressBar.isVisible = false
                            downloadProgressBar.isIndeterminate = true
                        }
                        downloadProgressBar.isVisible = true
                    }
                    DownloadStatus.Bar.DETERMINATE -> {
                        if (downloadProgressBar.isIndeterminate) {
                            downloadProgressBar.isVisible = false
                            downloadProgressBar.isIndeterminate = false
                        }
                        // Animate steps of one download, not a jump to a recycled card's value.
                        downloadProgressBar.setProgressCompat(status.percent ?: 0, sameItem && downloadProgressBar.isVisible)
                        downloadProgressBar.isVisible = true
                    }
                }
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
                                channelImage = item.channelLogo,
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
                    
                    val spoilerSubject = item.spoilerSubject()
                    val spoilers = spoilerShield.presentation(context, spoilerSubject, showsThumbnail = true)
                    spoilerShield.bindReveal(spoilerReveal, spoilerSubject, spoilers) { bindingAdapterPosition }
                    if (spoilers.hideThumbnail) {
                        thumbnail.showSpoilerPlaceholder()
                    } else {
                        fragment.requireContext().imageLoader.enqueue(
                            ImageRequest.Builder(fragment.requireContext()).apply {
                                data(item.thumbnail)
                                diskCachePolicy(CachePolicy.DISABLED)
                                crossfade(true)
                                target(thumbnail)
                            }.build()
                        )
                    }
                    
                    val uploadDate = item.uploadDate
                    if (uploadDate != null) {
                        date.visibility = View.VISIBLE
                        date.text = context.getString(R.string.uploaded_date, TwitchApiHelper.formatTime(context, uploadDate))
                    } else {
                        date.visibility = View.GONE
                    }
                    if (item.downloadDate != null) {
                        downloadDate.visibility = View.VISIBLE
                        downloadDate.text = context.getString(R.string.downloaded_date, TwitchApiHelper.formatTime(context, item.downloadDate))
                    } else {
                        downloadDate.visibility = View.GONE
                    }
                    if (item.type != null) {
                        val text = TwitchApiHelper.getType(context, item.type)
                        if (text != null) {
                            type.visibility = View.VISIBLE
                            type.text = text
                        } else {
                            type.visibility = View.GONE
                        }
                    } else {
                        type.visibility = View.GONE
                    }
                    if (item.channelLogo != null) {
                        userImage.visibility = View.VISIBLE
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
                        userImage.visibility = View.GONE
                    }
                    if (item.channelName != null) {
                        username.visibility = View.VISIBLE
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
                        username.visibility = View.GONE
                    }
                    val name = item.name
                    if (name != null) {
                        title.visibility = View.VISIBLE
                        title.text = if (spoilers.hideTitle) context.getString(R.string.spoiler_shield_title_hidden) else name.trim()
                    } else {
                        title.visibility = View.GONE
                    }
                    if (item.gameName != null) {
                        gameName.visibility = View.VISIBLE
                        gameName.text = item.gameName
                        gameName.setOnClickListener(gameListener)
                    } else {
                        gameName.visibility = View.GONE
                    }
                    val itemDuration = item.duration.takeUnless {
                        spoilers.hidesAnything || SpoilerFreeVideos.hideListSpoilers(context.prefs().getBoolean(C.PLAYER_SPOILER_FREE_VODS, false))
                    }
                    if (itemDuration != null) {
                        duration.visibility = View.VISIBLE
                        duration.text = DateUtils.formatElapsedTime(itemDuration / 1000L)
                        val sourceStartPosition = item.sourceStartPosition
                        if (sourceStartPosition != null) {
                            sourceStart.visibility = View.VISIBLE
                            sourceStart.text = context.getString(R.string.source_vod_start, DateUtils.formatElapsedTime(sourceStartPosition / 1000L))
                            sourceEnd.visibility = View.VISIBLE
                            sourceEnd.text = context.getString(R.string.source_vod_end, DateUtils.formatElapsedTime((sourceStartPosition + itemDuration) / 1000L))
                        } else {
                            sourceStart.visibility = View.GONE
                            sourceEnd.visibility = View.GONE
                        }
                        if (context.prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true) && item.lastWatchPosition != null && itemDuration > 0L) {
                            progressBar.progress = (item.lastWatchPosition!!.toFloat() / itemDuration * 100).toInt()
                            progressBar.visibility = View.VISIBLE
                        } else {
                            progressBar.visibility = View.GONE
                        }
                    } else {
                        duration.visibility = View.GONE
                        sourceStart.visibility = View.GONE
                        sourceEnd.visibility = View.GONE
                        progressBar.visibility = View.GONE
                    }
                    if (sourceEnd.isVisible && sourceStart.isVisible) {
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, context.resources.displayMetrics).toInt()
                        params.setMargins(0, margin, 0, 0)
                        sourceEnd.layoutParams = params
                    }
                    if (type.isVisible && (sourceStart.isVisible || sourceEnd.isVisible)) {
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, context.resources.displayMetrics).toInt()
                        params.setMargins(0, margin, 0, 0)
                        type.layoutParams = params
                    }
                    options.setOnClickListener { it ->
                        PopupMenu(context, it).apply {
                            inflate(R.menu.offline_item)
                            menu.findItem(R.id.spoilerShield).isVisible = spoilerShield.hasActions(spoilerSubject)
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
                                    R.id.spoilerShield -> spoilerShield.showActions(context, spoilerSubject)
                                    else -> menu.close()
                                }
                                true
                            }
                            show()
                        }
                    }
                    bindStatus(item, context)
                }
            }
        }
    }
}
