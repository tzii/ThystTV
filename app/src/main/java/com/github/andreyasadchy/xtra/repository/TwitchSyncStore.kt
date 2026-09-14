package com.github.andreyasadchy.xtra.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchSyncStore @Inject constructor(@ApplicationContext context: Context) {
    class OutboxFullException : IllegalStateException()
    private val prefs = context.getSharedPreferences("twitch_account_sync", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun enabled(account: String, kind: String, default: Boolean = false) =
        account.isNotBlank() && prefs.getBoolean("$account:$kind", default)

    fun enable(account: String, kind: String, enabled: Boolean) {
        if (account.isNotBlank()) prefs.edit { putBoolean("$account:$kind", enabled) }
    }

    @Synchronized
    fun read(account: String): TwitchSyncState = prefs.getString("$account:state", null)?.let {
        // Do not silently overwrite a corrupt outbox with an empty one.
        json.decodeFromString<TwitchSyncState>(it)
    } ?: TwitchSyncState()

    @Synchronized
    fun update(account: String, transform: (TwitchSyncState) -> TwitchSyncState) {
        val state = transform(read(account))
        if (state.progress.count { it.pending } > 500) throw OutboxFullException()
        val retained = state.progress.filter { it.pending } + state.progress.filterNot { it.pending }.takeLast(100)
        prefs.edit { putString("$account:state", json.encodeToString(state.copy(progress = retained))) }
    }

    fun progress(account: String, video: String, transform: (TwitchProgress) -> TwitchProgress) = update(account) { state ->
        val item = state.progress.find { it.videoId == video } ?: TwitchProgress(video)
        state.copy(progress = state.progress.filterNot { it.videoId == video } + transform(item))
    }

    fun milestone(account: String, streak: TwitchStreak) = update(account) { state ->
        val old = state.streaks.find { it.channelId == streak.channelId }
        if (old != null && (old.eventId == streak.eventId || old.timestamp >= streak.timestamp)) state else
            state.copy(streaks = (state.streaks.filterNot { it.channelId == streak.channelId } + streak).takeLast(100))
    }
}
