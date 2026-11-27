package com.github.andreyasadchy.xtra.ui.chat

import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * The chat's cards (poll, prediction, room status) are clickable in the layout only so a
 * tap stays on them instead of reaching the message behind (#129). They do nothing when
 * tapped, so TalkBack must not offer "double tap to activate" on them.
 */
internal object TapShieldAccessibility : AccessibilityDelegateCompat() {
    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.isClickable = false
        info.removeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
    }
}
