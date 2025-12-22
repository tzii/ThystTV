 package com.github.andreyasadchy.xtra.ui.view
 
 import android.content.Context
 import android.graphics.Canvas
 import android.graphics.Color
 import android.graphics.Paint
 import android.graphics.RectF
 import android.util.AttributeSet
 import android.view.View
 
 /**
  * Custom heatmap view for displaying peak viewing times by hour.
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
         color = Color.parseColor("#9E9E9E")
         textSize = 24f
         textAlign = Paint.Align.CENTER
     }
 
     private val rect = RectF()
     
     // 24 hours data (0-23), value is normalized 0-1
     private var hourlyData: List<Float> = List(24) { 0f }
 
     companion object {
         private val LOW_COLOR = Color.parseColor("#1E3A5F")
         private val HIGH_COLOR = Color.parseColor("#58A6FF")
     }
 
     fun setData(data: List<Pair<Int, Long>>) {
         val maxValue = data.maxOfOrNull { it.second }?.toFloat() ?: 1f
         if (maxValue == 0f) {
             hourlyData = List(24) { 0f }
             invalidate()
             return
         }
 
         val dataMap = data.associate { it.first to it.second }
         hourlyData = (0..23).map { hour ->
             (dataMap[hour]?.toFloat() ?: 0f) / maxValue
         }
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
             
             cellPaint.color = interpolateColor(LOW_COLOR, HIGH_COLOR, value)
             canvas.drawRoundRect(rect, 4f, 4f, cellPaint)
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
 
     private fun interpolateColor(start: Int, end: Int, fraction: Float): Int {
         val f = fraction.coerceIn(0f, 1f)
         val startA = Color.alpha(start)
         val startR = Color.red(start)
         val startG = Color.green(start)
         val startB = Color.blue(start)
         val endA = Color.alpha(end)
         val endR = Color.red(end)
         val endG = Color.green(end)
         val endB = Color.blue(end)
         return Color.argb(
             (startA + (endA - startA) * f).toInt(),
             (startR + (endR - startR) * f).toInt(),
             (startG + (endG - startG) * f).toInt(),
             (startB + (endB - startB) * f).toInt()
         )
     }
 }
