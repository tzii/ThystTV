package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.util.KeepaliveWatchdog
import com.github.andreyasadchy.xtra.util.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.net.ssl.X509TrustManager

class EventSubWebSocket(
    private val trustManager: X509TrustManager?,
    private val listener: Listener,
) {
    private var webSocket: WebSocket? = null
    private val keepalive = KeepaliveWatchdog()
    private var timeout = 10000L
    private val handledMessageIds = RecentIds(200)

    fun connect(coroutineScope: CoroutineScope): Job {
        webSocket = WebSocket("wss://eventsub.wss.twitch.tv/ws", trustManager, WebSocketListener())
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

    /** A keepalive or notification proves the session alive for another [timeout]. */
    private fun keepAlive(webSocket: WebSocket) {
        if (keepalive.isRunning) {
            keepalive.feed()
        } else {
            watch(webSocket)
        }
    }

    /** A welcome starts a new session, which may announce a different [timeout]. */
    private fun watch(webSocket: WebSocket) {
        val scope = webSocket.coroutineScope ?: return
        val connection = webSocket.connection
        keepalive.start(scope, timeout) { webSocket.disconnect(connection) }
    }

    interface Listener {
        suspend fun onConnect() {}
        suspend fun onWelcomeMessage(sessionId: String) {}
        suspend fun onChatMessage(event: JSONObject, timestamp: String?) {}
        suspend fun onUserNotice(event: JSONObject, timestamp: String?) {}
        suspend fun onClearChat(event: JSONObject, timestamp: String?) {}
        suspend fun onRoomState(event: JSONObject, timestamp: String?) {}
        suspend fun onDisconnect(message: String, fullMsg: String?) {}
    }

    private inner class WebSocketListener : WebSocket.Listener {
        override suspend fun onConnect(webSocket: WebSocket) {
            listener.onConnect()
        }

        override suspend fun onMessage(webSocket: WebSocket, message: String) {
            try {
                val json = if (message.isNotBlank()) JSONObject(message) else null
                if (json != null) {
                    val metadata = json.optJSONObject("metadata")
                    val messageId = if (metadata?.isNull("message_id") == false) metadata.optString("message_id").takeIf { it.isNotBlank() } else null
                    val timestamp = if (metadata?.isNull("message_timestamp") == false) metadata.optString("message_timestamp").takeIf { it.isNotBlank() } else null
                    if (!messageId.isNullOrBlank() && !handledMessageIds.add(messageId)) {
                        return
                    }
                    when (metadata?.optString("message_type")) {
                        "notification" -> {
                            keepAlive(webSocket)
                            val payload = json.optJSONObject("payload")
                            val event = payload?.optJSONObject("event")
                            if (event != null) {
                                when (metadata.optString("subscription_type")) {
                                    "channel.chat.message" -> listener.onChatMessage(event, timestamp)
                                    "channel.chat.notification" -> listener.onUserNotice(event, timestamp)
                                    "channel.chat.clear" -> listener.onClearChat(event, timestamp)
                                    "channel.chat_settings.update" -> listener.onRoomState(event, timestamp)
                                }
                            }
                        }
                        "session_keepalive" -> keepAlive(webSocket)
                        "session_reconnect" -> {
                            //val payload = json.optJSONObject("payload")
                            //val session = payload?.optJSONObject("session")
                            //val reconnectUrl = if (session?.isNull("reconnect_url") == false) session.optString("reconnect_url").takeIf { it.isNotBlank() } else null
                            keepalive.stop()
                            webSocket.disconnect()
                        }
                        "session_welcome" -> {
                            val payload = json.optJSONObject("payload")
                            val session = payload?.optJSONObject("session")
                            if (session?.isNull("keepalive_timeout_seconds") == false) {
                                session.optInt("keepalive_timeout_seconds").takeIf { it > 0 }?.let {
                                    timeout = it * 1000L
                                }
                            }
                            watch(webSocket)
                            val sessionId = session?.optString("id")
                            if (!sessionId.isNullOrBlank()) {
                                listener.onWelcomeMessage(sessionId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {

            }
        }

        override suspend fun onDisconnect(webSocket: WebSocket, message: String, fullMsg: String?) {
            listener.onDisconnect(message, fullMsg)
        }
    }
}
