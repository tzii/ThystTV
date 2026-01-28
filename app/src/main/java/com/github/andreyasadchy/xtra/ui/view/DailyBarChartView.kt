package com.github.andreyasadchy.xtra.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.stats.StatsDataHelper
import kotlin.math.max

/**
 * Custom bar chart view for displaying daily screen time.
 * Shows 7 days of data with animated bars, grid lines, and day labels.
 * Automatically adapts to light/dark theme.
 */
class DailyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DayData(
        val label: String,      // e.g., "Mon", "Tue", "Today"
        val seconds: Long       // Total seconds watched
    )

    private var data: List<DayData> = emptyList()
    private var animationProgress = 1f
    private var maxSeconds: Long = 6 * 3600L  // Default max 6 hours

    // Theme colors
    private val primaryColor: Int
    private val onSurfaceColor: Int
    private val outlineColor: Int

    // Paints
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private val gridLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        textAlign = Paint.Align.RIGHT
    }

    private val barRect = RectF()
    private val barCornerRadius = 12f

    // Margins and spacing
    private val leftMargin = 80f     // Space for grid labels
    private val rightMargin = 16f
    private val topMargin = 16f
    private val bottomMargin = 48f   // Space for day labels
    private val barSpacing = 0.25f   // Spacing between bars as fraction of bar width

    init {
        // Get theme colors
        val typedValue = TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        primaryColor = typedValue.data

        context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        val textColorSecondary = if (typedValue.resourceId != 0) {
            context.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }
        onSurfaceColor = textColorSecondary

        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)
        outlineColor = if (typedValue.resourceId != 0) {
            context.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }

        // Apply theme colors (can be overridden by XML attrs)
        barPaint.color = primaryColor
        gridLinePaint.color = outlineColor
        labelPaint.color = onSurfaceColor
        gridLabelPaint.color = onSurfaceColor

        // Apply XML attributes if provided
        context.theme.obtainStyledAttributes(attrs, R.styleable.DailyBarChartView, 0, 0).apply {
            try {
                barPaint.color = getColor(R.styleable.DailyBarChartView_barColor, primaryColor)
                gridLinePaint.color = getColor(R.styleable.DailyBarChartView_gridLineColor, outlineColor)
                labelPaint.color = getColor(R.styleable.DailyBarChartView_labelColor, onSurfaceColor)
            } finally {
                recycle()
            }
        }
    }

    fun setData(dayData: List<DayData>, animate: Boolean = true) {
        data = dayData
        // Calculate max, ensure at least 6 hours for scale
        maxSeconds = max(data.maxOfOrNull { it.seconds } ?: 0L, 6 * 3600L)

        if (animate) {
            animationProgress = 0f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 800
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animationProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animationProgress = 1f
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = 280
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) return

        val chartWidth = width - leftMargin - rightMargin
        val chartHeight = height - topMargin - bottomMargin
        val chartBottom = height - bottomMargin

        // Draw horizontal grid lines (at 3hr and 6hr marks)
        val gridLines = listOf(
            3 * 3600L to "3 hr",
            6 * 3600L to "6 hr"
        )

        for ((seconds, label) in gridLines) {
            if (seconds <= maxSeconds) {
                val y = chartBottom - (seconds.toFloat() / maxSeconds * chartHeight)
                canvas.drawLine(leftMargin, y, width - rightMargin, y, gridLinePaint)
                canvas.drawText(label, leftMargin - 8f, y + 10f, gridLabelPaint)
            }
        }

        // Calculate bar dimensions
        val numBars = data.size
        val totalBarWidth = chartWidth / numBars
        val barWidth = totalBarWidth * (1 - barSpacing)
        val gap = totalBarWidth * barSpacing / 2

        // Draw bars and labels
        data.forEachIndexed { index, dayData ->
            val barLeft = leftMargin + index * totalBarWidth + gap
            val barRight = barLeft + barWidth

            // Animated bar height
            val barHeightRatio = if (maxSeconds > 0) dayData.seconds.toFloat() / maxSeconds else 0f
            val animatedHeight = chartHeight * barHeightRatio * animationProgress
            val barTop = chartBottom - animatedHeight

            // Draw bar with rounded top corners
            barRect.set(barLeft, barTop, barRight, chartBottom)
            canvas.drawRoundRect(barRect, barCornerRadius, barCornerRadius, barPaint)

            // Draw day label
            val labelX = barLeft + barWidth / 2
            val labelY = height - 12f
            canvas.drawText(dayData.label, labelX, labelY, labelPaint)
        }
    }
}
