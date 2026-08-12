package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.misc.StvChannelResponse
import com.github.andreyasadchy.xtra.model.misc.StvGlobalResponse

internal fun stvEmoteSetUrl(setId: String): String =
    "https://7tv.io/v3/emote-sets/$setId"

internal suspend fun resolveStvChannelEmotes(
    response: StvChannelResponse,
    useWebp: Boolean,
    loadSet: suspend (String) -> StvGlobalResponse,
): Pair<String?, List<Emote>> {
    val topLevelId = response.emoteSetId?.takeIf { it.isNotBlank() }
    val embeddedId = response.emoteSet?.id?.takeIf { it.isNotBlank() } ?: topLevelId
    val embeddedEmotes = parseStvEmotes(
        response = response.emoteSet?.emotes.orEmpty(),
        useWebp = useWebp,
        source = Emote.CHANNEL_STV,
    )
    if (embeddedEmotes.isNotEmpty()) return embeddedId to embeddedEmotes
    if (topLevelId == null) return embeddedId to emptyList()

    val fetched = loadSet(topLevelId)
    val fetchedId = fetched.id?.takeIf { it.isNotBlank() } ?: topLevelId
    return fetchedId to parseStvEmotes(fetched.emotes, useWebp, Emote.CHANNEL_STV)
}
