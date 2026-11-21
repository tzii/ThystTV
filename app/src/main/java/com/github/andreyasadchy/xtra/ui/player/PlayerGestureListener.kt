package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.media.AudioManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import java.util.Locale

interface PlayerGestureCallback {
    val isPortrait: Boolean
    val isMaximized: Boolean
    val isControlsVisible: Boolean
    val controlsVisibleAtGestureStart: Boolean
    val playerWidth: Int
    val playerHeight: Int
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

    // Gesture arbitration: the single owner of the touch sequence
    fun claimSingleFingerGesture(owner: PlayerGestureArbiter.Owner): Boolean
    fun claimDoubleTapChat(): Boolean

    // Sideways swipe that shows or hides chat (see PlayerChatSwipePolicy)
    fun startsChatSwipe(startX: Float): Boolean
    /** Travel in pixels after which a chat swipe that started at [startX] acts. */
    fun chatSwipeTriggerPx(startX: Float): Float
    /** Shows or hides chat for a swipe of [dx] pixels; returns true when chat changed. */
    fun swipeChat(dx: Float): Boolean

    // One-finger pan of a picture magnified past Fill (extended zoom)
    /** Whether drags move the magnified picture instead of the usual gestures. */
    fun canPanMagnified(): Boolean
    /** Moves the magnified picture by [dx], [dy] pixels without passing its edges. */
    fun panMagnified(dx: Float, dy: Float)
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
    private var isChatSwipe = false
    private var chatSwipeDone = false
    private var isPan = false
    private var panFocusX = 0f
    private var panFocusY = 0f
    private var panPointers = 0

    // Flag to ensure we only notify the callback once per gesture
    private var hasNotifiedGestureStart = false
    
    // General flag to indicate any scroll gesture is active (prevents taps)
    private var isScrolling = false 
    
    // Initial values captured at the start of the gesture (ACTION_DOWN)
    private var startVolumeLevel = 0f
    private var startBrightness = 0f
    private var startPosition = 0L
    private var startSpeed = 1f
    private var gestureStartY = 0f
    private var gestureStartX = 0f
    private var duration = 0L

    // Brightness and volume measure travel from where the swipe locked, so
    // the first update shows the starting value instead of jumping by the
    // touch slop.
    private var levelAnchorY = 0f

    // Horizontal scrubs (seek, speed) measure travel from this anchor, the
    // near edge of the start detent, rather than from touch-down.
    private var scrubAnchorX = 0f
    private var lastSeekPosition = 0L

    // Spoiler-free seeks use a track centered on the start instead of the
    // whole video. There the handle stays where a seek reached either end of
    // the video while the finger pushes on, since the value cannot move.
    private var pinnedSeekFraction: Float? = null
    private var seekForward = true
    // Whether this seek has left its start, so coming back reads as a cancel.
    private var seekMoved = false
    private var hideSeekProgress = false
    private var hapticTracker: PlayerScrubHapticTracker? = null
    private var lastStepHapticAt = 0L
    private var feedbackStyle = PlayerGestureFeedbackStyle.DEFAULT

    // What a brightness or volume swipe last applied. The window and the audio
    // service are only called when the value changes: each call is a system
    // round trip (a window relayout for brightness) that would otherwise run
    // on every touch update.
    private var appliedBrightnessPercent: Int? = null
    private var brightnessApplied = false
    private var appliedVolumeIndex = 0
    private var maxVolume = 0
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Where the last volume swipe left the slider and the step it set, so
    // the next swipe continues from the same spot (see PlayerLevelGesturePolicy.volumeStart).
    private var lastVolumeLevel: Float? = null
    private var lastVolumeIndex = -1

