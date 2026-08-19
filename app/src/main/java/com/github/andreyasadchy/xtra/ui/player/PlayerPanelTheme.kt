package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * Shared palette for the player's control panels (Quality, Speed, Stream
 * volume). One resolver keeps the three surfaces visually identical in both
 * themes; every panel applies these colors in code instead of XML defaults.
 */
data class PlayerPanelColors(
    val panel: Int,
    val onPanel: Int,
    val secondaryText: Int,
    val panelStroke: Int,
    val handle: Int,
    val primary: Int,
    val controlFill: Int,
    val selectedFill: Int,
    val sliderActive: Int,
    val sliderInactive: Int,
)

object PlayerPanelTheme {

    fun resolve(context: Context): PlayerPanelColors {
        val fallbackSurface = if (isLightTheme(context)) Color.WHITE else Color.rgb(18, 18, 18)
        val panel = themeColor(context, com.google.android.material.R.attr.colorSurfaceContainer, fallbackSurface)
        val onPanel = themeColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
            if (ColorUtils.calculateLuminance(panel) > 0.5) Color.rgb(28, 28, 28) else Color.WHITE,
        )
        val primary = themeColor(context, androidx.appcompat.R.attr.colorPrimary, Color.rgb(0, 125, 202))
        val lightPanel = ColorUtils.calculateLuminance(panel) > 0.5
        val controlBlend = if (lightPanel) 0.08f else 0.16f
        val selectedBlend = if (lightPanel) 0.18f else 0.32f
        val strokeAlpha = if (lightPanel) 0.16f else 0.22f
        return PlayerPanelColors(
            panel = panel,
            onPanel = onPanel,
            secondaryText = ColorUtils.setAlphaComponent(onPanel, if (lightPanel) 150 else 178),
            panelStroke = ColorUtils.blendARGB(panel, onPanel, strokeAlpha),
            handle = ColorUtils.setAlphaComponent(onPanel, if (lightPanel) 96 else 128),
            primary = primary,
            controlFill = ColorUtils.blendARGB(panel, onPanel, controlBlend),
            selectedFill = ColorUtils.blendARGB(panel, primary, selectedBlend),
            sliderActive = primary,
            sliderInactive = ColorUtils.setAlphaComponent(onPanel, if (lightPanel) 52 else 64),
        )
    }

    private fun isLightTheme(context: Context): Boolean {
        val value = TypedValue()
        return context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, value, true) && value.data != 0
    }

    private fun themeColor(context: Context, attr: Int, fallback: Int): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) {
            return fallback
        }
        return if (value.resourceId != 0) {
            ContextCompat.getColor(context, value.resourceId)
        } else {
            value.data
        }
    }
}
