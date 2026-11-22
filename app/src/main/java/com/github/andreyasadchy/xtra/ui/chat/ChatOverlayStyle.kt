package com.github.andreyasadchy.xtra.ui.chat

/**
 * How chat rows draw when the list floats over video. Null on a chat that
 * sits on an app surface (sidebar, portrait, channel page).
 */
data class ChatOverlayStyle(
    val treatment: Treatment,
    val boldText: Boolean,
    val textScale: Float,
    val fadeOldMessages: Boolean,
) {
    enum class Treatment {
        /** No surface behind the text: a tight dark halo plus a soft shadow. */
        HALO,
        /** Translucent surface: a light 1 dp shadow. */
        SOFT_SHADOW,
        /** Nearly opaque surface: no shadow. */
        NONE,
    }
}
