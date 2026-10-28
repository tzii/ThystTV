package com.github.andreyasadchy.xtra.ui.player

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import android.net.NetworkRequest
import android.media.audiofx.DynamicsProcessing
import android.net.http.HttpEngine
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.ParsingLoadable
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.github.andreyasadchy.xtra.ui.stats.StatsRepository
import com.github.andreyasadchy.xtra.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.stats.WatchSession
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.player.lowlatency.CronetDataSource
import com.github.andreyasadchy.xtra.player.lowlatency.HlsPlaylistParser
import com.github.andreyasadchy.xtra.player.lowlatency.HttpEngineDataSource
import com.github.andreyasadchy.xtra.player.lowlatency.OkHttpDataSource
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Timer
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate


@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    @JvmField
    var httpEngine: Lazy<HttpEngine>? = null

    @Inject
    @JvmField
    var cronetEngine: Lazy<CronetEngine>? = null

    @Inject
    lateinit var cronetExecutor: ExecutorService

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var offlineRepository: OfflineRepository

    @Inject
    lateinit var statsRepository: StatsRepository

    private val statsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentChannelId: String? = null
    private var currentChannelName: String? = null
    private var currentChannelLogin: String? = null
    private var currentGameId: String? = null
    private var currentGameName: String? = null
    private var lastStatsTime: Long = 0L

    private var mediaSession: MediaSession? = null
    private var castBridge: PlayerCastBridge? = null
    private var liveRecovery: LivePlaybackRecovery? = null
    private var backgroundRetry: BackgroundPlaybackRetry? = null
    private val networkHandler = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var background = false
    private var proxyMediaPlaylist = false
    private var videoId: Long? = null
    private var offlineVideoId: Int? = null
    private var sleepTimer: Timer? = null
    private var sleepTimerEndTime = 0L
    private var lastSavedPosition: Long? = null
    private var savePositionTimer: Timer? = null
    private var sourceRequest: String? = null

    override fun onCreate() {
        super.onCreate()
        val sourceFactory = ConfiguredMediaSourceFactory(DefaultMediaSourceFactory(this))
        val bridge = PlayerCastBridge(this, enabled = prefs().getBoolean(C.PLAYER_CAST, false),
            sourceFactoryChanged = sourceFactory::configure, createLocal = {
            ExoPlayer.Builder(this).apply {
                setMediaSourceFactory(sourceFactory)
                setLoadControl(
                    DefaultLoadControl.Builder().apply {
                        setBufferDurationsMs(
                            prefs().getString(C.PLAYER_BUFFER_MIN, "15000")?.toIntOrNull() ?: 15000,
                            prefs().getString(C.PLAYER_BUFFER_MAX, "50000")?.toIntOrNull() ?: 50000,
                            prefs().getString(C.PLAYER_BUFFER_PLAYBACK, "2000")?.toIntOrNull() ?: 2000,
                            prefs().getString(C.PLAYER_BUFFER_REBUFFER, "2000")?.toIntOrNull() ?: 2000
                        )
                    }.build()
                )
                setAudioAttributes(AudioAttributes.DEFAULT, prefs().getBoolean(C.PLAYER_AUDIO_FOCUS, false))
                setHandleAudioBecomingNoisy(prefs().getBoolean(C.PLAYER_HANDLE_AUDIO_BECOMING_NOISY, true))
                setSeekBackIncrementMs(prefs().getString(C.PLAYER_REWIND, "10000")?.toLongOrNull() ?: 10000)
                setSeekForwardIncrementMs(prefs().getString(C.PLAYER_FORWARD, "10000")?.toLongOrNull() ?: 10000)
            }.build()
        })
        castBridge = bridge
        val player = bridge.player
        liveRecovery = LivePlaybackRecovery(player, Handler(Looper.getMainLooper()), ::isNetworkAvailable, {
            Toast.makeText(this, R.string.live_recovery_exhausted, Toast.LENGTH_LONG).show()
        })
        watchRecoveryConnectivity()
        backgroundRetry = BackgroundPlaybackRetry(player, Handler(Looper.getMainLooper())).also(player::addListener)
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        lastStatsTime = System.currentTimeMillis()
                        if (savePositionTimer == null && (videoId != null || offlineVideoId != null)) {
                            savePositionTimer = Timer().apply {
                                scheduleAtFixedRate(30000, 30000) {
                                    Handler(Looper.getMainLooper()).post {
                                        updateSavedPosition()
                                        updateStats()
                                    }
                                }
                            }
                        }
                    } else {
                        updateStats()
                        lastStatsTime = 0L
                        savePositionTimer?.cancel()
                        savePositionTimer = null
                        updateSavedPosition()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    backgroundRetry?.onError(error, background && liveRecovery?.isLiveSource != true &&
                        player.deviceInfo.playbackType != androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE)
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    dynamicsProcessing?.let {
                        it.release()
                        dynamicsProcessing = null
                    }
                    if (prefs().getBoolean(C.PLAYER_AUDIO_COMPRESSOR, false)) {
                        reinitializeDynamicsProcessing(audioSessionId)
                    }
                }
            }
        )
        mediaSession = MediaSession.Builder(
            this,
            bridge.guardCommands(object : ForwardingSimpleBasePlayer(player) {
                override fun handlePrepare(): ListenableFuture<*> {
                    liveRecovery?.onExplicitPrepare()
                    return super.handlePrepare()
                }

                override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
                    if (playWhenReady && liveRecovery?.isExhausted == true) liveRecovery?.onExplicitPrepare()
                    return super.handleSetPlayWhenReady(playWhenReady)
                }

                override fun getState(): State {
                    val state = super.getState()
                    return state
                        .buildUpon()
                        .setAvailableCommands(
                            state.availableCommands.buildUpon()
                                .add(COMMAND_SEEK_TO_NEXT)
                                .add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                                .build()
                        )
                        .build()
                }

                override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
                    return when (seekCommand) {
                        COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                            player.seekForward()
                            Futures.immediateVoidFuture()
                        }
                        COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                            player.seekBack()
                            Futures.immediateVoidFuture()
                        }
                        else -> super.handleSeek(mediaItemIndex, positionMs, seekCommand)
                    }
                }
            }, userIntent = true)
        ).apply {
            setSessionActivity(
                PendingIntent.getActivity(
                    this@PlaybackService,
                    REQUEST_CODE_RESUME,
                    Intent(this@PlaybackService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        action = MainActivity.INTENT_OPEN_PLAYER
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            setCallback(
                object : PlaybackSessionCallback() {
                    override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                        if (customCommand.customAction in SOURCE_START_COMMANDS) {
                            bridge.configure(prefs().getBoolean(C.PLAYER_CAST, false))
                            val item = MediaItem.Builder().setUri(customCommand.customExtras.getString(URI)).build()
                            if (player.deviceInfo.playbackType == androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE &&
                                // VoDs stay local. Live items gain their live configuration in
                                // START_STREAM, where setSource validates the full item.
                                (customCommand.customAction == START_OFFLINE_VIDEO || customCommand.customAction == START_VIDEO ||
                                    !CastPlaybackHandoff.canCast(item))) {
                                return Futures.immediateFuture(SessionResult(androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED))
                            }
                        }
                        return when (customCommand.customAction) {
                            CLOSE_SOURCE -> {
                                closeSource(customCommand.customExtras.getString(SOURCE_REQUEST),
                                    customCommand.customExtras.getBoolean(DISCONNECT_CAST))
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            GET_SOURCE_REQUEST -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS,
                                Bundle().apply { putString(SOURCE_REQUEST, sourceRequest) }))
                            END_CAST_SESSION -> {
                                bridge.endSession()
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            START_STREAM -> {
                                liveRecovery?.startSource(true, customCommand.customExtras.getBoolean(CUSTOM_PROXY))
                                val uri = customCommand.customExtras.getString(URI)
                                val title = customCommand.customExtras.getString(TITLE)
                                val channelId = customCommand.customExtras.getString(CHANNEL_ID)
                                val channelName = customCommand.customExtras.getString(CHANNEL_NAME)
                                val channelLogin = customCommand.customExtras.getString(CHANNEL_LOGIN)
                                val channelLogo = customCommand.customExtras.getString(CHANNEL_LOGO)
                                val gameId = customCommand.customExtras.getString(GAME_ID)
                                val gameName = customCommand.customExtras.getString(GAME_NAME)
                                currentChannelId = channelId
                                currentChannelName = channelName
                                currentChannelLogin = channelLogin
                                currentGameId = gameId
                                currentGameName = gameName
                                lastStatsTime = if (session.player.isPlaying) System.currentTimeMillis() else 0L
                                videoId = null
                                offlineVideoId = null
                                proxyMediaPlaylist = false
                                val proxyHost = prefs().getString(C.PROXY_HOST, null)
                                val proxyPort = prefs().getString(C.PROXY_PORT, null)?.toIntOrNull()
                                val proxyUser = prefs().getString(C.PROXY_USER, null)
                                val proxyPassword = prefs().getString(C.PROXY_PASSWORD, null)
                                val multivariantPlaylistProxyClient = if (prefs().getBoolean(C.PROXY_MULTIVARIANT_PLAYLIST, false) && !proxyHost.isNullOrBlank() && proxyPort != null) {
                                    okHttpClient.newBuilder().apply {
                                        proxySelector(
                                            object : ProxySelector() {
                                                override fun select(u: URI): List<Proxy> {
                                                    return if (Regex(MULTIVARIANT_PLAYLIST_REGEX).matches(u.host)) {
                                                        listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)), Proxy.NO_PROXY)
                                                    } else {
                                                        listOf(Proxy.NO_PROXY)
                                                    }
                                                }

                                                override fun connectFailed(u: URI, sa: SocketAddress, e: IOException) {}
                                            }
                                        )
                                        if (!proxyUser.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                                            proxyAuthenticator { _, response ->
                                                response.request.newBuilder().header(
                                                    "Proxy-Authorization", Credentials.basic(proxyUser, proxyPassword)
                                                ).build()
                                            }
                                        }
                                    }.build()
                                } else null
                                val mediaPlaylistProxyClient = if (prefs().getBoolean(C.PROXY_MEDIA_PLAYLIST, true) && !proxyHost.isNullOrBlank() && proxyPort != null) {
                                    okHttpClient.newBuilder().apply {
                                        proxySelector(
                                            object : ProxySelector() {
                                                override fun select(u: URI): List<Proxy> {
                                                    return if (Regex(MEDIA_PLAYLIST_REGEX).matches(u.host)) {
                                                        listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)), Proxy.NO_PROXY)
                                                    } else {
                                                        listOf(Proxy.NO_PROXY)
                                                    }
                                                }

                                                override fun connectFailed(u: URI, sa: SocketAddress, e: IOException) {}
                                            }
                                        )
                                        if (!proxyUser.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                                            proxyAuthenticator { _, response ->
                                                response.request.newBuilder().header(
                                                    "Proxy-Authorization", Credentials.basic(proxyUser, proxyPassword)
                                                ).build()
                                            }
                                        }
                                    }.build()
                                } else null
                                val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, "OkHttp")
                                val playlistParsers = CustomHlsPlaylistParserFactory()
                                val dataSourceFactory = DefaultDataSource.Factory(
                                    this@PlaybackService,
                                    when {
                                        networkLibrary == "HttpEngine" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7 && httpEngine != null -> {
                                            HttpEngineDataSource.Factory(httpEngine!!.get(), cronetExecutor, multivariantPlaylistProxyClient, mediaPlaylistProxyClient) { proxyMediaPlaylist }
                                        }
                                        networkLibrary == "Cronet" && cronetEngine != null -> {
                                            CronetDataSource.Factory(cronetEngine!!.get(), cronetExecutor, multivariantPlaylistProxyClient, mediaPlaylistProxyClient) { proxyMediaPlaylist }
                                        }
                                        else -> {
                                            OkHttpDataSource.Factory(multivariantPlaylistProxyClient ?: okHttpClient, mediaPlaylistProxyClient) { proxyMediaPlaylist }
                                        }
                                    }.apply {
                                        prefs().getString(C.PLAYER_STREAM_HEADERS, null)?.let {
                                            try {
                                                val json = JSONObject(it)
                                                hashMapOf<String, String>().apply {
                                                    json.keys().forEach { key ->
                                                        put(key, json.optString(key))
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }?.let {
                                            setDefaultRequestProperties(it)
                                        }
                                    }
                                )
                                val accepted = bridge.handoff.setSource(player,
                                    HlsMediaSource.Factory(dataSourceFactory).apply {
                                        setPlaylistParserFactory(playlistParsers)
                                        setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
                                    },
                                    MediaItem.Builder().apply {
                                        setUri(uri?.toUri())
                                        setMimeType(MimeTypes.APPLICATION_M3U8)
                                        setLiveConfiguration(MediaItem.LiveConfiguration.Builder().apply {
                                            prefs().getString(C.PLAYER_LIVE_MIN_SPEED, "")?.toFloatOrNull()?.let { setMinPlaybackSpeed(it) }
                                            prefs().getString(C.PLAYER_LIVE_MAX_SPEED, "")?.toFloatOrNull()?.let { setMaxPlaybackSpeed(it) }
                                            prefs().getString(C.PLAYER_LIVE_TARGET_OFFSET, "2000")?.toLongOrNull()?.let { setTargetOffsetMs(it) }
                                        }.build())
                                        setMediaMetadata(
                                            MediaMetadata.Builder().apply {
                                                setTitle(title)
                                                setArtist(channelName)
                                                setArtworkUri(channelLogo?.toUri())
                                            }.build()
                                        )
                                    }.build(),
                                    // Receivers cannot load usher playlists; resolve a variant with the same HTTP stack.
                                    loader = castPlaylistLoader(dataSourceFactory, playlistParsers),
                                )
                                if (!accepted) return Futures.immediateFuture(SessionResult(androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED))
                                sourceRequest = customCommand.customExtras.getString(SOURCE_REQUEST)
                                session.player.applyLocalPlaybackDefaults(prefs().getInt(C.PLAYER_VOLUME, 100) / 100f, 1f)
                                session.player.prepare()
                                if (!bridge.handoff.hasPendingSource) session.player.playWhenReady = true
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            START_VIDEO -> {
                                liveRecovery?.startSource(false)
                                val uri = customCommand.customExtras.getString(URI)
                                val title = customCommand.customExtras.getString(TITLE)
                                val channelId = customCommand.customExtras.getString(CHANNEL_ID)
                                val channelName = customCommand.customExtras.getString(CHANNEL_NAME)
                                val channelLogin = customCommand.customExtras.getString(CHANNEL_LOGIN)
                                val channelLogo = customCommand.customExtras.getString(CHANNEL_LOGO)
                                val gameId = customCommand.customExtras.getString(GAME_ID)
                                val gameName = customCommand.customExtras.getString(GAME_NAME)
                                currentChannelId = channelId
                                currentChannelName = channelName
                                currentChannelLogin = channelLogin
                                currentGameId = gameId
                                currentGameName = gameName
                                lastStatsTime = if (session.player.isPlaying) System.currentTimeMillis() else 0L
                                val newId = customCommand.customExtras.getLong(VIDEO_ID).takeIf { it != 0L }
                                val position = if (videoId == newId && session.player.currentMediaItem != null) {
                                    session.player.currentPosition
                                } else {
                                    customCommand.customExtras.getLong(PLAYBACK_POSITION)
                                }
                                videoId = newId
                                offlineVideoId = null
                                val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, "OkHttp")
                                val accepted = bridge.handoff.setSource(player,
                                    HlsMediaSource.Factory(
                                        DefaultDataSource.Factory(
                                            this@PlaybackService,
                                            when {
                                                networkLibrary == "HttpEngine" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7 && httpEngine != null -> {
                                                    HttpEngineDataSource.Factory(httpEngine!!.get(), cronetExecutor, null, null) { false }
                                                }
                                                networkLibrary == "Cronet" && cronetEngine != null -> {
                                                    CronetDataSource.Factory(cronetEngine!!.get(), cronetExecutor, null, null) { false }
                                                }
                                                else -> {
                                                    OkHttpDataSource.Factory(okHttpClient, null) { false }
                                                }
                                            }
                                        )
                                    ).apply {
                                        setPlaylistParserFactory(CustomHlsPlaylistParserFactory())
                                    },
                                    MediaItem.Builder().apply {
                                        setMediaId("video:$newId")
                                        setUri(uri?.toUri())
                                        setMimeType(MimeTypes.APPLICATION_M3U8)
                                        setMediaMetadata(
                                            MediaMetadata.Builder().apply {
                                                setTitle(title)
                                                setArtist(channelName)
                                                setArtworkUri(channelLogo?.toUri())
                                            }.build()
                                        )
                                    }.build(),
                                    startPositionMs = position,
                                )
                                if (!accepted) return Futures.immediateFuture(SessionResult(androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED))
                                sourceRequest = customCommand.customExtras.getString(SOURCE_REQUEST)
                                session.player.applyLocalPlaybackDefaults(prefs().getInt(C.PLAYER_VOLUME, 100) / 100f, prefs().getFloat(C.PLAYER_SPEED, 1f))
                                session.player.prepare()
                                session.player.playWhenReady = true
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            START_CLIP -> {
                                liveRecovery?.startSource(false)
                                val uri = customCommand.customExtras.getString(URI)
                                val title = customCommand.customExtras.getString(TITLE)
                                val channelId = customCommand.customExtras.getString(CHANNEL_ID)
                                val channelName = customCommand.customExtras.getString(CHANNEL_NAME)
                                val channelLogin = customCommand.customExtras.getString(CHANNEL_LOGIN)
                                val channelLogo = customCommand.customExtras.getString(CHANNEL_LOGO)
                                val gameId = customCommand.customExtras.getString(GAME_ID)
                                val gameName = customCommand.customExtras.getString(GAME_NAME)
                                currentChannelId = channelId
                                currentChannelName = channelName
                                currentChannelLogin = channelLogin
                                currentGameId = gameId
                                currentGameName = gameName
                                lastStatsTime = if (session.player.isPlaying) System.currentTimeMillis() else 0L
                                videoId = null
                                offlineVideoId = null
                                val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, "OkHttp")
                                val accepted = bridge.handoff.setSource(player,
                                    ProgressiveMediaSource.Factory(
                                        DefaultDataSource.Factory(
                                            this@PlaybackService,
                                            when {
                                                networkLibrary == "HttpEngine" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7 && httpEngine != null -> {
                                                    HttpEngineDataSource.Factory(httpEngine!!.get(), cronetExecutor, null, null) { false }
                                                }
                                                networkLibrary == "Cronet" && cronetEngine != null -> {
                                                    CronetDataSource.Factory(cronetEngine!!.get(), cronetExecutor, null, null) { false }
                                                }
                                                else -> {
                                                    OkHttpDataSource.Factory(okHttpClient, null) { false }
                                                }
                                            }
                                        )
                                    ),
                                    MediaItem.Builder().apply {
                                        setUri(uri?.toUri())
                                        setMimeType(MimeTypes.VIDEO_MP4)
                                        setMediaMetadata(
                                            MediaMetadata.Builder().apply {
                                                setTitle(title)
                                                setArtist(channelName)
                                                setArtworkUri(channelLogo?.toUri())
                                            }.build()
                                        )
                                    }.build()
                                )
                                if (!accepted) return Futures.immediateFuture(SessionResult(androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED))
                                sourceRequest = customCommand.customExtras.getString(SOURCE_REQUEST)
                                session.player.applyLocalPlaybackDefaults(prefs().getInt(C.PLAYER_VOLUME, 100) / 100f, prefs().getFloat(C.PLAYER_SPEED, 1f))
                                session.player.prepare()
                                session.player.playWhenReady = true
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            START_OFFLINE_VIDEO -> {
                                bridge.handoff.clear()
                                liveRecovery?.startSource(false)
                                val uri = customCommand.customExtras.getString(URI)
                                val title = customCommand.customExtras.getString(TITLE)
                                val channelId = customCommand.customExtras.getString(CHANNEL_ID)
                                val channelName = customCommand.customExtras.getString(CHANNEL_NAME)
                                val channelLogin = customCommand.customExtras.getString(CHANNEL_LOGIN)
                                val channelLogo = customCommand.customExtras.getString(CHANNEL_LOGO)
                                val gameId = customCommand.customExtras.getString(GAME_ID)
                                val gameName = customCommand.customExtras.getString(GAME_NAME)
                                currentChannelId = channelId
                                currentChannelName = channelName
                                currentChannelLogin = channelLogin
                                currentGameId = gameId
                                currentGameName = gameName
                                lastStatsTime = if (session.player.isPlaying) System.currentTimeMillis() else 0L
                                val newId = customCommand.customExtras.getInt(VIDEO_ID).takeIf { it != 0 }
                                val position = if (offlineVideoId == newId && session.player.currentMediaItem != null) {
                                    session.player.currentPosition
                                } else {
                                    customCommand.customExtras.getLong(PLAYBACK_POSITION)
                                }
                                videoId = null
                                offlineVideoId = newId
                                session.player.setMediaItem(
                                    MediaItem.Builder().apply {
                                        setUri(uri)
                                        setMediaMetadata(
                                            MediaMetadata.Builder().apply {
                                                setTitle(title)
                                                setArtist(channelName)
                                                setArtworkUri(channelLogo?.toUri())
                                            }.build()
                                        )
                                    }.build(),
                                    position,
                                )
                                sourceRequest = customCommand.customExtras.getString(SOURCE_REQUEST)
                                session.player.applyLocalPlaybackDefaults(prefs().getInt(C.PLAYER_VOLUME, 100) / 100f, prefs().getFloat(C.PLAYER_SPEED, 1f))
                                session.player.prepare()
                                session.player.playWhenReady = true
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            TOGGLE_DYNAMICS_PROCESSING -> {
                                if (dynamicsProcessing?.enabled == true) {
                                    dynamicsProcessing?.enabled = false
                                } else {
                                    if (dynamicsProcessing == null) {
                                        reinitializeDynamicsProcessing(player.audioSessionId)
                                    } else {
                                        dynamicsProcessing?.enabled = true
                                    }
                                }
                                val enabled = dynamicsProcessing?.enabled == true
                                prefs().edit { putBoolean(C.PLAYER_AUDIO_COMPRESSOR, enabled) }
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                                    putBoolean(RESULT, enabled)
                                }))
                            }
                            TOGGLE_PROXY -> {
                                proxyMediaPlaylist = customCommand.customExtras.getBoolean(USING_PROXY)
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            SET_SLEEP_TIMER -> {
                                val duration = customCommand.customExtras.getLong(DURATION)
                                background = duration != -1L
                                if (!background) backgroundRetry?.reset()
                                val endTime = sleepTimerEndTime
                                sleepTimer?.cancel()
                                sleepTimerEndTime = 0L
                                if (duration > 0L) {
                                    sleepTimer = Timer().apply {
                                        schedule(duration) {
                                            Handler(Looper.getMainLooper()).post {
                                                savePosition()
                                                mediaSession?.player?.clearMediaItems()
                                                pauseAllPlayersAndStopSelf()
                                            }
                                        }
                                    }
                                    sleepTimerEndTime = System.currentTimeMillis() + duration
                                }
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                                    putLong(RESULT, endTime)
                                }))
                            }
                            CHECK_ADS -> {
                                val playlist = (session.player.currentManifest as? HlsManifest)?.mediaPlaylist
                                val adSegment = playlist?.segments?.lastOrNull()?.let { segment ->
                                    val segmentStartTime = playlist.startTimeUs + segment.relativeStartTimeUs
                                    listOf("Amazon", "Adform", "DCM").any { segment.title.contains(it) } ||
                                            playlist.interstitials.find {
                                                val startTime = it.startDateUnixUs
                                                val endTime = it.endDateUnixUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }
                                                    ?: it.durationUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.let { startTime + it }
                                                    ?: it.plannedDurationUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.let { startTime + it }
                                                endTime != null && (it.id.startsWith("stitched-ad-") ||
                                                        it.clientDefinedAttributes.find { it.name == "CLASS" }?.textValue == "twitch-stitched-ad" ||
                                                        it.clientDefinedAttributes.find { it.name.startsWith("X-TV-TWITCH-AD-") } != null)
                                                        && segmentStartTime in startTime..endTime
                                            } != null
                                } == true
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                                    putBoolean(RESULT, adSegment)
                                }))
                            }
                            GET_QUALITIES -> {
                                val playlist = (session.player.currentManifest as? HlsManifest)?.multivariantPlaylist
                                val list = playlist?.variants?.mapIndexedNotNull { index, variant ->
                                    val videoName = playlist.videos.find { it.groupId == variant.videoGroupId }?.name
                                    val name = variant.format.toReadableQualityName(videoName, variant.url.toString(), index.toString())
                                    variant.format.toVideoQuality(name, variant.url.toString())
                                }
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                                    putStringArray(NAMES, list?.map { it.name.toString() }?.toTypedArray())
                                    putStringArray(CODECS, list?.map { it.codecs.toString() }?.toTypedArray())
                                    putStringArray(URLS, list?.map { it.url.toString() }?.toTypedArray())
                                    putIntArray(QUALITY_BITRATES, list?.map { it.bitrate ?: -1 }?.toIntArray())
                                    putIntArray(QUALITY_RESOLUTIONS, list?.map { it.resolution ?: -1 }?.toIntArray())
                                    putFloatArray(QUALITY_FRAME_RATES, list?.map { it.frameRate ?: -1f }?.toFloatArray())
                                    putStringArray(QUALITY_FORMAT_IDS, list?.map { it.formatId.orEmpty() }?.toTypedArray())
                                }))
                            }
                            GET_DURATION -> {
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                                    putLong(RESULT, (session.player.currentManifest as? HlsManifest)?.mediaPlaylist?.durationUs?.div(1000) ?: 0)
                                }))
                            }
                            GET_ERROR_CODE -> {
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                                    putInt(RESULT, LivePlaybackRecovery.httpCode(session.player.playerError))
                                    putBoolean(RECOVERING, liveRecovery?.isErrorHandled == true)
                                    putBoolean(RECOVERY_EXHAUSTED, liveRecovery?.isExhausted == true)
                                }))
                            }
                            GET_CAST_AVAILABLE -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS,
                                Bundle().apply { putBoolean(RESULT, bridge.castAvailable) }))
                            GET_MEDIA_PLAYLIST -> {
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                                    putStringArray(RESULT, (session.player.currentManifest as? HlsManifest)?.mediaPlaylist?.tags?.toTypedArray())
                                }))
                            }
                            GET_MULTIVARIANT_PLAYLIST -> {
                                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                                    putStringArray(RESULT, (session.player.currentManifest as? HlsManifest)?.multivariantPlaylist?.tags?.toTypedArray())
                                }))
                            }
                            else -> super.onCustomCommand(session, controller, customCommand, args)
                        }
                    }
                }
            )
        }.build()
        mediaSession?.player?.let { PlaybackSessionOwner.register(it, bridge) }
    }

    private fun reinitializeDynamicsProcessing(audioSessionId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, null).apply {
                for (channelIdx in 0 until channelCount) {
                    for (bandIdx in 0 until getMbcByChannelIndex(channelIdx).bandCount) {
                        setMbcBandByChannelIndex(
                            channelIdx,
                            bandIdx,
                            getMbcBandByChannelIndex(channelIdx, bandIdx).apply {
                                attackTime = 0f
                                releaseTime = 0.25f
                                ratio = 1.6f
                                threshold = -50f
                                kneeWidth = 40f
                                preGain = 0f
                                postGain = 10f
                            }
                        )
                    }
                }
                enabled = true
            }
        }
    }

    private fun updateStats() {
        val now = System.currentTimeMillis()
        if (lastStatsTime > 0) {
            val diff = (now - lastStatsTime) / 1000
            if (diff > 0) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                statsScope.launch {
                    statsRepository.updateScreenTime(date, diff)
                    currentChannelId?.let { channelId ->
                        statsRepository.updateStreamWatchStats(channelId, currentChannelName ?: channelId, diff)
                    }
                    // Record watch session with category info for detailed analytics
                    if (currentChannelId != null || currentGameId != null) {
                        val session = WatchSession(
                            channelId = currentChannelId,
                            channelName = currentChannelName,
                            channelLogin = currentChannelLogin,
                            gameId = currentGameId,
                            gameName = currentGameName,
                            startTime = lastStatsTime,
                            endTime = now,
                            durationSeconds = diff,
                            date = date,
                            hourOfDay = hourOfDay
                        )
                        statsRepository.recordWatchSession(session)
                    }
                }
            }
        }
        lastStatsTime = now
    }

    /** Close is conditional on service acceptance, not on an asynchronous UI acknowledgement. */
    internal fun closeSource(request: String?, disconnect: Boolean) {
        if (request == null || request != sourceRequest) return
        sourceRequest = null
        savePosition()
        castBridge?.handoff?.cancelPending(clearOwnership = true)
        mediaSession?.player?.let {
            it.pause()
            it.stop()
            it.clearMediaItems()
        }
        if (disconnect) castBridge?.endSession()
        sleepTimer?.cancel()
        sleepTimer = null
        sleepTimerEndTime = 0L
    }

    private fun savePosition() {
        mediaSession?.player?.let { player ->
            if (!player.currentTracks.isEmpty && prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                // Player getters are confined to its application thread, including during close.
                val position = player.currentPosition
                videoId?.let {
                    statsScope.launch {
                        playerRepository.saveVideoPosition(VideoPosition(it, position))
                    }
                } ?:
                offlineVideoId?.let {
                    statsScope.launch {
                        offlineRepository.updateVideoPosition(it, position)
                    }
                }
            }
        }
    }

    private fun updateSavedPosition() {
        mediaSession?.player?.let { player ->
            if (!player.currentTracks.isEmpty && prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                val currentPosition = player.currentPosition
                val savedPosition = lastSavedPosition
                if (savedPosition == null || currentPosition - savedPosition !in 0..2000) {
                    lastSavedPosition = currentPosition
                    videoId?.let {
                        statsScope.launch {
                            playerRepository.saveVideoPosition(VideoPosition(it, currentPosition))
                        }
                    } ?:
                    offlineVideoId?.let {
                        statsScope.launch {
                            offlineRepository.updateVideoPosition(it, currentPosition)
                        }
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun isNetworkAvailable(): Boolean {
        val connectivity = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
    }

    private fun watchRecoveryConnectivity() {
        val connectivity = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun update() {
                if (networkCallback !== this) return
                networkHandler.post {
                    if (networkCallback === this) liveRecovery?.onConnectivityChanged(isNetworkAvailable())
                }
            }
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update()
        }
        networkCallback = callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) connectivity.registerDefaultNetworkCallback(callback)
        else connectivity.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
    }

    private fun stopRecoveryConnectivity() {
        networkCallback?.let {
            (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it)
        }
        networkCallback = null
        networkHandler.removeCallbacksAndMessages(null)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.let {
            if (keepsCastingAfterExit(prefs().getBoolean(C.PLAYER_CAST_KEEP_ON_EXIT, false), it)) return
        }
        savePosition()
        mediaSession?.player?.clearMediaItems()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        mediaSession?.player?.let(PlaybackSessionOwner::unregister)
        stopRecoveryConnectivity()
        liveRecovery?.dispose()
        liveRecovery = null
        backgroundRetry?.reset()
        backgroundRetry = null
        castBridge?.dispose()
        mediaSession?.run {
            player.stop()
            player.release()
            release()
            mediaSession = null
        }
        castBridge = null
        super.onDestroy()
    }

    class CustomHlsPlaylistParserFactory(): HlsPlaylistParserFactory {
        override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> {
            return HlsPlaylistParser()
        }

        override fun createPlaylistParser(multivariantPlaylist: HlsMultivariantPlaylist, previousMediaPlaylist: HlsMediaPlaylist?): ParsingLoadable.Parser<HlsPlaylist> {
            return HlsPlaylistParser(multivariantPlaylist, previousMediaPlaylist)
        }
    }

    companion object {
        private val SOURCE_START_COMMANDS = setOf(START_STREAM, START_VIDEO, START_CLIP, START_OFFLINE_VIDEO)
        /** Receiver playback survives closing the app only when the user opted in and media is actually on the receiver. */
        fun keepsCastingAfterExit(keepCasting: Boolean, player: Player) = keepCasting &&
            player.deviceInfo.playbackType == androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE &&
            player.currentMediaItem != null

        const val START_STREAM = "startStream"
        const val START_VIDEO = "startVideo"
        const val START_CLIP = "startClip"
        const val START_OFFLINE_VIDEO = "startOfflineVideo"
        const val END_CAST_SESSION = "endCastSession"
        const val CLOSE_SOURCE = "closeSource"
        const val SOURCE_REQUEST = "sourceRequest"
        const val GET_SOURCE_REQUEST = "getSourceRequest"
        const val DISCONNECT_CAST = "disconnectCast"
        const val GET_CAST_AVAILABLE = "getCastAvailable"
        const val TOGGLE_DYNAMICS_PROCESSING = "toggleDynamicsProcessing"
        const val TOGGLE_PROXY = "toggleProxy"
        const val SET_SLEEP_TIMER = "setSleepTimer"
        const val CHECK_ADS = "checkAds"
        const val GET_QUALITIES = "getQualities"
        const val GET_DURATION = "getDuration"
        const val GET_ERROR_CODE = "getErrorCode"
        const val RECOVERING = "recovering"
        const val RECOVERY_EXHAUSTED = "recoveryExhausted"
        const val CUSTOM_PROXY = "customProxy"
        const val GET_MEDIA_PLAYLIST = "getMediaPlaylist"
        const val GET_MULTIVARIANT_PLAYLIST = "getMultivariantPlaylist"

        const val RESULT = "result"
        const val URI = "uri"
        const val VIDEO_ID = "videoId"
        const val PLAYBACK_POSITION = "playbackPosition"
        const val TITLE = "title"
        const val CHANNEL_ID = "channelId"
        const val CHANNEL_NAME = "channelName"
        const val CHANNEL_LOGIN = "channelLogin"
        const val CHANNEL_LOGO = "channelLogo"
        const val GAME_ID = "gameId"
        const val GAME_NAME = "gameName"
        const val USING_PROXY = "usingProxy"
        const val DURATION = "duration"
        const val NAMES = "names"
        const val CODECS = "codecs"
        const val URLS = "urls"
        const val QUALITY_BITRATES = "quality_bitrates"
        const val QUALITY_RESOLUTIONS = "quality_resolutions"
        const val QUALITY_FRAME_RATES = "quality_frame_rates"
        const val QUALITY_FORMAT_IDS = "quality_format_ids"

        const val MULTIVARIANT_PLAYLIST_REGEX = "^usher\\.ttvnw\\.net$"
        const val MEDIA_PLAYLIST_REGEX = "^(?:[a-z0-9-]+\\.playlist\\.(?:live-video|ttvnw)\\.net|video-weaver\\.[a-z0-9-]+\\.hls\\.ttvnw\\.net)$"

        const val REQUEST_CODE_RESUME = 2
    }
}
