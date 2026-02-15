package com.github.andreyasadchy.xtra.model.id

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TokenResponse(
    @SerialName("access_token")
    val token: String? = null,
    val message: String? = null,
)