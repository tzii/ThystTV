package com.github.andreyasadchy.xtra.ui.main

import android.app.Application
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

/** "Watch clip" looks the clip up first; the result must not outlive the player that asked. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class WatchClipDeliveryTest {
    private val activity = Mockito.mock(MainActivity::class.java, Mockito.CALLS_REAL_METHODS)
    private val manager: FragmentManager = mock()
    private val source: PlayerFragment = mock()
    private val clip = Clip(id = "clip-id")

    private fun setup(current: PlayerFragment?, requested: String? = clip.id) {
        doReturn(manager).whenever(activity).supportFragmentManager
        whenever(manager.isStateSaved).thenReturn(false)
        whenever(manager.findFragmentById(R.id.playerContainer)).thenReturn(current)
        ReflectionHelpers.setField(activity, "playerFragment", current)
        ReflectionHelpers.setField(activity, "watchClipRequest", requested?.let { it to source })
        doNothing().whenever(activity).startClip(any())
    }

    private fun pendingRequest(): Any? = ReflectionHelpers.getField(activity, "watchClipRequest")

    @Test fun `clip plays while the player that asked is still open`() {
        setup(current = source)
        activity.deliverClip(clip)
        verify(activity).startClip(clip)
        assertNull(pendingRequest())
    }

    @Test fun `clip is dropped after the player that asked was closed`() {
        setup(current = null)
        activity.deliverClip(clip)
        verify(activity, never()).startClip(any())
        assertNull(pendingRequest())
    }

    @Test fun `clip does not replace a stream picked while it loaded`() {
        setup(current = mock())
        activity.deliverClip(clip)
        verify(activity, never()).startClip(any())
    }

    @Test fun `clip links still open without a player`() {
        setup(current = null, requested = null)
        activity.deliverClip(clip)
        verify(activity).startClip(clip)
    }

    @Test fun `a link to another clip leaves the pending request alone`() {
        setup(current = null)
        val linked = Clip(id = "linked")
        activity.deliverClip(linked)
        verify(activity).startClip(linked)
        activity.deliverClip(clip)
        verify(activity, never()).startClip(clip)
    }
}
