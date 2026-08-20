package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.children
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerSpeedPopupBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlin.math.abs

/** Binds Playback Speed content inside the shared player popup host. */
internal class PlayerSpeedPopupBinder(
    private val context: Context,
    private val binding: LayoutPlayerSpeedPopupBinding,
    initialSpeed: Float,
    private val panelWidthPx: Int,
    private val onSpeedChanged: (Float) -> Unit,
    private val onDismissRequested: () -> Unit,
) {

    companion object {
        private const val MIN_SPEED = 0.25f
        private const val MAX_SPEED = 8.0f
        private const val MAX_PRESET_SPEED = 4.0f
        private const val SPEED_STEP = 0.05f
        private const val DEFAULT_PRESETS = "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0"
    }

    private val density = context.resources.displayMetrics.density
    private val colors = PlayerPanelTheme.resolve(context)
    private var selectedSpeed = initialSpeed.coerceIn(MIN_SPEED, MAX_SPEED)

    fun bind() {
        applyTheme()
        binding.speedSlider.value = selectedSpeed
        buildPresetRows(loadSpeedPresets())
        updateSpeedDisplay(selectedSpeed)

        binding.speedSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) applySpeed(value, save = false)
        }
        binding.speedSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit

            override fun onStopTrackingTouch(slider: Slider) {
                saveSpeed(slider.value)
            }
        })
        binding.btnDecreaseSpeed.setOnClickListener {
            applySpeed((selectedSpeed - SPEED_STEP).coerceAtLeast(MIN_SPEED))
        }
        binding.btnIncreaseSpeed.setOnClickListener {
            applySpeed((selectedSpeed + SPEED_STEP).coerceAtMost(MAX_SPEED))
        }
    }

    fun dispose() {
        binding.speedSlider.clearOnChangeListeners()
        binding.speedSlider.clearOnSliderTouchListeners()
        binding.btnDecreaseSpeed.setOnClickListener(null)
        binding.btnIncreaseSpeed.setOnClickListener(null)
        binding.speedPresetRows.removeAllViews()
    }

    fun constrainTo(maxPanelHeight: Int) {
        val overflow = (binding.root.measuredHeight - maxPanelHeight).coerceAtLeast(0)
        if (overflow > 0) {
            binding.speedPresetScroll.layoutParams = binding.speedPresetScroll.layoutParams.apply {
                height = (binding.speedPresetScroll.measuredHeight - overflow).coerceAtLeast(0)
            }
        }
    }

    private fun applyTheme() {
        with(binding) {
            speedPopupCard.setCardBackgroundColor(colors.panel)
            speedPopupCard.setStrokeColor(colors.panelStroke)
            speedPopupTitle.setTextColor(colors.onPanel)
            currentSpeedText.setTextColor(colors.onPanel)
            btnDecreaseSpeed.background = ovalDrawable(colors.controlFill)
            btnIncreaseSpeed.background = ovalDrawable(colors.controlFill)
            btnDecreaseSpeed.imageTintList = ColorStateList.valueOf(colors.onPanel)
            btnIncreaseSpeed.imageTintList = ColorStateList.valueOf(colors.onPanel)
            speedSlider.thumbTintList = ColorStateList.valueOf(colors.sliderActive)
            speedSlider.trackActiveTintList = ColorStateList.valueOf(colors.sliderActive)
            speedSlider.trackInactiveTintList = ColorStateList.valueOf(colors.sliderInactive)
            speedSlider.haloTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
    }

    private fun loadSpeedPresets(): List<Float> {
        val saved = context.prefs()
            .getString(C.PLAYER_SPEED_LIST, DEFAULT_PRESETS)
            ?.split("\n")
            ?.mapNotNull { it.toFloatOrNull() }
            .orEmpty()
        val defaults = DEFAULT_PRESETS.split("\n").mapNotNull { it.toFloatOrNull() }
        return (saved + defaults)
            .distinct()
            .sorted()
            .filter { it in MIN_SPEED..MAX_PRESET_SPEED }
    }

    private fun buildPresetRows(speeds: List<Float>) {
        val presetsPerRow = if (panelWidthPx < dp(460f)) 3 else 5
        val horizontalGap = dp(8f)
        val availableWidth = panelWidthPx - dp(32f)
        val presetWidth = (availableWidth - horizontalGap * (presetsPerRow - 1)) / presetsPerRow
        binding.speedPresetRows.removeAllViews()

        speeds.chunked(presetsPerRow).forEachIndexed { rowIndex, rowSpeeds ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            binding.speedPresetRows.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (rowIndex > 0) topMargin = dp(8f)
                },
            )

            rowSpeeds.forEachIndexed { chipIndex, speed ->
                val preset = TextView(context).apply {
                    tag = speed
                    text = formatPreset(speed)
                    background = presetBackground(colors.controlFill, colors.selectedFill)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    isClickable = true
                    isFocusable = true
                    minHeight = dp(48f)
                    setTextColor(colors.onPanel)
                    textSize = 16f
                    setOnClickListener {
                        applySpeed(speed)
                        onDismissRequested()
                    }
                }
                row.addView(
                    preset,
                    LinearLayout.LayoutParams(presetWidth, dp(48f)).apply {
                        if (chipIndex > 0) marginStart = horizontalGap
                    },
                )
            }
        }
    }

    private fun applySpeed(speed: Float, save: Boolean = true) {
        selectedSpeed = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        updateSpeedDisplay(selectedSpeed)
        onSpeedChanged(selectedSpeed)
        if (abs(binding.speedSlider.value - selectedSpeed) >= 0.001f) {
            binding.speedSlider.value = selectedSpeed
        }
        if (save) saveSpeed(selectedSpeed)
    }

    private fun updateSpeedDisplay(speed: Float) {
        binding.currentSpeedText.text = String.format(Locale.US, "%.2fx", speed)
        binding.speedPresetRows.children.forEach { row ->
            (row as? ViewGroup)?.children?.forEach { child ->
                val preset = child as? TextView ?: return@forEach
                val presetSpeed = preset.tag as? Float
                preset.isSelected = presetSpeed != null && abs(presetSpeed - speed) < 0.01f
            }
        }
    }

    private fun saveSpeed(speed: Float) {
        context.prefs().edit { putFloat(C.PLAYER_SPEED, speed) }
    }

    private fun formatPreset(speed: Float): String {
        return if (speed % 1f == 0f) "${speed.toInt()}.0" else speed.toString()
    }

    private fun ovalDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun presetBackground(normalColor: Int, selectedColor: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected), roundedDrawable(selectedColor, 18f))
        addState(intArrayOf(), roundedDrawable(normalColor, 18f))
    }

    private fun roundedDrawable(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Float): Int = (value * density).toInt()
}
