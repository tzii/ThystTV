package com.github.andreyasadchy.xtra.ui.chat

/**
 * Converts the duration argument of `/timeout` into the seconds Helix expects.
 *
 * Twitch chat accepts plain seconds (`600`) or one number with a unit (`30s`, `10m`, `1h`,
 * `2d`, `1w`). Helix `POST /moderation/bans` treats a request without `duration` as a
 * permanent ban, so an argument that can't be read must never be dropped silently. It
 * returns null instead, and the caller reports it.
 */
internal object ChatTimeoutDuration {
    const val DEFAULT_SECONDS = 600
    /** Helix accepts 1 second up to two weeks. */
    const val MAX_SECONDS = 1_209_600

    fun seconds(input: String?): Int? {
        val value = input?.trim()
        if (value.isNullOrEmpty()) return DEFAULT_SECONDS
        val unit = value.last().lowercaseChar()
        val multiplier = when (unit) {
            in '0'..'9' -> 1L
            's' -> 1L
            'm' -> 60L
            'h' -> 3_600L
            'd' -> 86_400L
            'w' -> 604_800L
            else -> return null
        }
        val digits = if (unit in '0'..'9') value else value.dropLast(1)
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        val amount = digits.toLongOrNull()?.takeIf { it <= MAX_SECONDS } ?: return null
        return (amount * multiplier).takeIf { it in 1..MAX_SECONDS }?.toInt()
    }
}
