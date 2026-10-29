package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.max
import kotlin.math.min

/**
 * Pan geometry for a picture magnified past Fill. The surface scales about the
 * player's center and then translates, so a translation of t moves the
 * picture's center t pixels from the player's center. Everything is per axis:
 * pass widths for X and heights for Y. Zooms are relative to Fit, like
 * [PinchZoomController].
 */
object PlayerMagnifyGeometry {

    /** Size of the Fit picture in a [widthPx] x [heightPx] player. */
    fun fitSize(videoAspectRatio: Float, widthPx: Int, heightPx: Int): Pair<Float, Float> {
        val aspect = if (videoAspectRatio > 0f) videoAspectRatio else PlayerDisplayModePreviewer.DEFAULT_VIDEO_ASPECT_RATIO
        if (widthPx <= 0 || heightPx <= 0) return 0f to 0f
        val width = min(widthPx.toFloat(), heightPx * aspect)
        return width to width / aspect
    }

    /** Furthest the picture's center can move before one of its edges enters the player. */
    fun panLimit(fitExtent: Float, viewportExtent: Float, zoom: Float): Float {
        return max(0f, (fitExtent * zoom - viewportExtent) / 2f)
    }

    /**
     * Translation that keeps the picture point under [fromFocus] under
     * [toFocus] while the zoom goes from [fromZoom] to [toZoom]. Focus
     * positions are relative to the player's center.
     */
    fun anchoredTranslation(translation: Float, fromFocus: Float, toFocus: Float, fromZoom: Float, toZoom: Float): Float {
        if (fromZoom <= 0f) return translation
        return toFocus - (fromFocus - translation) * (toZoom / fromZoom)
    }

    /**
     * Pan as a share of the picture's size, which survives zoom and player
     * size changes (rotation, side chat, folding).
     */
    fun panFraction(translation: Float, fitExtent: Float, zoom: Float): Float {
        val extent = fitExtent * zoom
        return if (extent > 0f) translation / extent else 0f
    }

    /** Translation for [panFraction] at [zoom], kept within the [panLimit]. */
    fun translation(panFraction: Float, fitExtent: Float, viewportExtent: Float, zoom: Float): Float {
        val limit = panLimit(fitExtent, viewportExtent, zoom)
        return (panFraction * fitExtent * zoom).coerceIn(-limit, limit)
    }
}
