package com.github.andreyasadchy.xtra.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.github.andreyasadchy.xtra.ui.stats.StatsDataHelper

/**
 * Custom heatmap view for displaying peak viewing times by hour.
 * Automatically adapts to light/dark theme.
 */
class HourlyHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()
    
    // 24 hours data (0-23), value is normalized 0-1
    private var hourlyData: List<Float> = List(24) { 0f }

    // Theme-aware colors
    private val lowColor: Int
    private val highColor: Int

    init {
        // Get theme colors
        val typedValue = TypedValue()
        
        // Text color
        context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        val textColor = if (typedValue.resourceId != 0) {
            context.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }
        textPaint.color = textColor

        // Primary color for heatmap high
        context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        highColor = typedValue.data

        // Surface container for heatmap low
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, typedValue, true)
        lowColor = if (typedValue.resourceId != 0) {
            context.getColor(typedValue.resourceId)
        } else {
            // Fallback: derive from primary with low alpha
            Color.argb(40, Color.red(highColor), Color.green(highColor), Color.blue(highColor))
        }
    }

    fun setData(data: List<Pair<Int, Long>>) {
        hourlyData = StatsDataHelper.normalizeHeatmapData(data)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 8f
        val labelHeight = 30f
        val availableWidth = width - padding * 2
        val availableHeight = height - padding * 2 - labelHeight
        val cellWidth = availableWidth / 24f
        val cellHeight = availableHeight

        // Draw cells
        hourlyData.forEachIndexed { hour, value ->
            val left = padding + hour * cellWidth
            val top = padding
            rect.set(left + 1, top, left + cellWidth - 1, top + cellHeight)
            
            cellPaint.color = StatsDataHelper.interpolateColor(lowColor, highColor, value)
            canvas.drawRoundRect(rect, 6f, 6f, cellPaint)
        }

        // Draw labels for every 6 hours
        listOf(0, 6, 12, 18).forEach { hour ->
            val x = padding + hour * cellWidth + cellWidth / 2
            val y = height - padding
            val label = when (hour) {
                0 -> "12a"
                6 -> "6a"
                12 -> "12p"
                18 -> "6p"
                else -> ""
            }
            canvas.drawText(label, x, y, textPaint)
        }
    }
}
