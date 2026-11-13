package com.github.andreyasadchy.xtra.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.google.android.material.color.MaterialColors

/**
 * Wide windows show the sections list beside the open section. These map a screen back to
 * the row in `root_preferences` that opens it, so the list can mark where the viewer is.
 */
internal object SettingsPanes {
    /** Shown on the right when a wide window opens Settings, since the root list is already on the left. */
    val DEFAULT_SECTION = R.id.themeSettingsFragment

    fun sectionKey(destinationId: Int): String? = when (destinationId) {
        R.id.themeSettingsFragment -> "theme_settings"
        R.id.uiSettingsFragment -> "ui_settings"
        R.id.playerSettingsFragment -> "player_settings"
        R.id.playerButtonSettingsFragment -> "player_button_settings"
        // Opened from Player buttons, so that row stays marked.
        R.id.playerMenuSettingsFragment -> "player_button_settings"
        R.id.chatSettingsFragment -> "chat_settings"
        R.id.downloadSettingsFragment -> "download_settings"
        R.id.apiTokenSettingsFragment -> "api_token_settings"
        R.id.playbackSettingsFragment -> "playback_settings"
        R.id.bufferSettingsFragment -> "buffer_settings"
        R.id.proxySettingsFragment -> "proxy_settings"
        R.id.updateSettingsFragment -> "update_settings"
        R.id.changelogSettingsFragment -> "changelog_settings"
        R.id.debugSettingsFragment -> "debug_settings"
        else -> null
    }
}

/**
 * Draws a rounded indicator behind the open section's row. Rows keep their own ripple, and
 * drawing under them avoids a custom adapter or preference class.
 */
internal class SelectedSectionDecoration(context: Context) : RecyclerView.ItemDecoration() {
    var selectedKey: String? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, 0)
    }
    private val horizontalInset = dp(context, 8f)
    private val verticalInset = dp(context, 2f)
    private val radius = dp(context, 16f)
    private val rect = RectF()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val key = selectedKey ?: return
        val positions = parent.adapter as? PreferenceGroup.PreferencePositionCallback ?: return
        val position = positions.getPreferenceAdapterPosition(key)
        if (position == RecyclerView.NO_POSITION) return
        val row = parent.findViewHolderForAdapterPosition(position)?.itemView ?: return
        rect.set(
            row.left + horizontalInset,
            row.top + row.translationY + verticalInset,
            row.right - horizontalInset,
            row.bottom + row.translationY - verticalInset,
        )
        paint.alpha = (row.alpha * 255).toInt()
        c.drawRoundRect(rect, radius, radius, paint)
    }

    private fun dp(context: Context, value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)
}
