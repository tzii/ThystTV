package com.github.andreyasadchy.xtra.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import kotlin.math.PI
import kotlin.math.sin

class SquiggleDividerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(3f)
    }
    private var phase = 0f
    private var animator: ValueAnimator? = null

    init {
        paint.color = MaterialColors.getColor(
            this,
            androidx.appcompat.R.attr.colorPrimary,
            ContextCompat.getColor(context, android.R.color.holo_blue_light)
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode) return
        animator = ValueAnimator.ofFloat(0f, (2f * PI).toFloat()).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val centerY = height / 2f
        val amplitude = height * 0.18f
        val wavelength = width / 8f
        path.reset()
        path.moveTo(0f, centerY)
        var x = 0f
        while (x <= width) {
            val y = centerY + sin((x / wavelength) * 2f * PI + phase) * amplitude
            path.lineTo(x, y.toFloat())
            x += dp(3f)
        }
        canvas.drawPath(path, paint)
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }
}
