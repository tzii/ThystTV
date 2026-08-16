package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.media.AudioManager
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.github.andreyasadchy.xtra.R
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.abs

interface PlayerGestureCallback {
    val isPortrait: Boolean
    val isMaximized: Boolean
    val isControlsVisible: Boolean
    val controlsVisibleAtGestureStart: Boolean
    val playerWidth: Int
    val playerHeight: Int
    val windowAttributes: android.view.WindowManager.LayoutParams
    val isEdgeSwipe: Boolean
    val playerGestureInsets: androidx.core.graphics.Insets?
    
    fun setWindowAttributes(params: android.view.WindowManager.LayoutParams)
    fun showController()
    fun hideController()
    fun updateProgress()
    fun cycleChatMode()
    fun getGestureFeedbackView(): View
    fun getHideGestureRunnable(): Runnable
    fun isControllerHideOnTouch(): Boolean

    // New methods for split-screen gestures
    fun getPlayerVideoType(): String?
    fun getCurrentPosition(): Long?
    fun getDuration(): Long
    fun seek(position: Long)
    fun setPlaybackSpeed(speed: Float)
    fun getCurrentSpeed(): Float?
    
    // Notify when gesture detector has claimed a swipe gesture
    fun onSwipeGestureStarted()
    fun onSwipeGestureEnded()
}

