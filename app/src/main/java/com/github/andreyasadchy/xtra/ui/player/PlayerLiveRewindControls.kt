package com.github.andreyasadchy.xtra.ui.player

import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.PlayerLayoutBinding
import com.github.andreyasadchy.xtra.util.isTelevision
import kotlin.math.max

/** Portrait live controls use one bottom bar, with the right-hand actions pinned. */
internal fun PlayerLayoutBinding.bindLiveRewindButton(show: Boolean, isPortrait: Boolean, onClick: () -> Unit) {
    liveRewind.isVisible = show
    liveRewind.setOnClickListener(if (show) View.OnClickListener { onClick() } else null)
    root.setPortraitLiveControls(show && isPortrait && !root.context.isTelevision())
    playPause.translationY = 0f
}

/**
 * Reuses the actual controls and their click listeners. Only the portrait parent changes;
 * landscape, VoD and minimized playback restore the original XML parents and parameters.
 * Native HorizontalScrollView keeps every enabled action and full-size stats reachable
 * when optional buttons or accessibility text cannot physically fit in the viewport.
 */
internal class PortraitLiveControlRow(private val root: PlayerTvControlsLayout) {
    private val left = root.findViewById<LinearLayout>(R.id.bottomLeftLayout)
    private val right = root.findViewById<View>(R.id.bottomRightLayout)
    private val info = root.findViewById<LinearLayout>(R.id.streamInfoLayout)
    private val uptime = root.findViewById<LinearLayout>(R.id.uptimeLayout)
    private val viewers = root.findViewById<LinearLayout>(R.id.viewersLayout)
    private val viewersIcon = root.findViewById<View>(R.id.viewersIcon)
    private val uptimeIcon = root.findViewById<View>(R.id.uptimeIcon)
    private val uptimeText = root.findViewById<View>(R.id.uptimeTimer)
    private val viewersText = root.findViewById<View>(R.id.viewersText)
    private val leftIndex = root.indexOfChild(left)
    private val infoIndex = root.indexOfChild(info)
    private val leftParams = left.layoutParams
    private val infoParams = info.layoutParams
    private val appearanceParams = listOf(viewersIcon, uptimeIcon, uptimeText, viewersText).associateWith { it.layoutParams }
    private val gravities = listOf(info, uptime, viewers).associateWith {
        // The getter was added in N. These containers use the XML default on M.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) it.gravity else Gravity.START or Gravity.TOP
    }
    private val row = LinearLayout(root.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isBaselineAligned = false
    }
    private val viewport = HorizontalScrollView(root.context).apply {
        id = View.generateViewId()
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(12))
        isFillViewport = true
        isFocusable = false
        addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private var attached = false
    private var resetScroll = false

    fun attach() {
        if (attached) return
        attached = true
        root.removeView(left)
        root.removeView(info)
        row.addView(left, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(info, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(4)
        })
        info.gravity = Gravity.CENTER
        uptime.gravity = Gravity.CENTER_VERTICAL
        viewers.gravity = Gravity.CENTER_VERTICAL
        viewersIcon.layoutParams = LinearLayout.LayoutParams(appearanceParams.getValue(viewersIcon) as LinearLayout.LayoutParams).apply {
            width = dp(18)
            height = dp(18)
        }
        for (view in listOf(uptimeIcon, uptimeText, viewersText)) {
            view.layoutParams = LinearLayout.LayoutParams(appearanceParams.getValue(view) as LinearLayout.LayoutParams).apply {
                topMargin = 0
                bottomMargin = 0
            }
        }
        root.addView(viewport, RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
            addRule(RelativeLayout.START_OF, right.id)
            addRule(RelativeLayout.ABOVE, R.id.bottomLayout)
            marginStart = dp(10)
            marginEnd = dp(4)
            bottomMargin = dp(5)
        })
        resetScroll = true
    }

    fun detach() {
        if (!attached) return
        attached = false
        row.removeView(left)
        row.removeView(info)
        root.removeView(viewport)
        root.addView(left, leftIndex, leftParams)
        root.addView(info, infoIndex, infoParams)
        appearanceParams.forEach { (view, params) -> view.layoutParams = params }
        gravities.forEach { (view, gravity) -> view.gravity = gravity }
    }

    fun onLayout() {
        if (!attached) return
        val top = viewport.top + (viewport.height - right.height) / 2
        right.layout(right.left, top, right.right, top + right.height)
        if (resetScroll) {
            viewport.scrollTo(if (root.layoutDirection == View.LAYOUT_DIRECTION_RTL) max(0, row.width - viewport.width) else 0, 0)
            resetScroll = false
        }
    }

    private fun dp(value: Int) = (value * root.resources.displayMetrics.density).toInt()
}
