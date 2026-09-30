package com.github.andreyasadchy.xtra.repository

import android.app.Application
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class HelixChatMetadataTest {
    private var request: Request? = null

    private fun repository(body: String) = HelixRepository(
        httpEngine = null,
        cronetEngine = null,
        cronetExecutor = mock(),
        okHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            request = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
        json = Json { ignoreUnknownKeys = true },
    )

    @Test fun `follower endpoint maps user identity and follow date`() = runTest {
        val repository = repository("""{"total":1,"data":[{"user_id":"viewer","user_login":"viewer_login","user_name":"Viewer","followed_at":"2026-09-01T12:00:00Z"}],"pagination":{}}""")
        val follower = repository.getUserFollowers("OkHttp", emptyMap(), "channel", "viewer").data.single()
        assertEquals("/helix/channels/followers", request!!.url.encodedPath)
        assertEquals("channel", request!!.url.queryParameter("broadcaster_id"))
        assertEquals("viewer", request!!.url.queryParameter("user_id"))
        assertEquals("viewer", follower.id)
        assertEquals("viewer_login", follower.login)
        assertEquals("Viewer", follower.displayName)
        assertEquals("2026-09-01T12:00:00Z", follower.followedAt)
    }

    @Test fun `followed channels endpoint keeps broadcaster identity mapping`() = runTest {
        val repository = repository("""{"data":[{"broadcaster_id":"channel","broadcaster_login":"channel_login","broadcaster_name":"Channel","followed_at":"2026-09-01T12:00:00Z"}]}""")
        val follow = repository.getUserFollows("OkHttp", emptyMap(), "viewer", "channel").data.single()
        assertEquals("channel", follow.id)
        assertEquals("channel_login", follow.login)
        assertEquals("Channel", follow.displayName)
    }

    @Test fun `shared success parser reads the returned chat color`() {
        val repository = repository("")
        assertEquals("#9146FF", repository.parseChatColorResponse("""{"data":[{"user_id":"viewer","color":"#9146FF"}]}"""))
    }

    @Test fun `missing chat color remains absent`() {
        val repository = repository("")
        for (body in listOf("""{"data":[]}""", """{"data":[{"color":null}]}""", """{"data":[{}]}""")) {
            assertNull(repository.parseChatColorResponse(body))
        }
    }
}
