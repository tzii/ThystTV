package com.github.andreyasadchy.xtra.ui.player

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import java.util.UUID

/** Wait for this LOAD's receiver status before PLAY; an empty receiver drops earlier PLAYs. */
@OptIn(UnstableApi::class)
internal class CastReceiverLoad(
    private val client: () -> RemoteMediaClient?,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    fun start(player: Player, item: MediaItem, play: () -> Boolean, ready: () -> Unit, failed: () -> Unit): () -> Unit {
        val remote = client()
        val receiver = item.localConfiguration?.tag as? CastReceiverUri
        if (remote == null || receiver == null) {
            failed()
            return {}
        }
        val request = UUID.randomUUID().toString()
        val replacement = item.buildUpon().setTag(receiver.copy(loadRequest = request)).build()
        var finished = false
        var timeout: Runnable? = null
        lateinit var callback: RemoteMediaClient.Callback
        fun finish(result: Boolean?) {
            if (finished) return
            finished = true
            timeout?.let(handler::removeCallbacks)
            remote.unregisterCallback(callback)
            when (result) {
                true -> runCatching { player.playWhenReady = play() }.fold({ ready() }, { failed() })
                false -> failed()
                null -> Unit // Caller invalidated this source/route; do not fall back or play.
            }
        }
        callback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                // Allow Media3's status listener to update its forwarding-player state first.
                handler.post {
                    if (!finished && client() === remote &&
                        remote.mediaStatus?.mediaInfo?.customData?.optString(CAST_LOAD_REQUEST) == request) finish(true)
                }
            }
        }
        remote.registerCallback(callback)
        timeout = Runnable { finish(false) }.also { handler.postDelayed(it, 15_000L) }
        runCatching {
            // Stage the LOAD paused (autoplay follows playWhenReady); PLAY waits for the ack.
            if (player.playWhenReady) player.playWhenReady = false
            player.setMediaItem(replacement)
        }.onFailure { finish(false) }
        return { finish(null) }
    }
}
