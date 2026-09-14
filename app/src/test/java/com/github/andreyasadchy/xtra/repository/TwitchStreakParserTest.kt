package com.github.andreyasadchy.xtra.repository

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class TwitchStreakParserTest {
    private val irc = "@msg-id=viewermilestone;msg-param-category=watch-streak;msg-param-id=event;msg-param-value=3;room-id=2;tmi-sent-ts=1681057151588;user-id=1 :tmi.twitch.tv USERNOTICE #channel"
    private val event get() = JSONObject("""{"notice_type":"watch_streak","chatter_user_id":"1","broadcaster_user_id":"2","broadcaster_user_login":"channel","message_id":"event","watch_streak":{"streak_count":3}}""")
    private val timestamp = "2026-09-14T10:00:00Z"

    @Test fun `IRC parses only own channel milestone`() {
        assertEquals(3, TwitchStreakParser.irc(irc, "1", "2")?.count)
        assertNull(TwitchStreakParser.irc(irc, "other", "2"))
        assertNull(TwitchStreakParser.irc(irc, "1", "other"))
        assertNull(TwitchStreakParser.irc(irc.replace(" USERNOTICE ", " PRIVMSG "), "1", "2"))
    }

    @Test fun `IRC ignores shared other channel invalid counts and missing identity`() {
        assertNull(TwitchStreakParser.irc(irc.replace("@", "@source-room-id=3;"), "1", "2"))
        assertNull(TwitchStreakParser.irc(irc.replace("value=3", "value=-1"), "1", "2"))
        assertNull(TwitchStreakParser.irc(irc.replace("msg-param-id=event", "msg-param-id="), "1", "2"))
        assertNull(TwitchStreakParser.irc(irc.replace("watch-streak", "other"), "1", "2"))
    }

    @Test fun `EventSub uses server identity count and timestamp`() {
        val streak = TwitchStreakParser.eventSub(event, timestamp, "1", "2")!!
        assertEquals(3, streak.count)
        assertEquals("event", streak.eventId)
        assertEquals("channel", streak.channel)
        assertTrue(streak.timestamp > 0)
    }

    @Test fun `EventSub rejects other accounts shared channels and absent timestamps`() {
        assertNull(TwitchStreakParser.eventSub(event, timestamp, "other", "2"))
        assertNull(TwitchStreakParser.eventSub(event, timestamp, "1", "other"))
        assertNull(TwitchStreakParser.eventSub(event.put("source_broadcaster_user_id", "3"), timestamp, "1", "2"))
        assertNull(TwitchStreakParser.eventSub(event, null, "1", "2"))
        assertNull(TwitchStreakParser.eventSub(event, "bad", "1", "2"))
    }
}
