package com.github.andreyasadchy.xtra.ui.player

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.github.andreyasadchy.xtra.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Cutout, system-bar and IME insets the floating chat stays clear of, in window pixels. */
internal data class FloatingChatInsets(
    val left: Int = 0,
    val right: Int = 0,
    val top: Int = 0,
    val bottom: Int = 0,
    val ime: Boolean = false,
)

/**
 * Owns floating chat card geometry, appearance, and direct touch interaction.
 * Dragging only updates card.x/card.y (zero allocations, no layout pass);
 * corner-grip resizing applies at most once per animation frame inside
 * [FloatingChatHostLayout], and preferences are written only on release.
 */
internal class FloatingChatController(
    private val host: FloatingChatHostLayout,
    private val card: FloatingChatCardView,
    private val header: FloatingChatHeaderLayout,
    private val content: View,
    private val pill: View,
    private val grip: View,
    private val composeButton: View,
    appearanceButton: View,
    closeButton: View,
    private val prefs: SharedPreferences,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun channelId(): String?
        fun holdControls(hold: Boolean)
        fun canCompose(): Boolean
        fun onAppearanceRequested(trigger: View)
        fun onComposeRequested()
        fun onCloseRequested()
        fun onCardOnScreenChanged(onScreen: Boolean)
    }

    private val density = host.resources.displayMetrics.density
    private val tablet = host.resources.configuration.smallestScreenWidthDp >= 600
    private val marginPx = FloatingChatPlacement.MARGIN_DP * density
    private val location = IntArray(2)

    private var layout = FloatingChatPlacement.defaultLayout(tablet)
    private var widthPx = dp(layout.widthDp)
    private var heightPx = dp(layout.heightDp)
    private var restoredChannel: String? = null
    private var restored = false

    var isShown = false
        private set
    private var controlsVisible = false
    /** How far the open message box reaches up from the card's bottom, or 0. */
    private var composerHeight = 0
    private var ducked = false
    private var interacting = false
    private var insets = FloatingChatInsets()
    private var appearance = FloatingChatAppearance.read(prefs)

    private val accessibilityActions = mutableListOf<Int>()
    private var exitFade: ValueAnimator? = null
    private var pendingHidden: (() -> Unit)? = null

    private var pendingW = 0
    private var pendingH = 0
    private var pendingX = 0f
    private var pendingY = 0f
    private var resizeFramePosted = false
    private val applyResizeFrame = Runnable {
        resizeFramePosted = false
        if (pendingW > 0 && pendingH > 0) {
            applySize(pendingW, pendingH)
            card.x = pendingX
            card.y = pendingY
        }
    }

    private val dragHandler = DragHandler()
    private val resizeHandler = ResizeHandler()

    init {
        pill.setOnTouchListener(dragHandler)
        header.dragListener = dragHandler
        grip.setOnTouchListener(resizeHandler)
        appearanceButton.setOnClickListener { callbacks.onAppearanceRequested(it) }
        composeButton.setOnClickListener { callbacks.onComposeRequested() }
        closeButton.setOnClickListener { callbacks.onCloseRequested() }
        host.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                if (isShown && !interacting) {
                    if (!restored) restoreLayout()
                    placeNow()
                }
            }
        }
        applyAppearance()
    }

    fun show(animate: Boolean) {
        restoreLayout()
        val wasShown = isShown
        isShown = true
        ducked = false
        // Cancelling a running hide fade invokes pendingHidden synchronously while isShown is already true.
        exitFade?.cancel()
        card.animate().cancel()
        card.isVisible = true
        syncHeader(animate = false)
        updateGripLift()
        updateAccessibilityActions()
        if (host.width > 0 && host.height > 0) {
            placeNow()
        } else {
            host.post {
                if (isShown) {
                    if (!restored) restoreLayout()
                    placeNow()
                }
            }
        }
        if (animate) {
            card.alpha = 0f
            card.animate().alpha(1f).setDuration(FADE_MS).start()
        } else {
            card.alpha = 1f
        }
        if (!wasShown) callbacks.onCardOnScreenChanged(true)
    }

    fun hide(animate: Boolean, onHidden: (() -> Unit)? = null) {
        val wasShown = isShown
        isShown = false
        ducked = false
        dragHandler.cancel()
        resizeHandler.cancel()
        clearPendingResize()
        endInteraction()
        header.animate().cancel()
        updateAccessibilityActions()
        exitFade?.cancel()
        card.animate().cancel()
        if (animate && card.isVisible && card.alpha > 0f) {
            pendingHidden = onHidden
            exitFade = ValueAnimator.ofFloat(card.alpha, 0f).apply {
                duration = FADE_MS
                addUpdateListener { card.alpha = it.animatedValue as Float }
                doOnEnd {
                    exitFade = null
                    if (!isShown) card.isVisible = false
                    card.alpha = 1f
                    val callback = pendingHidden
                    pendingHidden = null
                    callback?.invoke()
                }
                start()
            }
        } else {
            card.isVisible = false
            card.alpha = 1f
            onHidden?.invoke()
        }
        if (wasShown) callbacks.onCardOnScreenChanged(false)
    }

    fun onControlsVisibilityChanged(visible: Boolean) {
        if (controlsVisible == visible) return
        controlsVisible = visible
        syncHeader(animate = isShown)
    }

    fun setDucked(value: Boolean) {
        if (ducked == value) return
        ducked = value
        if (!isShown || exitFade != null) return
        card.animate().alpha(if (value) DUCKED_ALPHA else 1f).setDuration(if (value) DUCK_IN_MS else FADE_MS).start()
    }

    fun setInsets(value: FloatingChatInsets) {
        if (insets == value) return
        insets = value
        if (isShown && !interacting) placeNow()
    }

    /** [heightPx] is how far the open message box reaches up from the bottom of the chat, or 0 when closed. */
    fun setComposerHeight(heightPx: Int) {
        composerHeight = heightPx.coerceAtLeast(0)
        updateGripLift()
    }

    /**
     * The grip stays usable with the message box open: it sits just above the
     * box (and its reply line) instead of covering the text field or Send. A
     * card too short for both hides the grip until the box closes or the
     * emote menu does.
     */
    private fun updateGripLift() {
        val lift = -composerHeight.toFloat()
        if (grip.translationY != lift) grip.translationY = lift
        val gripHeight = grip.layoutParams.height.takeIf { it > 0 } ?: grip.height
        val fits = composerHeight == 0 || composerHeight + gripHeight + dp(PILL_HEIGHT_DP) <= card.height
        val visibility = if (fits) View.VISIBLE else View.INVISIBLE
        if (grip.visibility != visibility) grip.visibility = visibility
    }

    fun applyAppearance() {
        appearance = FloatingChatAppearance.read(prefs)
        val colors = PlayerPanelTheme.gestureFeedback(host.context)
        val base = ColorUtils.setAlphaComponent(colors.container, 255)
        card.setSurface(
            color = ColorUtils.setAlphaComponent(base, appearance.surfaceAlpha),
            outline = if (appearance.hasSurface) colors.outline else 0,
            elevated = appearance.background == FloatingChatBackground.SOLID,
        )
        composeButton.isVisible = callbacks.canCompose() && !appearance.replyBar
    }

    fun currentAppearance() = appearance

    fun restingBounds(): RectF? {
        if (!isShown || host.width == 0 || host.height == 0) return null
        val placed = target()
        return RectF(placed.x, placed.y, placed.x + placed.width, placed.y + placed.height)
    }

    private fun restoreLayout() {
        val channelId = callbacks.channelId()
        if (restored && restoredChannel == channelId) return
        val laidOut = host.width > 0 && host.height > 0
        val document = prefs.getString(FloatingChatLayoutStore.PREF_KEY, null)
        val stored = if (FloatingChatLayoutStore.hasChannel(document, channelId)) {
            FloatingChatLayoutStore.read(document, channelId)
        } else {
            migrateLegacy(channelId) ?: FloatingChatLayoutStore.read(document, null)
        }
        layout = stored ?: FloatingChatPlacement.defaultLayout(tablet)
        widthPx = dp(layout.widthDp)
        heightPx = dp(layout.heightDp)
        restoredChannel = channelId
        restored = laidOut || stored != null && !hasLegacy(channelId)
    }

    private fun hasLegacy(channelId: String?) = !channelId.isNullOrBlank() && prefs.contains("floating_chat_x_$channelId")

    private fun migrateLegacy(channelId: String?): FloatingChatLayout? {
        if (channelId.isNullOrBlank() || host.width == 0 || host.height == 0) return null
        val keys = listOf("floating_chat_x_$channelId", "floating_chat_y_$channelId", "floating_chat_w_$channelId", "floating_chat_h_$channelId")
        if (!prefs.contains(keys[0])) return null
        val migrated = FloatingChatLayoutStore.fromLegacy(
            x = prefs.getFloat(keys[0], -1f),
            y = prefs.getFloat(keys[1], -1f),
            widthPx = prefs.getInt(keys[2], -1),
            heightPx = prefs.getInt(keys[3], -1),
            parentWidth = host.width,
            parentHeight = host.height,
            density = density,
            fallback = FloatingChatPlacement.defaultLayout(tablet),
        )
        prefs.edit {
            keys.forEach(::remove)
            if (migrated != null) {
                putString(
                    FloatingChatLayoutStore.PREF_KEY,
                    FloatingChatLayoutStore.write(prefs.getString(FloatingChatLayoutStore.PREF_KEY, null), channelId, migrated),
                )
            }
        }
        return migrated
    }

    private fun save() {
        val document = prefs.getString(FloatingChatLayoutStore.PREF_KEY, null)
        prefs.edit { putString(FloatingChatLayoutStore.PREF_KEY, FloatingChatLayoutStore.write(document, callbacks.channelId(), layout)) }
    }

    private fun freeLeft() = insets.left + marginPx
    private fun freeTop() = topOverlap() + marginPx
    private fun freeRight() = max(freeLeft(), host.width - insets.right - marginPx)
    private fun freeBottom() = max(freeTop(), host.height - bottomOverlap() - marginPx)

    private fun restBounds() =
        FloatingChatPlacement.bounds(host.width, host.height, density, insets.left, insets.right, topOverlap())

    private fun freeBounds() =
        FloatingChatPlacement.bounds(host.width, host.height, density, insets.left, insets.right, topOverlap(), bottomOverlap())

    private fun topOverlap(): Int =
        if (insets.top <= 0 || !host.isAttachedToWindow) 0 else (insets.top - hostTopInWindow()).coerceAtLeast(0)

    private fun bottomOverlap(): Int {
        if (insets.bottom <= 0 || !host.isAttachedToWindow) return 0
        return (hostTopInWindow() + host.height - (host.rootView.height - insets.bottom)).coerceAtLeast(0)
    }

    private fun hostTopInWindow(): Int {
        val parent = host.parent as? View ?: return host.top
        parent.getLocationInWindow(location)
        return location[1] + host.top
    }

    private fun clampedSize(): Pair<Int, Int> =
        FloatingChatPlacement.clampSize(widthPx.toFloat(), heightPx.toFloat(), host.width, host.height, density)

    private fun target(): FloatingChatPlaced {
        val (width, height) = clampedSize()
        return FloatingChatPlacement.place(layout, width, height, restBounds(), freeBounds(), density, keyboard = insets.ime)
    }

    private fun placeNow() {
        if (host.width == 0 || host.height == 0) return
        val placed = target()
        applySize(placed.width, placed.height)
        card.x = placed.x
        card.y = placed.y
        updateGrip()
    }

    private fun applySize(width: Int, height: Int) {
        val params = card.layoutParams as FrameLayout.LayoutParams
        if (params.width == width && params.height == height) return
        params.width = width
        params.height = height
        card.layoutParams = params
        // Lay out immediately so new dimensions land in the same frame as card.x/y.
        if (card.isLaidOut && card.visibility != View.GONE) {
            card.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            card.layout(card.left, card.top, card.left + width, card.top + height)
        }
    }

    private fun scheduleResize(width: Int, height: Int, x: Float, y: Float) {
        pendingW = width
        pendingH = height
        pendingX = x
        pendingY = y
        if (!resizeFramePosted) {
            resizeFramePosted = true
            card.postOnAnimation(applyResizeFrame)
        }
    }

    private fun flushResize() {
        if (!resizeFramePosted) return
        card.removeCallbacks(applyResizeFrame)
        applyResizeFrame.run()
    }

    private fun clearPendingResize() {
        resizeFramePosted = false
        card.removeCallbacks(applyResizeFrame)
    }

    private fun syncHeader(animate: Boolean) {
        header.animate().cancel()
        composeButton.isVisible = callbacks.canCompose() && !appearance.replyBar
        val headerHeight = header.layoutParams.height.takeIf { it > 0 } ?: dp(HEADER_HEIGHT_DP)
        if (controlsVisible) {
            header.visibility = View.VISIBLE
            pill.visibility = View.INVISIBLE
            content.updatePadding(top = headerHeight)
            if (animate) header.animate().alpha(1f).setDuration(FADE_MS).start() else header.alpha = 1f
        } else if (animate && header.isVisible) {
            header.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                if (!controlsVisible) {
                    // INVISIBLE so hidden header buttons never intercept touches.
                    header.visibility = View.INVISIBLE
                    pill.visibility = View.VISIBLE
                    content.updatePadding(top = 0)
                }
            }.start()
        } else {
            header.alpha = 0f
            header.visibility = View.INVISIBLE
            pill.visibility = View.VISIBLE
            content.updatePadding(top = 0)
        }
    }

    // Sides are physical left/right on screen, also in RTL.
    @SuppressLint("RtlHardcoded")
    private fun updateGrip() {
        val params = grip.layoutParams as FrameLayout.LayoutParams
        val right = layout.side == FloatingChatSide.RIGHT
        val gravity = Gravity.BOTTOM or if (right) Gravity.LEFT else Gravity.RIGHT
        if (params.gravity != gravity) {
            params.gravity = gravity
            grip.layoutParams = params
        }
        grip.scaleX = if (right) 1f else -1f
    }

    private fun updateAccessibilityActions() {
        accessibilityActions.forEach { ViewCompat.removeAccessibilityAction(card, it) }
        accessibilityActions.clear()
        if (!isShown) return
        val context = host.context
        val other = if (layout.side == FloatingChatSide.LEFT) FloatingChatSide.RIGHT else FloatingChatSide.LEFT
        accessibilityActions += ViewCompat.addAccessibilityAction(
            card,
            context.getString(if (other == FloatingChatSide.LEFT) R.string.floating_chat_move_left else R.string.floating_chat_move_right),
        ) { _, _ ->
            layout = layout.copy(xFraction = if (other == FloatingChatSide.LEFT) 0f else 1f)
            save()
            placeNow()
            updateAccessibilityActions()
            true
        }
        accessibilityActions += ViewCompat.addAccessibilityAction(card, context.getString(R.string.floating_chat_larger)) { _, _ ->
            scaleBy(1.2f)
            true
        }
        accessibilityActions += ViewCompat.addAccessibilityAction(card, context.getString(R.string.floating_chat_smaller)) { _, _ ->
            scaleBy(1f / 1.2f)
            true
        }
    }

    private fun scaleBy(factor: Float) {
        val (width, height) = FloatingChatPlacement.clampSize(widthPx * factor, heightPx * factor, host.width, host.height, density)
        widthPx = width
        heightPx = height
        layout = layout.copy(widthDp = width / density, heightDp = height / density)
        save()
        placeNow()
    }

    private fun beginInteraction() {
        if (!interacting) {
            interacting = true
            callbacks.holdControls(true)
        }
    }

    private fun endInteraction() {
        if (interacting) {
            interacting = false
            callbacks.holdControls(false)
        }
    }

    private fun commitLayout(newWidth: Int = 0, newHeight: Int = 0) {
        val resized = newWidth > 0 && newHeight > 0
        if (resized) {
            widthPx = newWidth
            heightPx = newHeight
        }
        val base = if (resized) layout.copy(widthDp = newWidth / density, heightDp = newHeight / density) else layout
        layout = FloatingChatPlacement.toLayout(
            x = card.x,
            y = card.y,
            widthPx = if (resized) newWidth else card.width,
            heightPx = heightPx,
            rest = restBounds(),
            free = freeBounds(),
            density = density,
            stored = base,
            keyboard = insets.ime,
        )
        save()
        if (resized) placeNow() else updateGrip()
        updateAccessibilityActions()
    }

    // getRawX(int) needs API 29; every pointer in an event shares the same screen offset.
    private fun rawX(event: MotionEvent, index: Int) = event.getX(index) + event.rawX - event.x
    private fun rawY(event: MotionEvent, index: Int) = event.getY(index) + event.rawY - event.y

    private inner class DragHandler : View.OnTouchListener {
        private var pointerId = MotionEvent.INVALID_POINTER_ID
        private var downX = 0f
        private var downY = 0f
        private var startX = 0f
        private var startY = 0f
        private var moved = false

        fun cancel() {
            pointerId = MotionEvent.INVALID_POINTER_ID
            moved = false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isShown) return false
                    beginInteraction()
                    pointerId = event.getPointerId(0)
                    anchor(event, 0)
                    moved = false
                }
                MotionEvent.ACTION_POINTER_UP -> if (pointerId != MotionEvent.INVALID_POINTER_ID && event.getPointerId(event.actionIndex) == pointerId) {
                    val next = if (event.actionIndex == 0) 1 else 0
                    pointerId = event.getPointerId(next)
                    anchor(event, next)
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = if (pointerId == MotionEvent.INVALID_POINTER_ID) -1 else event.findPointerIndex(pointerId)
                    if (index < 0) return true
                    if (!isShown) {
                        cancel()
                        endInteraction()
                        return true
                    }
                    val minX = freeLeft()
                    val minY = freeTop()
                    val newX = (startX + rawX(event, index) - downX).coerceIn(minX, max(minX, freeRight() - card.width))
                    val newY = (startY + rawY(event, index) - downY).coerceIn(minY, max(minY, freeBottom() - card.height))
                    if (newX != card.x || newY != card.y) {
                        card.x = newX
                        card.y = newY
                        moved = true
                    }
                }
                MotionEvent.ACTION_UP -> if (pointerId != MotionEvent.INVALID_POINTER_ID) {
                    val didMove = moved
                    cancel()
                    endInteraction()
                    if (isShown && didMove) commitLayout()
                }
                MotionEvent.ACTION_CANCEL -> if (pointerId != MotionEvent.INVALID_POINTER_ID) {
                    val didMove = moved
                    cancel()
                    endInteraction()
                    if (isShown && didMove) placeNow()
                }
            }
            return true
        }

        private fun anchor(event: MotionEvent, index: Int) {
            downX = rawX(event, index)
            downY = rawY(event, index)
            startX = card.x
            startY = card.y
        }
    }

    private inner class ResizeHandler : View.OnTouchListener {
        private var pointerId = MotionEvent.INVALID_POINTER_ID
        private var downX = 0f
        private var downY = 0f
        private var startWidth = 0
        private var startHeight = 0
        private var startSide = FloatingChatSide.RIGHT
        private var fixedEdge = 0f
        private var resized = false

        fun cancel() {
            pointerId = MotionEvent.INVALID_POINTER_ID
            resized = false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isShown) return false
                    beginInteraction()
                    pointerId = event.getPointerId(0)
                    resized = false
                    downX = rawX(event, 0)
                    downY = rawY(event, 0)
                    startWidth = card.width
                    startHeight = card.height
                    startSide = layout.side
                    fixedEdge = if (startSide == FloatingChatSide.RIGHT) card.x + card.width else card.x
                }
                MotionEvent.ACTION_POINTER_UP -> if (pointerId != MotionEvent.INVALID_POINTER_ID && event.getPointerId(event.actionIndex) == pointerId) {
                    val next = if (event.actionIndex == 0) 1 else 0
                    pointerId = event.getPointerId(next)
                    downX = rawX(event, next)
                    downY = rawY(event, next)
                    startWidth = card.width
                    startHeight = card.height
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = if (pointerId == MotionEvent.INVALID_POINTER_ID) -1 else event.findPointerIndex(pointerId)
                    if (index < 0) return true
                    if (!isShown) {
                        cancel()
                        clearPendingResize()
                        endInteraction()
                        return true
                    }
                    val (width, height) = FloatingChatPlacement.resize(
                        startWidth, startHeight,
                        rawX(event, index) - downX, rawY(event, index) - downY,
                        startSide, host.width, host.height, density,
                    )
                    val roomWidth = (if (startSide == FloatingChatSide.RIGHT) fixedEdge - freeLeft() else freeRight() - fixedEdge).toInt().coerceAtLeast(1)
                    val roomHeight = (freeBottom() - card.y).toInt().coerceAtLeast(1)
                    val w = min(width, roomWidth).coerceAtLeast(1)
                    val h = min(height, roomHeight).coerceAtLeast(1)
                    if (w != startWidth || h != startHeight) resized = true
                    scheduleResize(w, h, if (startSide == FloatingChatSide.RIGHT) fixedEdge - w else fixedEdge, card.y)
                }
                MotionEvent.ACTION_UP -> if (pointerId != MotionEvent.INVALID_POINTER_ID) {
                    val didResize = resized
                    cancel()
                    flushResize()
                    endInteraction()
                    if (isShown && didResize) commitLayout(card.layoutParams.width, card.layoutParams.height)
                }
                MotionEvent.ACTION_CANCEL -> if (pointerId != MotionEvent.INVALID_POINTER_ID) {
                    val didResize = resized
                    cancel()
                    clearPendingResize()
                    endInteraction()
                    if (isShown && didResize) placeNow()
                }
            }
            return true
        }
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    private companion object {
        const val HEADER_HEIGHT_DP = 48f
        const val PILL_HEIGHT_DP = 28f
        const val FADE_MS = 200L
        const val DUCK_IN_MS = 120L
        const val DUCKED_ALPHA = 0.25f
    }
}
