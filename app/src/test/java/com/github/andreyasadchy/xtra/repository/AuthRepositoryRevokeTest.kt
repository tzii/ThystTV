package com.github.andreyasadchy.xtra.repository

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors

class AuthRepositoryRevokeTest {
    private val executor = Executors.newSingleThreadExecutor()

    @After fun tearDown() {
        executor.shutdownNow()
    }

    private fun repository(respond: (Interceptor.Chain) -> Response) = AuthRepository(
        null,
        null,
        executor,
        OkHttpClient.Builder().addInterceptor { respond(it) }.build(),
        Json,
    )

    private fun answering(status: Int) = repository { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message("")
            .body("".toResponseBody())
            .build()
    }

    @Test fun `only a final answer from Twitch ends a revocation`() = runBlocking {
        assertTrue("revoked", answering(200).revoke("OkHttp", "token=a"))
        assertTrue("already invalid", answering(400).revoke("OkHttp", "token=a"))
        assertFalse("rate limited", answering(429).revoke("OkHttp", "token=a"))
        assertFalse("unavailable", answering(503).revoke("OkHttp", "token=a"))
        assertFalse("no connection", repository { throw IOException("offline") }.revoke("OkHttp", "token=a"))
    }
}
