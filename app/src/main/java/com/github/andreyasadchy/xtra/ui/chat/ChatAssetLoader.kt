package com.github.andreyasadchy.xtra.ui.chat

import android.content.Context
import android.util.Log
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.repository.EmoteCache
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepository: PlayerRepository,
    private val emoteCache: EmoteCache
) {

    private val TAG = "ChatAssetLoader"

    private val _channelBadges = MutableStateFlow<List<TwitchBadge>?>(null)
    val channelBadges: StateFlow<List<TwitchBadge>?> = _channelBadges.asStateFlow()

    private val _channelStvEmotes = MutableStateFlow<List<Emote>?>(null)
    val channelStvEmotes: StateFlow<List<Emote>?> = _channelStvEmotes.asStateFlow()

    private val _channelBttvEmotes = MutableStateFlow<List<Emote>?>(null)
    val channelBttvEmotes: StateFlow<List<Emote>?> = _channelBttvEmotes.asStateFlow()

    private val _channelFfzEmotes = MutableStateFlow<List<Emote>?>(null)
    val channelFfzEmotes: StateFlow<List<Emote>?> = _channelFfzEmotes.asStateFlow()

    private val _cheerEmotes = MutableStateFlow<List<CheerEmote>?>(null)
    val cheerEmotes: StateFlow<List<CheerEmote>?> = _cheerEmotes.asStateFlow()

    private val _userEmotes = MutableStateFlow<List<Emote>?>(null)
    val userEmotes: StateFlow<List<Emote>?> = _userEmotes.asStateFlow()
    
    // Derived state for the ViewModel to observe or use
    private val _allEmotes = mutableListOf<Emote>()
    val allEmotes: List<Emote> get() = _allEmotes

    // Channel 7TV Emote Set ID
    var channelStvEmoteSetId: String? = null
        private set

    // Integrity check callback
    var onIntegrityRequest: (() -> Unit)? = null
    var onReloadMessagesRequest: (() -> Unit)? = null

    // Flag to prevent redundant loadEmoteSets calls
    private var loadedUserEmotes = false

    // Helper to add emotes to the big list
    private fun addEmotes(list: List<Emote>) {
        // Simple synchronized addition or just main thread access if called from coroutines
        synchronized(_allEmotes) {
             _allEmotes.addAll(list.filter { it !in _allEmotes })
        }
    }

    fun loadEmotes(
        scope: CoroutineScope,
        channelId: String?,
        channelLogin: String?
    ) {
        val networkLibrary = context.prefs().getString(C.NETWORK_LIBRARY, "OkHttp")
        val helixHeaders = TwitchApiHelper.getHelixHeaders(context)
        val gqlHeaders = TwitchApiHelper.getGQLHeaders(context, true)
        val emoteQuality = context.prefs().getString(C.CHAT_IMAGE_QUALITY, "4") ?: "4"
        val animateGifs = context.prefs().getBoolean(C.ANIMATED_EMOTES, true)
        val useWebp = context.prefs().getBoolean(C.CHAT_USE_WEBP, true)
        val enableIntegrity = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false)

        // Global Badges
        val savedBadges = emoteCache.globalBadges.value
        if (!savedBadges.isNullOrEmpty()) {
             requestReloadMessages()
        } else {
            scope.launch {
                try {
                    playerRepository.loadGlobalBadges(networkLibrary, helixHeaders, gqlHeaders, emoteQuality, enableIntegrity).let { badges ->
                        if (badges.isNotEmpty()) {
                            emoteCache.setGlobalBadges(badges)
                            requestReloadMessages()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load global badges", e)
                    if (e.message == "failed integrity check") {
                        onIntegrityRequest?.invoke()
                    }
                }
            }
        }

        // Global 7TV
        if (context.prefs().getBoolean(C.CHAT_ENABLE_STV, true)) {
            val saved = emoteCache.globalStvEmotes.value
            if (!saved.isNullOrEmpty()) {
                addEmotes(saved)
                requestReloadMessages()
            } else {
                scope.launch {
                    try {
                        playerRepository.loadGlobalStvEmotes(networkLibrary, useWebp).let { emotes ->
                            if (emotes.isNotEmpty()) {
                                emoteCache.setGlobalStvEmotes(emotes)
                                addEmotes(emotes)
                                requestReloadMessages()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load global 7tv emotes", e)
                    }
                }
            }

            if (!channelId.isNullOrBlank()) {
                scope.launch {
                    try {
                        playerRepository.loadStvEmotes(networkLibrary, channelId, useWebp).let {
                            if (it.second.isNotEmpty()) {
                                channelStvEmoteSetId = it.first
                                addEmotes(it.second)
                                _channelStvEmotes.value = it.second
                                requestReloadMessages()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load 7tv emotes for channel $channelId", e)
                    }
                }
            }
        }

        // Global BTTV
        if (context.prefs().getBoolean(C.CHAT_ENABLE_BTTV, true)) {
            val saved = emoteCache.globalBttvEmotes.value
            if (!saved.isNullOrEmpty()) {
                addEmotes(saved)
                requestReloadMessages()
            } else {
                scope.launch {
                    try {
                        playerRepository.loadGlobalBttvEmotes(networkLibrary, useWebp).let { emotes ->
                            if (emotes.isNotEmpty()) {
                                emoteCache.setGlobalBttvEmotes(emotes)
                                addEmotes(emotes)
                                requestReloadMessages()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load global BTTV emotes", e)
                    }
                }
            }
            if (!channelId.isNullOrBlank()) {
                scope.launch {
                    try {
                        playerRepository.loadBttvEmotes(networkLibrary, channelId, useWebp).let {
                            if (it.isNotEmpty()) {
                                addEmotes(it)
                                _channelBttvEmotes.value = it
                                requestReloadMessages()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load BTTV emotes for channel $channelId", e)
                    }
                }
            }
        }

        // Global FFZ
        if (context.prefs().getBoolean(C.CHAT_ENABLE_FFZ, true)) {
            val saved = emoteCache.globalFfzEmotes.value
            if (!saved.isNullOrEmpty()) {
                addEmotes(saved)
                requestReloadMessages()
            } else {
                scope.launch {
                    try {
                        playerRepository.loadGlobalFfzEmotes(networkLibrary, useWebp).let { emotes ->
                            if (emotes.isNotEmpty()) {
                                emoteCache.setGlobalFfzEmotes(emotes)
                                addEmotes(emotes)
                                requestReloadMessages()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load global FFZ emotes", e)
                    }
                }
            }
            if (!channelId.isNullOrBlank()) {
                scope.launch {
                    try {
                        playerRepository.loadFfzEmotes(networkLibrary, channelId, useWebp).let {
                            if (it.isNotEmpty()) {
                                addEmotes(it)
                                _channelFfzEmotes.value = it
                                requestReloadMessages()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load FFZ emotes for channel $channelId", e)
                    }
                }
            }
        }

        // Channel Badges and Cheermotes
        if (!channelId.isNullOrBlank() || !channelLogin.isNullOrBlank()) {
            scope.launch {
                try {
                    playerRepository.loadChannelBadges(networkLibrary, helixHeaders, gqlHeaders, channelId, channelLogin, emoteQuality, enableIntegrity).let {
                        if (it.isNotEmpty()) {
                            _channelBadges.value = it
                            requestReloadMessages()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load badges for channel $channelId", e)
                    if (e.message == "failed integrity check") {
                        onIntegrityRequest?.invoke()
                    }
                }
            }
            scope.launch {
                try {
                    playerRepository.loadCheerEmotes(networkLibrary, helixHeaders, gqlHeaders, channelId, channelLogin, animateGifs, enableIntegrity).let {
                        if (it.isNotEmpty()) {
                            _cheerEmotes.value = it
                            requestReloadMessages()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load cheermotes for channel $channelId", e)
                    if (e.message == "failed integrity check") {
                        onIntegrityRequest?.invoke()
                    }
                }
            }
        }
    }

    fun loadUserEmotes(scope: CoroutineScope, channelId: String?) {
        val saved = emoteCache.userEmotes.value
        if (!saved.isNullOrEmpty()) {
            addEmotes(
                saved.map {
                    Emote(
                        name = it.name,
                        url1x = it.url1x,
                        url2x = it.url2x,
                        url3x = it.url3x,
                        url4x = it.url4x,
                        format = it.format
                    )
                }
            )
            _userEmotes.value = saved.sortedByDescending { it.ownerId == channelId }.map {
                Emote(
                    name = it.name,
                    url1x = it.url1x,
                    url2x = it.url2x,
                    url3x = it.url3x,
                    url4x = it.url4x,
                    format = it.format
                )
            }
        } else {
            val helixHeaders = TwitchApiHelper.getHelixHeaders(context)
            val gqlHeaders = TwitchApiHelper.getGQLHeaders(context, true)
            if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() || !helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                scope.launch {
                    try {
                        val networkLibrary = context.prefs().getString(C.NETWORK_LIBRARY, "OkHttp")
                        val accountId = context.tokenPrefs().getString(C.USER_ID, null)
                        val animateGifs =  context.prefs().getBoolean(C.ANIMATED_EMOTES, true)
                        val enableIntegrity = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false)
                        playerRepository.loadUserEmotes(networkLibrary, helixHeaders, gqlHeaders, channelId, accountId, animateGifs, enableIntegrity).let { emotes ->
                            if (emotes.isNotEmpty()) {
                                val sorted = emotes.sortedByDescending { it.setId }
                                addEmotes(
                                    sorted.map {
                                        Emote(
                                            name = it.name,
                                            url1x = it.url1x,
                                            url2x = it.url2x,
                                            url3x = it.url3x,
                                            url4x = it.url4x,
                                            format = it.format
                                        )
                                    }
                                )
                                _userEmotes.value = sorted.sortedByDescending { it.ownerId == channelId }.map {
                                    Emote(
                                        name = it.name,
                                        url1x = it.url1x,
                                        url2x = it.url2x,
                                        url3x = it.url3x,
                                        url4x = it.url4x,
                                        format = it.format
                                    )
                                }
                                // EmoteCache update could happen here if we want to save them
                                emoteCache.setUserEmotes(sorted)
                                loadedUserEmotes = true 
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load user emotes", e)
                        if (e.message == "failed integrity check") {
                            onIntegrityRequest?.invoke()
                        }
                    }
                }
            }
        }
    }

    fun loadEmoteSets(scope: CoroutineScope, channelId: String?) {
        if (loadedUserEmotes) return
        val helixHeaders = TwitchApiHelper.getHelixHeaders(context)
        if (!emoteCache.emoteSets.value.isNullOrEmpty() && !helixHeaders[C.HEADER_CLIENT_ID].isNullOrBlank() && !helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            scope.launch {
                try {
                    val networkLibrary = context.prefs().getString(C.NETWORK_LIBRARY, "OkHttp")
                    val animateGifs =  context.prefs().getBoolean(C.ANIMATED_EMOTES, true)
                    val emotes = mutableListOf<TwitchEmote>()
                    emoteCache.emoteSets.value?.chunked(25)?.forEach { list ->
                        playerRepository.loadEmotesFromSet(networkLibrary, helixHeaders, list, animateGifs).let { emotes.addAll(it) }
                    }
                    if (emotes.isNotEmpty()) {
                        val sorted = emotes.sortedByDescending { it.setId }
                        emoteCache.setUserEmotes(sorted)
                        addEmotes(
                            sorted.map {
                                Emote(
                                    name = it.name,
                                    url1x = it.url1x,
                                    url2x = it.url2x,
                                    url3x = it.url3x,
                                    url4x = it.url4x,
                                    format = it.format
                                )
                            }
                        )
                        _userEmotes.value = sorted.sortedByDescending { it.ownerId == channelId }.map {
                            Emote(
                                name = it.name,
                                url1x = it.url1x,
                                url2x = it.url2x,
                                url3x = it.url3x,
                                url4x = it.url4x,
                                format = it.format
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load emote sets", e)
                }
            }
        }
    }

    fun reloadEmotes(scope: CoroutineScope, channelId: String?, channelLogin: String?) {
        emoteCache.clearGlobalCache()
        loadEmotes(scope, channelId, channelLogin)
    }

    private fun requestReloadMessages() {
        onReloadMessagesRequest?.invoke()
    }
    
    fun clear() {
        _channelBadges.value = null
        _channelStvEmotes.value = null
        _channelBttvEmotes.value = null
        _channelFfzEmotes.value = null
        _cheerEmotes.value = null
        _userEmotes.value = null
        channelStvEmoteSetId = null
        loadedUserEmotes = false
        synchronized(_allEmotes) {
            _allEmotes.clear()
        }
    }

    fun updateStvEmoteSet(
        setId: String,
        added: List<Emote>,
        removed: List<Emote>,
        updated: List<Pair<Emote, Emote>>,
        stvLiveUpdates: Boolean
    ) {
        if (setId == channelStvEmoteSetId && stvLiveUpdates) {
            val removedEmotes = (removed + updated.map { it.first }).map { it.name }
            val newEmotes = added + updated.map { it.second }
            synchronized(_allEmotes) {
                _allEmotes.removeAll { it.name in removedEmotes }
                _allEmotes.addAll(newEmotes.filter { it !in _allEmotes })
            }
            val existingSet = _channelStvEmotes.value?.filter { it.name !in removedEmotes } ?: emptyList()
            _channelStvEmotes.value = existingSet + newEmotes
            requestReloadMessages()
        }
    }

    fun setReplayAssets(
        twitchEmotes: List<TwitchEmote>?, // This seems unused in ChatViewModel or handled separately?
        twitchBadges: List<TwitchBadge>?,
        cheerEmotesList: List<CheerEmote>?,
        emotes: List<Emote>?
    ) {
        _channelBadges.value = twitchBadges
        _cheerEmotes.value = cheerEmotesList
        _channelStvEmotes.value = emotes
        // Also add to allEmotes?
        if (emotes != null) {
            addEmotes(emotes)
        }
    }
}
