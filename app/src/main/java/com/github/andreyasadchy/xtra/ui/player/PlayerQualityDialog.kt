package com.github.andreyasadchy.xtra.ui.player

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogPlayerQualityBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlayerQualityDialog : BottomSheetDialogFragment() {

    companion object {
        private const val LABELS = "labels"
        private const val TAGS = "tags"
        private const val CHECKED = "checked"
        private const val AUDIO_LABEL = "audio_label"
        private const val AUDIO_TAG = "audio_tag"
        private const val AUDIO_CHECKED = "audio_checked"

        fun newInstance(
            labels: Collection<CharSequence>,
            tags: Array<String>,
            checkedIndex: Int,
            audioLabel: CharSequence?,
            audioTag: String?,
            audioChecked: Boolean
        ): PlayerQualityDialog {
            return PlayerQualityDialog().apply {
                arguments = bundleOf(
                    LABELS to ArrayList(labels),
                    TAGS to tags,
                    CHECKED to checkedIndex,
                    AUDIO_LABEL to audioLabel,
                    AUDIO_TAG to audioTag,
                    AUDIO_CHECKED to audioChecked
                )
            }
        }
    }

    private var _binding: DialogPlayerQualityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogPlayerQualityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        makeSheetFloat()

        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        val labels = requireArguments().getCharSequenceArrayList(LABELS).orEmpty()
        val tags = requireArguments().getStringArray(TAGS)?.toList().orEmpty()
        val checkedIndex = requireArguments().getInt(CHECKED, -1)
        val audioLabel = requireArguments().getCharSequence(AUDIO_LABEL)
        val audioTag = requireArguments().getString(AUDIO_TAG)
        val audioChecked = requireArguments().getBoolean(AUDIO_CHECKED, false)
        binding.qualityPanel.layoutParams = binding.qualityPanel.layoutParams.apply {
            width = getPanelWidth()
        }
        buildQualityRows(labels, tags, checkedIndex)
        bindAudioOnlyOption(audioLabel, audioTag, audioChecked)
    }

    private fun makeSheetFloat() {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setDimAmount(0f)
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            background = ColorDrawable(Color.TRANSPARENT)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            layoutParams = layoutParams.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            (layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(0, 0, 0, 0)
            (layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.gravity =
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
    }

    private fun buildQualityRows(labels: List<CharSequence>, tags: List<String>, checkedIndex: Int) {
        val density = resources.displayMetrics.density
        val panelWidth = getPanelWidth()
        val optionsPerRow = if (panelWidth < density * 460f) 2 else 3
        val horizontalGap = (density * 8f).toInt()
        val rowAvailableWidth = panelWidth - (density * 32f).toInt()
        val optionWidth = ((rowAvailableWidth - (horizontalGap * (optionsPerRow - 1))) / optionsPerRow)
        binding.qualityPresetRows.removeAllViews()

        labels.chunked(optionsPerRow).forEachIndexed { rowIndex, rowLabels ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            if (rowIndex > 0) {
                row.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (density * 8f).toInt()
                }
            }

            rowLabels.forEachIndexed { columnIndex, label ->
                val optionIndex = rowIndex * optionsPerRow + columnIndex
                val option = TextView(requireContext()).apply {
                    text = label
                    background = requireContext().getDrawable(R.drawable.bg_player_speed_preset)
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    isClickable = true
                    isFocusable = true
                    isSelected = optionIndex == checkedIndex
                    maxLines = 1
                    minHeight = (density * 36f).toInt()
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setOnClickListener {
                        (parentFragment as? PlayerFragment)?.selectQualityByName(tags.getOrNull(optionIndex))
                        dismiss()
                    }
                }

                row.addView(
                    option,
                    LinearLayout.LayoutParams(
                        optionWidth,
                        (density * 38f).toInt()
                    ).apply {
                        if (columnIndex > 0) {
                            marginStart = horizontalGap
                        }
                    }
                )
            }

            binding.qualityPresetRows.addView(row)
        }
    }

    private fun bindAudioOnlyOption(label: CharSequence?, tag: String?, checked: Boolean) {
        with(binding.audioOnlyOption) {
            isVisible = !label.isNullOrBlank() && !tag.isNullOrBlank()
            text = label
            isSelected = checked
            setOnClickListener {
                (parentFragment as? PlayerFragment)?.selectQualityByName(tag)
                dismiss()
            }
        }
    }

    private fun getPanelWidth(): Int {
        val density = resources.displayMetrics.density
        val maxWidth = (density * 500f).toInt()
        val horizontalMargin = (density * 40f).toInt()
        return (resources.displayMetrics.widthPixels - horizontalMargin).coerceAtMost(maxWidth)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
