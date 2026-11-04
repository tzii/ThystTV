package com.github.andreyasadchy.xtra.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

class CancellableHttpTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val server = ServerSocket(0)
    private val url get() = "http://127.0.0.1:${server.localPort}/"
    private val http = CancellableHttp(null, null, executor, OkHttpClient())

    @After fun tearDown() {
        server.close()
        executor.shutdownNow()
    }

    /** Accept one request, leave it unanswered, and report when the client hangs up. */
    private fun silentServer(requested: CountDownLatch, clientLeft: CountDownLatch) = thread(isDaemon = true) {
        server.accept().use { socket ->
            val input = socket.getInputStream()
            val header = StringBuilder()
            while (!header.endsWith("\r\n\r\n")) {
                val next = input.read()
                if (next < 0) return@thread
                header.append(next.toChar())
            }
            requested.countDown()
            while (input.read() >= 0) {
                // Wait for the client to close the connection.
            }
            clientLeft.countDown()
        }
    }

    private fun readRequest(socket: Socket): String {
        val input = socket.getInputStream()
        val request = StringBuilder()
        while (!request.endsWith("\r\n\r\n")) {
            val next = input.read()
            if (next < 0) break
            request.append(next.toChar())
        }
        val length = Regex("(?i)content-length: (\\d+)").find(request)?.groupValues?.get(1)?.toInt() ?: 0
        repeat(length) { request.append(input.read().toChar()) }
        return request.toString()
    }

    @Test fun `a response returns its status and body, sending the method headers and body`() = runBlocking {
        var seen = ""
        val serving = thread(isDaemon = true) {
            server.accept().use { socket ->
                seen = readRequest(socket)
                socket.getOutputStream().write("HTTP/1.1 202 Accepted\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
            }
        }
        val result = http.execute("OkHttp", url, method = "POST", headers = mapOf("X-Test" to "yes"), body = "a=b".toByteArray())
        serving.join(5_000)
        assertEquals(HttpResult(202, "ok"), result)
        assertTrue(seen, seen.startsWith("POST "))
        assertTrue(seen, seen.contains("X-Test: yes"))
        assertTrue(seen, seen.endsWith("a=b"))
    }

    @Test fun `a POST without a body is still a POST`() = runBlocking {
        var seen = ""
        val serving = thread(isDaemon = true) {
            server.accept().use { socket ->
                seen = readRequest(socket)
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
        }
        http.execute("OkHttp", url, method = "POST")
        serving.join(5_000)
        assertTrue(seen, seen.startsWith("POST "))
    }

    @Test fun `the elapsed limit stops the request and is an ordinary failure, not a cancellation`() {
        val requested = CountDownLatch(1)
        val clientLeft = CountDownLatch(1)
        silentServer(requested, clientLeft)
        val elapsed = measureTimeMillis {
            try {
                runBlocking { http.execute("OkHttp", url, timeoutMs = 300L) }
                fail("expected a timeout")
            } catch (error: Exception) {
                assertFalse("must not read as the caller being cancelled", error is CancellationException)
                assertTrue(error.toString(), error is HttpTimeoutException)
            }
        }
        assertTrue("took $elapsed ms; the client's own timeouts are far longer", elapsed < 5_000)
        assertTrue("the request was sent", requested.await(5, TimeUnit.SECONDS))
        assertTrue("the connection was closed, not left running", clientLeft.await(5, TimeUnit.SECONDS))
    }

    @Test fun `cancelling the caller stops the underlying request right away`() = runBlocking {
        val requested = CountDownLatch(1)
        val clientLeft = CountDownLatch(1)
        silentServer(requested, clientLeft)
        val request = async(Dispatchers.IO) { http.execute("OkHttp", url) }
        assertTrue("the request was sent", requested.await(5, TimeUnit.SECONDS))
        val elapsed = measureTimeMillis { request.cancelAndJoin() }
        assertTrue("took $elapsed ms", elapsed < 5_000)
        assertTrue(request.isCancelled)
        assertTrue("the connection was closed, not left running", clientLeft.await(5, TimeUnit.SECONDS))
    }

    @Test fun `a refused connection fails instead of hanging`() {
        val port = server.localPort
        server.close()
        try {
            runBlocking { http.execute("OkHttp", "http://127.0.0.1:$port/") }
            fail("expected a failure")
        } catch (error: java.io.IOException) {
            assertFalse(error is HttpTimeoutException)
        }
    }
}