    private val density get() = context.resources.displayMetrics.density
    private val horizontalLockPx get() = PlayerScrubPolicy.HORIZONTAL_LOCK_DP * density
    private val startDetentPx get() = PlayerScrubPolicy.START_DETENT_DP * density

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
        isChatSwipe = false
        chatSwipeDone = false
        isPan = false
        panPointers = 0
        isScrolling = false
        hapticTracker = null
        lastStepHapticAt = 0L
        // Read per gesture, so a style changed in settings applies to an
        // open player at its next gesture.
        feedbackStyle = PlayerGestureFeedbackStyle.fromPref(
            context.prefs().getString(C.PLAYER_GESTURE_FEEDBACK_STYLE, null)
        )

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
        if (!isVolume && !isBrightness && !isSeek && !isSpeed && !isChatSwipe && !isPan) {
             // Measured from touch-down, so an early sideways wobble cannot
             // decide a swipe that then turns vertical.
             val dx = e2.x - gestureStartX
             val dy = e2.y - gestureStartY
             val direction = PlayerScrubPolicy.classify(dx, dy, horizontalLockPx)
             if (callback.canPanMagnified()) {
                 // A magnified picture moves with the finger in every direction.
                 isPan = true
             } else if (direction == PlayerScrubPolicy.Direction.VERTICAL) {
                 // Vertical Swipes (Volume / Brightness)
                 // Split left/right halves
                 levelAnchorY = e2.y
                 if (e1.x < width / 2) {
                     isBrightness = true
                     startBrightness = helper.getCurrentBrightness(callback.windowAttributes)
                     brightnessApplied = false
                     hapticTracker = PlayerScrubHapticTracker(
                         start = PlayerLevelGesturePolicy.percent(startBrightness).toLong(),
                         min = 0L,
                         max = 100L,
                         step = PlayerScrubPolicy.BRIGHTNESS_TICK_PERCENT,
                     )
                 } else {
                     isVolume = true
                     maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                     val startIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                     appliedVolumeIndex = startIndex
                     startVolumeLevel = PlayerLevelGesturePolicy.volumeStart(startIndex, maxVolume, lastVolumeLevel, lastVolumeIndex)
                     // Ticks on each real device step, not on the shown percent.
                     hapticTracker = PlayerScrubHapticTracker(
                         start = startIndex.toLong(),
                         min = 0L,
                         max = maxVolume.toLong(),
                         step = 1L,
                     )
                 }
             } else if (callback.startsChatSwipe(e1.x)) {
                 // Wait for a horizontal lock, matching seek/speed, so a vertical
                 // swipe with an early sideways wobble stays undecided and can still
                 // become brightness or volume. A VoD strip swipe near a narrow edge
                 // caps the lock at its shorter trigger so hiding toward the edge
                 // remains reachable.
                 val chatLockPx = minOf(horizontalLockPx, callback.chatSwipeTriggerPx(e1.x))
                 if (PlayerScrubPolicy.classify(dx, dy, chatLockPx) == PlayerScrubPolicy.Direction.HORIZONTAL) {
                     isChatSwipe = true
                 }
             } else if (direction == PlayerScrubPolicy.Direction.HORIZONTAL && callback.getPlayerVideoType() != PlayerFragment.STREAM) {
                 // Seekable media only, once the drag is clearly sideways:
                 // split top/bottom using the configured zone split ratio.
                 scrubAnchorX = PlayerScrubPolicy.anchorX(e2.x, dx, startDetentPx)
                 when (PlayerGestureZonePolicy.horizontalZone(e1.y, height, zoneSplit)) {
                     PlayerHorizontalGestureZone.PLAYBACK_SPEED -> {
                         // Upper zone -> Playback speed
                         isSpeed = true
                         startSpeed = callback.getCurrentSpeed() ?: 1f
                         hapticTracker = PlayerScrubHapticTracker(
                             start = speedHundredths(snapSpeed(startSpeed)),
                             min = speedHundredths(PlayerScrubPolicy.SPEED_MIN),
                             max = speedHundredths(PlayerScrubPolicy.SPEED_MAX),
                             step = PlayerScrubPolicy.SPEED_TICK_HUNDREDTHS,
                             startDetent = true,
                         )
                     }
                     PlayerHorizontalGestureZone.SEEK -> {
                         // Lower zone -> Seek
                         isSeek = true
                         startPosition = callback.getCurrentPosition() ?: 0L
                         duration = callback.getDuration()
                         lastSeekPosition = startPosition
                         pinnedSeekFraction = null
                         seekForward = dx >= 0f
                         seekMoved = false
                         hideSeekProgress = SpoilerFreeVideos.hidePlayerProgress(
                             context.prefs().getBoolean(C.PLAYER_SPOILER_FREE_VODS, false),
                             callback.getPlayerVideoType(),
                         )
                         if (duration > 0) {
                             hapticTracker = PlayerScrubHapticTracker(
                                 start = startPosition,
                                 min = 0L,
                                 max = duration,
                                 step = PlayerScrubPolicy.seekTickStepMs(0f, callback.playerWidth / density, duration, sensitivity),
                                 origin = startPosition,
                                 startDetent = true,
                             )
                         }
                     }
                 }
             }
             
             // Notify that we've claimed this gesture (prevents minimize gesture from triggering)
             if (isVolume || isBrightness || isSeek || isSpeed || isChatSwipe || isPan) {
                val owner = when {
                    isPan -> PlayerGestureArbiter.Owner.MAGNIFIED_PAN
                    isBrightness -> PlayerGestureArbiter.Owner.BRIGHTNESS
                    isVolume -> PlayerGestureArbiter.Owner.DEVICE_VOLUME
                    isSeek -> PlayerGestureArbiter.Owner.SEEK
                    isChatSwipe -> PlayerGestureArbiter.Owner.CHAT_SWIPE
                    else -> PlayerGestureArbiter.Owner.PLAYBACK_SPEED
                }
                if (!callback.claimSingleFingerGesture(owner)) {
                    // A pinch candidate owns this sequence; abandon the claim.
                    isVolume = false
                    isBrightness = false
                    isSeek = false
                    isSpeed = false
                    isChatSwipe = false
                    isPan = false
                    return false
                }
                isScrolling = true  // Mark that we're in a scroll gesture
                if (!hasNotifiedGestureStart) {
                    callback.onSwipeGestureStarted()
                    // A chat swipe gives its feedback when it acts; a pan needs none.
                    if (!isChatSwipe && !isPan) haptic(PlayerGestureHaptic.START)
                    hasNotifiedGestureStart = true
                }
            }
        }

