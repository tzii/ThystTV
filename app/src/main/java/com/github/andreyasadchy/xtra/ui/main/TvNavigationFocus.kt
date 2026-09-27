package com.github.andreyasadchy.xtra.ui.main

import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.util.applyTvFocusOutline
import com.github.andreyasadchy.xtra.util.isTelevision
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.lang.ref.WeakReference

/** Focus moves independently from destination selection; CENTER still uses the menu's click. */
internal class TvNavigationFocus(
    private val navBar: BottomNavigationView,
    private val contentRoot: () -> View?,
    private val fallbackRoot: () -> View? = { null },
) {
    private var lastContentFocus: WeakReference<View>? = null

    fun install() {
        navigationItems().forEach { item ->
            item.applyTvFocusOutline()
            item.setOnKeyListener { _, keyCode, event ->
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (event.action == KeyEvent.ACTION_DOWN) focusContent()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            val items = navigationItems()
                            val forward = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) !=
                                (navBar.layoutDirection == View.LAYOUT_DIRECTION_RTL)
                            val index = items.indexOf(item) + if (forward) 1 else -1
                            items.getOrNull(index)?.requestFocus()
                        }
                        true
                    }
                    // Clipped feed descendants may overlap this row geometrically.
                    KeyEvent.KEYCODE_DPAD_DOWN -> true
                    else -> false
                }
            }
        }
    }

    fun isNavigationFocused(): Boolean = navBar.hasFocus()

    fun focusNavigation(): Boolean {
        val items = navigationItems()
        return (items.firstOrNull { it.id == navBar.selectedItemId } ?: items.firstOrNull())?.requestFocus() == true
    }

    fun rememberContentFocus(view: View?) {
        if (view != null && isInContent(view)) lastContentFocus = WeakReference(view)
    }

    fun focusContent(): Boolean {
        val header = fallbackRoot()
        if (header?.context?.isTelevision() == true) allowHeaderRemoteFocus(header)
        lastContentFocus?.get()?.takeIf { isInContent(it) && isAvailable(it) }?.let {
            if (it.requestFocus()) return true
        }
        for (root in listOfNotNull(contentRoot(), header).distinct()) {
            for (candidate in root.getFocusables(View.FOCUS_FORWARD)) {
                if (isAvailable(candidate) && candidate.requestFocus()) return true
            }
        }
        return false
    }

    private fun allowHeaderRemoteFocus(view: View) {
        if (view is ViewGroup) {
            // Material app bars block ordinary focus when a TV also advertises
            // touchscreen input, leaving their visible tabs/actions unreachable.
            view.touchscreenBlocksFocus = false
            for (index in 0 until view.childCount) allowHeaderRemoteFocus(view.getChildAt(index))
        }
    }

    private fun navigationItems(): List<View> = (0 until navBar.menu.size()).mapNotNull { index ->
        navBar.menu.getItem(index).takeIf { it.isEnabled && it.isVisible }
            ?.let { navBar.findViewById<View>(it.itemId) }
            ?.takeIf { it.isShown && it.isEnabled }
    }

    private fun isInContent(view: View): Boolean =
        (view.isWithin(contentRoot()) || view.isWithin(fallbackRoot())) && isAvailable(view)

    private fun isAvailable(view: View): Boolean = view.isAttachedToWindow && view.isShown &&
        view.isEnabled && view.isFocusable && view !is RecyclerView && view !is HorizontalScrollView &&
        view.getGlobalVisibleRect(Rect())
}

internal fun View.isWithin(root: View?): Boolean {
    if (root == null) return false
    var current: View? = this
    while (current != null) {
        if (current === root) return true
        current = current.parent as? View
    }
    return false
}
