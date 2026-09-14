package com.github.andreyasadchy.xtra.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Twitch compatibility transport shared by the manual probe and account sync. No local Stats writes. */
@Singleton
class TwitchResumeProbeRepository @Inject constructor(okHttpClient: OkHttpClient, private val json: Json) {
    class Account(val id: String, headers: Map<String, String>) {
        val headers = headers.toMap()
        override fun toString() = "ResumeProbeAccount(redacted)"
    }

    enum class Failure {
        SIGN_IN_REQUIRED, ACCOUNT_MISMATCH, CLIENT_MISMATCH, SESSION_CHANGED,
        TOKEN_REJECTED, AUTHENTICATION, UNSUPPORTED_OPERATION, GRAPHQL, HTTP, NETWORK, INVALID_RESPONSE,
        UNAVAILABLE_VIDEO, INVALID_POSITION,
    }

    class ProbeException(val failure: Failure) : Exception(failure.name)
    data class Position(val videoId: String, val seconds: Long?)
    data class Recent(val videoId: String, val seconds: Long, val updatedAt: String?, val title: String)
    data class WriteResult(val seconds: Long, val observed: Long?, val readbackFailure: Failure? = null) {
        val matched: Boolean get() = readbackFailure == null && seconds == observed
    }

    // Existing app transport, with request-local credentials and no raw authenticated logging.
    private val client = okHttpClient.newBuilder().apply {
        interceptors().removeAll { it is HttpLoggingInterceptor }
        networkInterceptors().removeAll { it is HttpLoggingInterceptor }
    }.callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).build()

    suspend fun read(account: Account, videoId: String, isCurrent: () -> Boolean): Position = withContext(Dispatchers.IO) {
        validate(account, isCurrent)
        readPosition(account, videoId, isCurrent)
    }

    suspend fun recent(account: Account, isCurrent: () -> Boolean): List<Recent> = withContext(Dispatchers.IO) {
        validate(account, isCurrent)
        val data = operation(account, RECENT, buildJsonObject {
            put("includePreviewBlur", false)
            put("limit", 20)
        }, isCurrent)
        val user = data.obj("currentUser") ?: fail(Failure.AUTHENTICATION)
        if (user.string("id") != account.id) fail(Failure.ACCOUNT_MISMATCH)
        val edges = user.obj("viewedVideos")?.get("edges") as? JsonArray ?: fail(Failure.INVALID_RESPONSE)
        if (edges.size > 20) fail(Failure.INVALID_RESPONSE)
        edges.map { edge ->
            val item = edge as? JsonObject ?: fail(Failure.INVALID_RESPONSE)
            val node = item.obj("node") ?: fail(Failure.INVALID_RESPONSE)
            val history = item.obj("history") ?: fail(Failure.INVALID_RESPONSE)
            val id = node.string("id")?.takeIf(::validId) ?: fail(Failure.INVALID_RESPONSE)
            Recent(id, position(history), history.string("updatedAt")?.take(64), node.string("title").orEmpty().take(200))
        }
    }

    /** Caller owns opt-in, conflict checks and retry policy. This transport never uploads legacy data. */
    suspend fun writeAndReadBack(
        account: Account, videoId: String, positionMs: Long, isCurrent: () -> Boolean,
    ): WriteResult = withContext(Dispatchers.IO) {
        val seconds = toSeconds(positionMs)
        if (!validId(videoId)) fail(Failure.UNAVAILABLE_VIDEO)
        validate(account, isCurrent)
        val data = operation(account, WRITE, buildJsonObject {
            putJsonObject("input") {
                put("userID", account.id)
                put("videoID", videoId)
                put("videoType", "VOD")
                put("position", seconds)
            }
        }, isCurrent)
        if (data.obj("updateUserViewedVideo")?.obj("video")?.string("id") != videoId) {
            fail(Failure.INVALID_RESPONSE)
        }
        try {
            WriteResult(seconds, readPosition(account, videoId, isCurrent).seconds)
        } catch (error: ProbeException) {
            // Preserve the accepted-write distinction when verification fails afterwards.
            WriteResult(seconds, null, error.failure)
        }
    }

    suspend fun validate(account: Account, isCurrent: () -> Boolean): Unit = withContext(Dispatchers.IO) {
        checkCurrent(isCurrent)
        // Request.Builder.header replaces names case-insensitively: the last entry wins.
        // Validate that exact value even if custom integrity headers contain case variants.
        val authorization = account.headers.entries.lastOrNull { it.key.equals("Authorization", true) }?.value
        val parts = authorization?.split(' ', limit = 2)
        val token = parts?.getOrNull(1)?.takeIf { value -> value.isNotBlank() && value.none(Char::isWhitespace) }
        val prefix = parts?.firstOrNull()
        if (!validId(account.id) || token == null ||
            !(prefix.equals("OAuth", true) || prefix.equals("Bearer", true))
        ) fail(Failure.SIGN_IN_REQUIRED)
        val clientId = account.headers.entries.lastOrNull { it.key.equals("Client-ID", true) }?.value
            ?.takeIf { it.isNotBlank() } ?: fail(Failure.SIGN_IN_REQUIRED)
        val data = try {
            request(Request.Builder().url("https://id.twitch.tv/oauth2/validate")
                .header("Authorization", "OAuth $token").build(), isCurrent) as? JsonObject
                ?: fail(Failure.INVALID_RESPONSE)
        } catch (error: ProbeException) {
            if (error.failure == Failure.AUTHENTICATION) fail(Failure.TOKEN_REJECTED)
            throw error
        }
        if (data.string("user_id") != account.id) fail(Failure.ACCOUNT_MISMATCH)
        if (data.string("client_id") != clientId) fail(Failure.CLIENT_MISMATCH)
        // Twitch's live validation response determines validity. Legacy tokens can
        // have expires_in=0 (or omit it) without being expired; never cache that as
        // permanent authorization. https://dev.twitch.tv/docs/authentication/validate-tokens/
        val expiry = data["expires_in"]
        if (expiry != null && (expiry as? JsonPrimitive)?.takeUnless { it.isString }
                ?.longOrNull?.let { it >= 0 } != true) {
            fail(Failure.INVALID_RESPONSE)
        }
    }

    private suspend fun readPosition(account: Account, videoId: String, isCurrent: () -> Boolean): Position {
        if (!validId(videoId)) fail(Failure.UNAVAILABLE_VIDEO)
        val data = operation(account, READ, buildJsonObject { put("videoId", videoId) }, isCurrent)
        val video = data.obj("video") ?: fail(Failure.UNAVAILABLE_VIDEO)
        if (video.string("id") != videoId) fail(Failure.INVALID_RESPONSE)
        val self = video.obj("self") ?: fail(Failure.AUTHENTICATION)
        if (!self.containsKey("viewingHistory")) fail(Failure.INVALID_RESPONSE)
        val history = self["viewingHistory"]
        return Position(videoId, when (history) {
            kotlinx.serialization.json.JsonNull -> null
            is JsonObject -> position(history)
            else -> fail(Failure.INVALID_RESPONSE)
        })
    }

    private suspend fun operation(
        account: Account, operation: Pair<String, String>, variables: JsonObject, isCurrent: () -> Boolean,
    ): JsonObject {
        val body = JsonArray(listOf(buildJsonObject {
            put("operationName", operation.first)
            put("variables", variables)
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") { put("version", 1); put("sha256Hash", operation.second) }
            }
        }))
        val request = try {
            Request.Builder().url(TwitchEndpoints.GRAPHQL).apply {
                account.headers.forEach { (name, value) -> header(name, value) }
            }.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        } catch (_: IllegalArgumentException) {
            fail(Failure.AUTHENTICATION)
        }
        val envelope = request(request, isCurrent)
        val result = (envelope as? JsonArray)?.singleOrNull() as? JsonObject ?: fail(Failure.INVALID_RESPONSE)
        val errors = result["errors"]
        if (errors != null && errors !is kotlinx.serialization.json.JsonNull) {
            if (errors !is JsonArray) fail(Failure.INVALID_RESPONSE)
            if (errors.isNotEmpty()) {
                // Never put server error text in UI/logs: it can contain request details.
                val text = errors.toString().lowercase()
                fail(when {
                    "persistedquerynotfound" in text || "persistedquerynotsupported" in text -> Failure.UNSUPPORTED_OPERATION
                    "unauthorized" in text || "integrity" in text || "authentication" in text -> Failure.AUTHENTICATION
                    else -> Failure.GRAPHQL
                })
            }
        }
        return result.obj("data") ?: fail(Failure.INVALID_RESPONSE)
    }

    private suspend fun request(request: Request, isCurrent: () -> Boolean): JsonElement {
        checkCurrent(isCurrent)
        val body = try {
            readBody(request)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            fail(Failure.NETWORK)
        }
        checkCurrent(isCurrent)
        return try { json.parseToJsonElement(body) } catch (_: IllegalArgumentException) { fail(Failure.INVALID_RESPONSE) }
    }

    private suspend fun readBody(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.code == 401 || it.code == 403) fail(Failure.AUTHENTICATION)
                        if (!it.isSuccessful) fail(Failure.HTTP)
                        val body = it.body ?: fail(Failure.INVALID_RESPONSE)
                        if (body.contentLength() > MAX_BYTES) fail(Failure.INVALID_RESPONSE)
                        val source = body.source()
                        if (source.request(MAX_BYTES + 1L)) fail(Failure.INVALID_RESPONSE)
                        source.readUtf8()
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    private suspend fun checkCurrent(isCurrent: () -> Boolean) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) fail(Failure.SESSION_CHANGED)
    }

    private fun position(history: JsonObject): Long = (history["position"] as? JsonPrimitive)
        ?.takeUnless { it.isString }?.longOrNull?.takeIf { it in 0..Int.MAX_VALUE.toLong() }
        ?: fail(Failure.INVALID_RESPONSE)

    private fun JsonObject.obj(key: String) = get(key) as? JsonObject
    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        private const val MAX_BYTES = 512L * 1024L
        // Historical official-client captures; unsupported hashes are reported, never guessed.
        // https://github.com/crackededed/Xtra/issues/329#issuecomment-2943100913
        private val READ = "queryUserViewedVideo" to "86bf57f2a04e2f4705ce456d082bcffd80a372b6641a9c9120c409b10959a7a5"
        private val WRITE = "updateUserViewedVideo" to "bb58b1bd08a4ca0c61f2b8d323381a5f4cd39d763da8698f680ef1dfaea89ca1"
        private val RECENT = "FollowedStreamsContinueWatching" to "c689d0645defdd63aaab322166a570c785cefa97b6e97c1a1e7fb66ccdfcad82"
        private fun validId(id: String) = id.isNotEmpty() && id.length <= 32 && id.all { it in '0'..'9' }
        private fun fail(failure: Failure): Nothing = throw ProbeException(failure)
        internal fun toSeconds(positionMs: Long): Long {
            if (positionMs !in 0..Int.MAX_VALUE.toLong() * 1000L) fail(Failure.INVALID_POSITION)
            return positionMs / 1000L
        }
    }
}
