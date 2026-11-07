package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.util.KeepaliveWatchdog
import com.github.andreyasadchy.xtra.util.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

class ChatReadWebSocket(
    private val channelLogin: String,
    private val trustManager: X509TrustManager?,
    private val listener: Listener,
) {
    private var webSocket: WebSocket? = null
    private val keepalive = KeepaliveWatchdog()

    fun connect(coroutineScope: CoroutineScope): Job {
        webSocket = WebSocket("wss://irc-ws.chat.twitch.tv", trustManager, WebSocketListener())
        webSocket?.coroutineScope = coroutineScope
        return coroutineScope.launch(Dispatchers.IO) {
            webSocket?.start()
        }
    }

    suspend fun disconnect(job: Job?) = withContext(Dispatchers.IO) {
        keepalive.stop()
        job?.cancel()
        webSocket?.disconnect()
    }

    /** PING after [PING_INTERVAL_MS] without a PONG, and reconnect if the PONG does not come. */
    private fun watch(webSocket: WebSocket) {
        val scope = webSocket.coroutineScope ?: return
        keepalive.start(scope, PING_INTERVAL_MS, probe = { webSocket.write("PING") }, probeTimeoutMs = PONG_TIMEOUT_MS) {
            webSocket.disconnect()
        }
    }

    interface Listener {
        suspend fun onConnect() {}
        suspend fun onChatMessage(message: String, userNotice: Boolean) {}
        suspend fun onClearMessage(message: String) {}
        suspend fun onClearChat(message: String) {}
        suspend fun onNotice(message: String) {}
        suspend fun onRoomState(message: String) {}
        suspend fun onUserState(message: String) {}
        suspend fun onDisconnect(message: String, fullMsg: String?) {}
    }

    private inner class WebSocketListener : WebSocket.Listener {
        override suspend fun onConnect(webSocket: WebSocket) {
            webSocket.write("CAP REQ :twitch.tv/tags twitch.tv/commands")
            webSocket.write("NICK justinfan${Random.nextInt(1000, 10000)}")
            webSocket.write("JOIN #$channelLogin")
            listener.onConnect()
            watch(webSocket)
        }

        override suspend fun onMessage(webSocket: WebSocket, message: String) {
            message.removeSuffix("\r\n").split("\r\n").forEach {
                it.run {
                    when {
                        contains("PRIVMSG") -> listener.onChatMessage(this, false)
                        contains("USERNOTICE") -> listener.onChatMessage(this, true)
                        contains("CLEARMSG") -> listener.onClearMessage(this)
                        contains("CLEARCHAT") -> listener.onClearChat(this)
                        contains("NOTICE") -> listener.onNotice(this)
                        contains("ROOMSTATE") -> listener.onRoomState(this)
                        contains("USERSTATE") -> listener.onUserState(this)
                        startsWith("PING") -> {
                            webSocket.write("PONG")
                        }
                        startsWith("PONG") -> keepalive.feed()
                        startsWith("RECONNECT") -> {
                            keepalive.stop()
                            webSocket.disconnect()
                        }
                    }
                }
            }
        }

        override suspend fun onDisconnect(webSocket: WebSocket, message: String, fullMsg: String?) {
            listener.onDisconnect(message, fullMsg)
        }
    }

    internal companion object {
        /** Time after connecting or the last PONG before the next PING. */
        const val PING_INTERVAL_MS = 270_000L
        const val PONG_TIMEOUT_MS = 10_000L
    }
}
