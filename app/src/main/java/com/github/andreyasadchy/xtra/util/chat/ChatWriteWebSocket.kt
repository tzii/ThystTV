package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.util.KeepaliveWatchdog
import com.github.andreyasadchy.xtra.util.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.net.ssl.X509TrustManager

class ChatWriteWebSocket(
    private val userLogin: String?,
    private val userToken: String?,
    private val channelLogin: String,
    private val trustManager: X509TrustManager?,
    private val listener: ChatReadWebSocket.Listener,
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

    /** PING after [ChatReadWebSocket.PING_INTERVAL_MS] without a PONG, and reconnect if the PONG does not come. */
    private fun watch(webSocket: WebSocket) {
        val scope = webSocket.coroutineScope ?: return
        val connection = webSocket.connection
        keepalive.start(scope, ChatReadWebSocket.PING_INTERVAL_MS, probe = { webSocket.write("PING") },
            probeTimeoutMs = ChatReadWebSocket.PONG_TIMEOUT_MS) {
            webSocket.disconnect(connection)
        }
    }

    suspend fun send(message: CharSequence, replyId: String?) = withContext(Dispatchers.IO) {
        val reply = replyId?.let { "@reply-parent-msg-id=${it} " } ?: ""
        webSocket?.write("${reply}PRIVMSG #$channelLogin :$message")
    }

    private inner class WebSocketListener : WebSocket.Listener {
        override suspend fun onConnect(webSocket: WebSocket) {
            webSocket.write("CAP REQ :twitch.tv/tags twitch.tv/commands")
            webSocket.write("PASS oauth:$userToken")
            webSocket.write("NICK $userLogin")
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
}
