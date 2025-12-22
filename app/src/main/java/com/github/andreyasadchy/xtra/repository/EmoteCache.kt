 package com.github.andreyasadchy.xtra.repository
 
 import com.github.andreyasadchy.xtra.model.chat.Emote
 import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
 import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
 import kotlinx.coroutines.flow.MutableStateFlow
 import kotlinx.coroutines.flow.StateFlow
 import kotlinx.coroutines.flow.asStateFlow
 import javax.inject.Inject
 import javax.inject.Singleton
 
 /**
  * Singleton cache for global emotes and badges.
  * This replaces the static companion object variables in ChatViewModel
  * and provides proper lifecycle management and thread safety.
  */
 @Singleton
 class EmoteCache @Inject constructor() {
     
     // Global Badges
     private val _globalBadges = MutableStateFlow<List<TwitchBadge>?>(null)
     val globalBadges: StateFlow<List<TwitchBadge>?> = _globalBadges.asStateFlow()
     
     // 7TV Global Emotes
     private val _globalStvEmotes = MutableStateFlow<List<Emote>?>(null)
     val globalStvEmotes: StateFlow<List<Emote>?> = _globalStvEmotes.asStateFlow()
     
     // BTTV Global Emotes
     private val _globalBttvEmotes = MutableStateFlow<List<Emote>?>(null)
     val globalBttvEmotes: StateFlow<List<Emote>?> = _globalBttvEmotes.asStateFlow()
     
     // FFZ Global Emotes
     private val _globalFfzEmotes = MutableStateFlow<List<Emote>?>(null)
     val globalFfzEmotes: StateFlow<List<Emote>?> = _globalFfzEmotes.asStateFlow()
     
     // User Emotes (logged in user's emotes)
     private val _userEmotes = MutableStateFlow<List<TwitchEmote>?>(null)
     val userEmotes: StateFlow<List<TwitchEmote>?> = _userEmotes.asStateFlow()
     
     // Emote Sets (for user emotes)
     private val _emoteSets = MutableStateFlow<List<String>?>(null)
     val emoteSets: StateFlow<List<String>?> = _emoteSets.asStateFlow()
     
     // Cache timestamps for TTL management
     private var globalBadgesTimestamp: Long = 0
     private var globalStvEmotesTimestamp: Long = 0
     private var globalBttvEmotesTimestamp: Long = 0
     private var globalFfzEmotesTimestamp: Long = 0
     private var userEmotesTimestamp: Long = 0
     
     companion object {
         // Cache TTL: 30 minutes for global resources
         private const val CACHE_TTL_MS = 30 * 60 * 1000L
         // Cache TTL: 5 minutes for user-specific resources
         private const val USER_CACHE_TTL_MS = 5 * 60 * 1000L
     }
     
     // Setters with timestamp tracking
     
     fun setGlobalBadges(badges: List<TwitchBadge>) {
         _globalBadges.value = badges
         globalBadgesTimestamp = System.currentTimeMillis()
     }
     
     fun setGlobalStvEmotes(emotes: List<Emote>) {
         _globalStvEmotes.value = emotes
         globalStvEmotesTimestamp = System.currentTimeMillis()
     }
     
     fun setGlobalBttvEmotes(emotes: List<Emote>) {
         _globalBttvEmotes.value = emotes
         globalBttvEmotesTimestamp = System.currentTimeMillis()
     }
     
     fun setGlobalFfzEmotes(emotes: List<Emote>) {
         _globalFfzEmotes.value = emotes
         globalFfzEmotesTimestamp = System.currentTimeMillis()
     }
     
     fun setUserEmotes(emotes: List<TwitchEmote>) {
         _userEmotes.value = emotes
         userEmotesTimestamp = System.currentTimeMillis()
     }
     
     fun setEmoteSets(sets: List<String>) {
         _emoteSets.value = sets
     }
     
     // Cache validity checks
     
     fun isGlobalBadgesCacheValid(): Boolean {
         return _globalBadges.value != null && 
                System.currentTimeMillis() - globalBadgesTimestamp < CACHE_TTL_MS
     }
     
     fun isGlobalStvEmotesCacheValid(): Boolean {
         return _globalStvEmotes.value != null && 
                System.currentTimeMillis() - globalStvEmotesTimestamp < CACHE_TTL_MS
     }
     
     fun isGlobalBttvEmotesCacheValid(): Boolean {
         return _globalBttvEmotes.value != null && 
                System.currentTimeMillis() - globalBttvEmotesTimestamp < CACHE_TTL_MS
     }
     
     fun isGlobalFfzEmotesCacheValid(): Boolean {
         return _globalFfzEmotes.value != null && 
                System.currentTimeMillis() - globalFfzEmotesTimestamp < CACHE_TTL_MS
     }
     
     fun isUserEmotesCacheValid(): Boolean {
         return _userEmotes.value != null && 
                System.currentTimeMillis() - userEmotesTimestamp < USER_CACHE_TTL_MS
     }
     
     /**
      * Clears all cached global resources.
      * Call this when you need to force a refresh.
      */
     fun clearGlobalCache() {
         _globalBadges.value = null
         _globalStvEmotes.value = null
         _globalBttvEmotes.value = null
         _globalFfzEmotes.value = null
         globalBadgesTimestamp = 0
         globalStvEmotesTimestamp = 0
         globalBttvEmotesTimestamp = 0
         globalFfzEmotesTimestamp = 0
     }
     
     /**
      * Clears user-specific cached data.
      * Call this on logout or user switch.
      */
     fun clearUserCache() {
         _userEmotes.value = null
         _emoteSets.value = null
         userEmotesTimestamp = 0
     }
     
     /**
      * Clears all cached data.
      */
     fun clearAll() {
         clearGlobalCache()
         clearUserCache()
     }
 }
