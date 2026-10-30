package com.github.andreyasadchy.xtra.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.app.PictureInPictureParams
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class Media3Fragment : PlayerFragment() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val player: MediaController?
        get() = controllerFuture?.let { if (it.isDone && !it.isCancelled) it.get() else null }
    private var playerListener: Player.Listener? = null
    private val sourceCommands = PlayerSourceCommands(
        isCurrent = ::isCurrentSourceController,
        rejected = ::rejectSourceStart,
        accepted = ::acceptSourceStart,
    )
    private val castStopRestart = CastStopRestart()
    private var localOnlyDialog: AlertDialog? = null
    private var lastSourceCommand: SessionCommand? = null
    /** Set from a service rejection until the next source command; the controller's media is not ours. */
    private var rejectedStart = false
    /** A connected controller may still show another screen's retained media. */
    private var acceptedStart = false
    private var sourceRequest: String? = null
    /** This screen's own fresh start is resolving and has not sent its command yet. */
    private var freshStartPending = false
    /** A start resolved before the controller connected; the connect listener sends it. */
    private var pendingSourceStart: (() -> Unit)? = null
    /** A PiP Audio only tap waiting for this screen's start to be accepted and its qualities loaded. */
    private var audioOnlyPending = false
    private var castUi: PlayerCastUi? = null
    private var castUiRequest = 0
    private val updateProgressAction = Runnable { if (view != null) updateProgress() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreSourceState(savedInstanceState)
    }

    private fun restoreSourceState(savedInstanceState: Bundle?) {
        sourceRequest = savedInstanceState?.getString(PlaybackService.SOURCE_REQUEST)
        lastSourceCommand = savedInstanceState?.getBundle(STATE_SOURCE_COMMAND)?.let(SessionCommand::fromBundle)
        rejectedStart = savedInstanceState?.getBoolean(STATE_REJECTED_START) == true
        freshStartPending = savedInstanceState?.getBoolean(STATE_FRESH_START) == true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveSourceState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun saveSourceState(outState: Bundle) {
        outState.putString(PlaybackService.SOURCE_REQUEST, sourceRequest)
        lastSourceCommand?.takeUnless { acceptedStart }?.let { outState.putBundle(STATE_SOURCE_COMMAND, it.toBundle()) }
        outState.putBoolean(STATE_REJECTED_START, rejectedStart)
        outState.putBoolean(STATE_FRESH_START, freshStartPending)
    }

    override fun onStart() {
        super.onStart()
        val future = MediaController.Builder(
            requireContext(),
            SessionToken(
                requireContext(),
                ComponentName(requireContext(), PlaybackService::class.java)
            )
        ).buildAsync()
        controllerFuture = future
        future.addListener({
            // Releasing a controller that is still connecting cancels it, which runs this listener too.
            if (future.isCancelled || controllerFuture !== future) return@addListener
            val controller = controllerFuture?.get()
            controller?.let(::refreshCastUi)
            controller?.let(::refreshSourceOwnership)
            if (controller?.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE) == true) {
                controller.setVideoSurfaceView(binding.playerSurface)
            }
            val listener = object : Player.Listener {

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    controller?.let(::refreshCastUi)
                }

                override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                    updatePlaybackRouteUi()
                    setSubtitlesButton()
                    binding.castStatus.isVisible = isRemotePlayback()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && requireActivity().packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                        requireActivity().setPictureInPictureParams(PictureInPictureParams.Builder()
                            .setAutoEnterEnabled(!isRemotePlayback() && canEnterPictureInPicture() &&
                                requireContext().prefs().getBoolean(C.PLAYER_PICTURE_IN_PICTURE, true)).build())
                    }
                    updateKeepScreenOn()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) markLiveRewindReady()
                    binding.bufferingIndicator.isVisible = playbackState == Player.STATE_BUFFERING
                    val showPlayButton = Util.shouldShowPlayButton(player)
                    if (showPlayButton) {
                        binding.playerControls.playPause.setImageResource(R.drawable.baseline_play_arrow_black_48)
                        binding.playerControls.playPause.visibility = View.VISIBLE
                    } else {
                        binding.playerControls.playPause.setImageResource(R.drawable.baseline_pause_black_48)
                        if (videoType == STREAM && !requireContext().prefs().getBoolean(C.PLAYER_PAUSE, false)) {
                            binding.playerControls.playPause.visibility = View.GONE
                        }
                    }
                    setPipActions(!showPlayButton)
                    updateProgress()
                    controllerAutoHide = !showPlayButton
                    if (videoType != STREAM && useController) {
                        showController()
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    binding.bufferingIndicator.isVisible = player?.playbackState == Player.STATE_BUFFERING
                    val showPlayButton = Util.shouldShowPlayButton(player)
                    if (showPlayButton) {
                        binding.playerControls.playPause.setImageResource(R.drawable.baseline_play_arrow_black_48)
                        binding.playerControls.playPause.visibility = View.VISIBLE
                    } else {
                        binding.playerControls.playPause.setImageResource(R.drawable.baseline_pause_black_48)
                        if (videoType == STREAM && !requireContext().prefs().getBoolean(C.PLAYER_PAUSE, false)) {
                            binding.playerControls.playPause.visibility = View.GONE
                        }
                    }
                    setPipActions(!showPlayButton)
                    updateProgress()
                    controllerAutoHide = !showPlayButton
                    if (videoType != STREAM && useController) {
                        showController()
                    }
                }

                override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                    if (availableCommands.contains(Player.COMMAND_SET_VIDEO_SURFACE)) {
                        player?.setVideoSurfaceView(binding.playerSurface)
                    }
                    if (Util.shouldShowPlayButton(player)) {
                        binding.playerControls.playPause.setImageResource(R.drawable.baseline_play_arrow_black_48)
                        binding.playerControls.playPause.visibility = View.VISIBLE
                    } else {
                        binding.playerControls.playPause.setImageResource(R.drawable.baseline_pause_black_48)
                        if (videoType == STREAM && !requireContext().prefs().getBoolean(C.PLAYER_PAUSE, false)) {
                            binding.playerControls.playPause.visibility = View.GONE
                        }
                    }
                    val duration = player?.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0
                    binding.playerControls.progressBar.setDuration(duration)
                    binding.playerControls.duration.text = DateUtils.formatElapsedTime(duration / 1000)
                    updateProgress()
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize != VideoSize.UNKNOWN && player?.let { it.playbackState != Player.STATE_IDLE } == true) {
                        val aspectRatio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                        updateVideoAspectRatio(aspectRatio)
                    }
                }

                override fun onCues(cueGroup: CueGroup) {
                    binding.subtitleView.setCues(cueGroup.cues)
                }

                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    val duration = player?.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0
                    binding.playerControls.progressBar.setDuration(duration)
                    binding.playerControls.duration.text = DateUtils.formatElapsedTime(duration / 1000)
                    updateProgress()
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        chatFragment?.updatePosition(newPosition.positionMs)
                    }
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    chatFragment?.updateSpeed(playbackParameters.speed)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateProgress()
                    updateKeepScreenOn()
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (isRemotePlayback()) return
                    if (!tracks.isEmpty && !viewModel.loaded.value) {
                        viewModel.loaded.value = true
                        toggleSubtitles(requireContext().prefs().getBoolean(C.PLAYER_SUBTITLES_ENABLED, false))
                    }
                    setSubtitlesButton()
                    if (!tracks.isEmpty) {
                        if (viewModel.qualities?.find { it.name == AUTO_QUALITY } != null
                            && viewModel.quality?.name != AUDIO_ONLY_QUALITY
                            && !viewModel.hidden) {
                            changeQuality(viewModel.quality)
                        }
                        chatFragment?.startReplayChatLoad()
                    }
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    val duration = player?.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0
                    binding.playerControls.progressBar.setDuration(duration)
                    binding.playerControls.duration.text = DateUtils.formatElapsedTime(duration / 1000)
                    updateProgress()
                    if (isRemotePlayback()) return
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && !timeline.isEmpty && viewModel.qualities?.find { it.name == AUTO_QUALITY } != null) {
                        viewModel.updateQualities = viewModel.quality?.name != AUDIO_ONLY_QUALITY
                    }
                    if (viewModel.qualities.isNullOrEmpty() || viewModel.updateQualities) {
                        player?.sendCustomCommand(
                            SessionCommand(PlaybackService.GET_QUALITIES, Bundle.EMPTY),
                            Bundle.EMPTY
                        )?.let { result ->
                            result.addListener({
                                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                                    val list = result.get().extras.getStringArray(PlaybackService.NAMES)?.let { names ->
                                        result.get().extras.getStringArray(PlaybackService.CODECS)?.let { codecs ->
                                            result.get().extras.getStringArray(PlaybackService.URLS)?.let { urls ->
                                                names.mapIndexed { index, name ->
                                                    VideoQuality(
                                                        name, codecs.getOrNull(index).takeIf { it != "null" }, urls.getOrNull(index),
                                                        result.get().extras.getIntArray(PlaybackService.QUALITY_BITRATES)?.getOrNull(index)?.takeIf { it > 0 },
                                                        result.get().extras.getIntArray(PlaybackService.QUALITY_RESOLUTIONS)?.getOrNull(index)?.takeIf { it > 0 },
                                                        result.get().extras.getFloatArray(PlaybackService.QUALITY_FRAME_RATES)?.getOrNull(index)?.takeIf { it > 0 },
                                                        result.get().extras.getStringArray(PlaybackService.QUALITY_FORMAT_IDS)?.getOrNull(index)?.takeIf { it.isNotBlank() },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    updateAvailableQualities(list)
                                    if (list != null || reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                                        viewModel.updateQualities = false
                                    }
                                    applyPendingAudioOnly()
                                }
                            }, MoreExecutors.directExecutor())
                        }
                    }
                    if (videoType == STREAM) {
                        val hideAds = requireContext().prefs().getBoolean(C.PLAYER_HIDE_ADS, false)
                        val useProxy = requireContext().prefs().getBoolean(C.PROXY_MEDIA_PLAYLIST, true)
                                && !requireContext().prefs().getString(C.PROXY_HOST, null).isNullOrBlank()
                                && requireContext().prefs().getString(C.PROXY_PORT, null)?.toIntOrNull() != null
                        if (hideAds || useProxy) {
                            player?.sendCustomCommand(
                                SessionCommand(PlaybackService.CHECK_ADS, Bundle.EMPTY),
                                Bundle.EMPTY
                            )?.let { result ->
                                result.addListener({
                                    if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                                        val playingAds = result.get().extras.getBoolean(PlaybackService.RESULT)
                                        val oldValue = viewModel.playingAds
                                        viewModel.playingAds = playingAds
                                        if (playingAds) {
                                            if (viewModel.usingProxy) {
                                                if (!viewModel.stopProxy) {
                                                    player?.sendCustomCommand(
                                                        SessionCommand(
                                                            PlaybackService.TOGGLE_PROXY, Bundle().apply {
                                                                putBoolean(PlaybackService.USING_PROXY, false)
                                                            }
                                                        ), Bundle.EMPTY
                                                    )
                                                    viewModel.usingProxy = false
                                                    viewModel.stopProxy = true
                                                }
                                            } else {
                                                if (!oldValue) {
                                                    val playlist = viewModel.quality?.url
                                                    if (!viewModel.stopProxy && !playlist.isNullOrBlank() && useProxy) {
                                                        player?.sendCustomCommand(
                                                            SessionCommand(
                                                                PlaybackService.TOGGLE_PROXY, Bundle().apply {
                                                                    putBoolean(PlaybackService.USING_PROXY, false)
                                                                }
                                                            ), Bundle.EMPTY
                                                        )
                                                        viewModel.usingProxy = true
                                                        viewLifecycleOwner.lifecycleScope.launch {
                                                            for (i in 0 until 10) {
                                                                delay(10000)
                                                                if (!viewModel.checkPlaylist(requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"), playlist)) {
                                                                    break
                                                                }
                                                            }
                                                            player?.sendCustomCommand(
                                                                SessionCommand(
                                                                    PlaybackService.TOGGLE_PROXY, Bundle().apply {
                                                                        putBoolean(PlaybackService.USING_PROXY, false)
                                                                    }
                                                                ), Bundle.EMPTY
                                                            )
                                                            viewModel.usingProxy = false
                                                        }
                                                    } else {
                                                        if (hideAds) {
                                                            viewModel.hidden = true
                                                            player?.let { player ->
                                                                if (viewModel.quality?.name != AUDIO_ONLY_QUALITY) {
                                                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                                                        setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                                                    }.build()
                                                                    binding.playerSurface.visibility = View.GONE
                                                                }
                                                                player.volume = 0f
                                                            }
                                                            Toast.makeText(requireContext(), R.string.waiting_ads, Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            if (hideAds && viewModel.hidden) {
                                                viewModel.hidden = false
                                                player?.let { player ->
                                                    if (viewModel.quality?.name != AUDIO_ONLY_QUALITY) {
                                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                                        }.build()
                                                        binding.playerSurface.visibility = View.VISIBLE
                                                    }
                                                    player.volume = requireContext().prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                                                }
                                            }
                                        }
                                    }
                                }, MoreExecutors.directExecutor())
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (isRemotePlayback()) {
                        Toast.makeText(requireContext(), R.string.player_error_stopped, Toast.LENGTH_LONG).show()
                        return
                    }
                    if (handleLiveRewindFailure()) return
                    Log.e(tag, "Player error", error)
                    when (videoType) {
                        STREAM -> handleStreamError(error)
                        VIDEO -> {
                            player?.sendCustomCommand(
                                SessionCommand(PlaybackService.GET_ERROR_CODE, Bundle.EMPTY),
                                Bundle.EMPTY
                            )?.let { result ->
                                result.addListener({
                                    if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                                        val responseCode = result.get().extras.getInt(PlaybackService.RESULT)
                                        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                                        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                                        val isNetworkAvailable = networkCapabilities != null
                                                && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                                && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                                        if (isNetworkAvailable) {
                                            val skipAccessToken = requireContext().prefs().getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2
                                            when {
                                                skipAccessToken == 1 && viewModel.shouldRetry && responseCode != 0 -> {
                                                    viewModel.shouldRetry = false
                                                    playVideo(false, player?.currentPosition)
                                                }
                                                skipAccessToken == 2 && viewModel.shouldRetry && responseCode != 0 -> {
                                                    viewModel.shouldRetry = false
                                                    playVideo(true, player?.currentPosition)
                                                }
                                                responseCode == 403 -> {
                                                    Toast.makeText(requireContext(), R.string.video_subscribers_only, Toast.LENGTH_LONG).show()
                                                }
                                                else -> {
                                                    Toast.makeText(requireContext(), R.string.player_error, Toast.LENGTH_SHORT).show()
                                                    viewLifecycleOwner.lifecycleScope.launch {
                                                        delay(1500L)
                                                        try {
                                                            player?.prepare()
                                                        } catch (e: Exception) {
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }, MoreExecutors.directExecutor())
                            }
                        }
                    }
                }
            }
            controller?.addListener(listener)
            playerListener = listener
            if (!isRemotePlayback() && viewModel.restoreQuality) {
                viewModel.restoreQuality = false
                changeQuality(viewModel.previousQuality)
            }
            player?.sendCustomCommand(
                SessionCommand(
                    PlaybackService.SET_SLEEP_TIMER, Bundle().apply {
                        putLong(PlaybackService.DURATION, -1L)
                    }
                ), Bundle.EMPTY
            )?.let { result ->
                result.addListener({
                    if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                        val endTime = result.get().extras.getLong(PlaybackService.RESULT)
                        if (endTime > 0L) {
                            val duration = endTime - System.currentTimeMillis()
                            if (duration > 0L) {
                                (activity as? MainActivity)?.setSleepTimer(duration)
                            } else {
                                minimize()
                                (activity as? MainActivity)?.closePlayer(this@Media3Fragment)
                            }
                        }
                    }
                }, MoreExecutors.directExecutor())
            }
            if (!isRemotePlayback() && viewModel.resume) {
                viewModel.resume = false
                player?.playWhenReady = true
                player?.prepare()
            }
            val resolvedStart = pendingSourceStart
            pendingSourceStart = null
            player?.let { player ->
                updatePlaybackRouteUi()
                binding.castStatus.isVisible = isRemotePlayback()
                if (resolvedStart == null && viewModel.loaded.value && player.currentMediaItem == null) {
                    viewModel.started = false
                }
                if (viewModel.started && player.currentMediaItem != null) {
                    markLiveRewindReady()
                    chatFragment?.startReplayChatLoad()
                }
                updateKeepScreenOn()
                updateProgress()
                if (Util.shouldShowPlayButton(player)) {
                    binding.playerControls.playPause.setImageResource(R.drawable.baseline_play_arrow_black_48)
                    binding.playerControls.playPause.visibility = View.VISIBLE
                } else {
                    binding.playerControls.playPause.setImageResource(R.drawable.baseline_pause_black_48)
                    if (videoType == STREAM && !requireContext().prefs().getBoolean(C.PLAYER_PAUSE, false)) {
                        binding.playerControls.playPause.visibility = View.GONE
                    }
                }
            }
            if (resolvedStart != null) {
                resolvedStart()
            } else if ((isInitialized || !enableNetworkCheck) && !viewModel.started) {
                startOrReplay()
            }
            player?.let { player ->
                setPipActions(player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE && player.playWhenReady)
            }
        }, MoreExecutors.directExecutor())
    }

    override fun initialize() {
        if (player != null && !viewModel.started) {
            startOrReplay()
        }
        super.initialize()
    }

    private fun startOrReplay() {
        val rejected = lastSourceCommand?.takeIf { !acceptedStart }
        if (rejected != null) {
            // Preserve the resolved URL/position when a rejected or unknown start is recreated.
            viewModel.started = true
            sendSourceCommand(rejected)
        } else {
            // Saved with the screen: a recreated or restored screen must not start it a second time.
            freshStartPending = true
            startPlayer()
        }
    }

    /** A start resolved while this screen was stopped arrives before its new controller connects. */
    private fun deferSourceStart(start: () -> Unit) {
        pendingSourceStart = start
    }

    override fun isLiveRewindPlaybackReady(): Boolean = player?.let {
        it.playbackState == Player.STATE_READY &&
            it.currentMediaItem?.mediaId == "video:${arguments?.getString(KEY_VIDEO_ID)}"
    } == true

    override fun startStream(url: String?) {
        if (player == null) return deferSourceStart { startStream(url) }
        sendSourceCommand(
            SessionCommand(
                    PlaybackService.START_STREAM, bundleOf(
                        PlaybackService.CUSTOM_PROXY to viewModel.useCustomProxy,
                        PlaybackService.URI to url,
                        PlaybackService.TITLE to requireArguments().getString(KEY_TITLE),
                        PlaybackService.CHANNEL_ID to requireArguments().getString(KEY_CHANNEL_ID),
                        PlaybackService.CHANNEL_NAME to requireArguments().getString(KEY_CHANNEL_NAME),
                    PlaybackService.CHANNEL_LOGIN to requireArguments().getString(KEY_CHANNEL_LOGIN),
                    PlaybackService.CHANNEL_LOGO to requireArguments().getString(KEY_CHANNEL_IMAGE),
                    PlaybackService.GAME_ID to requireArguments().getString(KEY_GAME_ID),
                    PlaybackService.GAME_NAME to requireArguments().getString(KEY_GAME_NAME),
                    )
                )
            )
    }

    override fun startVideo(url: String?, playbackPosition: Long?, multivariantPlaylist: Boolean) {
        if (player == null) return deferSourceStart { startVideo(url, playbackPosition, multivariantPlaylist) }
        player?.let { player ->
            if (!isRemotePlayback()) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                }.build()
                binding.playerSurface.visibility = View.VISIBLE
            }
            sendSourceCommand(
                SessionCommand(
                    PlaybackService.START_VIDEO, bundleOf(
                        PlaybackService.URI to url,
                        PlaybackService.PLAYBACK_POSITION to playbackPosition,
                        PlaybackService.VIDEO_ID to requireArguments().getString(KEY_VIDEO_ID)?.toLongOrNull(),
                        PlaybackService.TITLE to requireArguments().getString(KEY_TITLE),
                        PlaybackService.CHANNEL_ID to requireArguments().getString(KEY_CHANNEL_ID),
                        PlaybackService.CHANNEL_NAME to requireArguments().getString(KEY_CHANNEL_NAME),
                        PlaybackService.CHANNEL_LOGIN to requireArguments().getString(KEY_CHANNEL_LOGIN),
                        PlaybackService.CHANNEL_LOGO to requireArguments().getString(KEY_CHANNEL_IMAGE),
                        PlaybackService.GAME_ID to requireArguments().getString(KEY_GAME_ID),
                        PlaybackService.GAME_NAME to requireArguments().getString(KEY_GAME_NAME),
                    )
                )
            )
        }
    }

    override fun startClip(url: String?) {
        if (player == null) return deferSourceStart { startClip(url) }
        player?.let { player ->
            if (!isRemotePlayback() && viewModel.quality?.name == AUDIO_ONLY_QUALITY) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                }.build()
                binding.playerSurface.visibility = View.GONE
            } else if (!isRemotePlayback()) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                }.build()
                binding.playerSurface.visibility = View.VISIBLE
            }
            sendSourceCommand(
                SessionCommand(
                    PlaybackService.START_CLIP, bundleOf(
                        PlaybackService.URI to url,
                        PlaybackService.TITLE to requireArguments().getString(KEY_TITLE),
                        PlaybackService.CHANNEL_ID to requireArguments().getString(KEY_CHANNEL_ID),
                        PlaybackService.CHANNEL_NAME to requireArguments().getString(KEY_CHANNEL_NAME),
                        PlaybackService.CHANNEL_LOGIN to requireArguments().getString(KEY_CHANNEL_LOGIN),
                        PlaybackService.CHANNEL_LOGO to requireArguments().getString(KEY_CHANNEL_IMAGE),
                        PlaybackService.GAME_ID to requireArguments().getString(KEY_GAME_ID),
                        PlaybackService.GAME_NAME to requireArguments().getString(KEY_GAME_NAME),
                    )
                )
            )
        }
    }

    override fun startOfflineVideo(url: String?, position: Long) {
        if (player == null) return deferSourceStart { startOfflineVideo(url, position) }
        player?.let { player ->
            if (!isRemotePlayback() && viewModel.quality?.name == AUDIO_ONLY_QUALITY) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                }.build()
                binding.playerSurface.visibility = View.GONE
            } else if (!isRemotePlayback()) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                }.build()
                binding.playerSurface.visibility = View.VISIBLE
            }
            sendSourceCommand(
                SessionCommand(
                    PlaybackService.START_OFFLINE_VIDEO, bundleOf(
                        PlaybackService.URI to url,
                        PlaybackService.VIDEO_ID to requireArguments().getInt(KEY_OFFLINE_VIDEO_ID),
                        PlaybackService.PLAYBACK_POSITION to position,
                        PlaybackService.TITLE to requireArguments().getString(KEY_TITLE),
                        PlaybackService.CHANNEL_ID to requireArguments().getString(KEY_CHANNEL_ID),
                        PlaybackService.CHANNEL_NAME to requireArguments().getString(KEY_CHANNEL_NAME),
                        PlaybackService.CHANNEL_LOGIN to requireArguments().getString(KEY_CHANNEL_LOGIN),
                        PlaybackService.CHANNEL_LOGO to requireArguments().getString(KEY_CHANNEL_IMAGE),
                        PlaybackService.GAME_ID to requireArguments().getString(KEY_GAME_ID),
                        PlaybackService.GAME_NAME to requireArguments().getString(KEY_GAME_NAME),
                    )
                )
            )
        }
    }

    private fun sendSourceCommand(command: SessionCommand) {
        player?.let {
            val request = UUID.randomUUID().toString()
            val sourceCommand = SessionCommand(command.customAction, Bundle(command.customExtras).apply {
                putString(PlaybackService.SOURCE_REQUEST, request)
            })
            sourceRequest = request
            lastSourceCommand = sourceCommand
            rejectedStart = false
            freshStartPending = false
            pendingSourceStart = null
            // A new source is not owned until the service accepts it.
            acceptedStart = false
            sourceCommands.send(it, sourceCommand)
        }
    }

    private fun acceptSourceStart() {
        acceptedStart = true
        applyPendingAudioOnly()
    }

    /** Audio only needs this screen's accepted start and its qualities to find the audio-only rendition. */
    private fun canStartAudioOnly() = acceptedStart && !viewModel.qualities.isNullOrEmpty()

    private fun applyPendingAudioOnly() {
        if (!audioOnlyPending || !canStartAudioOnly()) return
        audioOnlyPending = false
        // The tap waited in PiP for this; leaving PiP or stopping cleared it.
        if (isInPipMode()) {
            startAudioOnly()
            activity?.moveTaskToBack(false)
        }
    }

    private fun rejectSourceStart(action: String, resultCode: Int) {
        sourceCommands.cancel()
        viewModel.started = false
        rejectedStart = true
        acceptedStart = false
        pendingSourceStart = null
        audioOnlyPending = false
        val message = when {
            resultCode != androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED -> R.string.player_error_stopped
            action == PlaybackService.START_OFFLINE_VIDEO -> R.string.cast_disconnect_download
            else -> R.string.cast_local_only
        }
        val controller = player
        val command = lastSourceCommand?.takeIf { it.customAction == action }
        // A minimized screen keeps the toast; a modal offer would cover unrelated browsing UI.
        if (message != R.string.player_error_stopped && command != null && isMaximized &&
            controller?.deviceInfo?.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            offerStopCasting(controller, command, message)
        } else {
            discardRejectedStart(message)
        }
    }

    /**
     * Local-only sources rejected while casting can end the session and play here instead.
     * The rejected command is replayed as-is: it already carries the resolved URL and start
     * position, while the fragment arguments for explicit timestamps were consumed by the first start.
     */
    private fun offerStopCasting(controller: MediaController, command: SessionCommand, message: Int) {
        var stopping = false
        val dialog = requireContext().getAlertDialogBuilder()
            .setMessage(message)
            .setPositiveButton(R.string.cast_stop_and_play_here) { _, _ ->
                stopping = true
                castStopRestart.start(controller,
                    restart = {
                        if (view != null && player === controller) {
                            viewModel.started = true
                            sendSourceCommand(command)
                        }
                    },
                    failed = { if (view != null && player === controller) discardRejectedStart(R.string.player_error_stopped) })
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnDismissListener {
            if (localOnlyDialog === dialog) {
                localOnlyDialog = null
                if (!stopping) discardRejectedStart(message)
            }
        }
        localOnlyDialog?.let { localOnlyDialog = null; it.dismiss() }
        localOnlyDialog = dialog
        dialog.show()
    }

    private fun discardRejectedStart(message: Int) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        releaseController()
        (activity as? MainActivity)?.discardRejectedPlayer(this)
    }

    private fun releaseController() {
        sourceCommands.cancel()
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
        val future = controllerFuture
        controllerFuture = null
        future?.let { MediaController.releaseFuture(it) }
    }

    private fun isCurrentSourceController(controller: MediaController) =
        isAdded && view != null && player === controller &&
            (activity as? MainActivity)?.supportFragmentManager?.findFragmentById(R.id.playerContainer) === this

    /** Resolve either acceptance or a lost rejection after a stopped/recreated screen returns. */
    private fun refreshSourceOwnership(controller: MediaController) {
        val request = sourceRequest ?: return
        if (acceptedStart || rejectedStart) return
        val result = controller.sendCustomCommand(SessionCommand(PlaybackService.GET_SOURCE_REQUEST, Bundle.EMPTY), Bundle.EMPTY)
        result.addListener({
            val response = runCatching { result.get() }.getOrNull()
            if (!isCurrentSourceController(controller) || sourceRequest != request ||
                response?.resultCode != SessionResult.RESULT_SUCCESS) return@addListener
            if (response.extras.getString(PlaybackService.SOURCE_REQUEST) == request) {
                // Includes a restored screen whose earlier source the service kept playing.
                acceptSourceStart()
            } else {
                acceptedStart = false
                // This screen's own start is resolving or about to begin; another would load twice.
                if (freshStartPending || (lastSourceCommand == null && !viewModel.started)) return@addListener
                viewModel.started = false
                // The service did not accept this request. Replaying it delivers the normal
                // local-only offer, or starts it locally if Cast disconnected in the meantime.
                startOrReplay()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun handleStreamError(error: PlaybackException) {
        val controller = player ?: return
        val item = controller.currentMediaItem
        controller.sendCustomCommand(
            SessionCommand(PlaybackService.GET_ERROR_CODE, Bundle.EMPTY),
            Bundle.EMPTY
        )?.let { result ->
            result.addListener({
                if (!isAdded || view == null || player !== controller || controller.currentMediaItem != item) return@addListener
                val response = result.get()
                if (response.resultCode == SessionResult.RESULT_SUCCESS &&
                    !response.extras.getBoolean(PlaybackService.RECOVERING)) {
                    val responseCode = response.extras.getInt(PlaybackService.RESULT)
                    // Keep the existing one-way proxy fallback, without a delayed
                    // callback that could reopen a replaced or paused player.
                    if (viewModel.useCustomProxy && responseCode >= 400 && controller.playWhenReady) {
                        viewModel.useCustomProxy = false
                        Toast.makeText(requireContext(), R.string.proxy_error, Toast.LENGTH_LONG).show()
                        restartPlayer()
                        return@addListener
                    }
                    val message = when {
                        responseCode == 404 -> R.string.stream_ended
                        viewModel.useCustomProxy && responseCode >= 400 -> R.string.proxy_error
                        else -> R.string.player_error_stopped
                    }
                    if (!(response.extras.getBoolean(PlaybackService.RECOVERY_EXHAUSTED) &&
                            LivePlaybackRecovery.isTransient(error) && responseCode != 404)) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }
            }, ContextCompat.getMainExecutor(requireContext()))
        }
    }

    private fun refreshCastUi(controller: MediaController) {
        val request = ++castUiRequest
        val result = controller.sendCustomCommand(SessionCommand(PlaybackService.GET_CAST_AVAILABLE, Bundle.EMPTY), Bundle.EMPTY)
        result.addListener({
            if (!isAdded || view == null || player !== controller || request != castUiRequest) return@addListener
            val response = runCatching { result.get() }.getOrNull()
            val visible = videoType != OFFLINE_VIDEO && response?.resultCode == SessionResult.RESULT_SUCCESS &&
                response.extras.getBoolean(PlaybackService.RESULT)
            if (visible && castUi == null) castUi = PlayerCastUi(binding.playerControls.castButtonStub)
            castUi?.setVisible(visible)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     * Start position for a quality or audio-only source swap. Live returns to the live edge:
     * the old window offset can fall outside the new playlist's window.
     */
    private fun Player.sourceSwapPosition() =
        if (isCurrentMediaItemLive) androidx.media3.common.C.TIME_UNSET else currentPosition.coerceAtLeast(0)

    private fun updateKeepScreenOn() {
        view?.keepScreenOn = !isRemotePlayback() &&
            (requireContext().prefs().getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false) ||
                (canEnterPictureInPicture() && player?.isPlaying == true))
    }

    override fun getCurrentPosition() = player?.currentPosition

    override fun isRemotePlayback() = player?.deviceInfo?.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE

    override fun getDuration() = player?.duration ?: 0L

    override fun getCurrentSpeed() = player?.playbackParameters?.speed

    override fun getCurrentVolume() = player?.volume

    override fun playPause() {
        Util.handlePlayPauseButtonAction(player)
    }

    override fun rewind() {
        player?.seekBack()
    }

    override fun fastForward() {
        player?.seekForward()
    }

    override fun seek(position: Long) {
        player?.seekTo(position)
    }

    override fun seekToLivePosition() {
        player?.seekToDefaultPosition()
    }

    override fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        updatePlaybackSpeedUi(speed)
    }

    override fun changeVolume(volume: Float) {
        player?.volume = volume
    }

    override fun updateProgress() {
        checkLiveRewindCatchUp()
        with(binding.playerControls) {
            if (root.isVisible && !progressBar.isPressed) {
                val currentPosition = player?.currentPosition ?: 0
                position.text = DateUtils.formatElapsedTime(currentPosition / 1000)
                progressBar.setPosition(currentPosition)
                progressBar.setBufferedPosition(player?.bufferedPosition ?: 0)
                root.removeCallbacks(updateProgressAction)
                player?.let { player ->
                    if (player.isPlaying) {
                        val speed = player.playbackParameters.speed
                        val delay = if (speed > 0f) {
                            (progressBar.preferredUpdateDelay / speed).toLong().coerceIn(200L..1000L)
                        } else {
                            1000
                        }
                        root.postDelayed(updateProgressAction, delay)
                    }
                }
            }
        }
    }

    override fun toggleAudioCompressor() {
        if (isRemotePlayback()) return
        player?.sendCustomCommand(
            SessionCommand(
                PlaybackService.TOGGLE_DYNAMICS_PROCESSING,
                Bundle.EMPTY
            ), Bundle.EMPTY
        )?.let { result ->
            result.addListener({
                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                    val state = result.get().extras.getBoolean(PlaybackService.RESULT)
                    if (state) {
                        binding.playerControls.audioCompressor.setImageResource(R.drawable.baseline_audio_compressor_on_24dp)
                    } else {
                        binding.playerControls.audioCompressor.setImageResource(R.drawable.baseline_audio_compressor_off_24dp)
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    override fun setSubtitlesButton() {
        with(binding.playerControls) {
            val textTracks = player?.currentTracks?.groups?.find { !isRemotePlayback() && it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
            if (textTracks != null && requireContext().prefs().getBoolean(C.PLAYER_SUBTITLES, false)) {
                subtitles.visibility = View.VISIBLE
                if (textTracks.isSelected) {
                    subtitles.setImageResource(androidx.media3.ui.R.drawable.exo_ic_subtitle_on)
                    subtitles.setOnClickListener {
                        toggleSubtitles(false)
                        requireContext().prefs().edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, false) }
                    }
                } else {
                    subtitles.setImageResource(androidx.media3.ui.R.drawable.exo_ic_subtitle_off)
                    subtitles.setOnClickListener {
                        toggleSubtitles(true)
                        requireContext().prefs().edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, true) }
                    }
                }
            } else {
                subtitles.visibility = View.GONE
            }
            updateMorePopupSubtitles(textTracks)
        }
    }

    override fun toggleSubtitles(enabled: Boolean) {
        player?.let { player ->
            if (enabled) {
                player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }?.let {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
                        .build()
                }
            } else {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                    .build()
            }
        }
    }

    override fun showPlaylistTags(mediaPlaylist: Boolean) {
        player?.sendCustomCommand(
            SessionCommand(
                if (mediaPlaylist) {
                    PlaybackService.GET_MEDIA_PLAYLIST
                } else {
                    PlaybackService.GET_MULTIVARIANT_PLAYLIST
                },
                Bundle.EMPTY
            ), Bundle.EMPTY
        )?.let { result ->
            result.addListener({
                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                    val tags = result.get().extras.getStringArray(PlaybackService.RESULT)?.joinToString("\n")
                    if (!tags.isNullOrBlank()) {
                        requireContext().getAlertDialogBuilder().apply {
                            setView(NestedScrollView(context).apply {
                                addView(HorizontalScrollView(context).apply {
                                    addView(TextView(context).apply {
                                        text = tags
                                        textSize = 12F
                                        setTextIsSelectable(true)
                                    })
                                })
                            })
                            setNegativeButton(R.string.copy_clip) { _, _ ->
                                val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("label", tags))
                            }
                            setPositiveButton(android.R.string.ok, null)
                        }.show()
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    override fun changeQuality(selectedQuality: VideoQuality?) {
        if (isRemotePlayback()) return
        viewModel.previousQuality = viewModel.quality
        viewModel.quality = selectedQuality
        viewModel.quality?.let { quality ->
            player?.let { player ->
                player.currentMediaItem?.let { mediaItem ->
                    when (quality.name) {
                        AUTO_QUALITY -> {
                            viewModel.playlistUrl?.let { uri ->
                                if (mediaItem.localConfiguration?.uri != uri) {
                                    val position = player.sourceSwapPosition()
                                    player.setMediaItem(mediaItem.buildUpon().setUri(uri).build(), position)
                                    player.prepare()
                                }
                                viewModel.playlistUrl = null
                            } ?: player.prepare()
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                            }.build()
                            binding.playerSurface.visibility = View.VISIBLE
                        }
                        AUDIO_ONLY_QUALITY -> {
                            if (viewModel.usingProxy) {
                                player.sendCustomCommand(
                                    SessionCommand(
                                        PlaybackService.TOGGLE_PROXY, Bundle().apply {
                                            putBoolean(PlaybackService.USING_PROXY, false)
                                        }
                                    ), Bundle.EMPTY
                                )
                                viewModel.usingProxy = false
                            }
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                            }.build()
                            binding.playerSurface.visibility = View.GONE
                            quality.url?.let {
                                val position = player.sourceSwapPosition()
                                if (viewModel.qualities?.find { it.name == AUTO_QUALITY } != null) {
                                    viewModel.playlistUrl = mediaItem.localConfiguration?.uri
                                }
                                player.setMediaItem(mediaItem.buildUpon().setUri(it).build(), position)
                                player.prepare()
                            }
                        }
                        CHAT_ONLY_QUALITY -> {
                            if (viewModel.usingProxy) {
                                player.sendCustomCommand(
                                    SessionCommand(
                                        PlaybackService.TOGGLE_PROXY, Bundle().apply {
                                            putBoolean(PlaybackService.USING_PROXY, false)
                                        }
                                    ), Bundle.EMPTY
                                )
                                viewModel.usingProxy = false
                            }
                            player.stop()
                        }
                        else -> {
                            if (viewModel.qualities?.find { it.name == AUTO_QUALITY } != null) {
                                viewModel.playlistUrl?.let { uri ->
                                    player.currentMediaItem?.let {
                                        val position = player.sourceSwapPosition()
                                        player.setMediaItem(it.buildUpon().setUri(uri).build(), position)
                                        player.prepare()
                                        viewModel.playlistUrl = null
                                    }
                                } ?: player.prepare()
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                    binding.playerSurface.visibility = View.VISIBLE
                                    selectQualityTrack(quality, player.currentTracks.groups)?.let { override ->
                                        setOverrideForType(override)
                                    }
                                }.build()
                            } else {
                                player.currentMediaItem?.let {
                                    if (it.localConfiguration?.uri?.toString() != quality.url) {
                                        val position = player.sourceSwapPosition()
                                        player.setMediaItem(it.buildUpon().setUri(quality.url).build(), position)
                                        player.prepare()
                                    }
                                }
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                }.build()
                                binding.playerSurface.visibility = View.VISIBLE
                            }
                        }
                    }
                    val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                    val profile = PlayerQualityNetworkProfile.from(networkCapabilities)
                    if (shouldPersistQualitySelection(profile)) {
                        PlayerQualityPreferences.save(requireContext().prefs(), quality)
                    }
                }
            }
        }
    }

    override fun startAudioOnly(): Boolean {
        if (!canStartAudioOnly()) {
            // While this screen's own start is on its way or still loading its qualities, the
            // first tap waits in PiP for them; a second tap leaves as before.
            audioOnlyPending = !audioOnlyPending && viewModel.started && !rejectedStart && isInPipMode()
            if (audioOnlyPending) return false
            if (!acceptedStart) return true
        }
        player?.let { player ->
            if (player.isConnected) {
                savePosition()
                if (viewModel.usingProxy) {
                    player.sendCustomCommand(
                        SessionCommand(
                            PlaybackService.TOGGLE_PROXY, Bundle().apply {
                                putBoolean(PlaybackService.USING_PROXY, false)
                            }
                        ), Bundle.EMPTY
                    )
                    viewModel.usingProxy = false
                }
                if (viewModel.quality?.name != AUDIO_ONLY_QUALITY) {
                    viewModel.restoreQuality = true
                    viewModel.previousQuality = viewModel.quality
                    viewModel.quality = viewModel.qualities?.find { it.name == AUDIO_ONLY_QUALITY }
                    viewModel.quality?.let { quality ->
                        player.currentMediaItem?.let { mediaItem ->
                            if (requireContext().prefs().getBoolean(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true)) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                }.build()
                                binding.playerSurface.visibility = View.GONE
                            }
                            if (requireContext().prefs().getBoolean(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false)) {
                                quality.url?.let { url ->
                                    val position = player.sourceSwapPosition()
                                    if (viewModel.qualities?.find { it.name == AUTO_QUALITY } != null) {
                                        viewModel.playlistUrl = mediaItem.localConfiguration?.uri
                                    }
                                    player.setMediaItem(mediaItem.buildUpon().setUri(url).build(), position)
                                    player.prepare()
                                }
                            }
                        }
                    }
                }
                player.sendCustomCommand(
                    SessionCommand(
                        PlaybackService.SET_SLEEP_TIMER, Bundle().apply {
                            putLong(PlaybackService.DURATION, (activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
                        }
                    ), Bundle.EMPTY
                )
            }
        }
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        return true
    }

    override fun downloadVideo() {
        player?.sendCustomCommand(
            SessionCommand(PlaybackService.GET_DURATION, Bundle.EMPTY),
            Bundle.EMPTY
        )?.let { result ->
            result.addListener({
                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                    val totalDuration = result.get().extras.getLong(PlaybackService.RESULT)
                    val qualities = viewModel.qualities?.filter { !it.url.isNullOrBlank() }
                    DownloadDialog.newVideoInstance(
                        id = requireArguments().getString(KEY_VIDEO_ID),
                        channelId = requireArguments().getString(KEY_CHANNEL_ID),
                        channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN),
                        channelName = requireArguments().getString(KEY_CHANNEL_NAME),
                        channelImage = requireArguments().getString(KEY_CHANNEL_IMAGE),
                        gameId = requireArguments().getString(KEY_GAME_ID),
                        gameSlug = requireArguments().getString(KEY_GAME_SLUG),
                        gameName = requireArguments().getString(KEY_GAME_NAME),
                        title = requireArguments().getString(KEY_TITLE),
                        thumbnail = requireArguments().getString(KEY_THUMBNAIL),
                        createdAt = requireArguments().getString(KEY_CREATED_AT),
                        durationSeconds = requireArguments().getInt(KEY_DURATION_SECONDS),
                        type = requireArguments().getString(KEY_VIDEO_TYPE),
                        animatedPreviewUrl = requireArguments().getString(KEY_VIDEO_ANIMATED_PREVIEW),
                        totalDuration = totalDuration,
                        currentPosition = getCurrentPosition(),
                        qualityNames = qualities?.map { it.name.toString() }?.toTypedArray(),
                        qualityCodecs = qualities?.map { it.codecs.toString() }?.toTypedArray(),
                        qualityUrls = qualities?.map { it.url.toString() }?.toTypedArray(),
                        qualityBitrates = qualities?.map { it.bitrate ?: -1 }?.toIntArray(),
                    ).show(childFragmentManager, null)
                }
            }, MoreExecutors.directExecutor())
        }
    }

    override fun close() = closePlayback(disconnect = false)

    override fun closeSession() = closePlayback(disconnect = true)

    override fun closeOnExit(keepCasting: Boolean) {
        val controller = player
        if (keepCasting && isRemotePlayback() && controller?.currentMediaItem != null) {
            // Leave the receiver playing; the service and its notification keep control.
            if (acceptedStart) savePosition()
            releaseController()
        } else {
            closeSession()
        }
    }

    private fun closePlayback(disconnect: Boolean) {
        castStopRestart.cancel()
        localOnlyDialog?.let { localOnlyDialog = null; it.dismiss() }
        pendingSourceStart = null
        audioOnlyPending = false
        if (acceptedStart) savePosition()
        // The service knows whether it accepted this source even before its result arrives here.
        // A stale/pending/rejected screen can never close a different retained source.
        sourceRequest?.let { request ->
            player?.sendCustomCommand(SessionCommand(PlaybackService.CLOSE_SOURCE, bundleOf(
                PlaybackService.SOURCE_REQUEST to request,
                PlaybackService.DISCONNECT_CAST to disconnect,
            )), Bundle.EMPTY)
        }
        acceptedStart = false
        releaseController()
    }

    override fun onStop() {
        audioOnlyPending = false
        sourceCommands.cancel()
        // The restarted fragment repeats its start, so a stopped offer must not discard it.
        // Until then the controller shows media this screen does not own; never save it here.
        castStopRestart.cancel()
        localOnlyDialog?.let { localOnlyDialog = null; it.dismiss() }
        super.onStop()
        castUi?.dismissDialog()
        player?.takeIf { acceptedStart }?.let { player ->
            if (player.isConnected) {
                savePosition()
                if (viewModel.usingProxy) {
                    player.sendCustomCommand(
                        SessionCommand(
                            PlaybackService.TOGGLE_PROXY, Bundle().apply {
                                putBoolean(PlaybackService.USING_PROXY, false)
                            }
                        ), Bundle.EMPTY
                    )
                    viewModel.usingProxy = false
                }
                if (!isRemotePlayback()) {
                    val isInteractive = (requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
                    val isInPIPMode = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> requireActivity().isInPictureInPictureMode
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> !useController && isMaximized
                        else -> false
                    }
                    if ((!isInPIPMode && isInteractive && requireContext().prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO, true))
                        || (!isInPIPMode && !isInteractive && requireContext().prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO_LOCKED, true))
                        || (isInPIPMode && isInteractive && requireContext().prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO_PIP_CLOSED, false))
                        || (isInPIPMode && !isInteractive && requireContext().prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO_PIP_LOCKED, true))) {
                        if (player.playWhenReady && viewModel.quality?.name != AUDIO_ONLY_QUALITY) {
                            viewModel.restoreQuality = true
                            viewModel.previousQuality = viewModel.quality
                            viewModel.quality = viewModel.qualities?.find { it.name == AUDIO_ONLY_QUALITY }
                            viewModel.quality?.let { quality ->
                                player.currentMediaItem?.let { mediaItem ->
                                    if (requireContext().prefs().getBoolean(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true)) {
                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                        }.build()
                                        binding.playerSurface.visibility = View.GONE
                                    }
                                    if (requireContext().prefs().getBoolean(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false)) {
                                        quality.url?.let { url ->
                                            val position = player.sourceSwapPosition()
                                            if (viewModel.qualities?.find { it.name == AUTO_QUALITY } != null) {
                                                viewModel.playlistUrl = mediaItem.localConfiguration?.uri
                                            }
                                            player.setMediaItem(mediaItem.buildUpon().setUri(url).build(), position)
                                            player.prepare()
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        viewModel.resume = player.playWhenReady
                        player.pause()
                    }
                }
                player.sendCustomCommand(
                    SessionCommand(
                        PlaybackService.SET_SLEEP_TIMER, Bundle().apply {
                            putLong(PlaybackService.DURATION, (activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
                        }
                    ), Bundle.EMPTY
                )
            }
        }
        binding.playerControls.root.removeCallbacks(updateProgressAction)
        releaseController()
    }

    override fun onDestroyView() {
        castUi?.dispose()
        castUi = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        // A resolved start that was never sent dies with this screen; a recreated one resolves it again.
        if (pendingSourceStart != null) viewModel.started = false
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        if (isInPictureInPictureMode) castUi?.dismissDialog() else audioOnlyPending = false
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onNetworkRestored() {
        if (isRemotePlayback()) return
        // Live recovery belongs to the service, including background playback.
        if (isResumed && videoType != STREAM && player?.playWhenReady == true) {
            player?.prepare()
        }
    }

    override fun onNetworkLost() {
        if (isRemotePlayback()) return
        if (videoType != STREAM && isResumed) {
            player?.stop()
        }
    }

    companion object {
        private const val STATE_SOURCE_COMMAND = "media3SourceCommand"
        private const val STATE_REJECTED_START = "media3RejectedStart"
        private const val STATE_FRESH_START = "media3FreshStart"
        fun newInstance(item: Stream): Media3Fragment {
            return Media3Fragment().apply {
                arguments = getStreamArguments(item)
            }
        }

        fun newInstance(item: Video, offset: Long?, ignoreSavedPosition: Boolean): Media3Fragment {
            return Media3Fragment().apply {
                arguments = getVideoArguments(item, offset, ignoreSavedPosition)
            }
        }

        fun newInstance(item: Clip): Media3Fragment {
            return Media3Fragment().apply {
                arguments = getClipArguments(item)
            }
        }

        fun newInstance(item: OfflineVideo): Media3Fragment {
            return Media3Fragment().apply {
                arguments = getOfflineVideoArguments(item)
            }
        }
    }
}
