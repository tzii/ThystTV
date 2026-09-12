package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.Account
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.Failure
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.ProbeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TwitchResumeProbeRepositoryTest {
    private val account = Account("123", mapOf("Authorization" to "OAuth test-token", "Client-ID" to "test-client", "Client-Integrity" to "test-integrity"))
    private val validation = """{"user_id":"123","client_id":"test-client","expires_in":3600}"""
    private val writeAck = """[{"data":{"updateUserViewedVideo":{"video":{"id":"456"}}}}]"""
    private fun readResponse(seconds: Long) = """[{"data":{"video":{"id":"456","self":{"viewingHistory":{"position":$seconds}}}}}]"""
    private val requests = Collections.synchronizedList(mutableListOf<Request>())

    private fun response(request: Request, body: String, code: Int = 200) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
        .body(body.toResponseBody("application/json".toMediaType())).build()

    private fun repository(vararg responses: String, codeAt: Pair<Int, Int>? = null): TwitchResumeProbeRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val index = requests.size
            requests.add(chain.request())
            response(chain.request(), responses.getOrElse(index) { "unexpected request" },
                if (codeAt?.first == index) codeAt.second else 200)
        }.build()
        return TwitchResumeProbeRepository(client, Json)
    }

    private fun assertFailure(expected: Failure, block: suspend () -> Unit) = runBlocking {
        try { block(); fail("Expected $expected") } catch (error: ProbeException) { assertEquals(expected, error.failure) }
    }

    @Test fun `reads zero as a valid position and separates validation headers`() = runBlocking {
        val result = repository(validation, readResponse(0)).read(account, "456") { true }
        assertEquals(0L, result.seconds)
        assertEquals("id.twitch.tv", requests[0].url.host)
        assertEquals("OAuth test-token", requests[0].header("Authorization"))
        assertNull(requests[0].header("Client-Integrity"))
        assertNull(requests[0].header("Client-ID"))
        assertEquals("gql.twitch.tv", requests[1].url.host)
        assertEquals("test-integrity", requests[1].header("Client-Integrity"))
    }

    @Test fun `missing remote history remains distinct from zero`() = runBlocking {
        val body = """[{"data":{"video":{"id":"456","self":{"viewingHistory":null}}}}]"""
        assertNull(repository(validation, body).read(account, "456") { true }.seconds)
    }

    @Test fun `validation uses the exact last header values that graphql sends`() = runBlocking {
        val custom = Account("123", linkedMapOf(
            "Authorization" to "OAuth obsolete-token",
            "authorization" to "OAuth test-token",
            "Client-ID" to "obsolete-client",
            "client-id" to "test-client",
        ))
        repository(validation, readResponse(20)).read(custom, "456") { true }
        assertEquals("OAuth test-token", requests[0].header("Authorization"))
        assertEquals(requests[0].header("Authorization"), requests[1].header("Authorization"))
        assertEquals("test-client", requests[1].header("Client-ID"))
    }

    @Test fun `wrong token account cannot issue a graphql operation`() {
        assertFailure(Failure.ACCOUNT_MISMATCH) {
            repository(validation.replace("123", "999")).writeAndReadBack(account, "456", 5000) { true }
        }
        assertEquals(1, requests.size)
    }

    @Test fun `wrong client cannot issue a graphql operation`() {
        assertFailure(Failure.CLIENT_MISMATCH) {
            repository(validation.replace("test-client", "other-client")).read(account, "456") { true }
        }
        assertEquals(1, requests.size)
    }

    @Test fun `expired token fails closed`() {
        assertFailure(Failure.AUTHENTICATION) {
            repository(validation.replace("3600", "0")).read(account, "456") { true }
        }
        assertEquals(1, requests.size)
    }

    @Test fun `missing token performs no request`() {
        assertFailure(Failure.SIGN_IN_REQUIRED) { repository().read(Account("123", emptyMap()), "456") { true } }
        assertTrue(requests.isEmpty())
        assertFalse(account.toString().contains("test-token"))
    }

    @Test fun `account change after validation prevents the mutation`() {
        assertFailure(Failure.SESSION_CHANGED) {
            repository(validation).writeAndReadBack(account, "456", 5000) { requests.isEmpty() }
        }
        assertEquals(1, requests.size)
    }

    @Test fun `graphql errors on http 200 are failures and raw details are not exposed`() {
        assertFailure(Failure.GRAPHQL) {
            repository(validation, """[{"errors":[{"message":"secret account detail"}],"data":{}}]""")
                .read(account, "456") { true }
        }
    }

    @Test fun `obsolete persisted query is a compatibility failure`() {
        assertFailure(Failure.UNSUPPORTED_OPERATION) {
            repository(validation, """[{"errors":[{"message":"PersistedQueryNotFound"}]}]""")
                .read(account, "456") { true }
        }
    }

    @Test fun `invalid video identity is rejected`() {
        assertFailure(Failure.INVALID_RESPONSE) {
            repository(validation, readResponse(20).replace("456", "999")).read(account, "456") { true }
        }
    }

    @Test fun `malformed success is not missing history`() {
        assertFailure(Failure.INVALID_RESPONSE) {
            repository(validation, """[{"data":{"video":{"id":"456","self":{}}}}]""").read(account, "456") { true }
        }
    }

    @Test fun `unavailable video preserves a distinct failure`() {
        assertFailure(Failure.UNAVAILABLE_VIDEO) {
            repository(validation, """[{"data":{"video":null}}]""").read(account, "456") { true }
        }
    }

    @Test fun `rewind sends seconds exactly and verifies the smaller position`() = runBlocking {
        val result = repository(validation, writeAck, readResponse(10)).writeAndReadBack(account, "456", 10_999) { true }
        assertTrue(result.matched)
        val buffer = Buffer()
        requests[1].body!!.writeTo(buffer)
        val payload = Json.parseToJsonElement(buffer.readUtf8()).jsonArray.single().jsonObject
        assertEquals("updateUserViewedVideo", payload["operationName"]!!.jsonPrimitive.content)
        val input = payload["variables"]!!.jsonObject["input"]!!.jsonObject
        assertEquals("10", input["position"]!!.jsonPrimitive.content)
        assertEquals("123", input["userID"]!!.jsonPrimitive.content)
        assertEquals("456", input["videoID"]!!.jsonPrimitive.content)
        assertEquals("VOD", input["videoType"]!!.jsonPrimitive.content)
    }

    @Test fun `restart at zero can be sent and verified`() = runBlocking {
        assertTrue(repository(validation, writeAck, readResponse(0)).writeAndReadBack(account, "456", 0) { true }.matched)
    }

    @Test fun `different readback is not reported as verified`() = runBlocking {
        val result = repository(validation, writeAck, readResponse(80)).writeAndReadBack(account, "456", 10000) { true }
        assertFalse(result.matched)
        assertEquals(80L, result.observed)
        assertNull(result.readbackFailure)
    }

    @Test fun `readback failure preserves accepted write status`() = runBlocking {
        val result = repository(validation, writeAck, "failure", codeAt = 2 to 503)
            .writeAndReadBack(account, "456", 10000) { true }
        assertFalse(result.matched)
        assertEquals(Failure.HTTP, result.readbackFailure)
        assertEquals(3, requests.size)
    }

    @Test fun `failed mutation does not retry or read back`() {
        assertFailure(Failure.HTTP) {
            repository(validation, "failure", codeAt = 1 to 503).writeAndReadBack(account, "456", 10000) { true }
        }
        assertEquals(2, requests.size)
    }

    @Test fun `recent list checks account and includes available timestamps`() = runBlocking {
        val body = """[{"data":{"currentUser":{"id":"123","viewedVideos":{"edges":[{"history":{"position":0,"updatedAt":"2026-09-08T12:00:00Z"},"node":{"id":"456","title":"Example"}}]}}}}]"""
        val recent = repository(validation, body).recent(account) { true }.single()
        assertEquals(0L, recent.seconds)
        assertEquals("2026-09-08T12:00:00Z", recent.updatedAt)
    }

    @Test fun `recent list from another account is rejected`() {
        assertFailure(Failure.ACCOUNT_MISMATCH) {
            repository(validation, """[{"data":{"currentUser":{"id":"999","viewedVideos":{"edges":[]}}}}]""")
                .recent(account) { true }
        }
    }

    @Test fun `negative and overflowing millisecond positions are rejected before networking`() {
        for (position in listOf(-1L, Long.MAX_VALUE)) {
            assertFailure(Failure.INVALID_POSITION) { repository().writeAndReadBack(account, "456", position) { true } }
        }
        assertTrue(requests.isEmpty())
    }

    @Test fun `oversized unknown length bodies are rejected`() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), "").newBuilder().body(object : ResponseBody() {
                override fun contentType() = "application/json".toMediaType()
                override fun contentLength() = -1L
                override fun source(): BufferedSource = Buffer().writeUtf8("x".repeat(512 * 1024 + 1))
            }).build()
        }.build()
        assertFailure(Failure.INVALID_RESPONSE) { TwitchResumeProbeRepository(client, Json).read(account, "456") { true } }
    }

    @Test fun `cancellation cancels the call and closes a late response`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        var observedCall: Call? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            observedCall = chain.call()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            val source = object : ForwardingSource(Buffer().writeUtf8(validation)) {
                override fun close() { super.close(); closed.countDown() }
            }.buffer()
            response(chain.request(), "").newBuilder().body(object : ResponseBody() {
                override fun contentType() = "application/json".toMediaType()
                override fun contentLength() = validation.length.toLong()
                override fun source() = source
            }).build()
        }.build()
        val task = launch(Dispatchers.Default) { TwitchResumeProbeRepository(client, Json).read(account, "456") { true } }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        task.cancel()
        release.countDown()
        task.join()
        assertTrue(observedCall!!.isCanceled())
        assertTrue(closed.await(5, TimeUnit.SECONDS))
    }
}
