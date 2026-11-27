package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Full-screen, touch-transparent layer that holds the floating chat card.
 *
 * The card resizes live while the viewer drags its corner or pinches. Its size
 * never changes this host's own size, so the host lays its children out locally
 * on the next frame instead of re-measuring the player's root layout.
 */
class FloatingChatHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    // Nullable: View's constructor can request layout before this is assigned.
    private var localLayout: Runnable? = null
    private var localLayoutPending = false

    init {
        localLayout = Runnable {
            localLayoutPending = false
            if (!isAttachedToWindow || width == 0 || height == 0) return@Runnable
            measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
            layout(left, top, right, bottom)
        }
    }

    override fun requestLayout() {
        val runnable = localLayout
        val parentPending = parent?.isLayoutRequested == true
        if (runnable == null || !isLaidOut || isInLayout || width == 0 || height == 0 || parentPending) {
            super.requestLayout()
            return
        }
        forceLayout()
        if (!localLayoutPending) {
            localLayoutPending = true
            postOnAnimation(runnable)
        }
    }

    override fun onDetachedFromWindow() {
        localLayout?.let(::removeCallbacks)
        localLayoutPending = false
        super.onDetachedFromWindow()
    }
}
