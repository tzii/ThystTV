package com.github.andreyasadchy.xtra.ui.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Vertical Material 3 Expressive slider for brightness and device volume, at
 * the edge on the finger's side.
 *
 * The same Medium slider as the horizontal one, standing up: the accent part
 * grows from the bottom, a 4 dp handle with 6 dp gaps marks the level, and a
 * stop dot marks the top of the range. There is no number. The icon sits
 * inside the bottom of the accent part; when that part gets too short, the
 * icon moves above the handle.
 */
class PlayerGestureLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val pad = PlayerGestureSliderView.SHADOW_PAD_DP * density
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var colors: PlayerGestureFeedbackColors? = null
    private var icon: Drawable? = null
    @DrawableRes
    private var iconRes = 0
    private var levelAnimator: ValueAnimator? = null

    /** Where the icon sits for the drawn level. */
    enum class IconPlace { NONE, IN_FILL, ABOVE_HANDLE }

    /** Target level, 0-100. */
    var level = 0
        private set

    /** Level currently drawn; trails [level] while a step animates. */
    var displayedLevel = 0f
        private set

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            for (paint in listOf(activePaint, inactivePaint, handlePaint)) {
                paint.setShadowLayer(
                    PlayerGestureSliderView.SHADOW_RADIUS_DP * density,
                    0f,
                    PlayerGestureSliderView.SHADOW_DY_DP * density,
                    PlayerGestureSliderView.SHADOW_COLOR,
                )
            }
        }
    }

    fun setColors(colors: PlayerGestureFeedbackColors) {
        if (colors == this.colors) return
        this.colors = colors
        activePaint.color = colors.accent
        inactivePaint.color = colors.container
        handlePaint.color = colors.accent
        dotPaint.color = colors.accent
        invalidate()
    }

    fun setIcon(@DrawableRes res: Int) {
        if (res == iconRes && icon != null) return
        iconRes = res
        icon = AppCompatResources.getDrawable(context, res)?.mutate()
        invalidate()
    }

    /**
     * Moves the handle to [target]. A visible slider glides between steps so
     * volume's coarse steps read as motion; a freshly shown one jumps, so it
     * never sweeps from the previous gesture's level.
     */
    fun setLevel(target: Int, animate: Boolean) {
        val clamped = target.coerceIn(0, 100)
        level = clamped
        levelAnimator?.cancel()
        levelAnimator = null
        if (!animate || !isAttachedToWindow || displayedLevel == clamped.toFloat()) {
            displayedLevel = clamped.toFloat()
            invalidate()
            return
        }
        levelAnimator = ValueAnimator.ofFloat(displayedLevel, clamped.toFloat()).apply {
            duration = LEVEL_STEP_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                displayedLevel = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        levelAnimator?.cancel()
        levelAnimator = null
        displayedLevel = level.toFloat()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        levelAnimator?.cancel()
        levelAnimator = null
        displayedLevel = level.toFloat()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (PlayerGestureSliderView.HANDLE_LENGTH_DP * density + 2f * pad).toInt()
        val desiredHeight = (PlayerSurfacePolicy.EDGE_SLIDER_HEIGHT_DP * density + 2f * pad).toInt()
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    /** The slider's extent inside the shadow room, in view pixels. */
    internal fun trackBounds(): RectF = RectF(pad, pad, width - pad, height - pad)

    /** Y of the handle's center for the drawn level, in view pixels. */
    internal fun handleCenterY(): Float = handleBottom() - PlayerGestureSliderView.HANDLE_WIDTH_DP * density / 2f

    /** Height of the accent part below the handle. */
    internal fun fillHeight(): Float {
        val gap = PlayerGestureSliderView.GAP_DP * density
        return (height - pad - (handleBottom() + gap)).coerceAtLeast(0f)
    }

    /** Height of the dark part above the handle. */
    internal fun restHeight(): Float {
        val gap = PlayerGestureSliderView.GAP_DP * density
        val handleTop = handleBottom() - PlayerGestureSliderView.HANDLE_WIDTH_DP * density
        return (handleTop - gap - pad).coerceAtLeast(0f)
    }

    internal fun iconPlace(): IconPlace {
        val block = ICON_BLOCK_DP * density
        return when {
            icon == null -> IconPlace.NONE
            fillHeight() >= block -> IconPlace.IN_FILL
            restHeight() >= block -> IconPlace.ABOVE_HANDLE
            else -> IconPlace.NONE
        }
    }

    /** Whether the stop dot at the top of the range is drawn. */
    internal fun stopDotVisible(): Boolean {
        val dotBottom = STOP_DOT_CENTER_DP * density + PlayerGestureSliderView.DOT_DP * density / 2f
        val iconTop = if (iconPlace() == IconPlace.ABOVE_HANDLE) iconTop() - pad else Float.MAX_VALUE
        val clearance = PlayerGestureSliderView.DOT_CLEARANCE_DP * density
        return restHeight() >= dotBottom + clearance && iconTop >= dotBottom + clearance
    }

    /** Bottom edge of the handle for the drawn level, in view pixels. */
    private fun handleBottom(): Float {
        val handleWidth = PlayerGestureSliderView.HANDLE_WIDTH_DP * density
        val usable = (height - 2f * pad - handleWidth).coerceAtLeast(0f)
        return height - pad - usable * (displayedLevel.coerceIn(0f, 100f) / 100f)
    }

    private fun iconTop(): Float {
        val size = PlayerGestureSliderView.ICON_DP * density
        val inset = PlayerGestureSliderView.ICON_INSET_DP * density
        return when (iconPlace()) {
            IconPlace.IN_FILL -> height - pad - inset - size
            else -> {
                val gap = PlayerGestureSliderView.GAP_DP * density
                val handleTop = handleBottom() - PlayerGestureSliderView.HANDLE_WIDTH_DP * density
                handleTop - gap - inset - size
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val palette = colors ?: return
        if (width <= 0 || height <= 0) return
        val centerX = width / 2f
        val trackHalf = PlayerGestureSliderView.TRACK_DP * density / 2f
        val gap = PlayerGestureSliderView.GAP_DP * density
        val outer = PlayerGestureSliderView.OUTER_CORNER_DP * density
        val inner = PlayerGestureSliderView.INNER_CORNER_DP * density
        val handleWidth = PlayerGestureSliderView.HANDLE_WIDTH_DP * density
        val handleBottom = handleBottom()
        val handleTop = handleBottom - handleWidth

        rect.set(centerX - trackHalf, pad, centerX + trackHalf, handleTop - gap)
        drawPart(canvas, path, rect, startRadius = inner, endRadius = outer, horizontal = false, paint = inactivePaint)
        rect.set(centerX - trackHalf, handleBottom + gap, centerX + trackHalf, height - pad)
        drawPart(canvas, path, rect, startRadius = outer, endRadius = inner, horizontal = false, paint = activePaint)

        val handleHalf = PlayerGestureSliderView.HANDLE_LENGTH_DP * density / 2f
        rect.set(centerX - handleHalf, handleTop, centerX + handleHalf, handleBottom)
        canvas.drawRoundRect(rect, handleWidth / 2f, handleWidth / 2f, handlePaint)

        if (stopDotVisible()) {
            canvas.drawCircle(centerX, pad + STOP_DOT_CENTER_DP * density, PlayerGestureSliderView.DOT_DP * density / 2f, dotPaint)
        }

        val drawable = icon ?: return
        val place = iconPlace()
        if (place == IconPlace.NONE) return
        val size = (PlayerGestureSliderView.ICON_DP * density).toInt()
        val top = iconTop().toInt()
        val left = (centerX - size / 2f).toInt()
        drawable.setBounds(left, top, left + size, top + size)
        DrawableCompat.setTint(drawable, if (place == IconPlace.IN_FILL) palette.onAccent else palette.content)
        drawable.draw(canvas)
    }

    companion object {
        /** Room an icon needs inside a part: inset, icon, inset. */
        const val ICON_BLOCK_DP = 40f

        /** The stop dot's center, measured from the top of the track. */
        const val STOP_DOT_CENTER_DP = 10f
        const val LEVEL_STEP_MS = 120L
    }
}
