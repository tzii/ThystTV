package com.github.andreyasadchy.xtra.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.player.ExoPlayerService.PlayerSlot
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * Reusable video surface view for multi-stream support.
 * 
 * Features:
 * - SurfaceView wrapped in AspectRatioFrameLayout
 * - Buffering indicator
 * - Audio indicator (shows which stream has audio)
 * - Stream info overlay (channel name, title)
 * - Touch overlay for gestures
 */
@UnstableApi
class StreamSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // Views
    val aspectRatioFrame: AspectRatioFrameLayout
    val videoSurface: SurfaceView
    val bufferingIndicator: CircularProgressIndicator
    val audioIndicatorContainer: FrameLayout
    val audioIndicatorIcon: ImageView
    val streamInfoOverlay: LinearLayout
    val channelNameView: TextView
    val streamTitleView: TextView
    val touchOverlay: View

    // State
    private var slot: PlayerSlot = PlayerSlot.PRIMARY
    private var hasAudio: Boolean = false
    private var isBuffering: Boolean = false
    private var channelName: String? = null
    private var streamTitle: String? = null

    // Listener for tap events
    var onTapListener: ((PlayerSlot) -> Unit)? = null

    // Listener for long press events
    var onLongPressListener: ((PlayerSlot) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_stream_surface, this, true)

        // Bind views
        aspectRatioFrame = findViewById(R.id.aspectRatioFrame)
        videoSurface = findViewById(R.id.videoSurface)
        bufferingIndicator = findViewById(R.id.bufferingIndicator)
        audioIndicatorContainer = findViewById(R.id.audioIndicatorContainer)
        audioIndicatorIcon = findViewById(R.id.audioIndicatorIcon)
        streamInfoOverlay = findViewById(R.id.streamInfoOverlay)
        channelNameView = findViewById(R.id.channelName)
        streamTitleView = findViewById(R.id.streamTitle)
        touchOverlay = findViewById(R.id.touchOverlay)

        setupTouchOverlay()
    }

    private fun setupTouchOverlay() {
        touchOverlay.setOnClickListener {
            onTapListener?.invoke(slot)
        }
        touchOverlay.setOnLongClickListener {
            onLongPressListener?.invoke(slot)
            true
        }
    }

    /**
     * Configure this view for a specific player slot
     */
    fun setSlot(slot: PlayerSlot) {
        this.slot = slot
    }

    /**
     * Get the current slot
     */
    fun getSlot(): PlayerSlot = slot

    /**
     * Set the aspect ratio
     */
    fun setAspectRatio(widthHeightRatio: Float) {
        aspectRatioFrame.setAspectRatio(widthHeightRatio)
    }

    /**
     * Set the resize mode
     */
    fun setResizeMode(resizeMode: Int) {
        aspectRatioFrame.resizeMode = resizeMode
    }

    /**
     * Show/hide buffering indicator
     */
    fun setBuffering(buffering: Boolean) {
        isBuffering = buffering
        bufferingIndicator.visibility = if (buffering) View.VISIBLE else View.GONE
    }

    /**
     * Show/hide audio indicator
     */
    fun setAudioActive(active: Boolean) {
        hasAudio = active
        audioIndicatorContainer.visibility = if (active) View.VISIBLE else View.GONE
    }

    /**
     * Check if audio is active
     */
    fun isAudioActive(): Boolean = hasAudio

    /**
     * Set stream info for overlay
     */
    fun setStreamInfo(channel: String?, title: String?) {
        channelName = channel
        streamTitle = title
        channelNameView.text = channel ?: ""
        streamTitleView.text = title ?: ""
        streamTitleView.visibility = if (title.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /**
     * Show stream info overlay briefly (auto-hides after delay)
     */
    fun showStreamInfoOverlay(durationMs: Long = 3000) {
        if (channelName.isNullOrBlank()) return
        
        streamInfoOverlay.alpha = 0f
        streamInfoOverlay.visibility = View.VISIBLE
        streamInfoOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Auto-hide after delay
                    streamInfoOverlay.postDelayed({
                        hideStreamInfoOverlay()
                    }, durationMs)
                }
            })
            .start()
    }

    /**
     * Hide stream info overlay
     */
    fun hideStreamInfoOverlay() {
        streamInfoOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    streamInfoOverlay.visibility = View.GONE
                }
            })
            .start()
    }

    /**
     * Attach a player to this surface
     */
    fun attachPlayer(player: Player?) {
        player?.let {
            it.setVideoSurfaceView(videoSurface)
            // Listen for buffering state
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    setBuffering(playbackState == Player.STATE_BUFFERING)
                }
            })
        }
    }

    /**
     * Detach player from this surface
     */
    fun detachPlayer(player: Player?) {
        player?.clearVideoSurfaceView(videoSurface)
    }

    /**
     * Update video size info
     */
    fun onVideoSizeChanged(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            setAspectRatio(width.toFloat() / height.toFloat())
        }
    }

    /**
     * Pulse the audio indicator (for visual feedback when switching)
     */
    fun pulseAudioIndicator() {
        audioIndicatorContainer.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(150)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    audioIndicatorContainer.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
            })
            .start()
    }
}
