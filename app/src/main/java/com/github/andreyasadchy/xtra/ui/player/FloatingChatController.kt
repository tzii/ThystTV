package com.github.andreyasadchy.xtra.ui.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.github.andreyasadchy.xtra.R
import kotlin.math.abs
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
 * A translation and scale shared by the player (slidingLayout) and the
 * floating chat host, which have the same size and place.
 */
internal data class FloatingChatHostPose(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float,
) {
    fun lerp(to: FloatingChatHostPose, fraction: Float) = FloatingChatHostPose(
        translationX + (to.translationX - translationX) * fraction,
        translationY + (to.translationY - translationY) * fraction,
        scaleX + (to.scaleX - scaleX) * fraction,
        scaleY + (to.scaleY - scaleY) * fraction,
    )

    fun applyTo(view: View) {
        view.translationX = translationX
        view.translationY = translationY
        view.scaleX = scaleX
        view.scaleY = scaleY
    }

    companion object {
        val IDENTITY = FloatingChatHostPose(0f, 0f, 1f, 1f)

        fun of(view: View) = FloatingChatHostPose(view.translationX, view.translationY, view.scaleX, view.scaleY)
    }
}

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
    /** Holds the chat; it starts below the header while the header shows. */
    private val content: View,
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
        /**
         * The viewer started moving or sizing the card: fade the player
         * controls out, so it is placed on the clear picture.
         */
        fun dismissControls()
        /** Where the visible controls sit (each group of buttons or text), in host pixels. */
        fun controlBands(): FloatingChatControls
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
    /** Controls measured when they last showed or laid out. */
    private var bands = FloatingChatControls()
    /**
     * The header and grip stay while the viewer drags, resizes or pinches
     * with them, after the controls they came with have faded out.
     */
    private var chromeHeld = false
    /** Moves the card clear of the keyboard, insets or a new screen size. */
    private var keepClearMotion: ValueAnimator? = null
    /** Faded back while a brightness, volume, seek or speed slider shows. */
    private var ducked = false
    /**
     * The controls show over the card: it fades a little so they show
     * through, and a touch on one of them goes to it.
     */
    private var covering = false
    /** The header shows on the card's bottom edge: the controls cover its top edge more. */
    private var headerAtBottom = false
    /** The grip would sit on a control, so it stays hidden. */
    private var gripCovers = false
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
    /** Moves the whole host with the player while it minimizes or comes back. */
    private var hostMotion: ValueAnimator? = null
    /** The card is riding into the mini-player; [hide] runs when it gets there. */
    private var leaving = false
    /** The host follows the player's swipe down; keep-clear moves wait until it is back. */
    private var following = false
    /** A keep-clear change arrived while the host was moving with the player. */
    private var keepClearPending = false
    private val hostMoving get() = following || hostMotion != null

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
        card.passThroughAt = ::passesToControls
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
        resetHost()
        restoreLayout()
        isShown = true
        tuckedSide = null
        chromeHeld = false
        keepClearPending = false
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
            card.animate().alpha(restingAlpha()).scaleX(1f).scaleY(1f).setDuration(FADE_MS).setInterpolator(EMPHASIZED).start()
        } else {
            card.alpha = restingAlpha()
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
        resetHost()
        pinching = false
        ducked = false
        if (interacting) {
            interacting = false
            callbacks.holdControls(false)
        }
        val wasOnScreen = isShown && tuckedSide == null
        isShown = false
        tuckedSide = null
        chromeHeld = false
        keepClearPending = false
        springX.cancel()
        springY.cancel()
        keepClearMotion?.cancel()
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

    /**
     * The swipe that minimizes the player: the card moves down with the video
     * by [translationY] and fades as [progress] nears 1, the point where
     * letting go minimizes. Writes only the host's translation and alpha.
     */
    fun followPlayer(translationY: Float, progress: Float) {
        if (!isShown) return
        following = true
        hostMotion?.cancel()
        host.translationY = translationY
        host.alpha = 1f - FOLLOW_FADE * progress.coerceIn(0f, 1f)
    }

    /** A swipe let go before the threshold: the card goes back up with the video. */
    fun returnWithPlayer(durationMs: Long) {
        if (leaving || !following) return
        val fromAlpha = host.alpha
        animateHost(FloatingChatHostPose.IDENTITY, durationMs, alphaAt = { fromAlpha + (1f - fromAlpha) * it }) {
            following = false
        }
    }

    /**
     * Minimize begins: the card stops following the controls, insets and
     * screen size where it is, and waits for [hideWithPlayer]. Returns false,
     * and hides at once, when nothing of it is on screen.
     */
    fun holdForPlayer(): Boolean {
        if (leaving) return true
        if (!isShown || !card.isVisible && !tab.isVisible) {
            hide(animate = false)
            return false
        }
        pinching = false
        ducked = false
        if (interacting) {
            interacting = false
            callbacks.holdControls(false)
        }
        val wasOnScreen = tuckedSide == null
        isShown = false
        chromeHeld = false
        keepClearPending = false
        springX.cancel()
        springY.cancel()
        keepClearMotion?.cancel()
        tabSpring.cancel()
        exitFade?.cancel()
        card.animate().cancel()
        hostMotion?.cancel()
        updateAccessibilityActions()
        leaving = true
        if (wasOnScreen) callbacks.onCardOnScreenChanged(false)
        return true
    }

    /**
     * Minimize: the card rides into the mini-player with the video, which
     * PlayerFragment gives [pose] over [durationMs]. The host has the
     * player's size and place, so the same transform carries the card along;
     * it has faded out by [LEAVE_FADE_SHARE] of the way, then hides. Does
     * nothing once [show] or [hide] ended the wait (a quick maximize, or
     * floating chat turned off), so a late minimize never hides a shown card.
     */
    fun hideWithPlayer(pose: FloatingChatHostPose, durationMs: Long) {
        if (!leaving) return
        val fromAlpha = host.alpha
        animateHost(
            pose,
            durationMs,
            alphaAt = { fromAlpha * (1f - it / LEAVE_FADE_SHARE).coerceIn(0f, 1f) },
        ) { hide(animate = false) }
    }

    /**
     * Maximize: the card grows out of the video from the player's
     * minimized [pose] and fades in over the last part of the way.
     */
    fun showWithPlayer(pose: FloatingChatHostPose, durationMs: Long) {
        show(animate = false)
        pose.applyTo(host)
        host.alpha = 0f
        animateHost(
            FloatingChatHostPose.IDENTITY,
            durationMs,
            alphaAt = { ((it - (1f - ENTER_FADE_SHARE)) / ENTER_FADE_SHARE).coerceIn(0f, 1f) },
        ) {}
    }

    /**
     * Animates the host to [to]; [alphaAt] maps the linear progress to its
     * alpha. [onEnd] runs only when it gets there; then any keep-clear move
     * that waited for it plays.
     */
    private fun animateHost(to: FloatingChatHostPose, durationMs: Long, alphaAt: (Float) -> Float, onEnd: () -> Unit) {
        hostMotion?.cancel()
        val from = FloatingChatHostPose.of(host)
        var canceled = false
        hostMotion = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            // The player's own minimize curve (ViewPropertyAnimator's default), so both move as one.
            interpolator = PLAYER_MOTION
            addUpdateListener {
                from.lerp(to, it.animatedValue as Float).applyTo(host)
                host.alpha = alphaAt(it.animatedFraction)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    canceled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (hostMotion === animation) hostMotion = null
                    if (!canceled) {
                        leaving = false
                        onEnd()
                        applyPendingKeepClear()
                    }
                }
            })
            start()
        }
    }

    /** The host back at rest: no motion, no transform, fully opaque. */
    private fun resetHost() {
        val motion = hostMotion
        hostMotion = null
        motion?.cancel()
        leaving = false
        following = false
        FloatingChatHostPose.IDENTITY.applyTo(host)
        host.alpha = 1f
    }

    private fun applyPendingKeepClear() {
        if (!keepClearPending || hostMoving) return
        keepClearPending = false
        onKeepClearChanged()
    }

    fun onControlsVisibilityChanged(visible: Boolean) {
        if (controlsVisible == visible) return
        controlsVisible = visible
        if (visible) bands = callbacks.controlBands()
        // A card riding into the mini-player keeps its look; show() syncs it again.
        if (leaving) return
        // The controls never move the card, so its spring or glide keeps going.
        refreshCovering(animate = true)
        syncChrome(animate = true)
    }

    /**
     * Fades the card back while a gesture slider (brightness, volume, seek,
     * speed) is on screen, so a card on that edge never hides the slider, and
     * brings it back when the slider goes.
     */
    fun setDucked(value: Boolean) {
        if (ducked == value) return
        ducked = value
        if (!isShown || exitFade != null) return
        card.animate().alpha(restingAlpha()).setDuration(if (value) DUCK_IN_MS else FADE_MS).setInterpolator(EMPHASIZED).start()
    }

    private fun restingAlpha() = when {
        ducked -> DUCKED_ALPHA
        covering -> COVER_ALPHA
        else -> 1f
    }

    /**
     * Works out whether the controls show over the card where it rests, and
     * where its header and grip go so they stay off them. The controls never
     * move the card: it fades a little instead, and comes back when they hide.
     */
    private fun refreshCovering(animate: Boolean) {
        val placed = if (isShown && tuckedSide == null && host.width > 0 && host.height > 0) target() else null
        val obstacles = bands.obstacles
        val now = placed != null && controlsVisible && !insets.ime && FloatingChatPlacement.covers(placed, obstacles)
        // Mid-drag or resize the header stays where the finger found it, and a
        // header fading out with the controls fades where it is.
        if (!chromeHeld && controlsVisible) {
            var bottom = false
            if (now && placed != null && !composerOpen) {
                val left = placed.x
                val right = placed.x + placed.width
                val band = header.layoutParams.height.toFloat()
                val top = placed.y
                val end = placed.y + placed.height
                // The edge the controls cover less: the top right buttons outweigh
                // the corner of a ±10 button clipping the bottom band.
                val topCovered = FloatingChatPlacement.coveredArea(left, top, right, top + band, obstacles)
                val bottomCovered = FloatingChatPlacement.coveredArea(left, end - band, right, end, obstacles)
                bottom = topCovered > bottomCovered
            }
            if (bottom != headerAtBottom) {
                headerAtBottom = bottom
                applyHeaderEdge()
            }
        }
        var gripNow = headerAtBottom && (controlsVisible || chromeHeld)
        if (!gripNow && now && placed != null) {
            val size = grip.layoutParams.width.toFloat()
            // The grip sits on the bottom corner that faces the video; it gives way to a header there.
            val gripLeft = if (layout.side == FloatingChatSide.RIGHT) placed.x else placed.x + placed.width - size
            val end = placed.y + placed.height
            gripNow = obstacles.any { FloatingChatPlacement.covers(gripLeft, end - size, gripLeft + size, end, it) }
        }
        if (gripNow != gripCovers) {
            gripCovers = gripNow
            updateGripVisibility()
        }
        if (now == covering) return
        covering = now
        // A tucked card takes it too, so it comes back at the right alpha.
        if (!isShown || exitFade != null) return
        if (animate) {
            card.animate().alpha(restingAlpha()).setDuration(FADE_MS).setInterpolator(EMPHASIZED).start()
        } else {
            card.alpha = restingAlpha()
        }
    }

    /**
     * While the controls show over the card, a touch that starts on one of
     * them goes to it, also where the card's header lies over them (the
     * header moves off them when the other edge is clearer). The grip never
     * sits on a control.
     */
    private fun passesToControls(x: Float, y: Float): Boolean {
        if (!covering || !controlsVisible) return false
        return FloatingChatPlacement.hits(card.x + x, card.y + y, bands.obstacles)
    }

    /** Puts the header on the card's top or bottom edge, its shade fading toward the chat. */
    private fun applyHeaderEdge() {
        val params = header.layoutParams as FrameLayout.LayoutParams
        val gravity = if (headerAtBottom) Gravity.BOTTOM else Gravity.TOP
        if (params.gravity != gravity) {
            params.gravity = gravity
            header.layoutParams = params
        }
        (header.background?.mutate() as? GradientDrawable)?.orientation =
            if (headerAtBottom) GradientDrawable.Orientation.BOTTOM_TOP else GradientDrawable.Orientation.TOP_BOTTOM
        if (header.isVisible) padForHeader(header.layoutParams.height)
    }

    /** Keeps the chat lines out from under the header, on whichever edge it is. */
    private fun padForHeader(room: Int) {
        content.updatePadding(top = if (headerAtBottom) 0 else room, bottom = if (headerAtBottom) room else 0)
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
        refreshCovering(animate = true)
    }

    /** The message box opened or closed at the bottom of the card. */
    fun setComposerOpen(open: Boolean) {
        if (composerOpen == open) return
        composerOpen = open
        updateGripVisibility()
        refreshCovering(animate = true)
    }

    /**
     * The keyboard, insets or controls changed: the card glides to where it
     * belongs now (only the keyboard and insets move it) and fades for the
     * controls. While it moves with the player, or the viewer holds it, that waits.
     */
    private fun onKeepClearChanged() {
        if (!isShown || interacting) return
        if (hostMoving) {
            keepClearPending = true
            return
        }
        if (tuckedSide != null) placeTab(slideIn = false) else glide()
        refreshCovering(animate = true)
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

    /** The clear screen the stored position is a share of: cutouts and the status bar left out. */
    private fun restBounds() =
        FloatingChatPlacement.bounds(host.width, host.height, density, insets.left, insets.right, topOverlap())

    /** Where the card may be now: [restBounds] above the keyboard or navigation bar too. The controls never shrink it. */
    private fun freeBounds() =
        FloatingChatPlacement.bounds(host.width, host.height, density, insets.left, insets.right, topOverlap(), bottomOverlap())

    /** How much of the host a status bar covers, when one shows. */
    private fun topOverlap(): Int {
        if (insets.top <= 0 || !host.isAttachedToWindow) return 0
        return (insets.top - hostTopInWindow()).coerceAtLeast(0)
    }

    /** How much of the host the keyboard or navigation bar covers, whether or not the window resized for it. */
    private fun bottomOverlap(): Int {
        if (insets.bottom <= 0 || !host.isAttachedToWindow) return 0
        val coveredFrom = host.rootView.height - insets.bottom
        return (hostTopInWindow() + host.height - coveredFrom).coerceAtLeast(0)
    }

    /**
     * The host's top in the window as laid out. Read through its parent, so a
     * host moving with the player (translated, scaled) still measures its rest place.
     */
    private fun hostTopInWindow(): Int {
        val parent = host.parent as? View ?: return host.top
        parent.getLocationInWindow(location)
        return location[1] + host.top
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
        val free = freeBounds()
        // The controls never move the card; it fades under them instead (refreshCovering).
        return FloatingChatPlacement.place(layout, width, height, restBounds(), free, density, keyboard = insets.ime)
    }

    /**
     * Where the card rests now, in host pixels; null while it is off screen
     * or tucked. It may still be on its way there.
     */
    fun restingBounds(): RectF? {
        if (!isShown || tuckedSide != null || host.width == 0 || host.height == 0) return null
        val placed = target()
        return RectF(placed.x, placed.y, placed.x + placed.width, placed.y + placed.height)
    }

    private fun placeNow() {
        if (host.width == 0 || host.height == 0) return
        springX.cancel()
        springY.cancel()
        keepClearMotion?.cancel()
        val placed = target()
        applySize(placed.width, placed.height)
        card.x = placed.x
        card.y = placed.y
        updateGrip()
        refreshCovering(animate = false)
    }

    /** Springs the card to where it belongs now; a shorter fit drops its oldest (top) lines first. */
    private fun settle(stiffness: Float, damping: Float, velocityX: Float = 0f, velocityY: Float = 0f) {
        if (host.width == 0 || host.height == 0) return
        keepClearMotion?.cancel()
        val placed = resize(target())
        animate(placed.x, placed.y, stiffness, damping, velocityX, velocityY)
        refreshCovering(animate = true)
    }

    /**
     * Glides the card to where it belongs now over [KEEP_CLEAR_MS]
     * (emphasized), with no overshoot: it makes room for the keyboard or
     * insets and comes back when they go.
     */
    private fun glide() {
        if (host.width == 0 || host.height == 0) return
        springX.cancel()
        springY.cancel()
        keepClearMotion?.cancel()
        val placed = resize(target())
        val fromX = card.x
        val fromY = card.y
        if (abs(placed.x - fromX) < 0.5f && abs(placed.y - fromY) < 0.5f) {
            card.x = placed.x
            card.y = placed.y
            return
        }
        keepClearMotion = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = KEEP_CLEAR_MS
            interpolator = EMPHASIZED
            addUpdateListener {
                val fraction = it.animatedValue as Float
                card.x = fromX + (placed.x - fromX) * fraction
                card.y = fromY + (placed.y - fromY) * fraction
            }
            doOnEnd { if (keepClearMotion === it) keepClearMotion = null }
            start()
        }
    }

    /** Gives the card [placed]'s size; a shorter fit keeps its bottom (newest lines) still while the top is cut. */
    private fun resize(placed: FloatingChatPlaced): FloatingChatPlaced {
        val oldHeight = card.layoutParams.height
        if (oldHeight > 0 && placed.height < oldHeight) card.y += oldHeight - placed.height
        applySize(placed.width, placed.height)
        updateGrip()
        return placed
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
        if (hostMoving) {
            keepClearPending = true
            return
        }
        if (tuckedSide != null) {
            card.x = FloatingChatPlacement.tuckedX(tuckedSide!!, card.width, host.width, density)
            placeTab(slideIn = false)
        } else if (!interacting) {
            placeNow()
        }
    }

    private fun syncChrome(animate: Boolean) {
        val showHeader = controlsVisible || chromeHeld
        header.animate().cancel()
        if (showHeader) {
            header.visibility = View.VISIBLE
            // The newest lines stay put; the oldest one leaves the header's room.
            padForHeader(header.layoutParams.height)
            if (animate) header.animate().alpha(1f).setDuration(FADE_MS).start() else header.alpha = 1f
        } else if (animate && header.isVisible) {
            // INVISIBLE afterwards: an invisible header must not catch touches.
            // The chat takes the room back once the header has faded.
            header.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                if (!controlsVisible && !chromeHeld) {
                    header.visibility = View.INVISIBLE
                    padForHeader(0)
                }
            }.start()
        } else {
            header.alpha = 0f
            header.visibility = View.INVISIBLE
            padForHeader(0)
        }
        val passThrough = appearance.passThrough && !showHeader
        card.passThrough = passThrough
        card.pinchEnabled = showHeader
        pill.visibility = if (showHeader || passThrough) View.INVISIBLE else View.VISIBLE
        updateGripVisibility()
        composeButton.isVisible = callbacks.canCompose() && !appearance.replyBar
    }

    private fun updateGripVisibility() {
        grip.visibility = if ((controlsVisible || chromeHeld) && !composerOpen && !gripCovers) View.VISIBLE else View.INVISIBLE
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
        keepClearMotion?.cancel()
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
        val side = tuckedSide ?: return
        tuckedSide = null
        card.visibility = View.VISIBLE
        val inward = if (side == FloatingChatSide.LEFT) UNTUCK_VELOCITY else -UNTUCK_VELOCITY
        settle(SNAP_STIFFNESS, SNAP_DAMPING, inward * density, 0f)
        hideTab(side)
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
        val bounds = freeBounds()
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

    private fun hideTab(side: FloatingChatSide) {
        val left = side == FloatingChatSide.LEFT
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
            layout = layout.copy(xFraction = if (other == FloatingChatSide.LEFT) 0f else 1f)
            save()
            settle(RELEASE_STIFFNESS, RELEASE_DAMPING)
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
        settle(RELEASE_STIFFNESS, RELEASE_DAMPING)
    }

    private fun beginInteraction() {
        springX.cancel()
        springY.cancel()
        keepClearMotion?.cancel()
        if (!interacting) {
            interacting = true
            callbacks.holdControls(true)
        }
    }

    /**
     * The viewer moves or sizes the card with its header, grip or a pinch:
     * those stay while the finger is down, and the player controls fade out,
     * so the card is placed on the clear picture and stays where it is let go.
     */
    private fun holdChrome() {
        if (chromeHeld || !controlsVisible) return
        chromeHeld = true
        callbacks.dismissControls()
    }

    private fun endInteraction() {
        if (interacting) {
            interacting = false
            callbacks.holdControls(false)
        }
        if (chromeHeld) {
            chromeHeld = false
            syncChrome(animate = true)
        }
    }

    /**
     * Drag from the pill, or from the header while the controls are up (they
     * fade out once the drag starts). The card follows the finger anywhere on
     * the clear screen. One finger leads; when it lifts, another finger still
     * down takes over from where the card is, so the card never jumps between fingers.
     */
    private inner class DragHandler(private val requireControls: Boolean) : View.OnTouchListener {
        private var tracker: VelocityTracker? = null
        private var pointerId = MotionEvent.INVALID_POINTER_ID
        private var downX = 0f
        private var downY = 0f
        private var startX = 0f
        private var startY = 0f
        /** Where the card was when this drag began; a fling tucks only toward an edge it started at. */
        private var dragStartX = 0f
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isShown || tuckedSide != null || (requireControls && !controlsVisible)) return false
                    beginInteraction()
                    pointerId = event.getPointerId(0)
                    anchor(event, 0)
                    dragStartX = card.x
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
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        // A tap on Aa, tuck or close never gets here, so it never hides the controls.
                        if (requireControls) holdChrome()
                    }
                    if (dragging) {
                        val bounds = freeBounds()
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
                    if (dragging) release(velocityX, velocityY, dragStartX) else settle(RELEASE_STIFFNESS, RELEASE_DAMPING)
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
                    if (isShown) settle(RELEASE_STIFFNESS, RELEASE_DAMPING)
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

    private fun release(velocityX: Float, velocityY: Float, startX: Float) {
        val result = FloatingChatPlacement.release(
            x = card.x,
            y = card.y,
            velocityX = velocityX,
            velocityY = velocityY,
            widthPx = card.width,
            heightPx = heightPx,
            startX = startX,
            rest = restBounds(),
            free = freeBounds(),
            density = density,
            stored = layout,
        )
        layout = result.layout
        save()
        val tucked = result.tuckedSide
        if (tucked != null) {
            tuck(tucked, velocityX)
        } else {
            // Critically damped from rest: the card stays where it was let go, with no bounce.
            settle(RELEASE_STIFFNESS, RELEASE_DAMPING)
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
        private var startSide = FloatingChatSide.RIGHT
        /** The edge that stays put: the right edge of a right-side card, the left edge of a left-side one. */
        private var fixedEdge = 0f
        private var pendingWidth = 0
        private var pendingHeight = 0
        /** The size changed; a tap on the grip must not store the fitted height as the new size. */
        private var resized = false
        private var framePosted = false
        private val applyFrame = Runnable {
            framePosted = false
            applyResize()
        }

        private fun applyResize() {
            val x = if (startSide == FloatingChatSide.RIGHT) fixedEdge - pendingWidth else fixedEdge
            applyLive(pendingWidth, pendingHeight, x)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isShown || !(controlsVisible || chromeHeld)) return false
                    beginInteraction()
                    active = true
                    resized = false
                    downX = event.rawX
                    downY = event.rawY
                    startWidth = card.width
                    startHeight = card.height
                    startSide = layout.side
                    fixedEdge = if (startSide == FloatingChatSide.RIGHT) card.x + card.width else card.x
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!active) return true
                    val (width, height) = FloatingChatPlacement.resize(
                        startWidth, startHeight, event.rawX - downX, event.rawY - downY, startSide, host.width, host.height, density,
                    )
                    val bounds = freeBounds()
                    val room = if (startSide == FloatingChatSide.RIGHT) fixedEdge - bounds.left else bounds.right - fixedEdge
                    pendingWidth = min(width, room.toInt()).coerceAtLeast(1)
                    pendingHeight = min(height, max(dp(FloatingChatPlacement.MIN_HEIGHT_DP), (bounds.bottom - card.y).toInt()))
                    if (pendingWidth != startWidth || pendingHeight != startHeight) {
                        if (!resized) holdChrome()
                        resized = true
                    }
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
                        applyResize()
                    }
                    endInteraction()
                    if (isShown && resized) commitSize(card.layoutParams.width, card.layoutParams.height)
                    return true
                }
            }
            return true
        }
    }

    private fun applyLive(width: Int, height: Int, x: Float) {
        if (width <= 0 || height <= 0) return
        applySize(width, height)
        card.x = x
    }

    /** Stores a new size and where the card now is (an edge it reached pulls it flush). */
    private fun commitSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        widthPx = width
        heightPx = height
        val release = FloatingChatPlacement.release(
            x = card.x, y = card.y, velocityX = 0f, velocityY = 0f,
            widthPx = width, heightPx = height, startX = card.x,
            rest = restBounds(), free = freeBounds(), density = density,
            stored = layout.copy(widthDp = width / density, heightDp = height / density),
        )
        layout = release.layout
        save()
        settle(RELEASE_STIFFNESS, RELEASE_DAMPING)
    }

    /**
     * Pinch resize while the controls are up (they fade out as it starts). It
     * scales the stored size about the card's centre, not the height a short
     * screen cut the card down to, and stores only a change.
     */
    private inner class PinchHandler : FloatingChatCardView.PinchListener {
        private var startWidth = 0
        private var startHeight = 0
        private var pinchWidth = 0
        private var pinchHeight = 0
        private var centerX = 0f
        private var centerY = 0f

        override fun onPinchStart() {
            // Set before the card cancels a drag or resize under the first finger.
            pinching = true
            beginInteraction()
            holdChrome()
            centerX = card.x + card.width / 2f
            centerY = card.y + card.height / 2f
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
            val bounds = freeBounds()
            val fittedWidth = min(width, bounds.width.toInt())
            val fittedHeight = fitHeight(height, bounds)
            val x = (centerX - fittedWidth / 2f).coerceIn(bounds.left, max(bounds.left, bounds.right - fittedWidth))
            applyLive(fittedWidth, fittedHeight, x)
            card.y = (centerY - fittedHeight / 2f).coerceIn(bounds.top, max(bounds.top, bounds.bottom - fittedHeight))
        }

        override fun onPinchEnd() {
            if (!pinching) return
            pinching = false
            endInteraction()
            if (!isShown) return
            if (pinchWidth != startWidth || pinchHeight != startHeight) {
                commitSize(pinchWidth, pinchHeight)
            } else {
                settle(RELEASE_STIFFNESS, RELEASE_DAMPING)
            }
        }
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    private companion object {
        /** Tuck and untuck: a little life as the card leaves and comes back from the edge. */
        const val SNAP_STIFFNESS = 550f
        const val SNAP_DAMPING = 0.85f
        /** Where the viewer let go: no overshoot past that spot. */
        const val RELEASE_STIFFNESS = 450f
        const val RELEASE_DAMPING = 1f
        /** The player controls' own fade, so making room for them moves in step. */
        const val KEEP_CLEAR_MS = 250L
        /** dp per second the card comes back in with from its tab. */
        const val UNTUCK_VELOCITY = 900f
        const val FADE_MS = 200L
        /** Quicker than the slider's own entrance, so the slider is never covered. */
        const val DUCK_IN_MS = 120L
        const val DUCKED_ALPHA = 0.25f
        /** A light fade while the controls show over the card, so they show through. */
        const val COVER_ALPHA = 0.6f
        const val ENTER_SCALE = 0.96f
        /** How much the card fades by the point where a swipe down minimizes. */
        const val FOLLOW_FADE = 0.45f
        /** Share of the minimize motion by which the card has faded out. */
        const val LEAVE_FADE_SHARE = 0.6f
        /** Share at the end of the maximize motion over which the card fades in. */
        const val ENTER_FADE_SHARE = 0.65f
        const val MAX_UNREAD = 99
        const val TAB_HEIGHT_DP = 44f
        val EMPHASIZED = PathInterpolator(0.2f, 0f, 0f, 1f)
        val PLAYER_MOTION = AccelerateDecelerateInterpolator()
    }
}
