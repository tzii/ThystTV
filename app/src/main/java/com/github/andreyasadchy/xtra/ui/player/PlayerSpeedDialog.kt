package com.github.andreyasadchy.xtra.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.core.os.bundleOf
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogPlayerSpeedBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import java.util.Locale

class PlayerSpeedDialog : BottomSheetDialogFragment() {

    companion object {
        private const val SPEED = "speed"

        fun newInstance(speed: Float?): PlayerSpeedDialog {
            return PlayerSpeedDialog().apply {
                arguments = bundleOf(SPEED to speed)
            }
        }
    }

    private var _binding: DialogPlayerSpeedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogPlayerSpeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        with(binding) {
            val currentSpeed = arguments?.getFloat(SPEED) ?: 1f
            updateSpeedDisplay(currentSpeed)
            speedSlider.value = currentSpeed

            speedSlider.addOnChangeListener { _, value, _ ->
                updateSpeedDisplay(value)
                (parentFragment as? PlayerFragment)?.setPlaybackSpeed(value)
            }
            
            speedSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    saveSpeed(slider.value)
                }
            })

            btnDecreaseSpeed.setOnClickListener {
                val newValue = (speedSlider.value - 0.05f).coerceAtLeast(0.25f)
                speedSlider.value = newValue
                saveSpeed(newValue)
            }

            btnIncreaseSpeed.setOnClickListener {
                val newValue = (speedSlider.value + 0.05f).coerceAtMost(4.0f)
                speedSlider.value = newValue
                saveSpeed(newValue)
            }

            val speedList = requireContext().prefs().getString(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0")?.split("\n")
            speedList?.forEach { speedStr ->
                val speed = speedStr.toFloatOrNull() ?: return@forEach
                val chip = Chip(requireContext()).apply {
                    text = if (speed == 1.0f) "${speedStr}x (${getString(R.string.speed_normal)})" else "${speedStr}x"
                    isCheckable = true
                    isChecked = kotlin.math.abs(speed - currentSpeed) < 0.01f
                    setOnClickListener {
                        speedSlider.value = speed
                        saveSpeed(speed)
                    }
                }
                speedChipGroup.addView(chip)
            }
        }
    }

    private fun updateSpeedDisplay(speed: Float) {
        val formattedSpeed = String.format(Locale.US, "%.2fx", speed)
        binding.currentSpeedText.text = formattedSpeed
        
        // Update chip selection visually
        for (i in 0 until binding.speedChipGroup.childCount) {
            val chip = binding.speedChipGroup.getChildAt(i) as? Chip
            val chipText = chip?.text?.toString() ?: continue
            // Extract number part from "1.0x (Normal)"
            val chipSpeedStr = chipText.split("x")[0]
            val chipSpeed = chipSpeedStr.toFloatOrNull()
            
            if (chipSpeed != null && kotlin.math.abs(chipSpeed - speed) < 0.01f) {
                chip.isChecked = true
            } else {
                chip.isChecked = false
            }
        }
    }

    private fun saveSpeed(speed: Float) {
        requireContext().prefs().edit { putFloat(C.PLAYER_SPEED, speed) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}