package com.github.andreyasadchy.xtra.util.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ChatUtilsUserNoticeTest(private val noticeType: String) {

    @Test
    fun `no-message USERNOTICE preserves msg-id`() {
        val rawMessage = "@badge-info=;badges=;color=#9147FF;display-name=Viewer;id=notice-1;login=viewer;msg-id=$noticeType;system-msg=Viewer\\ssent\\sa\\snotice;tmi-sent-ts=1786147200000;user-id=42 :tmi.twitch.tv USERNOTICE #channel"

        val parsed = ChatUtils.parseChatMessage(rawMessage, userNotice = true)

        assertEquals(noticeType, parsed.msgId)
        assertNull(parsed.message)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "msg-id={0}")
        fun noticeTypes(): List<Array<String>> = listOf(
            arrayOf("sub"),
            arrayOf("resub"),
            arrayOf("subgift"),
            arrayOf("raid")
        )
    }
}
