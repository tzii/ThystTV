package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.github.andreyasadchy.xtra.util.applyTvFocusOutline
import com.github.andreyasadchy.xtra.util.isTelevision

/** Keep remote focus inside the visible player controls or popup, above the browse UI. */
class PlayerTvControlsLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {
    private var portraitLiveRow: PortraitLiveControlRow? = null

    internal fun setPortraitLiveControls(enabled: Boolean) {
        if (enabled) {
            val row = portraitLiveRow ?: PortraitLiveControlRow(this).also { portraitLiveRow = it }
            row.attach()
        } else {
            portraitLiveRow?.detach()
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        portraitLiveRow?.onLayout()
    }

    override fun focusSearch(focused: View, direction: Int): View? =
        if (context.isTelevision()) FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused
        else super.focusSearch(focused, direction)
}

class PlayerTvPopupLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    override fun focusSearch(focused: View, direction: Int): View? =
        if (context.isTelevision()) FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused
        else super.focusSearch(focused, direction)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun View.prepareTvPlayerFocus() {
    if (!context.isTelevision()) return
    if (isClickable || this is androidx.media3.ui.DefaultTimeBar || this is com.google.android.material.slider.Slider) {
        isFocusable = true
        applyTvFocusOutline()
    }
    if (this is ViewGroup) {
        for (index in 0 until childCount) getChildAt(index).prepareTvPlayerFocus()
    }
}
