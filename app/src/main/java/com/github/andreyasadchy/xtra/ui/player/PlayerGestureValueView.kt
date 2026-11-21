package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat

/**
 * The "number in the center" style: one large value in the middle of the
 * video, with no box. An optional icon sits above it (brightness and volume,
 * whose percentages would otherwise look alike) and an optional small line
 * under it (a seek's target time). A soft dark glow behind and a text shadow
 * keep it readable over bright video.
 */
class PlayerGestureValueView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val primaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        // Tabular digits keep the changing value from shimmering.
        fontFeatureSettings = "tnum"
    }
    private val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowMatrix = Matrix()
    private var glowSize = 0 to 0
    private var colors: PlayerGestureFeedbackColors? = null
    private var icon: Drawable? = null
    @DrawableRes
    private var iconRes = 0
    private var metrics = Metrics.REGULAR

    var value: String? = null
        private set

    var detail: String? = null
        private set

    /** Smaller type for short or narrow players. */
    var compact = false
        private set

    val hasIcon: Boolean get() = icon != null

    init {
        primaryPaint.setShadowLayer(TEXT_SHADOW_RADIUS_DP * density, 0f, TEXT_SHADOW_DY_DP * density, TEXT_SHADOW_COLOR)
        detailPaint.setShadowLayer(TEXT_SHADOW_RADIUS_DP * density, 0f, TEXT_SHADOW_DY_DP * density, TEXT_SHADOW_COLOR)
    }

    fun setColors(colors: PlayerGestureFeedbackColors) {
        if (colors == this.colors) return
        this.colors = colors
        primaryPaint.color = colors.content
        detailPaint.color = ColorUtils.setAlphaComponent(colors.content, DETAIL_ALPHA)
        icon?.let { DrawableCompat.setTint(it, colors.content) }
        invalidate()
    }

    fun setCompact(compact: Boolean) {
        if (compact == this.compact) return
        this.compact = compact
        metrics = if (compact) Metrics.COMPACT else Metrics.REGULAR
        requestLayout()
        invalidate()
    }

    /** The icon above the value; 0 for none. */
    fun setIcon(@DrawableRes res: Int) {
        if (res == iconRes) return
        iconRes = res
        icon = if (res == 0) null else AppCompatResources.getDrawable(context, res)?.mutate()?.also { drawable ->
            colors?.let { DrawableCompat.setTint(drawable, it.content) }
        }
        invalidate()
    }

    /** Updates the texts in place; the view keeps its size, so no layout pass. */
    fun setText(value: String?, detail: String?) {
        if (value == this.value && detail == this.detail) return
        this.value = value
        this.detail = detail
        invalidate()
    }

    fun clear() {
        value = null
        detail = null
        setIcon(0)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (metrics.widthDp * density).toInt()
        val desiredHeight = (metrics.heightDp * density).toInt()
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    /** Width the value is drawn at, after shrinking to fit. */
    internal fun valueDrawnWidth(): Float {
        sizeText()
        return primaryPaint.measureText(value.orEmpty())
    }

    /** Width the small line is drawn at, after shrinking to fit. */
    internal fun detailDrawnWidth(): Float {
        sizeText()
        return detailPaint.measureText(detail.orEmpty())
    }

    /** Room for text between the side insets. */
    internal fun textRoom(): Float = width - 2f * SIDE_INSET_DP * density

    /** Top and bottom of the icon, value and small line together, in view pixels. */
    internal fun contentBounds(): Pair<Float, Float> {
        sizeText()
        val top = (height - contentHeight()) / 2f
        return top to top + contentHeight()
    }

    private fun sizeText() {
        primaryPaint.textSize = sp(metrics.valueSp)
        detailPaint.textSize = sp(metrics.detailSp)
        fit(primaryPaint, value)
        fit(detailPaint, detail)
    }

    /** Shrinks [paint] until [text] fits between the side insets. */
    private fun fit(paint: TextPaint, text: String?) {
        if (text.isNullOrEmpty()) return
        val room = textRoom()
        if (room <= 0f) return
        val measured = paint.measureText(text)
        if (measured > room) paint.textSize *= room / measured
    }

    private fun lineHeight(paint: Paint): Float = paint.fontMetrics.let { it.descent - it.ascent }

    private fun contentHeight(): Float {
        var total = 0f
        var rows = 0
        if (icon != null) {
            total += metrics.iconDp * density
            rows++
        }
        if (!value.isNullOrEmpty()) {
            total += lineHeight(primaryPaint)
            rows++
        }
        if (!detail.isNullOrEmpty()) {
            total += lineHeight(detailPaint)
            rows++
        }
        return total + (rows - 1).coerceAtLeast(0) * ROW_GAP_DP * density
    }

    override fun onDraw(canvas: Canvas) {
        if (colors == null || width <= 0 || height <= 0) return
        drawGlow(canvas)
        sizeText()
        val centerX = width / 2f
        var y = (height - contentHeight()) / 2f
        val rowGap = ROW_GAP_DP * density
        icon?.let { drawable ->
            val size = (metrics.iconDp * density).toInt()
            val left = (centerX - size / 2f).toInt()
            drawable.setBounds(left, y.toInt(), left + size, y.toInt() + size)
            drawable.draw(canvas)
            y += size + rowGap
        }
        value?.takeIf { it.isNotEmpty() }?.let {
            canvas.drawText(it, centerX, y - primaryPaint.fontMetrics.ascent, primaryPaint)
            y += lineHeight(primaryPaint) + rowGap
        }
        detail?.takeIf { it.isNotEmpty() }?.let {
            canvas.drawText(it, centerX, y - detailPaint.fontMetrics.ascent, detailPaint)
        }
    }

    /** An elliptical dark glow filling the view, darkest behind the value. */
    private fun drawGlow(canvas: Canvas) {
        if (glowSize != width to height) {
            glowSize = width to height
            val radius = height / 2f
            glowPaint.shader = RadialGradient(
                0f, 0f, radius,
                intArrayOf(GLOW_CENTER, GLOW_MIDDLE, 0),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            ).apply {
                glowMatrix.reset()
                glowMatrix.setScale(width / height.toFloat(), 1f)
                glowMatrix.postTranslate(width / 2f, height / 2f)
                setLocalMatrix(glowMatrix)
            }
        }
        canvas.drawOval(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    }

    /** Type and glow size for one player size. */
    private enum class Metrics(
        val widthDp: Float,
        val heightDp: Float,
        val valueSp: Float,
        val detailSp: Float,
        val iconDp: Float,
    ) {
        REGULAR(WIDTH_DP, HEIGHT_DP, 48f, 16f, 40f),
        COMPACT(COMPACT_WIDTH_DP, COMPACT_HEIGHT_DP, 36f, 13f, 30f),
    }

    companion object {
        /** The glow's size, which is also the view's. */
        const val WIDTH_DP = 300f
        const val HEIGHT_DP = 180f
        const val COMPACT_WIDTH_DP = 220f
        const val COMPACT_HEIGHT_DP = 132f
        const val SIDE_INSET_DP = 24f
        const val ROW_GAP_DP = 2f
        const val DETAIL_ALPHA = 0xD9
        // 60% black behind the value keeps white text at 4.5:1 even on white video.
        const val GLOW_CENTER = 0x99000000.toInt()
        const val GLOW_MIDDLE = 0x70000000
        const val TEXT_SHADOW_RADIUS_DP = 3f
        const val TEXT_SHADOW_DY_DP = 1f
        const val TEXT_SHADOW_COLOR = 0x8C000000.toInt()
    }
}
