package com.github.andreyasadchy.xtra.ui.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.github.andreyasadchy.xtra.ui.chat.ChatOverlayStyle

/**
 * A chat row. Over video it can draw its text with a shadow treatment that
 * keeps white text readable on any picture without a box behind it.
 *
 * HALO draws the text twice: first with a soft drop shadow for depth, then with
 * a tight dark halo that outlines every glyph. One shadow layer alone either
 * blurs (wide) or disappears on snow and sky (tight). [ChatHaloPass] tells the
 * spans which pass is drawing, so emotes are drawn once.
 */
class ChatMessageTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var treatment: ChatOverlayStyle.Treatment? = null
    private val density = resources.displayMetrics.density

    fun setOverlayTreatment(value: ChatOverlayStyle.Treatment?) {
        if (treatment == value) return
        treatment = value
        when (value) {
            ChatOverlayStyle.Treatment.SOFT_SHADOW -> setShadowLayer(1.2f * density, 0f, 0.5f * density, SOFT_SHADOW)
            // HALO sets its layers per pass in onDraw. The widest one is set on the
            // view too: TextView widens its clip around the text by that shadow only.
            ChatOverlayStyle.Treatment.HALO -> setShadowLayer(HALO_SOFT_RADIUS_DP * density, 0f, HALO_SOFT_DY_DP * density, HALO_SOFT)
            else -> setShadowLayer(0f, 0f, 0f, 0)
        }
    }

    /**
     * Over video, fades apply alpha per draw instead of through an offscreen
     * layer the size of the row, which would also clip the halo at its edges.
     */
    override fun hasOverlappingRendering() = treatment == null && super.hasOverlappingRendering()

    override fun onDraw(canvas: Canvas) {
        if (treatment != ChatOverlayStyle.Treatment.HALO) {
            super.onDraw(canvas)
            return
        }
        // Paint-level changes only: setShadowLayer() would invalidate and redraw forever.
        try {
            ChatHaloPass.current = ChatHaloPass.SOFT
            paint.setShadowLayer(HALO_SOFT_RADIUS_DP * density, 0f, HALO_SOFT_DY_DP * density, HALO_SOFT)
            super.onDraw(canvas)
            ChatHaloPass.current = ChatHaloPass.TIGHT
            paint.setShadowLayer(HALO_TIGHT_RADIUS_DP * density, 0f, 0f, HALO_TIGHT)
            super.onDraw(canvas)
        } finally {
            ChatHaloPass.current = ChatHaloPass.NONE
        }
    }

    private companion object {
        const val SOFT_SHADOW = 0x8C000000.toInt()
        const val HALO_SOFT = 0x99000000.toInt()
        const val HALO_TIGHT = 0xF2000000.toInt()
        const val HALO_SOFT_RADIUS_DP = 3.5f
        const val HALO_SOFT_DY_DP = 0.75f
        const val HALO_TIGHT_RADIUS_DP = 1.25f
    }
}
