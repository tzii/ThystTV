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
 * What the floating chat keeps clear of, in pixels: cutout, rounded-corner or
 * system bar insets on each side and at the top, and what covers the bottom of
 * the window (navigation bar or keyboard). [ime] is set while the keyboard shows.
 */
internal data class FloatingChatInsets(
    val left: Int = 0,
    val right: Int = 0,
    val top: Int = 0,
    val bottom: Int = 0,
    val ime: Boolean = false,
)

/**
 * Moves, sizes and styles the floating chat card. PlayerFragment decides
 * when floating chat is on; this class owns how the card looks and moves.
 *
 * A drag only writes translation per touch event. A resize lays out just the
 * card, at most once per frame, so its new size and position land together;
 * [FloatingChatHostLayout] keeps such changes from re-laying out the player.
 */
internal class FloatingChatController(
    private val host: FloatingChatHostLayout,
    private val card: FloatingChatCardView,
    private val header: FloatingChatHeaderLayout,
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
    private var insets = FloatingChatInsets()
    /** Control bands measured when the controls last showed or laid out; drags reuse them. */
    private var bands = 0 to 0
    private val location = IntArray(2)
    private var interacting = false
    /** A two-finger pinch owns the card; the drag or resize it cancelled must not end the interaction. */
    private var pinching = false
    /** The message box is open at the bottom of the card; the grip would cover its buttons. */
    private var composerOpen = false
    private var unread = 0
    private var tabColor = 0
    private var appearance = FloatingChatAppearance.read(prefs)
    private var accessibilityActions = mutableListOf<Int>()
    private var exitFade: ValueAnimator? = null
    private var pendingHidden: (() -> Unit)? = null

    init {
        pill.setOnTouchListener(DragHandler(requireControls = false))
        header.dragListener = DragHandler(requireControls = true)
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
        pinching = false
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

    fun setInsets(value: FloatingChatInsets) {
        if (insets == value) return
        insets = value
        onKeepClearChanged()
    }

    /** The controls laid out again (first show, rotation, a row of buttons appeared). */
    fun onControlBandsChanged() {
        if (!controlsVisible) return
        val measured = callbacks.controlBands()
        if (measured == bands) return
        bands = measured
        onKeepClearChanged()
    }

    /** The message box opened or closed at the bottom of the card. */
    fun setComposerOpen(open: Boolean) {
        if (composerOpen == open) return
        composerOpen = open
        updateGripVisibility()
    }

    private fun onKeepClearChanged() {
        if (!isShown || interacting) return
        if (tuckedSide != null) placeTab(slideIn = false) else settle(KEEP_CLEAR_STIFFNESS, KEEP_CLEAR_DAMPING)
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
        tabColor = base
        updateTabBackground()
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
        if (tuckedSide == null || count <= 0 || unread >= MAX_UNREAD) return
        unread = (unread + count).coerceAtMost(MAX_UNREAD)
        updateTabCount()
    }

    private fun restoreLayout() {
        val channelId = callbacks.channelId()
        if (restored && restoredChannel == channelId) return
        val laidOut = host.width > 0 && host.height > 0
        if (!laidOut) {
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
        // A last-used layout stands in until a legacy position can be converted.
        restored = laidOut || stored != null && !hasLegacy(channelId)
    }

    private fun hasLegacy(channelId: String?) = !channelId.isNullOrBlank() && prefs.contains("floating_chat_x_$channelId")

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

    private fun restBounds() =
        FloatingChatPlacement.bounds(host.width, host.height, density, insets.left, insets.right, topOverlap())

    private fun currentBounds(): FloatingChatBounds {
        // While typing, the room above the keyboard matters more than the controls.
        val (top, bottom) = if (controlsVisible && !insets.ime) bands else 0 to 0
        return FloatingChatPlacement.bounds(
            host.width, host.height, density, insets.left, insets.right,
            max(top, topOverlap()), max(bottom, bottomOverlap()),
        )
    }

    /** How much of the host a status bar covers, when one shows. */
    private fun topOverlap(): Int {
        if (insets.top <= 0 || !host.isAttachedToWindow) return 0
        host.getLocationInWindow(location)
        return (insets.top - location[1]).coerceAtLeast(0)
    }

    /** How much of the host the keyboard or navigation bar covers, whether or not the window resized for it. */
    private fun bottomOverlap(): Int {
        if (insets.bottom <= 0 || !host.isAttachedToWindow) return 0
        host.getLocationInWindow(location)
        val coveredFrom = host.rootView.height - insets.bottom
        return (location[1] + host.height - coveredFrom).coerceAtLeast(0)
    }

    /** Height a card of [height] shows inside [bounds]; above the keyboard it may go below the usual floor. */
    private fun fitHeight(height: Int, bounds: FloatingChatBounds): Int = if (insets.ime) {
        min(height, bounds.height.toInt()).coerceAtLeast(1)
    } else {
        FloatingChatPlacement.visibleHeight(height, bounds, density)
    }

    private fun clampedSize(): Pair<Int, Int> =
        FloatingChatPlacement.clampSize(widthPx.toFloat(), heightPx.toFloat(), host.width, host.height, density)

    private fun target(): FloatingChatPlaced {
        val (width, height) = clampedSize()
        return FloatingChatPlacement.place(layout, width, height, restBounds(), currentBounds(), density, keyboard = insets.ime)
    }

    private fun placeNow() {
        if (host.width == 0 || host.height == 0) return
        springX.cancel()
        springY.cancel()
        val placed = target()
        applySize(placed.width, placed.height)
        card.x = placed.x
        card.y = placed.y
        updateGrip()
    }

    /** Springs the card to where it belongs now; a shorter fit drops its oldest (top) lines first. */
    private fun settle(stiffness: Float, damping: Float, velocityX: Float = 0f, velocityY: Float = 0f) {
        if (host.width == 0 || host.height == 0) return
        val placed = target()
        val oldHeight = card.layoutParams.height
        if (oldHeight > 0 && placed.height < oldHeight) {
            // Keep the bottom (newest lines) still while the top is cut.
            card.y += oldHeight - placed.height
        }
        applySize(placed.width, placed.height)
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
        // Lay the card out now, so a new size lands in the same frame as the
        // position written with it; the host's own pass then finds nothing to do.
        if (card.isLaidOut && card.visibility != View.GONE) {
            card.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            card.layout(card.left, card.top, card.left + width, card.top + height)
        }
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
        updateGripVisibility()
        composeButton.isVisible = callbacks.canCompose() && !appearance.replyBar
    }

    private fun updateGripVisibility() {
        grip.visibility = if (controlsVisible && !composerOpen) View.VISIBLE else View.INVISIBLE
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
        updateTabBackground()
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
        // Measured here: a tab that was GONE has no width yet.
        tab.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(dp(TAB_HEIGHT_DP), View.MeasureSpec.EXACTLY),
        )
        val tabWidth = tab.measuredWidth
        val bounds = currentBounds()
        val targetY = (card.y + dp(16f)).coerceIn(bounds.top, max(bounds.top, bounds.bottom - dp(TAB_HEIGHT_DP)))
        // Flush with the screen edge, or with a cutout on that side.
        val targetX = if (left) insets.left.toFloat() else (host.width - insets.right - tabWidth).toFloat()
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

    /** Rounded on the side that faces the video, flat against the screen edge. */
    private fun updateTabBackground() {
        tab.background = GradientDrawable().apply {
            setColor(ColorUtils.setAlphaComponent(tabColor, 0xEB))
            val r = 22 * density
            cornerRadii = if (tuckedSide == FloatingChatSide.LEFT) {
                floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            } else {
                floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
        }
    }

    private fun updateTabCount() {
        val visible = unread > 0
        val label = if (unread >= MAX_UNREAD) "$MAX_UNREAD+" else unread.toString()
        val widthChanged = tabCount.isVisible != visible || visible && tabCount.text.length != label.length
        tabCount.isVisible = visible
        tabCount.text = label
        tab.contentDescription = if (visible) {
            host.context.getString(R.string.floating_chat_show_unread, unread)
        } else {
            host.context.getString(R.string.floating_chat_show)
        }
        // A wider count widens the tab; keep it flush with the screen edge.
        if (widthChanged && tuckedSide != null && tab.isVisible) placeTab(slideIn = false)
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

    /**
     * Drag from the pill, or from the header while the controls are up. One
     * finger leads; when it lifts, another finger still down takes over from
     * where the card is, so the card never jumps between fingers.
     */
    private inner class DragHandler(private val requireControls: Boolean) : View.OnTouchListener {
        private var tracker: VelocityTracker? = null
        private var pointerId = MotionEvent.INVALID_POINTER_ID
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
                    pointerId = event.getPointerId(0)
                    anchor(event, 0)
                    startSide = layout.side
                    dragging = !requireControls
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain().also { track(it, event) }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    tracker?.let { track(it, event) }
                    if (event.getPointerId(event.actionIndex) == pointerId) {
                        val next = if (event.actionIndex == 0) 1 else 0
                        pointerId = event.getPointerId(next)
                        anchor(event, next)
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val tracker = tracker ?: return true
                    if (!isShown) {
                        finish()
                        return true
                    }
                    track(tracker, event)
                    val index = event.findPointerIndex(pointerId)
                    if (index < 0) return true
                    val dx = rawX(event, index) - downX
                    val dy = rawY(event, index) - downY
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
                    val tracker = tracker ?: return true
                    track(tracker, event)
                    tracker.computeCurrentVelocity(1000, maxFling)
                    val velocityX = tracker.getXVelocity(pointerId)
                    val velocityY = tracker.getYVelocity(pointerId)
                    finish()
                    if (!isShown) return true
                    if (dragging) release(velocityX, velocityY, startSide) else settle(SNAP_STIFFNESS, SNAP_DAMPING)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (tracker == null) return true
                    // A pinch that cancels the drag takes over the interaction and the card.
                    if (pinching) {
                        recycleTracker()
                        return true
                    }
                    finish()
                    if (isShown) settle(SNAP_STIFFNESS, SNAP_DAMPING)
                    return true
                }
            }
            return true
        }

        /** Measures the drag from pointer [index] and the card's current place. */
        private fun anchor(event: MotionEvent, index: Int) {
            downX = rawX(event, index)
            downY = rawY(event, index)
            startX = card.x
            startY = card.y
        }

        private fun finish() {
            recycleTracker()
            endInteraction()
        }

        private fun recycleTracker() {
            tracker?.recycle()
            tracker = null
            pointerId = MotionEvent.INVALID_POINTER_ID
        }

        // getRawX(int) needs API 29; every pointer shares the view's screen offset.
        private fun rawX(event: MotionEvent, index: Int) = event.getX(index) + event.rawX - event.x

        private fun rawY(event: MotionEvent, index: Int) = event.getY(index) + event.rawY - event.y

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
        private var active = false
        private var downX = 0f
        private var downY = 0f
        private var startWidth = 0
        private var startHeight = 0
        private var pendingWidth = 0
        private var pendingHeight = 0
        /** The size changed; a tap on the grip must not store the fitted height as the new size. */
        private var resized = false
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
                    active = true
                    resized = false
                    downX = event.rawX
                    downY = event.rawY
                    startWidth = card.width
                    startHeight = card.height
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!active) return true
                    val (width, height) = FloatingChatPlacement.resize(
                        startWidth, startHeight, event.rawX - downX, event.rawY - downY, layout.side, host.width, host.height, density,
                    )
                    val bounds = currentBounds()
                    pendingWidth = min(width, bounds.width.toInt())
                    pendingHeight = min(height, max(dp(FloatingChatPlacement.MIN_HEIGHT_DP), (bounds.bottom - card.y).toInt()))
                    if (pendingWidth != startWidth || pendingHeight != startHeight) resized = true
                    if (!framePosted) {
                        framePosted = true
                        card.postOnAnimation(applyFrame)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!active) return true
                    active = false
                    card.removeCallbacks(applyFrame)
                    // A pinch that cancels the resize takes over the interaction and the size.
                    if (pinching) {
                        framePosted = false
                        return true
                    }
                    if (framePosted) {
                        framePosted = false
                        applyLive(pendingWidth, pendingHeight)
                    }
                    endInteraction()
                    if (isShown && resized) commitSize(card.layoutParams.width, card.layoutParams.height)
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

    /**
     * Pinch resize while the controls are up. It scales the stored size, not
     * the height a short screen cut the card down to, and stores only a change.
     */
    private inner class PinchHandler : FloatingChatCardView.PinchListener {
        private var startWidth = 0
        private var startHeight = 0
        private var pinchWidth = 0
        private var pinchHeight = 0

        override fun onPinchStart() {
            // Set before the card cancels a drag or resize under the first finger.
            pinching = true
            beginInteraction()
            val (width, height) = clampedSize()
            startWidth = width
            startHeight = height
            pinchWidth = width
            pinchHeight = height
        }

        override fun onPinch(scale: Float) {
            if (!pinching) return
            val (width, height) = FloatingChatPlacement.clampSize(startWidth * scale, startHeight * scale, host.width, host.height, density)
            pinchWidth = width
            pinchHeight = height
            val bounds = currentBounds()
            val fittedWidth = min(width, bounds.width.toInt())
            val fittedHeight = fitHeight(height, bounds)
            applyLive(fittedWidth, fittedHeight)
            card.y = card.y.coerceIn(bounds.top, max(bounds.top, bounds.bottom - fittedHeight))
        }

        override fun onPinchEnd() {
            if (!pinching) return
            pinching = false
            endInteraction()
            if (!isShown) return
            if (pinchWidth != startWidth || pinchHeight != startHeight) {
                commitSize(pinchWidth, pinchHeight)
            } else {
                settle(SNAP_STIFFNESS, SNAP_DAMPING)
            }
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
