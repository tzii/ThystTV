package com.github.andreyasadchy.xtra.ui.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import androidx.core.graphics.ColorUtils
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import kotlin.math.ceil

/**
 * A name painted with an image: the image shows through the name's glyphs
 * only, so whatever is behind the row (an app surface, a highlight, or video)
 * stays visible around them.
 */
class NamePaintImageSpan(
    private val name: String,
    private val shadows: List<NamePaint.Shadow>?,
    val drawable: Drawable,
) : ReplacementSpan() {

    private val maskPaint = Paint()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val paintFm = paint.fontMetrics
            fm.ascent = paintFm.ascent.toInt()
            fm.bottom = paintFm.bottom.toInt()
            fm.descent = paintFm.descent.toInt()
            fm.leading = paintFm.leading.toInt()
            fm.top = paintFm.top.toInt()
        }
        return paint.measureText(name).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val yOffset = y.toFloat()
        val pass = ChatHaloPass.current
        if (pass != ChatHaloPass.NONE) {
            // A Clear row outlines the name like its other text; the paint goes on top.
            val color = paint.color
            paint.color = ColorUtils.setAlphaComponent(Color.BLACK, Color.alpha(color))
            canvas.drawText(name, x, yOffset, paint)
            paint.color = color
            if (pass == ChatHaloPass.SOFT) return
        }
        val xOffset = x.toInt()
        val width = paint.measureText(name).toInt()
        val height = bottom - top
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        val widthRatio = drawableWidth.toFloat() / drawableHeight.toFloat()
        val fullWidth: Int
        val fullHeight: Int
        if (height > drawableHeight) {
            val addedWidth = ceil((height - drawableHeight) * widthRatio).toInt()
            val newWidth = drawableWidth + addedWidth
            if (width > newWidth) {
                val addedHeight = ceil((width - newWidth) / widthRatio).toInt()
                fullWidth = xOffset + width
                fullHeight = bottom + addedHeight
            } else {
                fullWidth = xOffset + newWidth
                fullHeight = bottom
            }
        } else {
            if (width > drawableWidth) {
                val addedHeight = ceil((width - drawableWidth) / widthRatio).toInt()
                fullWidth = xOffset + width
                fullHeight = top + drawableHeight + addedHeight
            } else {
                fullWidth = xOffset + drawableWidth
                fullHeight = top + drawableHeight
            }
        }
        shadows?.forEach {
            paint.setShadowLayer(it.radius, it.xOffset, it.yOffset, it.color)
            canvas.drawText(name, x, yOffset, paint)
        }
        paint.clearShadowLayer()
        if (fullWidth <= xOffset || fullHeight <= top) return
        // Draw the image in its own layer, then keep it only where the glyphs are.
        val layer = canvas.saveLayer(xOffset.toFloat(), top.toFloat(), fullWidth.toFloat(), fullHeight.toFloat(), null)
        drawable.setBounds(xOffset, top, fullWidth, fullHeight)
        drawable.draw(canvas)
        maskPaint.set(paint)
        maskPaint.style = Paint.Style.FILL
        maskPaint.color = Color.BLACK
        maskPaint.xfermode = KEEP_UNDER_GLYPHS
        canvas.drawText(name, x, yOffset, maskPaint)
        canvas.restoreToCount(layer)
    }

    private companion object {
        val KEEP_UNDER_GLYPHS = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
}
