package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import org.json.JSONObject

/** Observe Twitch's own notices, including when displaying USERNOTICE messages is disabled. */
object TwitchStreakParser {
    fun irc(message: String, account: String, channel: String): TwitchStreak? {
        if (!message.startsWith('@') || !message.contains(" USERNOTICE #")) return null
        val tags = message.substringBefore(' ').drop(1).split(';').associate {
            it.substringBefore('=') to it.substringAfter('=', "")
        }
        if (account.isBlank() || channel.isBlank() || tags["user-id"] != account || tags["room-id"] != channel ||
            tags["msg-id"] != "viewermilestone" || tags["msg-param-category"] != "watch-streak" ||
            tags["source-room-id"]?.let { it != channel } == true) return null
        val count = tags["msg-param-value"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val time = tags["tmi-sent-ts"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val id = tags["msg-param-id"]?.takeIf { it.isNotBlank() } ?: return null
        return TwitchStreak(channel, message.substringAfter(" USERNOTICE #").substringBefore(' '), count, time, id)
    }

    fun eventSub(event: JSONObject, timestamp: String?, account: String, channel: String): TwitchStreak? {
        if (account.isBlank() || channel.isBlank() || event.optString("chatter_user_id") != account ||
            event.optString("broadcaster_user_id") != channel || event.optString("notice_type") != "watch_streak" ||
            event.optString("source_broadcaster_user_id").let { it.isNotBlank() && it != "null" && it != channel }) return null
        val count = event.optJSONObject("watch_streak")?.optInt("streak_count")?.takeIf { it > 0 } ?: return null
        val time = timestamp?.let(TwitchApiHelper::parseIso8601DateUTC) ?: return null
        val id = event.optString("message_id").takeIf { it.isNotBlank() } ?: return null
        return TwitchStreak(channel, event.optString("broadcaster_user_login"), count, time, id)
    }
}
