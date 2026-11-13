package com.github.andreyasadchy.xtra.util

import android.net.http.HttpEngine
import android.os.Build
import android.os.ext.SdkExtensions
import dagger.Lazy
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.chromium.net.CronetEngine
import org.chromium.net.apihelpers.UploadDataProviders
import java.io.IOException
import java.util.concurrent.ExecutorService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The whole request, including reading the body, took longer than its limit. */
internal class HttpTimeoutException(timeoutMs: Long) : IOException("No complete response within $timeoutMs ms")

internal data class HttpResult(val status: Int, val body: String)

/**
 * One request path for all three network libraries. Unlike a blocking call under
 * `withContext`, cancelling the coroutine stops the underlying HTTP request and returns
 * at once, and [execute] enforces a limit on the total elapsed time, not just on each read.
 *
 * Timing out is reported as [HttpTimeoutException], an ordinary failure, so it can't be
 * mistaken for the caller being cancelled.
 */
internal class CancellableHttp(
    private val httpEngine: Lazy<HttpEngine>?,
    private val cronetEngine: Lazy<CronetEngine>?,
    private val cronetExecutor: ExecutorService,
    private val okHttpClient: OkHttpClient,
) {
    suspend fun execute(
        networkLibrary: String?,
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): HttpResult = withTimeoutOrNull(timeoutMs) {
        when {
            networkLibrary == "HttpEngine" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7 && httpEngine != null -> {
                val response = suspendCancellableCoroutine { continuation ->
                    val request = httpEngine.get().newUrlRequestBuilder(url, cronetExecutor, HttpEngineUtils.byteArrayUrlCallback(continuation)).apply {
                        setHttpMethod(method)
                        headers.forEach { addHeader(it.key, it.value) }
                        body?.let { setUploadDataProvider(HttpEngineUtils.byteArrayUploadProvider(it), cronetExecutor) }
                    }.build()
                    continuation.invokeOnCancellation { request.cancel() }
                    request.start()
                    // A cancel that came before the start might not have counted.
                    if (continuation.isCancelled) request.cancel()
                }
                HttpResult(response.first.httpStatusCode, String(response.second))
            }
            networkLibrary == "Cronet" && cronetEngine != null -> {
                val response = suspendCancellableCoroutine { continuation ->
                    val request = cronetEngine.get().newUrlRequestBuilder(url, getByteArrayCronetCallback(continuation), cronetExecutor).apply {
                        setHttpMethod(method)
                        headers.forEach { addHeader(it.key, it.value) }
                        body?.let { setUploadDataProvider(UploadDataProviders.create(it), cronetExecutor) }
                    }.build()
                    continuation.invokeOnCancellation { request.cancel() }
                    request.start()
                    if (continuation.isCancelled) request.cancel()
                }
                HttpResult(response.first.httpStatusCode, String(response.second))
            }
            else -> suspendCancellableCoroutine { continuation ->
                val call = okHttpClient.newCall(Request.Builder().apply {
                    url(url)
                    headers.forEach { header(it.key, it.value) }
                    method(method, body?.toRequestBody() ?: if (method == "POST") ByteArray(0).toRequestBody() else null)
                }.build())
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = try {
                            response.use { HttpResult(it.code, it.body.string()) }
                        } catch (e: IOException) {
                            continuation.resumeWithException(e)
                            return
                        }
                        continuation.resume(result)
                    }
                })
            }
        }
    } ?: throw HttpTimeoutException(timeoutMs)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}
