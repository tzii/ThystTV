package com.github.andreyasadchy.xtra.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.Account
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.Failure
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.ProbeException
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** One application-owned writer. Only immutable samples cross the player boundary. */
@Singleton
class TwitchSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val store: TwitchSyncStore,
    private val repository: TwitchResumeProbeRepository,
    private val liveRepository: TwitchLiveWatchRepository,
) {
    data class Sample(
        val videoId: String? = null, val title: String = "", val positionMs: Long = 0,
        val durationMs: Long = 0, val playing: Boolean = false,
        val broadcastId: String? = null, val channelId: String? = null, val channelLogin: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writer = Mutex()
    private var worker: Job? = null
    private var liveJob: Job? = null
    private val clock = TwitchPlaybackClock()
    private var owner: String? = null
    private var sampleAccount: Account? = null
    private var previous: Sample? = null
    private var owned = false
    private var lastCheckpoint = 0L
    private var suppressedVideo: String? = null
    private val halted = mutableSetOf<String>()
    private val messages = mutableMapOf<String, Int>()
    private val liveMessages = mutableMapOf<String, Int>()
    private val historyMessages = mutableMapOf<String, Int>()
    private var refreshRevision = 0L
    // Keep a strong reference: SharedPreferences stores listeners weakly.
    private val tokenListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        scope.launch {
            worker?.cancel(); liveJob?.cancel()
            previous = null; owned = false; sampleAccount = null; clock.reset()
            refreshRevision++; historyMessages.clear()
        }
    }

    init { context.tokenPrefs().registerOnSharedPreferenceChangeListener(tokenListener) }

    fun account() = Account(context.tokenPrefs().getString(C.USER_ID, null).orEmpty(), TwitchApiHelper.getGQLHeaders(context, true))
    fun vodEnabled(id: String) = store.enabled(id, "vod")
    fun liveEnabled(id: String) = store.enabled(id, "live", context.prefs().getBoolean(C.CHAT_POINTS_COLLECT, true))
    private fun same(account: Account): Boolean = this.account().let { it.id == account.id && it.headers == account.headers }
    private fun current(account: Account) = same(account) && vodEnabled(account.id)

    fun status(id: String, live: Boolean = false): Int = (if (live) liveMessages else messages)[id] ?: R.string.twitch_sync_ready
    fun historyStatus(id: String): Int = historyMessages[id] ?: R.string.twitch_sync_history_not_loaded

    fun configure(id: String, kind: String, enabled: Boolean) {
        if (id != account().id) return
        store.enable(id, kind, enabled)
        if (kind == "vod") {
            worker?.cancel(); previous = null; owned = false
            halted.remove(id)
            if (enabled) wake()
        } else {
            liveJob?.cancel(); clock.reset()
        }
    }

    fun attach(session: String) {
        owner = session
        previous = null; owned = false; suppressedVideo = null; clock.reset()
        wake()
    }

    fun detach(session: String) {
        if (owner != session) return
        previous?.let { checkpoint(it, force = true) }
        owner = null; previous = null; owned = false; clock.reset()
        liveJob?.cancel()
        wake()
    }

    fun sample(session: String, value: Sample) {
        if (owner != session) return
        val account = account()
        if (sampleAccount?.let { it.id == account.id && it.headers == account.headers } != true) {
            previous = null; owned = false; clock.reset(); sampleAccount = account
            wake()
        }
        val old = previous
        if (old?.videoId != value.videoId) owned = false
        if (value.videoId != null && value.playing && old?.playing == true && old.videoId == value.videoId && value.positionMs > old.positionMs) owned = true
        checkpoint(value, force = old?.playing == true && !value.playing)
        previous = value
        val broadcast = value.broadcastId
        val channel = value.channelId
        val login = value.channelLogin
        if (!liveEnabled(account.id) || broadcast.isNullOrBlank() || channel.isNullOrBlank() || login.isNullOrBlank()) {
            clock.reset()
            return
        }
        val key = "$session:${account.id}:$channel:$broadcast"
        if (clock.sample(key, SystemClock.elapsedRealtime(), value.positionMs, value.playing) && liveJob?.isActive != true) {
            liveJob = scope.launch {
                val isCurrent = { same(account) && liveEnabled(account.id) && owner == session &&
                    previous?.broadcastId == broadcast && previous?.channelId == channel }
                try {
                    repository.validate(account, isCurrent)
                    liveRepository.send(account.id, broadcast, channel, login, isCurrent)
                    liveMessages[account.id] = R.string.twitch_sync_live_sent
                } catch (error: CancellationException) { throw error
                } catch (_: Exception) { liveMessages[account.id] = R.string.twitch_sync_live_failed }
            }
        }
    }

    fun checkpointNow(session: String, video: String, position: Long?, duration: Long) {
        val old = previous ?: return
        if (owner != session || old.videoId != video || position == null) return
        val latest = old.copy(positionMs = position, durationMs = duration)
        previous = latest
        checkpoint(latest, force = true)
    }

    private fun checkpoint(value: Sample, force: Boolean) {
        val account = sampleAccount ?: return
        val video = value.videoId ?: return
        if (!owned || video == suppressedVideo || !current(account) || value.positionMs < 0 ||
            value.durationMs <= 0 || value.positionMs > value.durationMs) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastCheckpoint < 5_000) return
        lastCheckpoint = now
        try {
            store.progress(account.id, video) {
                if (it.remoteChosen || it.seconds == value.positionMs / 1000) it else it.checkpoint(value.positionMs / 1000).copy(title = value.title.take(200))
            }
            wake()
        } catch (error: Exception) {
            messages[account.id] = if (error is TwitchSyncStore.OutboxFullException) R.string.twitch_sync_queue_full else R.string.twitch_sync_storage_error
            halted.add(account.id)
        }
    }

    /** Called before starting a VoD; the caller retains explicit links and completed-video policy. */
    suspend fun resume(video: String, local: Long?): Long? {
        val account = account()
        if (!vodEnabled(account.id)) return local
        return try {
            val observed = withTimeoutOrNull(3_000) {
                writer.withLock {
                    val remote = repository.read(account, video) { current(account) }.seconds
                    store.progress(account.id, video) {
                        if (it.pending) it.copy(conflict = it.conflict || it.conflictsWith(remote), remote = remote) else
                            it.copy(seconds = remote, baseline = remote, baselineKnown = true, remote = remote)
                    }
                    remote?.times(1000)
                }
            }
            finishResume(account, video, local, observed)
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) {
            if (!current(account)) return local
            if ((error as? ProbeException)?.failure == Failure.UNAVAILABLE_VIDEO) {
                runCatching { store.progress(account.id, video) { it.copy(unavailable = true) } }
                messages[account.id] = R.string.twitch_sync_video_unavailable
            } else failure(account.id, error)
            runCatching { finishResume(account, video, local, null) }.getOrDefault(local)
        }
    }

    private fun finishResume(account: Account, video: String, local: Long?, observed: Long?): Long? {
        if (!current(account)) return local
        val saved = store.read(account.id).progress.find { it.videoId == video }
        val result = saved?.takeIf { it.pending }?.seconds?.times(1000) ?: observed ?:
            if (saved?.remoteChosen == true) (saved.seconds ?: 0L) * 1000 else local
        // Persist the hold across view recreation, releasing it only on an actual new resume.
        if (saved?.remoteChosen == true) store.progress(account.id, video) { it.copy(remoteChosen = false) }
        return result
    }

    /** Refresh only the account's recent Continue Watching shelf; never import into local Stats. */
    suspend fun refresh(): List<TwitchRecentVideo> {
        val account = account()
        if (account.id.isBlank()) return emptyList()
        val revision = ++refreshRevision
        val isCurrent = { same(account) && refreshRevision == revision }
        historyMessages[account.id] = R.string.twitch_sync_history_loading
        halted.remove(account.id)
        return try {
            val recent = repository.recent(account, isCurrent).map { TwitchRecentVideo(it.videoId, it.title, it.seconds) }
            if (!isCurrent()) return emptyList()
            store.update(account.id) { it.copy(recent = recent) }
            historyMessages[account.id] = if (recent.isEmpty()) R.string.resume_probe_empty else R.string.twitch_sync_refreshed
            messages[account.id] = R.string.twitch_sync_refreshed
            wake()
            recent
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) {
            if (!isCurrent()) return emptyList()
            historyMessages[account.id] = failureMessage(error, history = true)
            failure(account.id, error)
            runCatching { store.read(account.id).recent }.getOrDefault(emptyList())
        } finally {
            if (isCurrent() && historyMessages[account.id] == R.string.twitch_sync_history_loading) historyMessages.remove(account.id)
        }
    }

    fun resolve(video: String, keepLocal: Boolean) {
        val account = account()
        store.progress(account.id, video) {
            if (!it.conflict) it else if (keepLocal) {
                // A subsequent preflight will stop again if Twitch changes after this choice.
                it.copy(baseline = it.remote, baselineKnown = true, conflict = false, pending = true, revision = it.revision + 1)
            } else {
                if (previous?.videoId == video) suppressedVideo = video
                it.copy(seconds = it.remote, baseline = it.remote, baselineKnown = true, conflict = false, pending = false, remoteChosen = true, revision = it.revision + 1)
            }
        }
        wake()
    }

    fun unavailableVideo(video: String, retry: Boolean) {
        val account = account()
        store.progress(account.id, video) {
            if (!retry && previous?.videoId == video) suppressedVideo = video
            it.copy(unavailable = false, pending = retry && it.pending, remoteChosen = !retry, revision = it.revision + 1)
        }
        wake()
    }

    private fun wake() {
        val account = account()
        if (!vodEnabled(account.id) || account.id in halted || worker?.isActive == true) return
        worker = scope.launch {
            var backoff = 30_000L
            // Coalesce rapid seek/pause samples before taking a snapshot.
            delay(1_000)
            while (isActive && current(account) && account.id !in halted) {
                try {
                    val sent = store.read(account.id).progress.firstOrNull { it.pending && !it.conflict && !it.unavailable } ?: break
                    try {
                        writer.withLock { upload(account, sent) }
                    } catch (error: ProbeException) {
                        if (error.failure != Failure.UNAVAILABLE_VIDEO && error.failure != Failure.INVALID_POSITION) throw error
                        store.progress(account.id, sent.videoId) { it.copy(unavailable = true) }
                        messages[account.id] = R.string.twitch_sync_video_unavailable
                    }
                    backoff = 30_000
                } catch (error: CancellationException) { throw error
                } catch (error: Exception) {
                    failure(account.id, error)
                    backoff = (backoff * 2).coerceAtMost(300_000)
                }
                delay(backoff)
            }
        }
    }

    private suspend fun upload(account: Account, sent: TwitchProgress) {
        val desired = sent.seconds ?: return
        val isCurrent = { current(account) }
        val remote = repository.read(account, sent.videoId, isCurrent).seconds
        if (sent.conflictsWith(remote)) {
            store.progress(account.id, sent.videoId) { it.copy(conflict = true, remote = remote) }
            messages[account.id] = R.string.twitch_sync_conflict
            return
        }
        // A prior timed-out mutation may already have succeeded. Read before retrying it.
        if (remote == desired) {
            store.progress(account.id, sent.videoId) { it.acknowledge(sent, desired) }
        } else {
            val result = repository.writeAndReadBack(account, sent.videoId, desired * 1000, isCurrent)
            if (result.readbackFailure != null) throw ProbeException(result.readbackFailure)
            if (!result.matched) {
                store.progress(account.id, sent.videoId) { it.copy(conflict = true, remote = result.observed) }
                messages[account.id] = R.string.twitch_sync_conflict
                return
            }
            store.progress(account.id, sent.videoId) { it.acknowledge(sent, desired) }
        }
        messages[account.id] = R.string.twitch_sync_verified
    }

    private fun failure(id: String, error: Exception) {
        messages[id] = failureMessage(error)
        if (error is kotlinx.serialization.SerializationException || error is TwitchSyncStore.OutboxFullException) {
            halted.add(id)
            return
        }
        val reason = (error as? ProbeException)?.failure
        if (reason != null && reason !in listOf(Failure.NETWORK, Failure.HTTP, Failure.SESSION_CHANGED)) halted.add(id)
    }

    private fun failureMessage(error: Exception, history: Boolean = false): Int {
        if (error is kotlinx.serialization.SerializationException) return R.string.twitch_sync_storage_error
        if (error is TwitchSyncStore.OutboxFullException) return R.string.twitch_sync_queue_full
        return when ((error as? ProbeException)?.failure) {
            Failure.SIGN_IN_REQUIRED -> R.string.resume_probe_sign_in
            Failure.ACCOUNT_MISMATCH -> R.string.resume_probe_account_mismatch
            Failure.CLIENT_MISMATCH -> R.string.resume_probe_client_mismatch
            Failure.TOKEN_REJECTED -> R.string.resume_probe_token_rejected
            Failure.AUTHENTICATION -> R.string.resume_probe_authentication
            Failure.NETWORK -> if (history) R.string.resume_probe_network else R.string.twitch_sync_retry
            Failure.HTTP -> if (history) R.string.resume_probe_http else R.string.twitch_sync_retry
            Failure.UNSUPPORTED_OPERATION, Failure.GRAPHQL, Failure.INVALID_RESPONSE -> R.string.twitch_sync_unsupported
            Failure.SESSION_CHANGED -> R.string.twitch_sync_ready
            else -> R.string.twitch_sync_retry
        }
    }

    fun observeIrc(message: String, id: String?, channel: String?) {
        observe(id, channel) { TwitchStreakParser.irc(message, it, channel.orEmpty()) }
    }

    fun observeEventSub(event: JSONObject, timestamp: String?, id: String?, channel: String?) {
        observe(id, channel) { TwitchStreakParser.eventSub(event, timestamp, it, channel.orEmpty()) }
    }

    private fun observe(id: String?, channel: String?, parse: (String) -> TwitchStreak?) {
        if (id.isNullOrBlank() || channel.isNullOrBlank() || id != account().id) return
        val streak = parse(id) ?: return
        try { store.milestone(id, streak) } catch (_: Exception) { /* Never interrupt chat for a cache failure. */ }
    }
}