        if (isPan) {
            // The picture follows the fingers' midpoint. The detector never
            // sees a second finger land or lift, so its distances would jump
            // then; re-anchor whenever the finger count changes instead.
            val pointers = e2.pointerCount
            var focusX = 0f
            var focusY = 0f
            for (i in 0 until pointers) {
                focusX += e2.getX(i)
                focusY += e2.getY(i)
            }
            focusX /= pointers
            focusY /= pointers
            when (panPointers) {
                // Scroll distances run opposite to the finger.
                0 -> callback.panMagnified(-distanceX, -distanceY)
                pointers -> callback.panMagnified(focusX - panFocusX, focusY - panFocusY)
            }
            panFocusX = focusX
            panFocusY = focusY
            panPointers = pointers
            return true
        }

        if (isChatSwipe) {
            // Acts once per sequence, when the swipe passes the threshold.
            if (!chatSwipeDone) {
                val dx = e2.x - gestureStartX
                val dy = e2.y - gestureStartY
                val trigger = callback.chatSwipeTriggerPx(gestureStartX)
                if (PlayerChatSwipePolicy.reachedTrigger(dx, dy, trigger)) {
                    chatSwipeDone = true
                    if (callback.swipeChat(dx)) {
                        haptic(PlayerGestureHaptic.START)
                    }
                }
            }
            return true
        }

        val percentY = (levelAnchorY - e2.y) / height

        if (isBrightness) {
            val level = PlayerLevelGesturePolicy.level(startBrightness, percentY)
            val brightness = PlayerLevelGesturePolicy.brightnessPercent(level)
            val isAuto = brightness == null
            // Whole percents, applied only when the percent changes.
            if (!brightnessApplied || brightness != appliedBrightnessPercent) {
                val lp = callback.windowAttributes
                lp.screenBrightness = brightness?.let { it / 100f } ?: -1f
                callback.setWindowAttributes(lp)
                appliedBrightnessPercent = brightness
                brightnessApplied = true
            }

            val percent = brightness ?: 0
            haptic(hapticTracker?.update(percent.toLong()), e2.eventTime)
            val valueText = if (isAuto) "Auto" else "%d%%".format(percent)
            presentFeedback(
                kind = PlayerGestureFeedbackKind.BRIGHTNESS,
                iconRes = PlayerGestureFeedbackState.brightnessIcon(percent, isAuto),
                level = percent,
                visibleText = valueText,
                a11yText = if (isAuto) {
                    context.getString(R.string.gesture_feedback_auto_brightness)
                } else {
                    labeledA11y(PlayerGestureFeedbackKind.BRIGHTNESS, valueText)
                },
                // The slider shows no number and follows the finger between
                // percents; the centered value keeps its icon so it cannot be
                // mistaken for volume.
                reading = PlayerGestureReading(
                    fraction = if (isAuto) 0f else level.coerceAtLeast(PlayerLevelGesturePolicy.BRIGHTNESS_MIN_PERCENT / 100f),
                    value = valueText,
                    valueIcon = true,
                ),
            )
            return true
        }

