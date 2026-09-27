package com.github.andreyasadchy.xtra.ui.main

import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.applyTvFocusOutline
import com.github.andreyasadchy.xtra.util.isTelevision
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
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

    fun prepareContent() {
        fallbackRoot()?.takeIf { it.context.isTelevision() }?.let(::allowHeaderRemoteFocus)
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
        if (view is AppBarLayout) {
            // Keep simple tabs/ranges fixed, but leave room for content beneath
            // profile banners by collapsing them only as far as their pinned toolbar.
            var hasProfileHeader = false
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index)
                val flags = if (child is CollapsingToolbarLayout) {
                    hasProfileHeader = true
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
                } else 0
                (child.layoutParams as? AppBarLayout.LayoutParams)?.scrollFlags = flags
            }
            view.setExpanded(!hasProfileHeader, false)
            if (hasProfileHeader) {
                view.doOnLayout {
                    // CoordinatorLayout applies the collapse offset after the app
                    // bar's layout callback. Inspect the final pinned geometry.
                    view.post { restoreVisibleProfileFocus(view) }
                }
            }
        }
        if (view is ViewGroup) {
            // Material app bars block ordinary focus when a TV also advertises
            // touchscreen input, leaving their visible tabs/actions unreachable.
            view.touchscreenBlocksFocus = false
            for (index in 0 until view.childCount) allowHeaderRemoteFocus(view.getChildAt(index))
        }
    }

    private fun restoreVisibleProfileFocus(header: AppBarLayout) {
        if (!header.isAttachedToWindow) return
        val focused = header.findFocus() ?: return
        val profile = header.findViewById<View>(R.id.toolbarContainer) ?: return
        if (!focused.isWithin(profile)) return
        val toolbar = header.findViewById<ViewGroup>(R.id.toolbar) ?: return
        val focusedBounds = Rect()
        val covered = !focused.getGlobalVisibleRect(focusedBounds) ||
            listOfNotNull(toolbar, header.findViewById<View>(R.id.toolbarContainer2)).any { pinned ->
                val pinnedBounds = Rect()
                pinned.getGlobalVisibleRect(pinnedBounds) && Rect.intersects(pinnedBounds, focusedBounds)
            }
        if (!covered) return

        val tabs = header.findViewById<TabLayout>(R.id.tabLayout)
        val selectedTab = (tabs?.getChildAt(0) as? ViewGroup)?.let { row ->
            tabs?.selectedTabPosition?.takeIf { it in 0 until row.childCount }?.let(row::getChildAt)
        }
        val candidates = listOfNotNull(selectedTab) + toolbar.getFocusables(View.FOCUS_FORWARD)
        candidates.firstOrNull { candidate ->
            val bounds = Rect()
            isAvailable(candidate) && candidate.getGlobalVisibleRect(bounds) &&
                bounds.width() == candidate.width && bounds.height() == candidate.height && candidate.requestFocus()
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
