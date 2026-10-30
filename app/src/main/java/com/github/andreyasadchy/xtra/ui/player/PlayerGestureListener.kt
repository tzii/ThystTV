package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.media.AudioManager
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
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
    fun swipeChat(open: Boolean) {}
    fun getGestureFeedbackView(): View
    fun getHideGestureRunnable(): Runnable
    fun isControllerHideOnTouch(): BooleanFreeVideos.seekLabel(
                    helper.formatDuration(newPosition),
                    helper.formatDuration(duration),
                    SpoilerFreeVideos.hidePlayerProgress(
                        context.prefs().getBoolean(C.PLAYER_SPOILER_FREE_VODS, false),
                        callback.getPlayerVideoType(),
                    ),
                )
                presentFeedback(
                    kind = PlayerGestureFeedbackKind.SEEK,
                    iconRes = if (seekAmount > 0) R.drawable.baseline_add_black_24 else R.drawable.baseline_remove_black_24,
                    level = null,
                    visibleText = labeledText(PlayerGestureFeedbackKind.SEEK, valueText),
                    a11yText = labeledA11y(PlayerGestureFeedbackKind.SEEK, valueText),
                )
            }
            return true
        }

        if (isSpeed) {
            // Speed logic: 0.05x increments
            // Swipe full width = 1.0x change, adjusted by sensitivity
            val speedChange = (percentX * 2.0f * sensitivity)
            // Round to nearest 0.05
            var newSpeed = startSpeed + speedChange
            newSpeed = (Math.round(newSpeed * 20) / 20.0f).coerceIn(0.25f, 4.0f)

            if (newSpeed != callback.getCurrentSpeed()) {
                callback.setPlaybackSpeed(newSpeed)
            }

            val valueText = "%.2fx".format(newSpeed)
            presentFeedback(
                kind = PlayerGestureFeedbackKind.PLAYBACK_SPEED,
                iconRes = R.drawable.baseline_speed_black_24,
                level = null,
                visibleText = labeledText(PlayerGestureFeedbackKind.PLAYBACK_SPEED, valueText),
                a11yText = labeledA11y(PlayerGestureFeedbackKind.PLAYBACK_SPEED, valueText),
            )
            return true
        }

        return false
    }

    private fun surfaceClass(): PlayerSurfaceClass {
        return PlayerSurfacePolicy.classify(callback.playerWidth, context.resources.displayMetrics.density)
    }

    private fun labelRes(kind: PlayerGestureFeedbackKind): Int {
        return when (kind) {
            PlayerGestureFeedbackKind.BRIGHTNESS -> R.string.gesture_feedback_brightness
            PlayerGestureFeedbackKind.DEVICE_VOLUME -> R.string.gesture_feedback_device_volume
            PlayerGestureFeedbackKind.SEEK -> R.string.gesture_feedback_seek
            PlayerGestureFeedbackKind.PLAYBACK_SPEED -> R.string.gesture_feedback_playback_speed
            PlayerGestureFeedbackKind.PINCH -> R.string.gesture_feedback_pinch
        }
    }

    private fun labeledText(kind: PlayerGestureFeedbackKind, value: String): String {
        return if (surfaceClass() == PlayerSurfaceClass.LARGE) {
            context.getString(labelRes(kind)) + " \u00B7 " + value
        } else {
            value
        }
    }

    /**
     * Accessibility text always carries the control name; visible text on
     * large surfaces does too, while the vertical edge pill drops visible text
     * entirely and relies on this description.
     */
    private fun labeledA11y(kind: PlayerGestureFeedbackKind, value: String): String {
        return context.getString(labelRes(kind)) + " \u00B7 " + value
    }

    private fun presentFeedback(
        kind: PlayerGestureFeedbackKind,
        iconRes: Int,
        level: Int?,
        visibleText: String,
        a11yText: String,
    ) {
        PlayerSurfacePolicy.presentFeedback(
            context = context,
            feedbackRoot = callback.getGestureFeedbackView(),
            kind = kind,
            surfaceWidthPx = callback.playerWidth,
            surfaceHeightPx = callback.playerHeight,
            insets = callback.playerGestureInsets,
            presentation = PlayerGestureFeedbackState.presentation(
                kind = kind,
                surfaceClass = surfaceClass(),
                level = level,
                text = visibleText,
            ),
            iconRes = iconRes,
            a11yText = a11yText,
            hideRunnable = callback.getHideGestureRunnable(),
        )
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
            if (callback.claimDoubleTapChat()) {
                callback.cycleChatMode()
                performHapticFeedback() // Feedback for double tap action
            }
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
