package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.util.WebSocket.Companion.isCertificateFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlin.random.Random

class ReconnectPolicyTest {

    /** A Random that always picks the top or bottom of the range, to check both bounds. */
    private class Extreme(private val high: Boolean) : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextLong(until: Long) = if (high) until - 1 else 0L
    }

    @Test
    fun `waits double from one second up to thirty and never give up`() {
        val longest = ReconnectPolicy(Extreme(high = true))
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), List(7) { longest.nextDelayMs() })
        // The old sockets stopped after 20 attempts; this keeps going at the ceiling.
        repeat(500) { assertEquals(30_000L, longest.nextDelayMs()) }

        val shortest = ReconnectPolicy(Extreme(high = false))
        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 15_000L), List(6) { shortest.nextDelayMs() })
    }

    @Test
    fun `an opened connection makes the next drop retry quickly`() {
        val policy = ReconnectPolicy(Extreme(high = true))
        repeat(10) { policy.nextDelayMs() }
        policy.connected()
        assertEquals(1_000L, policy.nextDelayMs())
        assertEquals(2_000L, policy.nextDelayMs())
    }

    @Test
    fun `a rate limit waits a minute without speeding up later attempts`() {
        val policy = ReconnectPolicy(Extreme(high = true))
        assertEquals(1_000L, policy.nextDelayMs())
        assertEquals(60_000L, policy.nextDelayMs(rateLimited = true))
        assertEquals(2_000L, policy.nextDelayMs())
    }

    @Test
    fun `waits stay within their range with real randomness`() {
        val policy = ReconnectPolicy(Random(42))
        var ceiling = 1_000L
        repeat(50) {
            val wait = policy.nextDelayMs()
            assertTrue("$wait outside ${ceiling / 2}..$ceiling", wait in ceiling / 2..ceiling)
            ceiling = minOf(30_000L, ceiling * 2)
        }
    }

    @Test
    fun `only certificate problems end the socket on a failed handshake`() {
        assertTrue(SSLHandshakeException("untrusted").apply {
            initCause(CertificateException(CertPathValidatorException("Trust anchor for certification path not found.")))
        }.isCertificateFailure())
        assertTrue(SSLHandshakeException("untrusted").apply {
            initCause(CertPathValidatorException("Trust anchor for certification path not found."))
        }.isCertificateFailure())
        // What a weak connection looks like during a handshake: retried, not fatal.
        assertFalse(SSLHandshakeException("SSL handshake aborted: I/O error during system call, Connection reset by peer").apply {
            initCause(SocketException("Connection reset by peer"))
        }.isCertificateFailure())
        assertFalse(SSLHandshakeException("Connection closed by peer").isCertificateFailure())
    }
}
