package com.github.andreyasadchy.xtra.repository

import android.net.http.HttpEngine
import com.github.andreyasadchy.xtra.model.id.DeviceCodeResponse
import com.github.andreyasadchy.xtra.model.id.TokenResponse
import com.github.andreyasadchy.xtra.model.id.ValidationResponse
import com.github.andreyasadchy.xtra.util.CancellableHttp
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    httpEngine: Lazy<HttpEngine>?,
    cronetEngine: Lazy<CronetEngine>?,
    cronetExecutor: ExecutorService,
    okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val http = CancellableHttp(httpEngine, cronetEngine, cronetExecutor, okHttpClient)

    suspend fun validate(networkLibrary: String?, token: String): ValidationResponse = withContext(Dispatchers.IO) {
        val response = http.execute(networkLibrary, "https://id.twitch.tv/oauth2/validate", headers = mapOf("Authorization" to token))
        if (response.status == 401) {
            throw IllegalStateException("401")
        }
        json.decodeFromString<ValidationResponse>(response.body)
    }

    suspend fun revoke(networkLibrary: String?, body: String) {
        postForm(networkLibrary, "https://id.twitch.tv/oauth2/revoke", body)
    }

    suspend fun getDeviceCode(networkLibrary: String?, body: String): DeviceCodeResponse = withContext(Dispatchers.IO) {
        json.decodeFromString<DeviceCodeResponse>(postForm(networkLibrary, "https://id.twitch.tv/oauth2/device", body))
    }

    suspend fun getToken(networkLibrary: String?, body: String): TokenResponse = withContext(Dispatchers.IO) {
        json.decodeFromString<TokenResponse>(postForm(networkLibrary, "https://id.twitch.tv/oauth2/token", body))
    }

    private suspend fun postForm(networkLibrary: String?, url: String, body: String): String = http.execute(
        networkLibrary,
        url,
        method = "POST",
        headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        body = body.toByteArray(),
    ).body
}