        if (isVolume) {
            // The shown level moves one percent at a time with the finger;
            // the device follows at its own step size, and only when its
            // step changes.
            val level = PlayerLevelGesturePolicy.level(startVolumeLevel, percentY)
            val newVolume = PlayerLevelGesturePolicy.volumeIndex(level, maxVolume)
            if (newVolume != appliedVolumeIndex) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                appliedVolumeIndex = newVolume
            }
            lastVolumeLevel = level
            lastVolumeIndex = newVolume
            haptic(hapticTracker?.update(newVolume.toLong()), e2.eventTime)

            val percent = PlayerLevelGesturePolicy.percent(level)
            val valueText = "%d%%".format(percent)
            presentFeedback(
                kind = PlayerGestureFeedbackKind.DEVICE_VOLUME,
                iconRes = PlayerGestureFeedbackState.volumeIcon(percent),
                level = percent,
                visibleText = valueText,
                a11yText = labeledA11y(
                    PlayerGestureFeedbackKind.DEVICE_VOLUME,
                    if (newVolume == 0) context.getString(R.string.gesture_feedback_muted) else valueText,
                ),
                reading = PlayerGestureReading(fraction = level, value = valueText, valueIcon = true),
            )
            return true
        }

        if (isSeek) {
            if (duration > 0) {
                // Dragging back into the start detent returns exactly to the
                // starting position, so a seek can be undone by feel.
                val travel = PlayerScrubPolicy.detentTravel(e2.x - scrubAnchorX, startDetentPx)
                val widthDp = callback.playerWidth / density
                val offset = PlayerScrubPolicy.seekOffsetMs(travel / density, widthDp, duration, sensitivity)
                val newPosition = (startPosition + offset).coerceIn(0L, duration)
                val seekAmount = newPosition - startPosition
                // Seek only when the target moves: holding still, or pushing
                // past either end of the video, issues no repeated seeks.
                if (newPosition != lastSeekPosition) {
                    callback.seek(newPosition)
                    lastSeekPosition = newPosition
                }
                if (seekAmount != 0L) {
                    seekForward = seekAmount > 0
                    seekMoved = true
                }
                haptic(
                    hapticTracker?.update(
                        newPosition,
                        PlayerScrubPolicy.seekTickStepMs(travel / density, widthDp, duration, sensitivity),
                    ),
                    e2.eventTime,
                )

                val targetTime = helper.formatDuration(newPosition)
                // The description keeps the length unless spoiler-free VODs hide it.
                val target = SpoilerFreeVideos.seekLabel(
                    targetTime,
                    helper.formatDuration(duration),
                    hideSeekProgress,
                )
                val change = PlayerScrubPolicy.signedDuration(seekAmount, helper::formatDuration)
                // The track is the whole video, like the seek bar, with a dot
                // where the seek started. Spoiler-free VODs center the start
                // instead, so the track never shows where the video is or how
                // long it runs.
                val (fraction, startFraction) = if (hideSeekProgress) {
                    val relative = PlayerScrubPolicy.relativeFraction(travel, callback.playerWidth)
                    pinnedSeekFraction = PlayerScrubPolicy.pinFraction(
                        pinned = pinnedSeekFraction,
                        fraction = relative,
                        atLimit = newPosition <= 0L || newPosition >= duration,
                    )
                    (pinnedSeekFraction ?: relative) to 0.5f
                } else {
                    newPosition.toFloat() / duration to startPosition.toFloat() / duration
                }
                // Dragging back to the start undoes the seek; say so rather
                // than showing an unsigned 0:00.
                val backAtStart = seekMoved && seekAmount == 0L
                val cancel = context.getString(android.R.string.cancel)
                presentFeedback(
                    kind = PlayerGestureFeedbackKind.SEEK,
                    iconRes = when {
                        backAtStart -> R.drawable.ic_gesture_undo
                        seekForward -> R.drawable.ic_gesture_seek_forward
                        else -> R.drawable.ic_gesture_seek_back
                    },
                    level = null,
                    visibleText = labeledText(PlayerGestureFeedbackKind.SEEK, target),
                    a11yText = labeledA11y(PlayerGestureFeedbackKind.SEEK, if (backAtStart) "$cancel · $target" else "$change · $target"),
                    // The change is the value. Under it, both styles show the
                    // target time and the video's length (only the target time
                    // when spoiler-free VODs hide the length): a caption under
                    // the slider, the small line under the centered number.
                    reading = if (backAtStart) {
                        PlayerGestureReading(fraction, value = null, startFraction = startFraction, detail = cancel, valueIcon = true, caption = target)
                    } else {
                        PlayerGestureReading(fraction, value = change, startFraction = startFraction, detail = target, caption = target)
                    },
                )
            }
            return true
        }

        if (isSpeed) {
            // Speed logic: 0.05x increments
            // Swipe full width = 1.0x change, adjusted by sensitivity
            val travel = PlayerScrubPolicy.detentTravel(e2.x - scrubAnchorX, startDetentPx)
            val speedChange = (travel / width) * 2.0f * sensitivity
            val newSpeed = snapSpeed(startSpeed + speedChange)

            if (newSpeed != callback.getCurrentSpeed()) {
                callback.setPlaybackSpeed(newSpeed)
            }
            haptic(hapticTracker?.update(speedHundredths(newSpeed)), e2.eventTime)

            // Same format as the player's speed button and menu.
            val valueText = String.format(Locale.US, "%.2fx", newSpeed)
            presentFeedback(
                kind = PlayerGestureFeedbackKind.PLAYBACK_SPEED,
                iconRes = R.drawable.baseline_speed_black_24,
                level = null,
                visibleText = labeledText(PlayerGestureFeedbackKind.PLAYBACK_SPEED, valueText),
                a11yText = labeledA11y(PlayerGestureFeedbackKind.PLAYBACK_SPEED, valueText),
                reading = PlayerGestureReading(
                    fraction = PlayerScrubPolicy.speedFraction(newSpeed),
                    value = valueText,
                    startFraction = PlayerScrubPolicy.speedFraction(startSpeed),
                ),
            )
            return true
        }

        return false
    }

    /** Rounds to the nearest 0.05x within the gesture's speed range. */
    private fun snapSpeed(speed: Float): Float {
        return (Math.round(speed * 20) / 20.0f).coerceIn(PlayerScrubPolicy.SPEED_MIN, PlayerScrubPolicy.SPEED_MAX)
    }

    private fun speedHundredths(speed: Float): Long = Math.round(speed * 100).toLong()

    /** Width class, deciding whether visible labels have room for the control name. */
    private fun surfaceClass(): PlayerSurfaceClass {
        return PlayerSurfacePolicy.classify(callback.playerWidth, density)
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
     * large surfaces does too, while the edge slider shows only the short
     * value and relies on this description for the name.
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
        reading: PlayerGestureReading? = null,
    ) {
        PlayerSurfacePolicy.presentFeedback(
            context = context,
            feedbackRoot = callback.getGestureFeedbackView(),
            kind = kind,
            surfaceWidthPx = callback.playerWidth,
            surfaceHeightPx = callback.playerHeight,
            presentation = PlayerGestureFeedbackState.presentation(
                kind = kind,
                surfaceClass = surfaceClass(),
                level = level,
                text = visibleText,
                reading = reading,
                style = feedbackStyle,
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
                haptic(PlayerGestureHaptic.START)
            }
            true
        } else {
            false
        }
    }

    /**
     * Plays [haptic] when gesture haptics are on. Step ticks closer together
     * than [PlayerScrubPolicy.STEP_TICK_MIN_INTERVAL_MS] are dropped so a fast
     * drag does not buzz; detents and limits always play.
     */
    private fun haptic(haptic: PlayerGestureHaptic?, eventTime: Long = 0L) {
        if (haptic == null || !hapticEnabled) return
        if (haptic == PlayerGestureHaptic.STEP) {
            if (eventTime - lastStepHapticAt < PlayerScrubPolicy.STEP_TICK_MIN_INTERVAL_MS) return
            lastStepHapticAt = eventTime
        }
        try {
            PlayerGestureHaptics.perform(callback.getGestureFeedbackView(), haptic)
        } catch (e: Exception) {
            // Ignore if view not available or haptics failed
        }
    }
}
