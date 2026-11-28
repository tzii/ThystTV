package com.github.andreyasadchy.xtra.ui.view

/**
 * Which pass of the Clear outline a [ChatMessageTextView] is drawing. Spans
 * that draw their own content read it: emotes and badges skip the soft pass so
 * they draw once, and painted names draw the outline under their paint in both
 * passes. Rows draw on the main thread, one at a time.
 */
internal enum class ChatHaloPass {
    /** Not inside a Clear row's draw. */
    NONE,
    /** The wide, soft shadow under the text. */
    SOFT,
    /** The tight dark outline, drawn with the text itself. */
    TIGHT;

    companion object {
        var current = NONE
    }
}
