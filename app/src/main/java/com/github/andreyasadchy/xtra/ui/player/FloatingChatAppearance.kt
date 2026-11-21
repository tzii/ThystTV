package com.github.andreyasadchy.xtra.ui.player

import android.content.SharedPreferences
import com.github.andreyasadchy.xtra.ui.chat.ChatOverlayStyle
import com.github.andreyasadchy.xtra.util.C

internal enum class FloatingChatBackground(val opacity: Int) {
    CLEAR(0),
    SHADE(55),
    SOLID(92);

    companion object {
        /** Anyone who never moved the opacity slider gets Shade. */
        const val DEFAULT_OPACITY = 55

        fun fromOpacity(opacity: Int): FloatingChatBackground = when {
            opacity < 20 -> CLEAR
            opacity < 80 -> SHADE
            else -> SOLID
        }
    }
}

internal enum class FloatingChatTextSize(val value: String, val scale: Float) {
    SMALL("s", 0.88f),
    MEDIUM("m", 1f),
    LARGE("l", 1.15f);

    companion object {
        fun fromValue(value: String?) = entries.firstOrNull { it.value == value } ?: MEDIUM
    }
}

/** Everything the viewer can change from Aa or Settings, read in one place. */
internal data class FloatingChatAppearance(
    val opacity: Int,
    val boldText: Boolean,
    val textSize: FloatingChatTextSize,
    val fadeOldMessages: Boolean,
    val passThrough: Boolean,
    val replyBar: Boolean,
) {
    val background get() = FloatingChatBackground.fromOpacity(opacity)

    /** Surface alpha 0-255 for the card. */
    val surfaceAlpha get() = (opacity.coerceIn(0, 100) * 255 + 50) / 100

    /** Outline and shadow only make sense once there is a visible surface. */
    val hasSurface get() = background != FloatingChatBackground.CLEAR

    fun overlayStyle() = ChatOverlayStyle(
        treatment = when (background) {
            FloatingChatBackground.CLEAR -> ChatOverlayStyle.Treatment.HALO
            FloatingChatBackground.SHADE -> ChatOverlayStyle.Treatment.SOFT_SHADOW
            FloatingChatBackground.SOLID -> ChatOverlayStyle.Treatment.NONE
        },
        boldText = boldText,
        textScale = textSize.scale,
        fadeOldMessages = fadeOldMessages,
    )

    companion object {
        fun read(prefs: SharedPreferences) = FloatingChatAppearance(
            opacity = prefs.getInt(C.FLOATING_CHAT_TRANSPARENCY, FloatingChatBackground.DEFAULT_OPACITY).coerceIn(0, 100),
            boldText = prefs.getBoolean(C.FLOATING_CHAT_BOLD_TEXT, false),
            textSize = FloatingChatTextSize.fromValue(prefs.getString(C.FLOATING_CHAT_TEXT_SIZE, null)),
            fadeOldMessages = prefs.getBoolean(C.FLOATING_CHAT_FADE_OLD, false),
            passThrough = prefs.getBoolean(C.FLOATING_CHAT_PASS_THROUGH, false),
            replyBar = prefs.getBoolean(C.FLOATING_CHAT_REPLY_BAR, false),
        )
    }
}
