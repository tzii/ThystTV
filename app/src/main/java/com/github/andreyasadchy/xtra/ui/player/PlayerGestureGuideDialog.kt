package com.github.andreyasadchy.xtra.ui.player

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Compact, context-aware gesture guide. One screen, no demonstrations; the
 * copy never teaches double-tap seeking (double tap toggles chat).
 */
class PlayerGestureGuideDialog : DialogFragment() {

    companion object {
        private const val CONTEXT = "context"

        fun newInstance(context: PlayerGestureGuideContext): PlayerGestureGuideDialog {
            return PlayerGestureGuideDialog().apply {
                arguments = Bundle().apply {
                    putString(CONTEXT, context.name)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val contextName = requireArguments().getString(CONTEXT)
        val guideContext = PlayerGestureGuideContext.entries.find { it.name == contextName }
            ?: PlayerGestureGuideContext.SEEKABLE
        val prefs = requireContext().prefs()
        val rows = PlayerGestureGuideContent.rowsFor(
            guideContext,
            swipeChat = prefs.getBoolean(C.PLAYER_GESTURES_SWIPE_CHAT, true) && !prefs.getBoolean(C.CHAT_DISABLE, false),
        )
        val padding = (16 * resources.displayMetrics.density).toInt()
        val rowsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        rows.forEach { row ->
            rowsContainer.addView(guideRow(row, guideContext))
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.player_gesture_guide_title))
            .setView(rowsContainer)
            .setPositiveButton(getString(R.string.player_gesture_guide_got_it)) { _, _ ->
                requireContext().prefs().edit {
                    putInt(C.PLAYER_GESTURE_GUIDE_VERSION, PlayerGestureEducationState.GUIDE_VERSION)
                }
                (parentFragment as? PlayerFragment)?.onGestureGuideDismissed()
            }
            .show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return null
    }

    private fun guideRow(row: PlayerGestureGuideRow, context: PlayerGestureGuideContext): View {
        val gestureLabel = getString(
            when (row) {
                PlayerGestureGuideRow.BRIGHTNESS -> R.string.gesture_row_brightness
                PlayerGestureGuideRow.DEVICE_VOLUME -> R.string.gesture_row_device_volume
                PlayerGestureGuideRow.SEEK -> R.string.gesture_row_seek
                PlayerGestureGuideRow.PLAYBACK_SPEED -> R.string.gesture_row_playback_speed
                PlayerGestureGuideRow.PINCH -> R.string.gesture_row_pinch
                PlayerGestureGuideRow.DOUBLE_TAP_CHAT -> R.string.gesture_row_double_tap
                PlayerGestureGuideRow.SWIPE_CHAT -> if (context == PlayerGestureGuideContext.LIVE) {
                    R.string.gesture_row_swipe_chat
                } else {
                    R.string.gesture_row_swipe_chat_edge
                }
            }
        )
        val actionLabel = getString(
            when (row) {
                PlayerGestureGuideRow.BRIGHTNESS -> R.string.gesture_feedback_brightness
                PlayerGestureGuideRow.DEVICE_VOLUME -> R.string.gesture_feedback_device_volume
                PlayerGestureGuideRow.SEEK -> if (context == PlayerGestureGuideContext.SETTINGS) {
                    R.string.gesture_action_seek_settings
                } else {
                    R.string.gesture_feedback_seek
                }
                PlayerGestureGuideRow.PLAYBACK_SPEED -> if (context == PlayerGestureGuideContext.SETTINGS) {
                    R.string.gesture_action_playback_speed_settings
                } else {
                    R.string.gesture_feedback_playback_speed
                }
                PlayerGestureGuideRow.PINCH -> if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    requireContext().prefs().getBoolean(C.PLAYER_EXTENDED_ZOOM, false)
                ) {
                    R.string.gesture_action_pinch_extended
                } else {
                    R.string.gesture_action_pinch
                }
                PlayerGestureGuideRow.DOUBLE_TAP_CHAT -> R.string.gesture_action_toggle_chat
                PlayerGestureGuideRow.SWIPE_CHAT -> if (context == PlayerGestureGuideContext.SETTINGS) {
                    R.string.gesture_action_swipe_chat_settings
                } else {
                    R.string.gesture_action_swipe_chat
                }
            }
        )
        val rowLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, (10 * resources.displayMetrics.density).toInt())
        }
        rowLayout.addView(
            TextView(requireContext()).apply {
                text = gestureLabel
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        rowLayout.addView(
            TextView(requireContext()).apply {
                text = actionLabel
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        return rowLayout
    }
}
