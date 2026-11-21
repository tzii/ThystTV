package com.github.andreyasadchy.xtra.ui.player

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Plays [PlayerGestureHaptic]s through the view's haptic feedback, so the
 * system touch-feedback setting still applies. Newer releases get the
 * dedicated segment and reject effects; older ones the closest classic ones.
 */
object PlayerGestureHaptics {

    fun constantFor(haptic: PlayerGestureHaptic): Int {
        return when (haptic) {
            PlayerGestureHaptic.START -> HapticFeedbackConstants.CLOCK_TICK
            PlayerGestureHaptic.STEP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            PlayerGestureHaptic.DETENT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.SEGMENT_TICK
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
            PlayerGestureHaptic.LIMIT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
    }

    fun perform(view: View?, haptic: PlayerGestureHaptic) {
        try {
            view?.performHapticFeedback(constantFor(haptic))
        } catch (e: Exception) {
            // Haptic failure must not block the gesture.
        }
    }
}