class PlayerGestureListener(
    private val context: Context,
    private val callback: PlayerGestureCallback,
    private val doubleTapEnabled: Boolean,
    private val gesturesEnabled: Boolean = true,
    private val hapticEnabled: Boolean = false,
    private val sensitivity: Float = 1.0f, // Multiplier: 0.5f, 1.0f, 2.0f
    private val zoneSplit: Float = 0.5f    // Top zone ratio: 0.4f, 0.5f, 0.6f
) : GestureDetector.SimpleOnGestureListener() {

    private val helper = PlayerGestureHelper(context)
    
    // State machine flags to track which gesture mode we are currently in
    private var isVolume = false
    private var isBrightness = false
    private var isSeek = false
    private var isSpeed = false
    
    // Flag to ensure we only notify the callback once per gesture
    private var hasNotifiedGestureStart = false
    
    // General flag to indicate any scroll gesture is active (prevents taps)
    private var isScrolling = false 
    
    // Initial values captured at the start of the gesture (ACTION_DOWN)
    private var startVolume = 0
    private var startBrightness = 0f
    private var startPosition = 0L
    private var startSpeed = 1f
    private var gestureStartY = 0f
    private var gestureStartX = 0f
    private var duration = 0L

    override fun onDown(e: MotionEvent): Boolean {
        // End any previous gesture cleanup if needed
        if (hasNotifiedGestureStart) {
            callback.onSwipeGestureEnded()
            hasNotifiedGestureStart = false
        }
        
        // Reset all state flags for the new gesture sequence
        isVolume = false
        isBrightness = false
        isSeek = false
        isSpeed = false
        isScrolling = false
        
        // Capture start coordinates
        gestureStartY = e.y
        gestureStartX = e.x
        return true
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        // Master toggle check
        if (!gesturesEnabled) return false

        // Block all gestures when:
        // - touch started in edge zone (system gesture area)
        // - controls were visible when gesture started (use controlsVisibleAtGestureStart to lock this for entire gesture)
        // - player is in portrait or minimized mode
        if (e1 == null || callback.isPortrait || !callback.isMaximized || callback.isEdgeSwipe || callback.controlsVisibleAtGestureStart) return false
        
        val width = callback.playerWidth.toFloat()
        val height = callback.playerHeight.toFloat()
        
        // If we haven't locked onto a specific gesture type yet, determine it now
        if (!isVolume && !isBrightness && !isSeek && !isSpeed) {
             if (abs(distanceY) > abs(distanceX)) {
                 // Vertical Swipes (Volume / Brightness)
                 // Split left/right halves
                 if (e1.x < width / 2) {
                     isBrightness = true
                     startBrightness = helper.getCurrentBrightness(callback.windowAttributes)
                 } else {
                     isVolume = true
                     val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                     startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                 }
             } else {
                 // Horizontal Swipes (VoD only: Seek / Speed)
                 if (callback.getPlayerVideoType() != PlayerFragment.STREAM) {
                     // Split top/bottom using configured zone split ratio
                     if (e1.y < height * zoneSplit) {
                         // Top Zone -> Seek
                         isSeek = true
                         startPosition = callback.getCurrentPosition() ?: 0L
                         duration = callback.getDuration()
                     } else {
                         // Bottom Zone -> Speed
                         isSpeed = true
                         startSpeed = callback.getCurrentSpeed() ?: 1f
                     }
                 }
             }
             
             // Notify that we've claimed this gesture (prevents minimize gesture from triggering)
             if (isVolume || isBrightness || isSeek || isSpeed) {
                 isScrolling = true  // Mark that we're in a scroll gesture
                 if (!hasNotifiedGestureStart) {
                     callback.onSwipeGestureStarted()
                     performHapticFeedback() // Feedback on gesture start
                     hasNotifiedGestureStart = true
                 }
             }
        }

        val percentY = (gestureStartY - e2.y) / height
        val percentX = (e2.x - gestureStartX) / width // Left to Right is positive
        
        val feedback = callback.getGestureFeedbackView()
        val container = feedback.findViewById<LinearLayout>(R.id.feedbackContainer)
        val icon = feedback.findViewById<ImageView>(R.id.feedbackIcon)
        val progress = feedback.findViewById<LinearProgressIndicator>(R.id.feedbackProgress)
        val text = feedback.findViewById<TextView>(R.id.feedbackText)

        if (isBrightness) {
            progress.visibility = View.VISIBLE
            text.maxLines = 1
            container.minimumWidth = dpToPx(148)
            container.layoutParams = container.layoutParams.apply { this.width = ViewGroup.LayoutParams.WRAP_CONTENT }
            val rawBrightness = startBrightness + percentY
            val isAuto = rawBrightness < 0.05f
            val newBrightness = if (isAuto) -1f else rawBrightness.coerceIn(0.05f, 1.0f)
            
            val lp = callback.windowAttributes
            lp.screenBrightness = newBrightness
            callback.setWindowAttributes(lp)
            
            icon.setImageResource(R.drawable.ic_brightness_medium_black_24dp)

            if (isAuto) {
                progress.progress = 0
                text.text = labeledText(PlayerGestureFeedbackKind.BRIGHTNESS, "Auto")
            } else {
                progress.progress = (newBrightness * 100).toInt()
                text.text = labeledText(PlayerGestureFeedbackKind.BRIGHTNESS, "%d%%".format((newBrightness * 100).toInt()))
            }
            presentFeedback(feedback, container, PlayerGestureFeedbackKind.BRIGHTNESS)
            return true
        }
        
        if (isVolume) {
            progress.visibility = View.VISIBLE
            text.maxLines = 1
            container.minimumWidth = dpToPx(148)
            container.layoutParams = container.layoutParams.apply { this.width = ViewGroup.LayoutParams.WRAP_CONTENT }
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val newVolume = (startVolume + (percentY * maxVolume)).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
            
            icon.setImageResource(if (newVolume == 0) R.drawable.baseline_volume_off_black_24 else R.drawable.baseline_volume_up_black_24)

            progress.progress = ((newVolume.toFloat() / maxVolume.toFloat()) * 100).toInt()
            text.text = labeledText(
                PlayerGestureFeedbackKind.DEVICE_VOLUME,
                "%d".format(((newVolume.toFloat() / maxVolume.toFloat()) * 100).toInt()),
            )
            presentFeedback(feedback, container, PlayerGestureFeedbackKind.DEVICE_VOLUME)
            return true
        }

        if (isSeek) {
            if (duration > 0) {
                progress.visibility = View.GONE
                text.maxLines = 1
                container.minimumWidth = 0
                container.layoutParams = container.layoutParams.apply { this.width = ViewGroup.LayoutParams.WRAP_CONTENT }
                val newPosition = helper.calculateResponsiveSeekPosition(
                    currentPosition = startPosition,
                    duration = duration,
                    gestureDelta = e2.x - gestureStartX,
                    screenWidth = callback.playerWidth,
                    sensitivity = sensitivity
                )
                val seekAmount = newPosition - startPosition
                callback.seek(newPosition)

                icon.setImageResource(if (seekAmount > 0) R.drawable.baseline_add_black_24 else R.drawable.baseline_remove_black_24)

                text.text = labeledText(
                    PlayerGestureFeedbackKind.SEEK,
                    "${helper.formatDuration(newPosition)} / ${helper.formatDuration(duration)}",
                )
                presentFeedback(feedback, container, PlayerGestureFeedbackKind.SEEK)
            }
            return true
        }

        if (isSpeed) {
            progress.visibility = View.GONE
            text.maxLines = 1
            container.minimumWidth = 0
            container.layoutParams = container.layoutParams.apply { this.width = ViewGroup.LayoutParams.WRAP_CONTENT }
            // Speed logic: 0.05x increments
            // Swipe full width = 1.0x change, adjusted by sensitivity
            val speedChange = (percentX * 2.0f * sensitivity)
            // Round to nearest 0.05
            var newSpeed = startSpeed + speedChange
            newSpeed = (Math.round(newSpeed * 20) / 20.0f).coerceIn(0.25f, 4.0f)
            
            if (newSpeed != callback.getCurrentSpeed()) {
                callback.setPlaybackSpeed(newSpeed)
            }

            icon.setImageResource(R.drawable.baseline_speed_black_24)

            text.text = labeledText(PlayerGestureFeedbackKind.PLAYBACK_SPEED, "%.2fx".format(newSpeed))
            presentFeedback(feedback, container, PlayerGestureFeedbackKind.PLAYBACK_SPEED)
            return true
        }

        return false
    }

    private fun dpToPx(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun surfaceClass(): PlayerSurfaceClass {
        return PlayerSurfacePolicy.classify(callback.playerWidth, context.resources.displayMetrics.density)
    }

    private fun labeledText(kind: PlayerGestureFeedbackKind, value: String): String {
        return if (surfaceClass() == PlayerSurfaceClass.LARGE) {
            val labelRes = when (kind) {
                PlayerGestureFeedbackKind.BRIGHTNESS -> R.string.gesture_feedback_brightness
                PlayerGestureFeedbackKind.DEVICE_VOLUME -> R.string.gesture_feedback_device_volume
                PlayerGestureFeedbackKind.SEEK -> R.string.gesture_feedback_seek
                PlayerGestureFeedbackKind.PLAYBACK_SPEED -> R.string.gesture_feedback_playback_speed
            }
            context.getString(labelRes) + " \u00B7 " + value
        } else {
            value
        }
    }

    private fun presentFeedback(feedback: View, container: LinearLayout, kind: PlayerGestureFeedbackKind) {
        val insets = callback.playerGestureInsets
        PlayerSurfacePolicy.applyPlacement(
            feedbackRoot = feedback,
            container = container,
            placement = PlayerSurfacePolicy.placementFor(
                kind = kind,
                surfaceClass = surfaceClass(),
                density = context.resources.displayMetrics.density,
                insetStartPx = insets?.left ?: 0,
                insetEndPx = insets?.right ?: 0,
            ),
        )
        feedback.animate().cancel()
        feedback.alpha = 1f
        feedback.visibility = View.VISIBLE
        feedback.removeCallbacks(callback.getHideGestureRunnable())
        feedback.postDelayed(callback.getHideGestureRunnable(), PlayerSurfacePolicy.FEEDBACK_HOLD_MS)
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        // Don't trigger tap if a scroll gesture occurred
        if (isScrolling) return false
        
        return if (!doubleTapEnabled || callback.isPortrait) {
            handleSingleTap()
            true
        } else {
            false
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        // Don't trigger tap if a scroll gesture occurred
        if (isScrolling) return false
        
        return if (doubleTapEnabled && !callback.isPortrait) {
            handleSingleTap()
            true
        } else {
            false
        }
    }

    private fun handleSingleTap() {
        val visible = callback.isControlsVisible
        if (visible) {
            if (callback.isControllerHideOnTouch()) {
                callback.hideController()
            }
        } else {
            callback.showController()
        }
        if (!visible) {
            callback.updateProgress()
        }
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        return if (doubleTapEnabled && !callback.isPortrait && callback.isMaximized) {
            callback.cycleChatMode()
            performHapticFeedback() // Feedback for double tap action
            true
        } else {
            false
        }
    }

    private fun performHapticFeedback() {
        if (hapticEnabled) {
            // Try to use the feedback view to perform haptic feedback
            try {
                val view = callback.getGestureFeedbackView()
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            } catch (e: Exception) {
                // Ignore if view not available or haptics failed
            }
        }
    }
}
