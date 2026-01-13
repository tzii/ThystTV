package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for StreamPickerDialog.
 * Loads followed live streams and provides search/filter functionality.
 */
@HiltViewModel
class StreamPickerViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository
) : ViewModel() {

    private val _streams = MutableStateFlow<List<Stream>>(emptyList())
    val streams: StateFlow<List<Stream>> = _streams.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var allStreams: List<Stream> = emptyList()
    private var excludeChannelId: String? = null
    private var searchQuery: String = ""

    /**
     * Load followed streams, excluding the specified channel (current primary stream)
     */
    fun loadFollowedStreams(excludeChannelId: String? = null) {
        this.excludeChannelId = excludeChannelId
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val streams = loadStreamsFromApi()
                allStreams = streams.filter { it.channelId != excludeChannelId }
                    .sortedByDescending { it.viewerCount }
                applySearchFilter()
            } catch (e: Exception) {
                allStreams = emptyList()
                _streams.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Set search query to filter streams
     */
    fun setSearchQuery(query: String) {
        searchQuery = query.trim().lowercase()
        applySearchFilter()
    }

    private fun applySearchFilter() {
        _streams.value = if (searchQuery.isEmpty()) {
            allStreams
        } else {
            allStreams.filter { stream ->
                stream.channelName?.lowercase()?.contains(searchQuery) == true ||
                stream.channelLogin?.lowercase()?.contains(searchQuery) == true ||
                stream.title?.lowercase()?.contains(searchQuery) == true ||
                stream.gameName?.lowercase()?.contains(searchQuery) == true
            }
        }
    }

    private suspend fun loadStreamsFromApi(): List<Stream> {
        val result = mutableListOf<Stream>()
        
        // First, try to load local follows
        val localFollowIds = localFollowsChannel.loadFollows()
            .mapNotNull { it.userId }
            .takeIf { it.isNotEmpty() }

        // Load streams for local follows
        localFollowIds?.let { ids ->
            try {
                val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
                val response = graphQLRepository.loadQueryUsersStream(
                    applicationContext.prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                    gqlHeaders,
                    ids
                )
                response.data?.users?.forEach { user ->
                    user?.stream?.let { stream ->
                        result.add(Stream(
                            id = stream.id,
                            channelId = user.id,
                            channelLogin = user.login,
                            channelName = user.displayName,
                            gameId = stream.game?.id,
                            gameName = stream.game?.displayName,
                            title = stream.broadcaster?.broadcastSettings?.title,
                            viewerCount = stream.viewersCount,
                            thumbnailUrl = stream.previewImageURL,
                            profileImageUrl = user.profileImageURL
                        ))
                    }
                }
            } catch (e: Exception) {
                // Continue with logged-in user's followed streams
            }
        }

        // Load followed streams from logged-in user
        val gqlToken = applicationContext.tokenPrefs().getString(C.TOKEN, null)
        if (!gqlToken.isNullOrBlank()) {
            try {
                val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
                val response = graphQLRepository.loadQueryUserFollowedStreams(
                    applicationContext.prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                    gqlHeaders,
                    100,
                    null
                )
                response.data?.user?.followedLiveUsers?.edges?.forEach { edge ->
                    edge?.node?.let { user ->
                        user.stream?.let { stream ->
                            // Avoid duplicates
                            if (result.none { it.channelId == user.id }) {
                                result.add(Stream(
                                    id = stream.id,
                                    channelId = user.id,
                                    channelLogin = user.login,
                                    channelName = user.displayName,
                            gameId = stream.game?.id,
                            gameName = stream.game?.displayName,
                            title = stream.broadcaster?.broadcastSettings?.title,
                            viewerCount = stream.viewersCount,
                            thumbnailUrl = stream.previewImageURL,
                                    profileImageUrl = user.profileImageURL
                                ))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // If GQL fails, try Helix
                try {
                    val userId = applicationContext.tokenPrefs().getString(C.USER_ID, null)
                    if (!userId.isNullOrBlank()) {
                        val helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext)
                        val response = helixRepository.getFollowedStreams(
                            "OkHttp", // Assuming network library is OkHttp or passed appropriately
                            helixHeaders,
                            userId,
                            100,
                            null
                        )
                        response.data.forEach { stream ->
                            if (result.none { it.channelId == stream.channelId }) {
                                result.add(Stream(
                                    id = stream.id,
                                    channelId = stream.channelId,
                                    channelLogin = stream.channelLogin,
                                    channelName = stream.channelName,
                                    gameId = stream.gameId,
                                    gameName = stream.gameName,
                                    title = stream.title,
                                    viewerCount = stream.viewerCount,
                                    thumbnailUrl = stream.thumbnailUrl
                                ))
                            }
                        }
                    }
                } catch (e2: Exception) {
                    // Both failed
                }
            }
        }

        return result
    }
}
