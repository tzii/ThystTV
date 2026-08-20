package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerQualityPopupBinding
import com.github.andreyasadchy.xtra.model.VideoQuality
import java.util.Locale

/** Binds Quality content inside the shared player popup host. */
internal class PlayerQualityPopupBinder(
    private val context: Context,
    private val binding: LayoutPlayerQualityPopupBinding,
    qualities: List<VideoQuality>,
    private val selectedTag: String?,
    private val panelWidthPx: Int,
    private val surfaceHeightPx: Int,
    private val onQualitySelected: (String) -> Unit,
    private val onDismissRequested: () -> Unit,
) {

    private val density = context.resources.displayMetrics.density
    private val colors = PlayerPanelTheme.resolve(context)
    private val options = PlayerQualityPopupModel.build(
        qualities = qualities,
        labels = PlayerQualityPopupLabels(
            auto = context.getString(R.string.auto),
            source = context.getString(R.string.source),
            audioOnly = context.getString(R.string.audio_only),
            chatOnly = context.getString(R.string.chat_only),
        ),
    )

    fun bind() {
        applyTheme()
        val video = options.filter { it.kind == PlayerQualityPopupOption.Kind.VIDEO }
        val utilities = options.filter { it.kind != PlayerQualityPopupOption.Kind.VIDEO }
        buildGrid(binding.qualityVideoRows, video, columns = 3, stableCodecHeight = video.any { it.codecLabel != null })
        if (utilities.isNotEmpty()) {
            binding.qualityUtilitySection.visibility = View.VISIBLE
            buildGrid(binding.qualityUtilityRows, utilities, columns = utilities.size.coerceAtMost(2), stableCodecHeight = false)
        } else {
            binding.qualityUtilitySection.visibility = View.GONE
        }
        constrainVideoScroll(video.size, utilities.isNotEmpty(), video.any { it.codecLabel != null })
    }

    fun dispose() {
        binding.qualityVideoRows.removeAllViews()
        binding.qualityUtilityRows.removeAllViews()
    }

    fun constrainTo(maxPanelHeight: Int) {
        val overflow = (binding.root.measuredHeight - maxPanelHeight).coerceAtLeast(0)
        if (overflow > 0) {
            binding.qualityVideoScroll.layoutParams = binding.qualityVideoScroll.layoutParams.apply {
                height = (binding.qualityVideoScroll.measuredHeight - overflow).coerceAtLeast(0)
            }
        }
    }

    private fun applyTheme() {
        binding.qualityPopupCard.setCardBackgroundColor(colors.panel)
        binding.qualityPopupCard.setStrokeColor(colors.panelStroke)
        binding.qualityPopupTitle.setTextColor(colors.onPanel)
        binding.videoQualitySectionTitle.setTextColor(colors.secondaryText)
        binding.audioChatSectionTitle.setTextColor(colors.secondaryText)
    }

    private fun buildGrid(
        target: LinearLayout,
        entries: List<PlayerQualityPopupOption>,
        columns: Int,
        stableCodecHeight: Boolean,
    ) {
        target.removeAllViews()
        if (entries.isEmpty()) return
        val horizontalGap = dp(8f)
        val availableWidth = panelWidthPx - dp(32f)
        val chipWidth = (availableWidth - horizontalGap * (columns - 1)) / columns
        entries.chunked(columns).forEachIndexed { rowIndex, rowEntries ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            target.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (rowIndex > 0) topMargin = dp(8f)
                },
            )
            rowEntries.forEachIndexed { chipIndex, option ->
                row.addView(
                    qualityChip(option, stableCodecHeight),
                    LinearLayout.LayoutParams(chipWidth, if (stableCodecHeight) dp(60f) else dp(48f)).apply {
                        if (chipIndex > 0) marginStart = horizontalGap
                    },
                )
            }
        }
    }

    private fun qualityChip(option: PlayerQualityPopupOption, stableCodecHeight: Boolean): View {
        val selected = isSelected(option)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = chipBackground(colors.controlFill, colors.selectedFill)
            isSelected = selected
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(option.primaryLabel, option.codecLabel).joinToString(", ")
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))

            addView(TextView(context).apply {
                text = option.primaryLabel
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                setTextColor(colors.onPanel)
                textSize = 15f
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            })
            if (stableCodecHeight) {
                addView(TextView(context).apply {
                    text = option.codecLabel.orEmpty()
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    setTextColor(colors.secondaryText)
                    textSize = 12f
                    visibility = if (option.codecLabel == null) View.INVISIBLE else View.VISIBLE
                })
            }
            setOnClickListener {
                onQualitySelected(option.tag)
                onDismissRequested()
            }
        }
    }

    private fun isSelected(option: PlayerQualityPopupOption): Boolean {
        val selected = selectedTag.normalizedValue()
        return option.tag.normalizedValue() == selected || when (option.kind) {
            PlayerQualityPopupOption.Kind.AUDIO_ONLY -> selected == "audio_only"
            PlayerQualityPopupOption.Kind.CHAT_ONLY -> selected == "chat_only"
            PlayerQualityPopupOption.Kind.VIDEO -> false
        }
    }

    private fun constrainVideoScroll(videoCount: Int, hasUtilityFooter: Boolean, codecRows: Boolean) {
        val rows = (videoCount + 2) / 3
        val rowHeight = dp(if (codecRows) 60f else 48f)
        val estimated = rows * rowHeight + (rows - 1).coerceAtLeast(0) * dp(8f)
        val reserved = dp(if (hasUtilityFooter) 190f else 112f)
        val available = (surfaceHeightPx - reserved).coerceAtLeast(dp(96f))
        val maxVideoHeight = minOf(dp(240f), available)
        binding.qualityVideoScroll.layoutParams = binding.qualityVideoScroll.layoutParams.apply {
            height = if (estimated > maxVideoHeight) maxVideoHeight else ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun chipBackground(normalColor: Int, selectedColor: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected), roundedDrawable(selectedColor, 18f))
        addState(intArrayOf(), roundedDrawable(normalColor, 18f))
    }

    private fun roundedDrawable(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun String?.normalizedValue(): String = this
        ?.trim()
        ?.lowercase(Locale.US)
        ?.replace(' ', '_')
        ?.replace('-', '_')
        .orEmpty()

    private fun dp(value: Float): Int = (value * density).toInt()
}
