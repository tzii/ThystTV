package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.view.isEmpty
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.LayoutFloatingChatPopupBinding
import com.github.andreyasadchy.xtra.util.C
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * The floating chat's Aa panel: background, text size and behavior. Every
 * change is saved and applied live; the chat does not reconnect.
 */
internal class FloatingChatPopupBinder(
    private val context: Context,
    private val binding: LayoutFloatingChatPopupBinding,
    private val prefs: SharedPreferences,
    private val canCompose: Boolean,
    private val onChanged: () -> Unit,
    private val onDismissRequested: () -> Unit,
) {
    private val colors = PlayerPanelTheme.resolve(context)
    private val density = context.resources.displayMetrics.density
    private val segments = mutableListOf<List<TextView>>()

    fun bind() {
        applyTheme()
        binding.floatingChatPopupClose.setOnClickListener { onDismissRequested() }
        val appearance = FloatingChatAppearance.read(prefs)
        val rows = binding.floatingChatPopupRows
        rows.removeAllViews()

        addLabel(R.string.floating_chat_background)
        addSegments(
            options = listOf(
                FloatingChatBackground.CLEAR to R.string.floating_chat_background_clear,
                FloatingChatBackground.SHADE to R.string.floating_chat_background_shade,
                FloatingChatBackground.SOLID to R.string.floating_chat_background_solid,
            ),
            selected = appearance.background,
        ) { background ->
            prefs.edit { putInt(C.FLOATING_CHAT_TRANSPARENCY, background.opacity) }
            onChanged()
        }

        addLabel(R.string.floating_chat_text_size)
        addSegments(
            options = listOf(
                FloatingChatTextSize.SMALL to R.string.floating_chat_text_small,
                FloatingChatTextSize.MEDIUM to R.string.floating_chat_text_medium,
                FloatingChatTextSize.LARGE to R.string.floating_chat_text_large,
            ),
            selected = appearance.textSize,
        ) { size ->
            prefs.edit { putString(C.FLOATING_CHAT_TEXT_SIZE, size.value) }
            onChanged()
        }

        addSwitch(R.string.floating_chat_fade_old, C.FLOATING_CHAT_FADE_OLD, appearance.fadeOldMessages)
        addSwitch(R.string.floating_chat_bold_text, C.FLOATING_CHAT_BOLD_TEXT, appearance.boldText)
        addSwitch(R.string.floating_chat_pass_through, C.FLOATING_CHAT_PASS_THROUGH, appearance.passThrough)
        if (canCompose) {
            addSwitch(R.string.floating_chat_reply_bar, C.FLOATING_CHAT_REPLY_BAR, appearance.replyBar)
        }
    }

    fun dispose() {
        binding.floatingChatPopupClose.setOnClickListener(null)
        binding.floatingChatPopupRows.removeAllViews()
        segments.clear()
    }

    private fun applyTheme() {
        binding.floatingChatPopupCard.apply {
            setCardBackgroundColor(colors.panel)
            strokeWidth = density.toInt().coerceAtLeast(1)
            setStrokeColor(colors.panelStroke)
        }
        binding.floatingChatPopupTitle.setTextColor(colors.onPanel)
        binding.floatingChatPopupClose.imageTintList = ColorStateList.valueOf(colors.secondaryText)
    }

    private fun addLabel(text: Int) {
        val label = TextView(context).apply {
            setText(text)
            setTextColor(colors.secondaryText)
            textSize = 13f
        }
        binding.floatingChatPopupRows.addView(
            label,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(if (binding.floatingChatPopupRows.isEmpty()) 4f else 12f)
                bottomMargin = dp(6f)
            },
        )
    }

    private fun <T> addSegments(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = options.mapIndexed { index, (value, label) ->
            TextView(context).apply {
                setText(label)
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = true
                textSize = 14f
                minHeight = dp(44f)
                background = chipBackground()
                setTextColor(
                    ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                        intArrayOf(colors.onSelected, colors.onPanel),
                    ),
                )
                isSelected = value == selected
                row.addView(
                    this,
                    LinearLayout.LayoutParams(0, dp(44f), 1f).apply { if (index > 0) marginStart = dp(6f) },
                )
            }
        }
        chips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                if (chip.isSelected) return@setOnClickListener
                chips.forEach { it.isSelected = it === chip }
                onSelect(options[index].first)
            }
        }
        segments += chips
        binding.floatingChatPopupRows.addView(
            row,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun addSwitch(text: Int, key: String, checked: Boolean) {
        val toggle = MaterialSwitch(context).apply {
            setText(text)
            setTextColor(colors.onPanel)
            textSize = 15f
            isChecked = checked
            minHeight = dp(48f)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean(key, isChecked) }
                onChanged()
            }
        }
        binding.floatingChatPopupRows.addView(
            toggle,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(if (binding.floatingChatPopupRows.childCount <= 4) 8f else 0f)
            },
        )
    }

    private fun chipBackground(): Drawable {
        val shapes = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), rounded(colors.selectedFill))
            addState(intArrayOf(), rounded(colors.controlFill))
        }
        return RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.onPanel, 48)), shapes, rounded(Color.WHITE))
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(22f).toFloat()
    }

    private fun dp(value: Float) = (value * density).toInt()
}
