package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.misc.StvResponse

internal fun parseStvEmotes(
    response: List<StvResponse>,
    useWebp: Boolean,
    source: Int,
): List<Emote> = response.mapNotNull { emote ->
    emote.name?.takeIf { it.isNotBlank() }?.let { name ->
        emote.data?.let { data ->
            data.host?.let { host ->
                host.url?.takeIf { it.isNotBlank() }?.let { template ->
                    val urls = host.files?.mapNotNull { file ->
                        file.name?.takeIf {
                            it.isNotBlank() && if (useWebp) {
                                file.format == "WEBP"
                            } else {
                                file.format == "GIF" || file.format == "PNG"
                            }
                        }?.let { fileName -> "https:${template}/${fileName}" }
                    }
                    Emote(
                        name = name,
                        url1x = urls?.getOrNull(0) ?: "https:${template}/1x.webp",
                        url2x = urls?.getOrNull(1) ?: if (urls.isNullOrEmpty()) "https:${template}/2x.webp" else null,
                        url3x = urls?.getOrNull(2) ?: if (urls.isNullOrEmpty()) "https:${template}/3x.webp" else null,
                        url4x = urls?.getOrNull(3) ?: if (urls.isNullOrEmpty()) "https:${template}/4x.webp" else null,
                        format = urls?.getOrNull(0)?.substringAfterLast(".") ?: "webp",
                        isAnimated = data.animated != false,
                        isOverlayEmote = emote.flags == 1,
                        source = source,
                    )
                }
            }
        }
    }
}
