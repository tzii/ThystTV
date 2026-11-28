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

    /**
     * [x] is new in 1.7.1's free placement. Entries written before it have
     * only a [side] and an [anchor] (TOP, BOTTOM or FREE with [y]); both are
     * still written, so a build that predates [x] reads a close position.
     */
    @Serializable
    private data class Stored(
        val side: String,
        val anchor: String,
        val y: Float,
        val w: Float,
        val h: Float,
        val x: Float? = null,
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
     * Converts 1.7.0's per-channel pixel position into a layout.
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
        val bounds = FloatingChatPlacement.bounds(parentWidth, parentHeight)
        val travelX = max(0f, bounds.width - width)
        val travelY = max(0f, bounds.height - height)
        return FloatingChatLayout(
            xFraction = share(x - bounds.left, travelX),
            yFraction = share(y - bounds.top, travelY),
            widthDp = width / density,
            heightDp = height / density,
        )
    }

    private fun share(offset: Float, travel: Float): Float =
        if (travel <= 0f) 0f else (offset / travel).coerceIn(0f, 1f)

    private fun parse(document: String?): Document? {
        if (document.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(Document.serializer(), document) }.getOrNull()
    }

    private fun FloatingChatLayout.toStored() = Stored(
        side = side.name,
        anchor = when {
            yFraction <= 0f -> "TOP"
            yFraction >= 1f -> "BOTTOM"
            else -> "FREE"
        },
        y = yFraction,
        w = widthDp,
        h = heightDp,
        x = xFraction,
    )

    private fun Stored.toLayout(): FloatingChatLayout? {
        if (!w.isFinite() || !h.isFinite() || w <= 0f || h <= 0f) return null
        val storedY = if (y.isFinite()) y.coerceIn(0f, 1f) else 0f
        if (x != null) {
            return FloatingChatLayout(if (x.isFinite()) x.coerceIn(0f, 1f) else 1f, storedY, w, h)
        }
        // Written before free placement: flush with its side, TOP and BOTTOM flush with that edge.
        val xFraction = when (side) {
            "LEFT" -> 0f
            "RIGHT" -> 1f
            else -> return null
        }
        val yFraction = when (anchor) {
            "TOP" -> 0f
            "BOTTOM" -> 1f
            "FREE" -> storedY
            else -> return null
        }
        return FloatingChatLayout(xFraction, yFraction, w, h)
    }
}
