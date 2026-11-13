package com.github.andreyasadchy.xtra.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket

class WebSocketDisconnectTest {

    private class FakeSocket : Socket() {
        val sent = ByteArrayOutputStream()

        @Volatile
        private var closed = false

        override fun getOutputStream(): OutputStream = sent
        override fun isClosed() = closed
        override fun close() {
            closed = true
        }
    }

    /** Installs [socket] the way a (re)connect does. */
    private fun WebSocket.connectTo(socket: Socket) {
        for ((name, value) in listOf("socket" to socket, "outputStream" to socket.outputStream)) {
            WebSocket::class.java.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
        }
    }

    @Test
    fun `an expiry closes the connection it watched, not the one a reconnect made`() = runBlocking {
        val webSocket = WebSocket("wss://irc-ws.chat.twitch.tv", null, object : WebSocket.Listener {})
        val watched = FakeSocket()
        webSocket.connectTo(watched)
        val connection = webSocket.connection
        val replacement = FakeSocket()
        webSocket.connectTo(replacement)

        val expiry = launch(Dispatchers.IO) { webSocket.disconnect(connection) }
        withTimeout(5_000) {
            while (watched.sent.size() == 0) delay(10)
        }
        // The close itself follows five seconds after the frame; the frame is what matters here.
        expiry.cancel()

        assertEquals(0x88, watched.sent.toByteArray()[0].toInt() and 0xFF) // FIN + CLOSE
        assertEquals(0, replacement.sent.size())
        assertFalse(replacement.isClosed)
    }
}
