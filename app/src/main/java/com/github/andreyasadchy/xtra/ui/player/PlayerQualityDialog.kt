package com.github.andreyasadchy.xtra.ui.player

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.TextUtils
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
import androidx.core.graphics.ColorUtils
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogPlayerQualityBinding
import java.util.Locale

class PlayerQualityDialog : DialogFragment() {

    companion object {
        private const val LABELS = "labels"
        private const val TAGS = "tags"
        private const val SELECTED = "selected"
        private const val AUDIO_ONLY = "audio_only"
        private const val CHAT_ONLY = "chat_only"

        fun newInstance(labels: Collection<CharSequence>, tags: Array<String>, selected: String?): PlayerQualityDialog {
            return PlayerQualityDialog().apply {
                arguments = Bundle().apply {
                    putCharSequenceArrayList(LABELS, ArrayList(labels))
                    putStringArray(TAGS, tags)
                    putString(SELECTED, selected)
                }
            }
        }
    }

    private var _binding: DialogPlayerQualityBinding? = null
    private val binding get() = _binding!!
    private lateinit var colors: QualityDialogColors

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogPlayerQualityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setDimAmount(0f)

        colors = resolveColors()
        with(binding) {
            qualityPanel.layoutParams = qualityPanel.layoutParams.apply {
                width = getPanelWidth()
            }
            qualityPanel.setCardBackgroundColor(colors.panel)
            qualityPanel.strokeWidth = dp(1f)
            qualityPanel.setStrokeColor(colors.panelStroke)
            qualityHandle.background = roundedDrawable(colors.handle, 3f)
            qualityTitle.setTextColor(colors.onPanel)
            buildRows()
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

    private fun buildRows() {
        val labels = requireArguments().getCharSequenceArrayList(LABELS).orEmpty()
        val tags = requireArguments().getStringArray(TAGS).orEmpty()
        val selected = requireArguments().getString(SELECTED)
        val entries = labels.zip(tags).map { (label, tag) ->
            val mode = UtilityMode.from(tag, label.toString())
            QualityEntry(
                label = when (mode) {
                    UtilityMode.AudioOnly -> getString(R.string.audio_only)
                    UtilityMode.ChatOnly -> getString(R.string.chat_only)
                    null -> label.toString()
                },
                tag = mode?.canonicalTag ?: tag,
                mode = mode
            )
        }
        val videoEntries = entries.filterNot { it.isAudioChatMode }
        val audioChatEntries = entries
            .filter { it.isAudioChatMode }
            .groupBy { it.mode }
            .values
            .map { modeEntries ->
                modeEntries.firstOrNull { it.tag.normalizedQualityName() == it.mode?.canonicalTag } ?: modeEntries.first()
            }
            .sortedBy { it.mode?.order ?: Int.MAX_VALUE }

        binding.qualityRows.removeAllViews()
        addSection(R.string.video_quality, videoEntries, selected)
        if (audioChatEntries.isNotEmpty()) {
            addSection(R.string.audio_chat_modes, audioChatEntries, selected)
        }
        constrainScrollHeight(videoEntries.size, audioChatEntries.size)
    }

    private fun addSection(titleRes: Int, entries: List<QualityEntry>, selected: String?) {
        if (entries.isEmpty()) return

        binding.qualityRows.addView(sectionLabel(getString(titleRes)))
        addChipGrid(entries, selected)
    }

    private fun addChipGrid(entries: List<QualityEntry>, selected: String?) {
        val panelWidth = getPanelWidth()
        val horizontalGap = dp(7f)
        val rowAvailableWidth = panelWidth - dp(32f)
        val minChipWidth = if (entries.any { it.isAudioChatMode }) dp(132f) else dp(92f)
        val maxChipsPerRow = if (entries.any { it.isAudioChatMode }) 2 else if (panelWidth < dp(460f)) 3 else 4
        val chipsPerRow = ((rowAvailableWidth + horizontalGap) / (minChipWidth + horizontalGap))
            .coerceIn(1, maxChipsPerRow)
            .coerceAtMost(entries.size)
        val chipWidth = (rowAvailableWidth - horizontalGap * (chipsPerRow - 1)) / chipsPerRow

        entries.chunked(chipsPerRow).forEachIndexed { rowIndex, rowEntries ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            binding.qualityRows.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) {
                    topMargin = dp(8f)
                }
            })

