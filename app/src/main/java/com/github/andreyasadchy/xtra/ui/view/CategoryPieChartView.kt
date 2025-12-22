 package com.github.andreyasadchy.xtra.ui.view
 
 import android.content.Context
 import android.graphics.Canvas
 import android.graphics.Color
 import android.graphics.Paint
 import android.graphics.RectF
 import android.util.AttributeSet
 import android.view.View
 
 /**
  * Custom pie chart view for displaying category watch time breakdown.
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
         color = Color.WHITE
         textSize = 32f
         textAlign = Paint.Align.CENTER
     }
 
     private val rect = RectF()
     private var slices: List<Slice> = emptyList()
 
     companion object {
         private val DEFAULT_COLORS = listOf(
             Color.parseColor("#58A6FF"), // Blue
             Color.parseColor("#F78166"), // Orange
             Color.parseColor("#7EE787"), // Green
             Color.parseColor("#D2A8FF"), // Purple
             Color.parseColor("#FF7B72"), // Red
             Color.parseColor("#79C0FF"), // Light Blue
             Color.parseColor("#FFA657"), // Yellow
             Color.parseColor("#A5D6FF"), // Cyan
         )
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
             textPaint.textSize = 40f
             canvas.drawText("No data", width / 2f, height / 2f, textPaint)
             return
         }
 
         val padding = 40f
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
