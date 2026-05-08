package com.github.andreyasadchy.xtra.ui.player

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.core.view.children
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogPlayerSpeedBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlin.math.abs

class PlayerSpeedDialog : DialogFragment() {

    companion object {
        private const val SPEED = "speed"
        private const val MIN_SPEED = 0.25f
        private const val MAX_SPEED = 8.0f
        private const val MAX_PRESET_SPEED = 4.0f
        private const val SPEED_STEP = 0.05f
        private const val DEFAULT_PRESETS = "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0"

        fun newInstance(speed: Float?): PlayerSpeedDialog {
            return PlayerSpeedDialog().apply {
                arguments = bundleOf(SPEED to speed)
            }
        }
    }

    private var _binding: DialogPlayerSpeedBinding? = null
    private val binding get() = _binding!!
    private var selectedSpeed = 1f
    private lateinit var speedColors: SpeedDialogColors

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogPlayerSpeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setDimAmount(0f)

        with(binding) {
            speedPanel.layoutParams = speedPanel.layoutParams.apply {
                width = getPanelWidth()
            }
            speedColors = resolveSpeedDialogColors()
            applyThemeColors(speedColors)
            selectedSpeed = arguments?.getFloat(SPEED) ?: 1f
            speedSlider.value = selectedSpeed.coerceIn(MIN_SPEED, MAX_SPEED)
            buildPresetRows(loadSpeedPresets())
            updateSpeedDisplay(selectedSpeed)

            speedSlider.addOnChangeListener { _, value, _ ->
                applySpeed(value, save = false)
            }

            speedSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    saveSpeed(slider.value)
                }
            })

            btnDecreaseSpeed.setOnClickListener {
                applySpeed((selectedSpeed - SPEED_STEP).coerceAtLeast(MIN_SPEED))
            }

            btnIncreaseSpeed.setOnClickListener {
                applySpeed((selectedSpeed + SPEED_STEP).coerceAtMost(MAX_SPEED))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0f)
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun loadSpeedPresets(): List<Float> {
        val saved = requireContext()
            .prefs()
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
        val density = resources.displayMetrics.density
        val panelWidth = getPanelWidth()
        val presetsPerRow = if (panelWidth < density * 460f) 3 else 5
        val horizontalGap = (density * 7f).toInt()
        val rowAvailableWidth = panelWidth - (density * 32f).toInt()
        val presetWidth = ((rowAvailableWidth - (horizontalGap * (presetsPerRow - 1))) / presetsPerRow)
        binding.speedPresetRows.removeAllViews()

        speeds.chunked(presetsPerRow).forEachIndexed { index, rowSpeeds ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }
            if (index > 0) {
                row.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (density * 8f).toInt()
                }
            }

            rowSpeeds.forEachIndexed { chipIndex, speed ->
                val preset = TextView(requireContext()).apply {
                    text = formatPreset(speed)
                    background = presetBackground(speedColors.preset, speedColors.selectedPreset)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    isClickable = true
                    isFocusable = true
                    minHeight = (density * 36f).toInt()
                    minWidth = (density * 72f).toInt()
                    setTextColor(speedColors.onPanel)
                    textSize = 17f
                    setOnClickListener {
                        applySpeed(speed)
                        dismiss()
                    }
                }

                row.addView(
                    preset,
                    LinearLayout.LayoutParams(
                        presetWidth,
                        (density * 38f).toInt()
                    ).apply {
                        if (chipIndex > 0) {
                            marginStart = horizontalGap
                        }
                    }
                )
            }

            binding.speedPresetRows.addView(row)
        }
    }

    private fun applyThemeColors(colors: SpeedDialogColors) {
        with(binding) {
            speedPanel.setCardBackgroundColor(colors.panel)
            speedPanel.strokeWidth = dp(1f)
            speedPanel.setStrokeColor(colors.panelStroke)
            speedHandle.background = roundedDrawable(colors.handle, 3f)
            currentSpeedText.setTextColor(colors.onPanel)
            btnDecreaseSpeed.background = ovalDrawable(colors.control)
            btnIncreaseSpeed.background = ovalDrawable(colors.control)
            btnDecreaseSpeed.imageTintList = ColorStateList.valueOf(colors.onPanel)
            btnIncreaseSpeed.imageTintList = ColorStateList.valueOf(colors.onPanel)
            speedSlider.thumbTintList = ColorStateList.valueOf(colors.primary)
            speedSlider.trackActiveTintList = ColorStateList.valueOf(colors.primary)
            speedSlider.trackInactiveTintList = ColorStateList.valueOf(colors.sliderInactive)
            speedSlider.haloTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
    }

    private fun resolveSpeedDialogColors(): SpeedDialogColors {
        val context = requireContext()
        val fallbackSurface = if (isLightTheme(context)) Color.WHITE else Color.rgb(18, 18, 18)
        val panel = themeColor(context, com.google.android.material.R.attr.colorSurfaceContainer, fallbackSurface)
        val onPanel = themeColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
            if (ColorUtils.calculateLuminance(panel) > 0.5) Color.rgb(28, 28, 28) else Color.WHITE
        )
        val primary = themeColor(context, androidx.appcompat.R.attr.colorPrimary, Color.rgb(0, 125, 202))
        val lightPanel = ColorUtils.calculateLuminance(panel) > 0.5
        val controlBlend = if (lightPanel) 0.08f else 0.16f
        val selectedBlend = if (lightPanel) 0.18f else 0.32f
        val strokeAlpha = if (lightPanel) 0.16f else 0.22f
        return SpeedDialogColors(
            panel = panel,
            onPanel = onPanel,
            primary = primary,
            panelStroke = ColorUtils.blendARGB(panel, onPanel, strokeAlpha),
            control = ColorUtils.blendARGB(panel, onPanel, controlBlend),
            preset = ColorUtils.blendARGB(panel, onPanel, controlBlend),
            selectedPreset = ColorUtils.blendARGB(panel, primary, selectedBlend),
            sliderInactive = ColorUtils.setAlphaComponent(onPanel, if (lightPanel) 52 else 64),
            handle = ColorUtils.setAlphaComponent(onPanel, if (lightPanel) 96 else 128),
        )
    }

    private fun isLightTheme(context: Context): Boolean {
        val value = TypedValue()
        return context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, value, true) && value.data != 0
    }

    private fun themeColor(context: Context, attr: Int, fallback: Int): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) {
            return fallback
        }
        return if (value.resourceId != 0) {
            ContextCompat.getColor(context, value.resourceId)
        } else {
            value.data
        }
    }

    private fun ovalDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun presetBackground(normalColor: Int, selectedColor: Int): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), roundedDrawable(selectedColor, 18f))
            addState(intArrayOf(), roundedDrawable(normalColor, 18f))
        }
    }

    private fun roundedDrawable(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Float): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun formatPreset(speed: Float): String {
        return if (speed % 1f == 0f) {
            speed.toInt().toString() + ".0"
        } else {
            speed.toString()
        }
    }

    private fun applySpeed(speed: Float, save: Boolean = true) {
        selectedSpeed = speed
        updateSpeedDisplay(speed)
        (parentFragment as? PlayerFragment)?.setPlaybackSpeed(speed)
        if (binding.speedSlider.value != speed) {
            binding.speedSlider.value = speed
        }
        if (save) {
            saveSpeed(speed)
        }
    }

    private fun updateSpeedDisplay(speed: Float) {
        selectedSpeed = speed
        binding.currentSpeedText.text = String.format(Locale.US, "%.2fx", speed)

        binding.speedPresetRows.children.forEach { row ->
            (row as? ViewGroup)?.children?.forEach { child ->
                val preset = child as? TextView ?: return@forEach
                val presetSpeed = preset.text
                    ?.toString()
                    ?.toFloatOrNull()
                preset.isSelected = presetSpeed != null && abs(presetSpeed - speed) < 0.01f
            }
        }
    }

    private fun getPanelWidth(): Int {
        val density = resources.displayMetrics.density
        val maxWidth = (density * 500f).toInt()
        val horizontalMargin = (density * 40f).toInt()
        return (resources.displayMetrics.widthPixels - horizontalMargin).coerceAtMost(maxWidth)
    }

    private fun saveSpeed(speed: Float) {
        requireContext().prefs().edit { putFloat(C.PLAYER_SPEED, speed) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class SpeedDialogColors(
        val panel: Int,
        val onPanel: Int,
        val primary: Int,
        val panelStroke: Int,
        val control: Int,
        val preset: Int,
        val selectedPreset: Int,
        val sliderInactive: Int,
        val handle: Int,
    )
}
