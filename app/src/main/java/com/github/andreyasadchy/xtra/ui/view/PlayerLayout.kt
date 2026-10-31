package com.github.andreyasadchy.xtra.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class PlayerLayout : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var isPortrait = false
    var isMinimized = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (isMinimized) {
            // Fit the base viewport inside its parent before scaling. A wide phone
            // otherwise clips the bottom of a width-derived 16:9 frame.
            val playerWidth = minOf(measuredWidth, (measuredHeight * (16f / 9f)).toInt())
            val playerHeight = (playerWidth / (16f / 9f)).toInt()
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(playerWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(playerHeight, MeasureSpec.EXACTLY)
            )
        } else if (isPortrait) {
            val playerHeight = (measuredWidth / (16f / 9f)).toInt()
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(playerHeight, MeasureSpec.EXACTLY)
            )
        }
    }
}
