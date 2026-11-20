package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.abs
import kotlin.math.min

/**
 * Horizontal Material 3 Expressive slider for seek, playback speed and pinch
 * zoom, shown at the top of the video.
 *
 * Medium size: a 40 dp track split by a 4 dp handle with 6 dp gaps, the
 * active part in the accent color and the rest on a dark translucent track.
 * The icon sits inside the start of the active part and the value, the only
 * text, at the end of the other part. When a part gets too short, its icon or
 * value moves across the handle, as Expressive sliders do with their icons. A
 * dot marks where the gesture started. An optional caption, a small dark
 * pill centered under the track, holds a seek's target time and the video's
 * length; it stays put while the handle moves. The track draws in physical
 * left-to-right order, matching the drag: right is always later, faster or
 * closer to Fill.
 *
 * It redraws on every touch update, so it draws only plain shapes: a blurred
 * shadow under shapes that change each frame costs offscreen passes per frame
 * and made the sliders stutter. The near-opaque dark track keeps it readable.
 */
class PlayerGestureSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Which part of the track an icon or the value sits in. */
    enum class Side { NONE, ACTIVE, INACTIVE }

    private val density = resources.displayMetrics.density
    private val pad = PAD_DP * density
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
        // Tabular digits keep the changing value from shimmering.
        fontFeatureSettings = "tnum"
    }
    private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
    }
    private val captionBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val captionRect = RectF()
    private val path = Path()
    private val rect = RectF()
    private var colors: PlayerGestureFeedbackColors? = null
    private var icon: Drawable? = null
    @DrawableRes
    private var iconRes = 0

    /** Where the handle is, 0-1. */
    var fraction = 0f
        private set

    /** Where the gesture started, 0-1, or null for no dot. */
    var startFraction: Float? = null
        private set

    var label: String? = null
        private set

    /** Small fixed line under the track, or null for none. */
    var caption: String? = null
        private set

    fun setColors(colors: PlayerGestureFeedbackColors) {
        if (colors == this.colors) return
        this.colors = colors
        activePaint.color = colors.accent
        inactivePaint.color = colors.container
        handlePaint.color = colors.accent
        captionBackgroundPaint.color = colors.container
        captionPaint.color = colors.content
        invalidate()
    }

    /** The gesture's icon; 0 for none. */
    fun setIcon(@DrawableRes res: Int) {
        if (res == iconRes) return
        iconRes = res
        icon = if (res == 0) null else AppCompatResources.getDrawable(context, res)?.mutate()
        invalidate()
    }

    /** Updates the slider in place; its size never changes, so no layout pass. */
    fun setReading(fraction: Float, startFraction: Float?, label: String?, caption: String? = null) {
        val clamped = fraction.coerceIn(0f, 1f)
        val start = startFraction?.coerceIn(0f, 1f)
        if (clamped == this.fraction && start == this.startFraction && label == this.label && caption == this.caption) return
        // The caption band is part of the measured height: lay out again only
        // when a caption appears or goes, never for a new caption text.
        val bandChanged = caption.isNullOrEmpty() != this.caption.isNullOrEmpty()
        this.fraction = clamped
        this.startFraction = start
        this.label = label
        this.caption = caption
        if (bandChanged) requestLayout()
        invalidate()
    }

    fun clear() {
        fraction = 0f
        startFraction = null
        label = null
        caption = null
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (WIDTH_DP * density + 2f * pad).toInt()
        val captionBand = if (caption.isNullOrEmpty()) 0f else CAPTION_BAND_DP * density
        val desiredHeight = (HANDLE_LENGTH_DP * density + captionBand + 2f * pad).toInt()
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    /** Left and right ends of the track, in view pixels. */
    internal fun trackLeft(): Float = pad

    internal fun trackRight(): Float = width - pad

    /** X of the handle's center, in view pixels. */
    internal fun handleCenterX(): Float = handleLeft() + HANDLE_WIDTH_DP * density / 2f

    /** X of the start dot's center, or null when no dot is drawn. */
    internal fun startDotX(): Float? = layout().dotX

    internal fun iconSide(): Side = layout().iconSide

    internal fun labelSide(): Side = layout().labelSide

    /** Left and right of the drawn value, in view pixels. */
    internal fun labelBounds(): Pair<Float, Float>? = layout().labelBounds

    /** Left and right of the drawn icon, in view pixels. */
    internal fun iconBounds(): Pair<Float, Float>? = layout().iconBounds

    /** Y of the track's center, in view pixels: the top of the view, below the pad. */
    internal fun trackCenterY(): Float = pad + HANDLE_LENGTH_DP * density / 2f

    /**
     * The caption's pill, in view pixels, or null when there is no caption or
     * no room under the track for it. The text shrinks to fit the track's width.
     */
    internal fun captionBounds(): RectF? {
        val text = caption
        if (text.isNullOrEmpty()) return null
        val top = pad + HANDLE_LENGTH_DP * density + CAPTION_GAP_DP * density
        val boxHeight = CAPTION_HEIGHT_DP * density
        if (top + boxHeight > height - pad + 0.5f) return null
        val padH = CAPTION_PAD_DP * density
        val room = trackRight() - trackLeft()
        captionPaint.textSize = sp(CAPTION_SP)
        val measured = captionPaint.measureText(text)
        if (measured + 2f * padH > room && measured > 0f) {
            captionPaint.textSize *= ((room - 2f * padH).coerceAtLeast(0f) / measured)
        }
        val boxWidth = (captionPaint.measureText(text) + 2f * padH).coerceAtMost(room)
        val centerX = (trackLeft() + trackRight()) / 2f
        captionRect.set(centerX - boxWidth / 2f, top, centerX + boxWidth / 2f, top + boxHeight)
        return captionRect
    }

    private fun handleLeft(): Float {
        val usable = (trackRight() - trackLeft() - HANDLE_WIDTH_DP * density).coerceAtLeast(0f)
        return trackLeft() + usable * fraction
    }

    /** Where the parts, the icon, the value and the dot go for the current reading. */
    private class Layout(
        val activeRight: Float,
        val inactiveLeft: Float,
        val iconSide: Side,
        val iconBounds: Pair<Float, Float>?,
        val labelSide: Side,
        val labelBounds: Pair<Float, Float>?,
        val labelSize: Float,
        val dotX: Float?,
    )

    private fun layout(): Layout {
        val gap = GAP_DP * density
        val handleWidth = HANDLE_WIDTH_DP * density
        val left = trackLeft()
        val right = trackRight()
        val handleLeft = handleLeft()
        val activeRight = handleLeft - gap
        val inactiveLeft = handleLeft + handleWidth + gap
        val activeWidth = (activeRight - left).coerceAtLeast(0f)
        val inactiveWidth = (right - inactiveLeft).coerceAtLeast(0f)
        val labelInset = LABEL_INSET_DP * density
        val iconInset = ICON_INSET_DP * density
        val iconSize = ICON_DP * density

        // The value prefers the end of the inactive part; when that is too
        // short it moves to the end of the active part, beside the handle.
        labelPaint.textSize = sp(LABEL_SP)
        val text = label
        var labelWidth = if (text.isNullOrEmpty()) 0f else labelPaint.measureText(text)
        val labelSide = when {
            labelWidth == 0f -> Side.NONE
            inactiveWidth >= labelWidth + 2f * labelInset -> Side.INACTIVE
            activeWidth >= labelWidth + 2f * labelInset -> Side.ACTIVE
            inactiveWidth >= activeWidth -> Side.INACTIVE
            else -> Side.ACTIVE
        }
        if (labelSide != Side.NONE) {
            // Neither part fits the full value on a very narrow slider: shrink it.
            val room = (if (labelSide == Side.INACTIVE) inactiveWidth else activeWidth) - 2f * labelInset
            if (labelWidth > room && labelWidth > 0f) {
                labelPaint.textSize = (labelPaint.textSize * room.coerceAtLeast(0f) / labelWidth)
                    .coerceAtLeast(sp(LABEL_MIN_SP))
                labelWidth = labelPaint.measureText(text)
            }
        }
        val labelRight = when (labelSide) {
            Side.INACTIVE -> right - labelInset
            Side.ACTIVE -> activeRight - labelInset
            Side.NONE -> 0f
        }
        val labelBounds = if (labelSide == Side.NONE) null else (labelRight - labelWidth) to labelRight

        // The icon prefers the start of the active part, then the start of
        // the inactive part, and leaves room for a value sharing its part.
        val iconBlock = iconInset + iconSize + iconInset
        val labelBlock = labelWidth + labelInset
        val iconSide = when {
            icon == null -> Side.NONE
            activeWidth >= iconBlock + (if (labelSide == Side.ACTIVE) labelBlock else 0f) -> Side.ACTIVE
            inactiveWidth >= iconBlock + (if (labelSide == Side.INACTIVE) labelBlock else 0f) -> Side.INACTIVE
            else -> Side.NONE
        }
        val iconLeft = when (iconSide) {
            Side.ACTIVE -> left + iconInset
            Side.INACTIVE -> inactiveLeft + iconInset
            Side.NONE -> 0f
        }
        val iconBounds = if (iconSide == Side.NONE) null else iconLeft to iconLeft + iconSize

        // The start dot, unless the handle, the icon or the value covers it.
        val dotX = startFraction?.let { start ->
            val usable = (right - left - handleWidth).coerceAtLeast(0f)
            val x = left + usable * start + handleWidth / 2f
            val clearance = DOT_DP * density / 2f + DOT_CLEARANCE_DP * density
            val nearHandle = abs(x - (handleLeft + handleWidth / 2f)) < handleWidth / 2f + gap + clearance
            val onIcon = iconBounds != null && x > iconBounds.first - clearance && x < iconBounds.second + clearance
            val onLabel = labelBounds != null && x > labelBounds.first - clearance && x < labelBounds.second + clearance
            val inTrack = x - clearance >= left && x + clearance <= right
            if (nearHandle || onIcon || onLabel || !inTrack) null else x
        }
        return Layout(activeRight, inactiveLeft, iconSide, iconBounds, labelSide, labelBounds, labelPaint.textSize, dotX)
    }

    override fun onDraw(canvas: Canvas) {
        val palette = colors ?: return
        if (width <= 0 || height <= 0) return
        val layout = layout()
        val left = trackLeft()
        val right = trackRight()
        val centerY = trackCenterY()
        val trackHalf = TRACK_DP * density / 2f
        val outer = OUTER_CORNER_DP * density
        val inner = INNER_CORNER_DP * density

        rect.set(layout.inactiveLeft, centerY - trackHalf, right, centerY + trackHalf)
        drawPart(canvas, path, rect, startRadius = inner, endRadius = outer, horizontal = true, paint = inactivePaint)
        rect.set(left, centerY - trackHalf, layout.activeRight, centerY + trackHalf)
        drawPart(canvas, path, rect, startRadius = outer, endRadius = inner, horizontal = true, paint = activePaint)

        val handleLeft = handleLeft()
        val handleWidth = HANDLE_WIDTH_DP * density
        val handleHalf = HANDLE_LENGTH_DP * density / 2f
        rect.set(handleLeft, centerY - handleHalf, handleLeft + handleWidth, centerY + handleHalf)
        canvas.drawRoundRect(rect, handleWidth / 2f, handleWidth / 2f, handlePaint)

        layout.dotX?.let { x ->
            dotPaint.color = if (x < handleLeft) palette.onAccent else palette.accent
            canvas.drawCircle(x, centerY, DOT_DP * density / 2f, dotPaint)
        }

        val drawable = icon
        val iconBounds = layout.iconBounds
        if (drawable != null && iconBounds != null) {
            val size = ICON_DP * density
            val top = (centerY - size / 2f).toInt()
            drawable.setBounds(iconBounds.first.toInt(), top, iconBounds.first.toInt() + size.toInt(), top + size.toInt())
            DrawableCompat.setTint(drawable, if (layout.iconSide == Side.ACTIVE) palette.onAccent else palette.content)
            drawable.draw(canvas)
        }

        val text = label
        val labelBounds = layout.labelBounds
        if (!text.isNullOrEmpty() && labelBounds != null) {
            labelPaint.textSize = layout.labelSize
            labelPaint.color = if (layout.labelSide == Side.ACTIVE) palette.onAccent else palette.content
            val baseline = centerY - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(text, labelBounds.second, baseline, labelPaint)
        }

        val captionText = caption
        val box = captionBounds()
        if (!captionText.isNullOrEmpty() && box != null) {
            canvas.drawRoundRect(box, box.height() / 2f, box.height() / 2f, captionBackgroundPaint)
            val baseline = box.centerY() - (captionPaint.descent() + captionPaint.ascent()) / 2f
            canvas.drawText(captionText, box.centerX(), baseline, captionPaint)
        }
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    }

    companion object {
        /** Slider width on regular players and on short or narrow ones. */
        const val WIDTH_DP = 300f
        const val COMPACT_WIDTH_DP = 220f

        /** Medium Expressive slider: track, handle and spacing. */
        const val TRACK_DP = 40f
        const val HANDLE_LENGTH_DP = 44f
        const val HANDLE_WIDTH_DP = 4f
        const val GAP_DP = 6f
        const val OUTER_CORNER_DP = 12f
        const val INNER_CORNER_DP = 2f
        const val DOT_DP = 4f

        /** Space kept between the start dot and the handle, icon or value. */
        const val DOT_CLEARANCE_DP = 4f
        const val ICON_DP = 24f
        const val ICON_INSET_DP = 8f
        const val LABEL_INSET_DP = 12f
        const val LABEL_SP = 16f
        const val LABEL_MIN_SP = 11f

        /** The caption's pill under the track: gap below the handle, height, side padding and type. */
        const val CAPTION_GAP_DP = 6f
        const val CAPTION_HEIGHT_DP = 24f
        const val CAPTION_PAD_DP = 10f
        const val CAPTION_SP = 13f
        const val CAPTION_BAND_DP = CAPTION_GAP_DP + CAPTION_HEIGHT_DP

        /**
         * Room around the drawn slider, included in the view's size, so the
         * entrance spring's slight overshoot is never clipped.
         */
        const val PAD_DP = 4f
    }
}

/**
 * Draws one part of an Expressive slider track: [startRadius] on the corners
 * at its start (left, or bottom when not [horizontal]) and [endRadius] at its
 * end, each clamped to half the part's size.
 */
internal fun drawPart(
    canvas: Canvas,
    path: Path,
    rect: RectF,
    startRadius: Float,
    endRadius: Float,
    horizontal: Boolean,
    paint: Paint,
) {
    if (rect.width() < 1f || rect.height() < 1f) return
    val limit = min(rect.width(), rect.height()) / 2f
    val start = min(startRadius, limit)
    val end = min(endRadius, limit)
    // Corner order: top-left, top-right, bottom-right, bottom-left.
    val radii = if (horizontal) {
        floatArrayOf(start, start, end, end, end, end, start, start)
    } else {
        floatArrayOf(end, end, end, end, start, start, start, start)
    }
    path.reset()
    path.addRoundRect(rect, radii, Path.Direction.CW)
    canvas.drawPath(path, paint)
}
