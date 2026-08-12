package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksNetworkRestorationTest {

    @Test
    fun `bookmarks observes restoration and initializes while offline`() {
        val fragment = BookmarksFragment()
        val networkCheck = BaseNetworkFragment::class.java.getDeclaredMethod("getEnableNetworkCheck")
        val initializeOffline = BaseNetworkFragment::class.java.getDeclaredMethod("getInitializeWhileOffline")
        networkCheck.isAccessible = true
        initializeOffline.isAccessible = true

        assertTrue(networkCheck.invoke(fragment) as Boolean)
        assertTrue(initializeOffline.invoke(fragment) as Boolean)
    }
}
