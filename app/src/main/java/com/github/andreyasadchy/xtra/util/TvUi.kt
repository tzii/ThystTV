package com.github.andreyasadchy.xtra.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.LayerDrawable
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.github.andreyasadchy.xtra.R

fun Context.isTelevision(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

/** Preserve existing ripples and checked indicators underneath the remote focus ring. */
fun View.applyTvFocusOutline() {
    if (getTag(R.id.tvFocusOutlineApplied) == true) return
    val outline = AppCompatResources.getDrawable(context, R.drawable.tv_focus_outline) ?: return
    foreground = foreground?.let { LayerDrawable(arrayOf(it, outline)) } ?: outline
    setTag(R.id.tvFocusOutlineApplied, true)
}
