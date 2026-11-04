package com.github.andreyasadchy.xtra.model.helix.clip

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CreateClipResponse(
    val data: List<CreatedClip> = emptyList(),
)

@Serializable
class CreatedClip(
    val id: String? = null,
    @SerialName("edit_url")
    val editUrl: String? = null,
)