            rowEntries.forEachIndexed { chipIndex, entry ->
                row.addView(
                    qualityChip(entry, entry.isSelected(selected)),
                    LinearLayout.LayoutParams(
                        chipWidth,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (chipIndex > 0) {
                            marginStart = horizontalGap
                        }
                    }
                )
            }
        }
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(colors.secondaryText)
            textSize = 12f
            isAllCaps = true
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, if (binding.qualityRows.childCount == 0) 0 else dp(16f), 0, dp(8f))
        }
    }

    private fun qualityChip(entry: QualityEntry, selected: Boolean): TextView {
        return TextView(requireContext()).apply {
            text = entry.label
            setTextColor(if (selected) colors.selectedText else colors.onPanel)
            textSize = if (entry.isAudioChatMode) 15f else 16f
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            minHeight = if (entry.isAudioChatMode) dp(42f) else dp(38f)
            isSelected = selected
            isClickable = true
            isFocusable = true
            background = chipBackground(colors.chip, colors.selectedChip)
            setPadding(dp(8f), dp(7f), dp(8f), dp(7f))
            setOnClickListener {
                (parentFragment as? PlayerFragment)?.selectQuality(entry.tag)
                dismiss()
            }
        }
    }

    private fun constrainScrollHeight(videoEntryCount: Int, audioChatEntryCount: Int) {
        val panelWidth = getPanelWidth()
        val videoPerRow = if (panelWidth < dp(460f)) 3 else 4
        val videoRows = if (videoEntryCount == 0) 0 else (videoEntryCount + videoPerRow - 1) / videoPerRow
        val audioRows = if (audioChatEntryCount == 0) 0 else (audioChatEntryCount + 1) / 2
        val sectionCount = listOf(videoEntryCount, audioChatEntryCount).count { it > 0 }
        val estimatedHeight = dp(34f) * sectionCount + dp(46f) * (videoRows + audioRows) + dp(24f)
        val maxHeight = (resources.displayMetrics.heightPixels * 0.52f).toInt().coerceAtMost(dp(430f))
        binding.qualityScroll.layoutParams = binding.qualityScroll.layoutParams.apply {
            height = if (estimatedHeight > maxHeight) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun resolveColors(): QualityDialogColors {
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
        val rowBlend = if (lightPanel) 0.08f else 0.16f
        val selectedBlend = if (lightPanel) 0.18f else 0.32f
        val strokeAlpha = if (lightPanel) 0.16f else 0.22f
        return QualityDialogColors(
            panel = panel,
            onPanel = onPanel,
            selectedText = if (lightPanel) onPanel else Color.WHITE,
            secondaryText = ColorUtils.setAlphaComponent(onPanel, if (lightPanel) 150 else 178),
            panelStroke = ColorUtils.blendARGB(panel, onPanel, strokeAlpha),
            chip = ColorUtils.blendARGB(panel, onPanel, rowBlend),
            selectedChip = ColorUtils.blendARGB(panel, primary, selectedBlend),
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

    private fun chipBackground(normalColor: Int, selectedColor: Int): StateListDrawable {
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

    private fun getPanelWidth(): Int {
        val maxWidth = dp(500f)
        val horizontalMargin = dp(40f)
        return (resources.displayMetrics.widthPixels - horizontalMargin).coerceAtMost(maxWidth)
    }

    private fun dp(value: Float): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class QualityEntry(
        val label: String,
        val tag: String,
        val mode: UtilityMode?,
    ) {
        val isAudioChatMode = mode != null

        fun isSelected(selected: String?): Boolean {
            return tag == selected || (mode != null && mode == UtilityMode.from(selected, selected))
        }
    }

    private enum class UtilityMode(val canonicalTag: String, val order: Int) {
        AudioOnly(PlayerQualityDialog.AUDIO_ONLY, 0),
        ChatOnly(PlayerQualityDialog.CHAT_ONLY, 1);

        companion object {
            fun from(tag: String?, label: String?): UtilityMode? {
                return when {
                    tag.normalizedQualityName() == PlayerQualityDialog.AUDIO_ONLY ||
                        label.normalizedQualityName() == PlayerQualityDialog.AUDIO_ONLY -> AudioOnly
                    tag.normalizedQualityName() == PlayerQualityDialog.CHAT_ONLY ||
                        label.normalizedQualityName() == PlayerQualityDialog.CHAT_ONLY -> ChatOnly
                    else -> null
                }
            }
        }
    }

    private data class QualityDialogColors(
        val panel: Int,
        val onPanel: Int,
        val selectedText: Int,
        val secondaryText: Int,
        val panelStroke: Int,
        val chip: Int,
        val selectedChip: Int,
        val handle: Int,
    )
}

private fun String?.normalizedQualityName(): String {
    return this
        ?.trim()
        ?.lowercase(Locale.US)
        ?.replace(' ', '_')
        ?.replace('-', '_')
        .orEmpty()
}
