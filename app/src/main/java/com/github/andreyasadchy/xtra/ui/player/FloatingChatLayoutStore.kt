package com.github.andreyasadchy.xtra.ui.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max

/**
 * Per-channel floating chat positions in one JSON preference, most recent last,
 * capped at [MAX_CHANNELS]. A channel without its own entry uses the last
 * position the viewer chose anywhere.
 */
internal object FloatingChatLayoutStore {
    const val PREF_KEY = "floating_chat_layouts"
    const val MAX_CHANNELS = 50

    @Serializable
    private data class Stored(
        val side: String,
        val anchor: String,
        val y: Float,
        val w: Float,
        val h: Float,
    )

    @Serializable
    private data class Entry(val id: String, val layout: Stored)

    @Serializable
    private data class Document(
        val last: Stored? = null,
        val channels: List<Entry> = emptyList(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The channel's layout, else the last used one, else null. */
    fun read(document: String?, channelId: String?): FloatingChatLayout? {
        val parsed = parse(document) ?: return null
        val own = channelId?.let { id -> parsed.channels.lastOrNull { it.id == id } }?.layout
        return (own ?: parsed.last)?.toLayout()
    }

    /** True when [channelId] has its own entry (not just the last-used fallback). */
    fun hasChannel(document: String?, channelId: String?): Boolean {
        if (channelId == null) return false
        return parse(document)?.channels?.any { it.id == channelId } == true
    }

    fun write(document: String?, channelId: String?, layout: FloatingChatLayout): String {
        val parsed = parse(document) ?: Document()
        val stored = layout.toStored()
        val channels = if (channelId.isNullOrBlank()) {
            parsed.channels
        } else {
            (parsed.channels.filter { it.id != channelId } + Entry(channelId, stored)).takeLast(MAX_CHANNELS)
        }
        return json.encodeToString(Document.serializer(), Document(last = stored, channels = channels))
    }

    /**
     * Converts 1.7.0's per-channel pixel position into an anchored layout.
     * Returns null when the old values are missing.
     */
    fun fromLegacy(
        x: Float,
        y: Float,
        widthPx: Int,
        heightPx: Int,
        parentWidth: Int,
        parentHeight: Int,
        density: Float,
        fallback: FloatingChatLayout,
    ): FloatingChatLayout? {
        if (x < 0f || y < 0f || parentWidth <= 0 || parentHeight <= 0 || density <= 0f) return null
        val width = if (widthPx > 0) widthPx else (fallback.widthDp * density).toInt()
        val height = if (heightPx > 0) heightPx else (fallback.heightDp * density).toInt()
        val side = if (x + width / 2f < parentWidth / 2f) FloatingChatSide.LEFT else FloatingChatSide.RIGHT
        val bounds = FloatingChatPlacement.bounds(parentWidth, parentHeight, density)
        val travel = max(0f, bounds.height - height)
        val magnet = FloatingChatPlacement.MAGNET_DP * density
        val top = y.coerceIn(bounds.top, bounds.top + travel)
        val anchor = when {
            top - bounds.top <= magnet -> FloatingChatAnchor.TOP
            bounds.top + travel - top <= magnet -> FloatingChatAnchor.BOTTOM
            else -> FloatingChatAnchor.FREE
        }
        return FloatingChatLayout(
            side = side,
            anchor = anchor,
            yFraction = if (travel > 0f) ((top - bounds.top) / travel).coerceIn(0f, 1f) else 0f,
            widthDp = width / density,
            heightDp = height / density,
        )
    }

    private fun parse(document: String?): Document? {
        if (document.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(Document.serializer(), document) }.getOrNull()
    }

    private fun FloatingChatLayout.toStored() = Stored(side.name, anchor.name, yFraction, widthDp, heightDp)

    private fun Stored.toLayout(): FloatingChatLayout? {
        val side = FloatingChatSide.entries.firstOrNull { it.name == side } ?: return null
        val anchor = FloatingChatAnchor.entries.firstOrNull { it.name == anchor } ?: return null
        if (!w.isFinite() || !h.isFinite() || w <= 0f || h <= 0f) return null
        return FloatingChatLayout(side, anchor, if (y.isFinite()) y.coerceIn(0f, 1f) else 0f, w, h)
    }
}
