package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.media.AudioManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.abs

interface PlayerGestureCallback {
    val isPortrait: Boolean
    val isMaximized: Boolean
    val isControlsVisible: Boolean
    val controlsVisibleAtGestureStart: Boolean
    val screenWidth: Int
    val screenHeight: Int
    val windowAttributes: android.view.WindowManager.LayoutParams
    val isEdgeSwipe: Boolean
    
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
    private val doubleTapEnabled: Boolean
) : GestureDetector.SimpleOnGestureListener() {

    private val helper = PlayerGestureHelper(context)
    private var isVolume = false
    private var isBrightness = false
    private var isSeek = false
    private var isSpeed = false
    private var hasNotifiedGestureStart = false
    private var isScrolling = false  // Track if a scroll gesture is in progress
    private var startVolume = 0
    private var startBrightness = 0f
    private var startPosition = 0L
    private var startSpeed = 1f
    private var gestureStartY = 0f
    private var gestureStartX = 0f
    private var duration = 0L

    override fun onDown(e: MotionEvent): Boolean {
        // End any previous gesture
        if (hasNotifiedGestureStart) {
            callback.onSwipeGestureEnded()
            hasNotifiedGestureStart = false
        }
        isVolume = false
        isBrightness = false
        isSeek = false
        isSpeed = false
        isScrolling = false  // Reset scrolling flag on new gesture
        gestureStartY = e.y
        gestureStartX = e.x
        return true
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        // Block all gestures when:
        // - touch started in edge zone (system gesture area)
        // - controls were visible when gesture started (use controlsVisibleAtGestureStart to lock this for entire gesture)
        if (e1 == null || callback.isPortrait || !callback.isMaximized || callback.isEdgeSwipe || callback.controlsVisibleAtGestureStart) return false
        
        val width = callback.screenWidth.toFloat()
        val height = callback.screenHeight.toFloat()
        
        if (!isVolume && !isBrightness && !isSeek && !isSpeed) {
             if (abs(distanceY) > abs(distanceX)) {
                 // Vertical Swipes
                 if (e1.x < width / 2) {
                     isBrightness = true
                     startBrightness = callback.windowAttributes.screenBrightness
                     if (startBrightness < 0) startBrightness = 0.5f // Default fallback
                 } else {
                     isVolume = true
                     val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                     startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                 }
             } else {
                 // Horizontal Swipes (VoD only)
                 if (callback.getPlayerVideoType() != PlayerFragment.STREAM) {
                     if (e1.y < height / 2) {
                         // Top 50% -> Seek
                         isSeek = true
                         startPosition = callback.getCurrentPosition() ?: 0L
                         duration = callback.getDuration()
                     } else {
                         // Bottom 50% -> Speed
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
                     hasNotifiedGestureStart = true
                 }
             }
        }

        val percentY = (gestureStartY - e2.y) / height
        val percentX = (e2.x - gestureStartX) / width // Left to Right is positive
        
        val feedback = callback.getGestureFeedbackView()
        val icon = feedback.findViewById<ImageView>(R.id.feedbackIcon)
        val progress = feedback.findViewById<LinearProgressIndicator>(R.id.feedbackProgress)
        val text = feedback.findViewById<TextView>(R.id.feedbackText)

        if (isBrightness) {
            val rawBrightness = startBrightness + percentY
            val isAuto = rawBrightness < 0.05f
            val newBrightness = if (isAuto) -1f else rawBrightness.coerceIn(0.05f, 1.0f)
            
            val lp = callback.windowAttributes
            lp.screenBrightness = newBrightness
            callback.setWindowAttributes(lp)
            
            icon.setImageResource(R.drawable.ic_brightness_medium_black_24dp)
            feedback.visible()
            feedback.removeCallbacks(callback.getHideGestureRunnable())
            feedback.postDelayed(callback.getHideGestureRunnable(), 800)
            
            if (isAuto) {
                progress.progress = 0
                text.text = "Auto"
            } else {
                progress.progress = (newBrightness * 100).toInt()
                text.text = "%d%%".format((newBrightness * 100).toInt())
            }
            return true
        }
        
        if (isVolume) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val newVolume = (startVolume + (percentY * maxVolume)).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
            
            icon.setImageResource(if (newVolume == 0) R.drawable.baseline_volume_off_black_24 else R.drawable.baseline_volume_up_black_24)
            feedback.visible()
            feedback.removeCallbacks(callback.getHideGestureRunnable())
            feedback.postDelayed(callback.getHideGestureRunnable(), 800)
            
            progress.progress = ((newVolume.toFloat() / maxVolume.toFloat()) * 100).toInt()
            text.text = "%d".format(((newVolume.toFloat() / maxVolume.toFloat()) * 100).toInt())
            return true
        }

        if (isSeek) {
            if (duration > 0) {
                // Seek logic: 90 seconds per screen width swipe
                val seekAmount = (percentX * 90000).toLong() 
                val newPosition = (startPosition + seekAmount).coerceIn(0, duration)
                callback.seek(newPosition)

                icon.setImageResource(if (seekAmount > 0) R.drawable.baseline_add_black_24 else R.drawable.baseline_remove_black_24)
                
                feedback.visible()
                feedback.removeCallbacks(callback.getHideGestureRunnable())
                feedback.postDelayed(callback.getHideGestureRunnable(), 800)
                
                progress.progress = ((newPosition.toFloat() / duration.toFloat()) * 100).toInt()
                text.text = "${helper.formatDuration(newPosition)} / ${helper.formatDuration(duration)}"
                // Adjust text width for long duration strings if needed
                text.layoutParams.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            }
            return true
        }

        if (isSpeed) {
            // Speed logic: 0.05x increments
            // Swipe full width = 1.0x change?
            val speedChange = (percentX * 2.0f) // Sensitivity
            // Round to nearest 0.05
            var newSpeed = startSpeed + speedChange
            newSpeed = (Math.round(newSpeed * 20) / 20.0f).coerceIn(0.25f, 4.0f)
            
            if (newSpeed != callback.getCurrentSpeed()) {
                callback.setPlaybackSpeed(newSpeed)
            }

            icon.setImageResource(R.drawable.baseline_speed_black_24)
            feedback.visible()
            feedback.removeCallbacks(callback.getHideGestureRunnable())
            feedback.postDelayed(callback.getHideGestureRunnable(), 800)
            
            // Map 0.25-4.0 to 0-100 progress
            val progressVal = ((newSpeed - 0.25f) / (4.0f - 0.25f) * 100).toInt()
            progress.progress = progressVal
            text.text = "%.2fx".format(newSpeed)
            return true
        }

        return false
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
            true
        } else {
            false
        }
    }
}
