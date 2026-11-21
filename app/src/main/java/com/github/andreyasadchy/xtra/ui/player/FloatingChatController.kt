package com.github.andreyasadchy.xtra.ui.player

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.github.andreyasadchy.xtra.R
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Moves, sizes and styles the floating chat card. PlayerFragment decides
 * when floating chat is on; this class owns how the card looks and moves.
 *
 * Per touch event it only writes translation; size changes are coalesced to
 * one layout per frame inside [FloatingChatHostLayout].
 */
internal class FloatingChatController(
    private val host: FloatingChatHostLayout,
    private val card: FloatingChatCardView,
    private val header: View,
    private val pill: View,
    private val grip: View,
    private val tab: View,
    private val tabCount: TextView,
    private val composeButton: View,
    appearanceButton: View,
    tuckButton: View,
    closeButton: View,
    private val prefs: SharedPreferences,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun channelId(): String?
        /** Keeps the player controls up while the viewer moves or styles the chat. */
        fun holdControls(hold: Boolean)
        /** Bottom of the controls' top bar and height of their bottom bar, in host pixels. */
        fun controlBands(): Pair<Int, Int>
        fun canCompose(): Boolean
        fun onAppearanceRequested(trigger: View)
        fun onComposeRequested()
        fun onCloseRequested()
        /** The card left or came back to the screen (hidden, tucked, shown). */
        fun onCardOnScreenChanged(onScreen: Boolean)
    }

    private val density = host.resources.displayMetrics.density
    private val tablet = host.resources.configuration.smallestScreenWidthDp >= 600
    private val touchSlop = ViewConfiguration.get(host.context).scaledTouchSlop
    private val maxFling = ViewConfiguration.get(host.context).scaledMaximumFlingVelocity.toFloat()

    private val springX = SpringAnimation(card, DynamicAnimation.X).setSpring(SpringForce())
    private val springY = SpringAnimation(card, DynamicAnimation.Y).setSpring(SpringForce())
    private val tabSpring = SpringAnimation(tab, DynamicAnimation.X).setSpring(SpringForce())

    private var layout = FloatingChatPlacement.defaultLayout(tablet)
    private var widthPx = dp(layout.widthDp)
    private var heightPx = dp(layout.heightDp)
    private var restoredChannel: String? = null
    private var restored = false

    var isShown = false
        private set
    private var tuckedSide: FloatingChatSide? = null
    val isTucked get() = tuckedSide != null
    private var controlsVisible = false
    private var safeLeft = 0
    private var safeRight = 0
    private var bottomInset = 0
    /** Control bands measured when the controls last showed or laid out; drags reuse them. */
    private var bands = 0 to 0
    private val location = IntArray(2)
    private var interacting = false
    private var unread = 0
    private var appearance = FloatingChatAppearance.read(prefs)
    private var accessibilityActions = mutableListOf<Int>()
    private var exitFade: ValueAnimator? = null
    private var pendingHidden: (() -> Unit)? = null

    init {
        pill.setOnTouchListener(DragHandler(requireControls = false))
        header.setOnTouchListener(DragHandler(requireControls = true))
        grip.setOnTouchListener(ResizeHandler())
        appearanceButton.setOnClickListener { callbacks.onAppearanceRequested(it) }
        composeButton.setOnClickListener { callbacks.onComposeRequested() }
        tuckButton.setOnClickListener { tuck(layout.side, 0f) }
        closeButton.setOnClickListener { callbacks.onCloseRequested() }
        tab.setOnClickListener { untuck() }
        card.pinchListener = PinchHandler()
        host.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) onHostResized()
        }
        tab.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            // The tab sits on the screen edge, where the system back gesture starts.
            ViewCompat.setSystemGestureExclusionRects(view, listOf(Rect(0, 0, view.width, view.height)))
        }
        applyAppearance()
    }

    fun show(animate: Boolean) {
        restoreLayout()
        isShown = true
        tuckedSide = null
        tabSpring.cancel()
        tab.isVisible = false
        // Ends a running fade-out first; its callback sees isShown and keeps the card.
        exitFade?.cancel()
        card.animate().cancel()
        card.isVisible = true
        syncChrome(animate = false)
        updateAccessibilityActions()
        if (host.width > 0 && host.height > 0) placeNow() else host.post { if (isShown) placeNow() }
        if (animate) {
            card.alpha = 0f
            card.scaleX = ENTER_SCALE
            card.scaleY = ENTER_SCALE
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(FADE_MS).setInterpolator(EMPHASIZED).start()
        } else {
            card.alpha = 1f
            card.scaleX = 1f
            card.scaleY = 1f
        }
        callbacks.onCardOnScreenChanged(true)
    }

    /**
     * Hides the card. [onHidden] runs once it is gone: after the fade, or at
     * once when that fade is cut short by another show or hide.
     */
    fun hide(animate: Boolean, onHidden: (() -> Unit)? = null) {
        if (interacting) {
            interacting = false
            callbacks.holdControls(false)
        }
        val wasOnScreen = isShown && tuckedSide == null
        isShown = false
        tuckedSide = null
        springX.cancel()
        springY.cancel()
        tabSpring.cancel()
        tab.isVisible = false
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
            card.scaleX = 1f
            card.scaleY = 1f
            onHidden?.invoke()
        }
        if (wasOnScreen) callbacks.onCardOnScreenChanged(false)
    }

    fun onControlsVisibilityChanged(visible: Boolean) {
        if (controlsVisible == visible) return
        controlsVisible = visible
        if (visible) bands = callbacks.controlBands()
        syncChrome(animate = true)
        if (!isShown) return
        if (tuckedSide != null) placeTab(slideIn = false) else if (!interacting) settle(KEEP_CLEAR_STIFFNESS, KEEP_CLEAR_DAMPING)
    }

    /**
     * Cutout or rounded-corner insets on each side, and what covers the bottom
     * of the window (keyboard or navigation bar), in pixels.
     */
    fun setInsets(left: Int, right: Int, bottom: Int) {
        if (safeLeft == left && safeRight == right && bottomInset == bottom) return
        safeLeft = left
        safeRight = right
        bottomInset = bottom
        if (isShown && tuckedSide == null && !interacting) settle(KEEP_CLEAR_STIFFNESS, KEEP_CLEAR_DAMPING)
    }

    /** The controls laid out again (first show, rotation, a row of buttons appeared). */
    fun onControlBandsChanged() {
        if (!controlsVisible) return
        val measured = callbacks.controlBands()
        if (measured == bands) return
        bands = measured
        if (isShown && tuckedSide == null && !interacting) settle(KEEP_CLEAR_STIFFNESS, KEEP_CLEAR_DAMPING)
    }

    /** Re-reads background, text and behavior after Aa or Settings changed them. */
    fun applyAppearance() {
        appearance = FloatingChatAppearance.read(prefs)
        val colors = PlayerPanelTheme.gestureFeedback(host.context)
        val base = ColorUtils.setAlphaComponent(colors.container, 255)
        card.setSurface(
            color = ColorUtils.setAlphaComponent(base, appearance.surfaceAlpha),
            outline = if (appearance.hasSurface) colors.outline else 0,
            elevated = appearance.background == FloatingChatBackground.SOLID,
        )
        tab.background = tabBackground(base)
        tabCount.background = GradientDrawable().apply {
            cornerRadius = 10 * density
            setColor(colors.accent)
        }
        tabCount.setTextColor(colors.onAccent)
        syncChrome(animate = false)
    }

    fun currentAppearance() = appearance

    /** New chat lines while tucked show as a count on the tab. */
    fun onMessagesAppended(count: Int) {
        if (tuckedSide == null || count <= 0) return
        unread = (unread + count).coerceAtMost(MAX_UNREAD)
        updateTabCount()
    }

    private fun restoreLayout() {
        val channelId = callbacks.channelId()
        if (restored && restoredChannel == channelId) return
        if (host.width == 0 || host.height == 0) {
            // Legacy positions need the screen size; try again once laid out.
            host.post { if (isShown && !restored) { restoreLayout(); placeNow() } }
        }
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
        restored = host.width > 0 && host.height > 0 || stored != null
    }

    /** 1.7.0 kept raw pixels per channel; convert them once and drop the old keys. */
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
        val channelId = callbacks.channelId()
        val document = prefs.getString(FloatingChatLayoutStore.PREF_KEY, null)
        prefs.edit { putString(FloatingChatLayoutStore.PREF_KEY, FloatingChatLayoutStore.write(document, channelId, layout)) }
    }

    private fun restBounds() = FloatingChatPlacement.bounds(host.width, host.height, density, safeLeft, safeRight)

    private fun currentBounds(): FloatingChatBounds {
        val (top, bottom) = if (controlsVisible) bands else 0 to 0
        return FloatingChatPlacement.bounds(host.width, host.height, density, safeLeft, safeRight, top, max(bottom, bottomOverlap()))
    }

    /** How much of the host the keyboard or navigation bar covers, whether or not the window resized for it. */
    private fun bottomOverlap(): Int {
        if (bottomInset <= 0 || !host.isAttachedToWindow) return 0
        host.getLocationInWindow(location)
        val coveredFrom = host.rootView.height - bottomInset
        return (location[1] + host.height - coveredFrom).coerceAtLeast(0)
    }

    private fun clampedSize(): Pair<Int, Int> =
        FloatingChatPlacement.clampSize(widthPx.toFloat(), heightPx.toFloat(), host.width, host.height, density)

    private fun target(): FloatingChatPlaced {
        val (width, height) = clampedSize()
        return FloatingChatPlacement.place(layout, width, height, restBounds(), currentBounds(), density)
    }

    private fun placeNow() {
        if (host.width == 0 || host.height == 0) return
        springX.cancel()
        springY.cancel()
        val placed = target()
        applySize(clampedSize().first, placed.height)
        card.x = placed.x
        card.y = placed.y
        updateGrip()
    }

    /** Springs the card to where it belongs now; a shorter fit drops its oldest (top) lines first. */
    private fun settle(stiffness: Float, damping: Float, velocityX: Float = 0f, velocityY: Float = 0f) {
        if (host.width == 0 || host.height == 0) return
        val placed = target()
        val width = clampedSize().first
        val oldHeight = card.layoutParams.height
        if (oldHeight > 0 && placed.height < oldHeight) {
            // Keep the bottom (newest lines) still while the top is cut.
            card.y += oldHeight - placed.height
        }
        applySize(width, placed.height)
        animate(placed.x, placed.y, stiffness, damping, velocityX, velocityY)
        updateGrip()
    }

    private fun animate(x: Float, y: Float, stiffness: Float, damping: Float, velocityX: Float, velocityY: Float) {
        springX.spring.setStiffness(stiffness).setDampingRatio(damping)
        springY.spring.setStiffness(stiffness).setDampingRatio(damping)
        if (velocityX != 0f || !springX.isRunning) springX.setStartVelocity(velocityX)
        if (velocityY != 0f || !springY.isRunning) springY.setStartVelocity(velocityY)
        springX.animateToFinalPosition(x)
        springY.animateToFinalPosition(y)
    }

    private fun applySize(width: Int, height: Int) {
        val params = card.layoutParams as FrameLayout.LayoutParams
        if (params.width == width && params.height == height) return
        params.width = width
        params.height = height
        card.layoutParams = params
    }

    private fun onHostResized() {
        if (!isShown) return
        if (tuckedSide != null) {
            card.x = FloatingChatPlacement.tuckedX(tuckedSide!!, card.width, host.width, density)
            placeTab(slideIn = false)
        } else if (!interacting) {
            placeNow()
        }
    }

    private fun syncChrome(animate: Boolean) {
        val showHeader = controlsVisible
        header.animate().cancel()
        if (showHeader) {
            header.visibility = View.VISIBLE
            if (animate) header.animate().alpha(1f).setDuration(FADE_MS).start() else header.alpha = 1f
        } else if (animate && header.isVisible) {
            // INVISIBLE afterwards: an invisible header must not catch touches.
            header.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                if (!controlsVisible) header.visibility = View.INVISIBLE
            }.start()
        } else {
            header.alpha = 0f
            header.visibility = View.INVISIBLE
        }
        val passThrough = appearance.passThrough && !controlsVisible
        card.passThrough = passThrough
        card.pinchEnabled = controlsVisible
        pill.visibility = if (controlsVisible || passThrough) View.INVISIBLE else View.VISIBLE
        grip.visibility = if (controlsVisible) View.VISIBLE else View.INVISIBLE
        composeButton.isVisible = callbacks.canCompose() && !appearance.replyBar
    }

    // Card sides are physical (left or right of the screen), also in RTL.
    @SuppressLint("RtlHardcoded")
    private fun updateGrip() {
        val params = grip.layoutParams as FrameLayout.LayoutParams
        val right = layout.side == FloatingChatSide.RIGHT
        // The grip sits on the corner that faces the video.
        val gravity = android.view.Gravity.BOTTOM or if (right) android.view.Gravity.LEFT else android.view.Gravity.RIGHT
        if (params.gravity != gravity) {
            params.gravity = gravity
            grip.layoutParams = params
        }
        grip.scaleX = if (right) 1f else -1f
    }

    private fun tuck(side: FloatingChatSide, velocityX: Float) {
        if (!isShown) return
        tuckedSide = side
        layout = layout.copy(side = side)
        unread = 0
        updateTabCount()
        springX.spring.setStiffness(SNAP_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
        springX.setStartVelocity(velocityX)
        springX.animateToFinalPosition(FloatingChatPlacement.tuckedX(side, card.width, host.width, density))
        springX.addEndListener(object : DynamicAnimation.OnAnimationEndListener {
            override fun onAnimationEnd(animation: DynamicAnimation<*>?, canceled: Boolean, value: Float, velocity: Float) {
                springX.removeEndListener(this)
                if (!canceled && tuckedSide != null) card.visibility = View.INVISIBLE
            }
        })
        placeTab(slideIn = true)
        updateAccessibilityActions()
        callbacks.onCardOnScreenChanged(false)
    }

    private fun untuck() {
        if (tuckedSide == null) return
        tuckedSide = null
        card.visibility = View.VISIBLE
        val inward = if (layout.side == FloatingChatSide.LEFT) UNTUCK_VELOCITY else -UNTUCK_VELOCITY
        settle(SNAP_STIFFNESS, SNAP_DAMPING, inward * density, 0f)
        hideTab()
        updateAccessibilityActions()
        callbacks.onCardOnScreenChanged(true)
    }

    /** Puts the tab on the tucked side; [slideIn] brings it in from off screen. */
    private fun placeTab(slideIn: Boolean) {
        val side = tuckedSide ?: return
        val wasVisible = tab.isVisible
        tab.isVisible = true
        val left = side == FloatingChatSide.LEFT
        tab.background = tabBackground(ColorUtils.setAlphaComponent(PlayerPanelTheme.gestureFeedback(host.context).container, 255))
        // Measured here: a tab that was GONE has no width yet.
        tab.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(dp(TAB_HEIGHT_DP), View.MeasureSpec.EXACTLY),
        )
        val tabWidth = tab.measuredWidth
        val bounds = currentBounds()
        val targetY = (card.y + dp(16f)).coerceIn(bounds.top, max(bounds.top, bounds.bottom - dp(TAB_HEIGHT_DP)))
        // Flush with the screen edge, or with a cutout on that side.
        val targetX = if (left) safeLeft.toFloat() else (host.width - safeRight - tabWidth).toFloat()
        tab.animate().cancel()
        if (slideIn || !wasVisible) {
            tab.y = targetY
            if (!tabSpring.isRunning) tab.x = if (left) -tabWidth.toFloat() else host.width.toFloat()
            tabSpring.spring.setStiffness(SNAP_STIFFNESS).setDampingRatio(SNAP_DAMPING)
            tabSpring.animateToFinalPosition(targetX)
        } else {
            if (tabSpring.isRunning) tabSpring.animateToFinalPosition(targetX) else tab.x = targetX
            tab.animate().y(targetY).setDuration(FADE_MS).setInterpolator(EMPHASIZED).start()
        }
    }

    private fun hideTab() {
        val left = layout.side == FloatingChatSide.LEFT
        tabSpring.spring.setStiffness(SNAP_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
        tabSpring.animateToFinalPosition(if (left) -tab.width - dp(8f).toFloat() else host.width + dp(8f).toFloat())
        tabSpring.addEndListener(object : DynamicAnimation.OnAnimationEndListener {
            override fun onAnimationEnd(animation: DynamicAnimation<*>?, canceled: Boolean, value: Float, velocity: Float) {
                tabSpring.removeEndListener(this)
                if (tuckedSide == null) tab.isVisible = false
            }
        })
    }

    private fun tabBackground(color: Int) = GradientDrawable().apply {
        setColor(ColorUtils.setAlphaComponent(color, 0xEB))
        val r = 22 * density
        cornerRadii = if (tuckedSide == FloatingChatSide.LEFT) {
            floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        } else {
            floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        }
    }

    private fun updateTabCount() {
        tabCount.isVisible = unread > 0
        tabCount.text = if (unread >= MAX_UNREAD) "$MAX_UNREAD+" else unread.toString()
        tab.contentDescription = if (unread > 0) {
            host.context.getString(R.string.floating_chat_show_unread, unread)
        } else {
            host.context.getString(R.string.floating_chat_show)
        }
        // The count widens the tab; keep it flush with the screen edge.
        if (tuckedSide != null && tab.isVisible) placeTab(slideIn = false)
    }

    private fun updateAccessibilityActions() {
        accessibilityActions.forEach { ViewCompat.removeAccessibilityAction(card, it) }
        accessibilityActions.clear()
        if (!isShown || tuckedSide != null) return
        val context = host.context
        val other = if (layout.side == FloatingChatSide.LEFT) FloatingChatSide.RIGHT else FloatingChatSide.LEFT
        accessibilityActions += ViewCompat.addAccessibilityAction(
            card,
            context.getString(if (other == FloatingChatSide.LEFT) R.string.floating_chat_move_left else R.string.floating_chat_move_right),
        ) { _, _ ->
            layout = layout.copy(side = other)
            save()
            settle(SNAP_STIFFNESS, SNAP_DAMPING)
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
        accessibilityActions += ViewCompat.addAccessibilityAction(card, context.getString(R.string.floating_chat_tuck)) { _, _ ->
            tuck(layout.side, 0f)
            true
        }
    }

    private fun scaleBy(factor: Float) {
        val (width, height) = FloatingChatPlacement.clampSize(widthPx * factor, heightPx * factor, host.width, host.height, density)
        widthPx = width
        heightPx = height
        layout = layout.copy(widthDp = width / density, heightDp = height / density)
        save()
        settle(SNAP_STIFFNESS, SNAP_DAMPING)
    }

    private fun beginInteraction() {
        springX.cancel()
        springY.cancel()
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

    /** Drag from the pill, or from the header while the controls are up. */
    private inner class DragHandler(private val requireControls: Boolean) : View.OnTouchListener {
        private var tracker: VelocityTracker? = null
        private var downX = 0f
        private var downY = 0f
        private var startX = 0f
        private var startY = 0f
        private var startSide = FloatingChatSide.RIGHT
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isShown || tuckedSide != null || (requireControls && !controlsVisible)) return false
                    beginInteraction()
                    downX = event.rawX
                    downY = event.rawY
                    startX = card.x
                    startY = card.y
                    startSide = layout.side
                    dragging = !requireControls
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain().also { track(it, event) }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.let { track(it, event) }
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlop) dragging = true
                    if (dragging) {
                        val bounds = currentBounds()
                        val lowest = max(bounds.top, bounds.bottom - card.height)
                        card.x = startX + dx
                        card.y = rubberBand(startY + dy, bounds.top, lowest)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val tracker = tracker
                    var velocityX = 0f
                    var velocityY = 0f
                    if (tracker != null) {
                        track(tracker, event)
                        tracker.computeCurrentVelocity(1000, maxFling)
                        velocityX = tracker.xVelocity
                        velocityY = tracker.yVelocity
                    }
                    finish()
                    if (dragging) release(velocityX, velocityY, startSide) else settle(SNAP_STIFFNESS, SNAP_DAMPING)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    finish()
                    settle(SNAP_STIFFNESS, SNAP_DAMPING)
                    return true
                }
            }
            return true
        }

        private fun finish() {
            tracker?.recycle()
            tracker = null
            endInteraction()
        }

        /** The handle moves with the card, so velocity uses screen coordinates. */
        private fun track(tracker: VelocityTracker, event: MotionEvent) {
            val dx = event.rawX - event.x
            val dy = event.rawY - event.y
            event.offsetLocation(dx, dy)
            tracker.addMovement(event)
            event.offsetLocation(-dx, -dy)
        }

        private fun rubberBand(value: Float, low: Float, high: Float): Float = when {
            value < low -> low - sqrt(low - value) * 4f * sqrt(density)
            value > high -> high + sqrt(value - high) * 4f * sqrt(density)
            else -> value
        }
    }

    private fun release(velocityX: Float, velocityY: Float, startSide: FloatingChatSide) {
        val result = FloatingChatPlacement.release(
            x = card.x,
            y = card.y,
            velocityX = velocityX,
            velocityY = velocityY,
            widthPx = card.width,
            heightPx = heightPx,
            startSide = startSide,
            rest = restBounds(),
            current = currentBounds(),
            density = density,
            stored = layout,
        )
        layout = result.layout
        save()
        val tucked = result.tuckedSide
        if (tucked != null) {
            tuck(tucked, velocityX)
        } else {
            settle(SNAP_STIFFNESS, SNAP_DAMPING, velocityX, velocityY)
            updateAccessibilityActions()
        }
    }

    /** Resize from the grip; the outer edge and the top stay put. */
    private inner class ResizeHandler : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startWidth = 0
        private var startHeight = 0
        private var pendingWidth = 0
        private var pendingHeight = 0
        private var framePosted = false
        private val applyFrame = Runnable {
            framePosted = false
            applyLive(pendingWidth, pendingHeight)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isShown || !controlsVisible) return false
                    beginInteraction()
                    downX = event.rawX
                    downY = event.rawY
                    startWidth = card.width
                    startHeight = card.height
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val (width, height) = FloatingChatPlacement.resize(
                        startWidth, startHeight, event.rawX - downX, event.rawY - downY, layout.side, host.width, host.height, density,
                    )
                    val bounds = currentBounds()
                    pendingWidth = min(width, bounds.width.toInt())
                    pendingHeight = min(height, max(dp(FloatingChatPlacement.MIN_HEIGHT_DP), (bounds.bottom - card.y).toInt()))
                    if (!framePosted) {
                        framePosted = true
                        card.postOnAnimation(applyFrame)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    card.removeCallbacks(applyFrame)
                    if (framePosted) {
                        framePosted = false
                        applyLive(pendingWidth, pendingHeight)
                    }
                    endInteraction()
                    commitSize(card.layoutParams.width, card.layoutParams.height)
                    return true
                }
            }
            return true
        }
    }

    private fun applyLive(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        applySize(width, height)
        val bounds = currentBounds()
        card.x = if (layout.side == FloatingChatSide.RIGHT) bounds.right - width else bounds.left
    }

    /** Stores a new size and lets the card find its anchor again (it may now touch the bottom). */
    private fun commitSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        widthPx = width
        heightPx = height
        val release = FloatingChatPlacement.release(
            x = card.x, y = card.y, velocityX = 0f, velocityY = 0f,
            widthPx = width, heightPx = height, startSide = layout.side,
            rest = restBounds(), current = currentBounds(), density = density,
            stored = layout.copy(widthDp = width / density, heightDp = height / density),
        )
        layout = release.layout.copy(side = layout.side)
        save()
        settle(SNAP_STIFFNESS, SNAP_DAMPING)
    }

    private inner class PinchHandler : FloatingChatCardView.PinchListener {
        private var startWidth = 0
        private var startHeight = 0

        override fun onPinchStart() {
            beginInteraction()
            startWidth = card.width
            startHeight = card.height
        }

        override fun onPinch(scale: Float) {
            val (width, height) = FloatingChatPlacement.clampSize(startWidth * scale, startHeight * scale, host.width, host.height, density)
            val bounds = currentBounds()
            val fittedWidth = min(width, bounds.width.toInt())
            val fittedHeight = min(height, bounds.height.toInt())
            applyLive(fittedWidth, fittedHeight)
            card.y = card.y.coerceIn(bounds.top, max(bounds.top, bounds.bottom - fittedHeight))
        }

        override fun onPinchEnd() {
            endInteraction()
            commitSize(card.layoutParams.width, card.layoutParams.height)
        }
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    private companion object {
        const val SNAP_STIFFNESS = 550f
        const val SNAP_DAMPING = 0.85f
        const val KEEP_CLEAR_STIFFNESS = 600f
        const val KEEP_CLEAR_DAMPING = 1f
        /** dp per second the card comes back in with from its tab. */
        const val UNTUCK_VELOCITY = 900f
        const val FADE_MS = 200L
        const val ENTER_SCALE = 0.96f
        const val MAX_UNREAD = 99
        const val TAB_HEIGHT_DP = 44f
        val EMPHASIZED = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}
