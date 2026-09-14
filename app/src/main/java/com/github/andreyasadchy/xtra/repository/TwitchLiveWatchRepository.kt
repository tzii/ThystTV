package com.github.andreyasadchy.xtra.repository

import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Best-effort reports of actually observed minutes. No retries or replay of missed live time. */
@Singleton
class TwitchLiveWatchRepository @Inject constructor(okHttpClient: OkHttpClient) {
    private val client = okHttpClient.newBuilder().apply {
        interceptors().removeAll { it is HttpLoggingInterceptor }
        networkInterceptors().removeAll { it is HttpLoggingInterceptor }
    }.callTimeout(15, TimeUnit.SECONDS).connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private var endpoint: String? = null
    private var discoveredAt = 0L

    suspend fun send(account: String, broadcast: String, channel: String, login: String, isCurrent: () -> Boolean) = withContext(Dispatchers.IO) {
        require(listOf(account, broadcast, channel).all { it.toLongOrNull()?.let { n -> n > 0 } == true })
        require(login.matches(Regex("[A-Za-z0-9_]{1,50}")))
        if (endpoint == null || SystemClock.elapsedRealtime() - discoveredAt > 3_600_000) {
            val page = request(Request.Builder().url("https://www.twitch.tv/$login").build(), isCurrent)
            val settings = Regex("https://[\\w.]+/config/settings\\.[\\w-]+\\.js").find(page)?.value ?: throw IOException()
            if (!trustedUrl(settings)) throw IOException()
            val config = request(Request.Builder().url(settings).build(), isCurrent)
            val found = Regex("\"(?:beacon_url|spade_url)\"\\s*:\\s*\"([^\"]+)\"").find(config)?.groupValues?.get(1) ?: throw IOException()
            if (!trustedUrl(found)) throw IOException()
            endpoint = found
            discoveredAt = SystemClock.elapsedRealtime()
        }
        try {
            request(Request.Builder().url(requireNotNull(endpoint)).post(form(account, broadcast, channel)).build(), isCurrent)
        } catch (error: IOException) {
            endpoint = null
            throw error
        }
    }

    private suspend fun request(request: Request, isCurrent: () -> Boolean): String = suspendCancellableCoroutine { continuation ->
        if (!isCurrent()) {
            continuation.cancel()
            return@suspendCancellableCoroutine
        }
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Live report unavailable"))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        if (!continuation.isActive || !isCurrent()) throw IOException()
                        if (!it.isSuccessful) throw IOException()
                        val source = it.body.source()
                        if (source.request(2_097_153)) throw IOException()
                        source.readUtf8()
                    }
                    if (continuation.isActive) {
                        if (isCurrent()) continuation.resume(result) else continuation.cancel()
                    }
                } catch (_: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("Live report unavailable"))
                }
            }
        })
    }

    companion object {
        internal fun trustedUrl(value: String): Boolean {
            val url = value.toHttpUrlOrNull() ?: return false
            return url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                listOf("twitch.tv", "twitchcdn.net", "ttvnw.net").any { url.host == it || url.host.endsWith(".$it") }
        }

        internal fun form(account: String, broadcast: String, channel: String): FormBody {
            val event = buildJsonObject {
                put("event", "minute-watched")
                putJsonObject("properties") {
                    put("channel_id", channel)
                    put("broadcast_id", broadcast)
                    put("player", "site")
                    put("user_id", account.toLong())
                }
            }
            return FormBody.Builder().add("data", Base64.encodeToString(event.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)).build()
        }
    }
}
