package com.github.andreyasadchy.xtra.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * Custom pie chart view for displaying category watch time breakdown.
 * Automatically adapts to light/dark theme.
 */
class CategoryPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(
        val label: String,
        val value: Float,
        val color: Int
    )

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()
    private var slices: List<Slice> = emptyList()

    companion object {
        // Vibrant colors that work on both light and dark themes
        private val DEFAULT_COLORS = listOf(
            Color.parseColor("#6366F1"), // Indigo
            Color.parseColor("#F97316"), // Orange
            Color.parseColor("#22C55E"), // Green
            Color.parseColor("#A855F7"), // Purple
            Color.parseColor("#EF4444"), // Red
            Color.parseColor("#06B6D4"), // Cyan
            Color.parseColor("#EAB308"), // Yellow
            Color.parseColor("#EC4899"), // Pink
        )
    }

    init {
        // Get theme text color for "No data" message
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        val textColor = if (typedValue.resourceId != 0) {
            context.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }
        textPaint.color = textColor
    }

    fun setData(data: List<Pair<String, Long>>) {
        val total = data.sumOf { it.second }.toFloat()
        if (total == 0f) {
            slices = emptyList()
            invalidate()
            return
        }

        slices = data.take(8).mapIndexed { index, (label, value) ->
            Slice(
                label = label,
                value = value.toFloat() / total * 360f,
                color = DEFAULT_COLORS[index % DEFAULT_COLORS.size]
            )
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (slices.isEmpty()) {
            canvas.drawText("No data", width / 2f, height / 2f, textPaint)
            return
        }

        val padding = 24f
        val size = minOf(width, height) - padding * 2
        val left = (width - size) / 2
        val top = (height - size) / 2
        rect.set(left, top, left + size, top + size)

        var startAngle = -90f
        slices.forEach { slice ->
            slicePaint.color = slice.color
            canvas.drawArc(rect, startAngle, slice.value, true, slicePaint)
            startAngle += slice.value
        }
    }

    fun getSlices(): List<Slice> = slices
}
