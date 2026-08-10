package com.github.andreyasadchy.xtra.model.misc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class StvChannelResponse(
    @SerialName("emote_set")
    val emoteSet: StvGlobalResponse? = null,
    @SerialName("emote_set_id")
    val emoteSetId: String? = null,
)