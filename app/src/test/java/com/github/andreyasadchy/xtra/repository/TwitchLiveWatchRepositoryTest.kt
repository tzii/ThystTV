package com.github.andreyasadchy.xtra.repository

import android.app.Application
import android.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.IOException
import java.net.URLDecoder
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class TwitchLiveWatchRepositoryTest {
    private val requests = Collections.synchronizedList(mutableListOf<Request>())
    private val page = "<script src=\"https://static.twitchcdn.net/config/settings.test.js\"></script>"
    private val config = """{"spade_url":"https://spade.twitch.tv/track"}"""
    private fun repository(vararg bodies: String, code: Int = 200): TwitchLiveWatchRepository {
        return TwitchLiveWatchRepository(OkHttpClient.Builder().addInterceptor { chain ->
            val index = requests.size
            requests.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("fixture")
                .code(code).body(bodies.getOrElse(index) { "" }.toResponseBody()).build()
        }.build())
    }

    @Test fun `only trusted https destinations without credentials are accepted`() {
        for (url in listOf("https://spade.twitch.tv/track", "https://static.twitchcdn.net/config/settings.x.js")) assertTrue(TwitchLiveWatchRepository.trustedUrl(url))
        for (url in listOf("http://spade.twitch.tv/track", "https://twitch.tv.attacker.test/", "https://eviltwitch.tv/", "https://user:pass@spade.twitch.tv/", "https://spade.twitch.tv:444/", "https://127.0.0.1/")) assertFalse(TwitchLiveWatchRepository.trustedUrl(url))
    }

    @Test fun `form encoding round trips a genuine minute with numeric user identity`() {
        val buffer = Buffer()
        TwitchLiveWatchRepository.form("1", "3", "2").writeTo(buffer)
        val value = URLDecoder.decode(buffer.readUtf8().substringAfter("data="), "UTF-8")
        val event = JSONObject(String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8))
        assertEquals("minute-watched", event.getString("event"))
        val properties = event.getJSONObject("properties")
        assertEquals("3", properties.getString("broadcast_id"))
        assertEquals("2", properties.getString("channel_id"))
        assertEquals(1L, properties.getLong("user_id"))
    }

    @Test fun `endpoint discovery is cached and reports never include authorization`() = runBlocking {
        val repo = repository(page, config, "", "")
        repo.send("1", "3", "2", "channel") { true }
        repo.send("1", "3", "2", "channel") { true }
        assertEquals(listOf("GET", "GET", "POST", "POST"), requests.map { it.method })
        assertTrue(requests.all { it.header("Authorization") == null && it.header("Cookie") == null })
        assertEquals("spade.twitch.tv", requests.last().url.host)
    }

    @Test fun `untrusted discovery fails without posting`() = runBlocking {
        val repo = repository(page, """{"spade_url":"https://attacker.test/track"}""")
        try { repo.send("1", "3", "2", "channel") { true }; fail() } catch (_: IOException) { }
        assertEquals(2, requests.size)
    }

    @Test fun `unsuccessful HTTP response is not counted as acceptance`() = runBlocking {
        try { repository(page, code = 500).send("1", "3", "2", "channel") { true }; fail() } catch (_: IOException) { }
        assertEquals(1, requests.size)
    }

    @Test fun `oversized discovery response fails closed`() = runBlocking {
        try { repository("x".repeat(2_097_153)).send("1", "3", "2", "channel") { true }; fail() } catch (_: IOException) { }
        assertEquals(1, requests.size)
    }
}
