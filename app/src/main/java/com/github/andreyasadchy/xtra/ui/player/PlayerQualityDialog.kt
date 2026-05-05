package com.github.andreyasadchy.xtra.ui.player

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import androidx.core.graphics.ColorUtils
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogPlayerQualityBinding

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
            QualityEntry(label = label.toString(), tag = tag)
        }
        val videoEntries = entries.filterNot { it.isAudioChatMode }
        val audioChatEntries = entries.filter { it.isAudioChatMode }

        binding.qualityRows.removeAllViews()
        addSection(R.string.video_quality, videoEntries, selected)
        if (audioChatEntries.isNotEmpty()) {
            addSection(R.string.audio_chat_modes, audioChatEntries, selected)
        }
        constrainScrollHeight(entries.size)
    }

    private fun addSection(titleRes: Int, entries: List<QualityEntry>, selected: String?) {
        if (entries.isEmpty()) return

        binding.qualityRows.addView(sectionLabel(getString(titleRes)))
        entries.forEachIndexed { index, entry ->
            binding.qualityRows.addView(qualityRow(entry, entry.tag == selected), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    topMargin = dp(8f)
                }
            })
        }
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(colors.secondaryText)
            textSize = 12f
            isAllCaps = true
            includeFontPadding = false
            setPadding(0, if (binding.qualityRows.childCount == 0) 0 else dp(14f), 0, dp(8f))
        }
    }

    private fun qualityRow(entry: QualityEntry, selected: Boolean): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isSelected = selected
            isClickable = true
            isFocusable = true
            minimumHeight = dp(48f)
            background = rowBackground(colors.row, colors.selectedRow)
            setPadding(dp(14f), dp(9f), dp(14f), dp(9f))
            addView(TextView(context).apply {
                text = entry.label
                setTextColor(if (selected) colors.selectedText else colors.onPanel)
                textSize = 17f
                includeFontPadding = false
            })
            entry.subtitleRes?.let { subtitleRes ->
                addView(TextView(context).apply {
                    text = getString(subtitleRes)
                    setTextColor(colors.secondaryText)
                    textSize = 12f
                    includeFontPadding = false
                    setPadding(0, dp(5f), 0, 0)
                })
            }
            setOnClickListener {
                (parentFragment as? PlayerFragment)?.selectQuality(entry.tag)
                dismiss()
            }
        }
    }

    private fun constrainScrollHeight(entryCount: Int) {
        val estimatedHeight = dp(72f) + entryCount * dp(58f)
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
            row = ColorUtils.blendARGB(panel, onPanel, rowBlend),
            selectedRow = ColorUtils.blendARGB(panel, primary, selectedBlend),
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

    private fun rowBackground(normalColor: Int, selectedColor: Int): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), roundedDrawable(selectedColor, 13f))
            addState(intArrayOf(), roundedDrawable(normalColor, 13f))
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
    ) {
        val isAudioChatMode = tag == AUDIO_ONLY || tag == CHAT_ONLY
        val subtitleRes = when (tag) {
            AUDIO_ONLY -> R.string.audio_only_quality_summary
            CHAT_ONLY -> R.string.chat_only_quality_summary
            else -> null
        }
    }

    private data class QualityDialogColors(
        val panel: Int,
        val onPanel: Int,
        val selectedText: Int,
        val secondaryText: Int,
        val panelStroke: Int,
        val row: Int,
        val selectedRow: Int,
        val handle: Int,
    )
}
