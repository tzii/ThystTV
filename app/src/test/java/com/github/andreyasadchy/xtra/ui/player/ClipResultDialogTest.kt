package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import android.content.ClipboardManager
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.google.android.material.slider.Slider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class ClipResultDialogTest {
    private lateinit var activity: ActivityController<AppCompatActivity>
    private lateinit var fragment: PlayerFragment
    private lateinit var model: PlayerViewModel
    private val clip = ClipCreationResult.Created("clip-id", "https://clips.twitch.tv/clip-id/edit")
    private lateinit var result: MutableStateFlow<ClipCreationResult?>

    @Before fun setup() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.BaseDarkTheme)
        }.setup()
        model = mock()
        result = MutableStateFlow(clip)
        whenever(model.clipCreation).thenReturn(result)
        fragment = mock(defaultAnswer = Mockito.CALLS_REAL_METHODS, useConstructor = UseConstructor.parameterless())
        doReturn(activity.get()).whenever(fragment).context
        doReturn(activity.get()).whenever(fragment).activity
        doReturn(true).whenever(fragment).isResumed
        doReturn(Bundle().apply { putString("channelId", "channel") }).whenever(fragment).arguments
        ReflectionHelpers.setField(fragment, "viewModel\$delegate", lazyOf(model))
        ReflectionHelpers.setField(fragment, "videoType", PlayerFragment.STREAM)
    }

    @After fun teardown() {
        dialog()?.dismiss()
        activity.pause().stop().destroy()
    }

    private fun show() = ReflectionHelpers.callInstanceMethod<Unit>(fragment, "showClipResult")
    private fun dialog(): AlertDialog? = ReflectionHelpers.getField(fragment, "clipDialog")

    @Test fun `lifecycle dismissal preserves links until an explicit close`() {
        show()
        val first = dialog()!!
        assertTrue(first.isShowing)
        assertEquals(clip, result.value)
        first.dismiss() // The same dismissal used by stop, minimize and PiP.
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(clip, result.value)
        show()
        val restored = dialog()!!
        assertNotSame(first, restored)
        restored.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(result.value)
    }

    @Test fun `Back or outside cancellation acknowledges the result`() {
        show()
        dialog()!!.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(result.value)
        show()
        assertNull(dialog())
    }

    @Test fun `a held result waits for the full resumed player and opens only once`() {
        fragment.isMaximized = false
        show()
        assertNull(dialog())
        fragment.isMaximized = true
        doReturn(false).whenever(fragment).isResumed
        show()
        assertNull(dialog())
        doReturn(true).whenever(fragment).isResumed
        show()
        val first = dialog()
        show()
        assertSame(first, dialog())
    }

    @Test fun `repeated creation during processing gives feedback without another request`() {
        result.value = null
        whenever(model.isCreatingClip).thenReturn(true)
        fragment.createClip()
        assertEquals(activity.get().getString(R.string.clip_creating), ShadowToast.getTextOfLatestToast())
        verify(model, never()).createClip(anyOrNull(), any(), any(), any(), any(), any())
    }

    private fun showOptions(previous: ClipRequest? = null) = ReflectionHelpers.callInstanceMethod<Unit>(
        fragment, "showClipOptions",
        ReflectionHelpers.ClassParameter.from(String::class.java, "channel"),
        ReflectionHelpers.ClassParameter.from(ClipRequest::class.java, previous),
    )

    @Test fun `the options offer Twitch's clip lengths and default to its 30 seconds`() {
        result.value = null
        showOptions()
        val slider = dialog()!!.findViewById<Slider>(R.id.clipDuration)!!
        assertEquals(5f, slider.valueFrom)
        assertEquals(60f, slider.valueTo)
        assertEquals(30f, slider.value)
        assertEquals(activity.get().getString(R.string.clip_length_seconds, 30), dialog()!!.findViewById<TextView>(R.id.clipLength)!!.text.toString())
        slider.value = 45f
        assertEquals(activity.get().getString(R.string.clip_length_seconds, 45), dialog()!!.findViewById<TextView>(R.id.clipLength)!!.text.toString())
    }

    @Test fun `cancelling the options requests nothing`() {
        result.value = null
        showOptions()
        dialog()!!.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(dialog())
        verify(model, never()).createClip(anyOrNull(), any(), any(), any(), any(), any())
    }

    @Test fun `create sends the chosen title and length`() {
        result.value = null
        ReflectionHelpers.setField(fragment, "authRepository", mock<AuthRepository>())
        showOptions()
        dialog()!!.findViewById<EditText>(R.id.clipTitle)!!.setText(" Big play ")
        dialog()!!.findViewById<Slider>(R.id.clipDuration)!!.value = 12f
        dialog()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        verify(model).createClip(anyOrNull(), any(), eq("channel"), eq(ClipRequest("Big play", 12f)), any(), any())
    }

    @Test fun `a refused title reopens the options with the viewer's choices`() {
        whenever(model.lastClipRequest).thenReturn(ClipRequest("Bad title", 12f))
        result.value = ClipCreationResult.TitleRejected
        doReturn(Bundle().apply { putString("channelId", "channel") }).whenever(fragment).requireArguments()
        show()
        assertNull(result.value)
        assertEquals(activity.get().getString(R.string.clip_title_rejected), ShadowToast.getTextOfLatestToast())
        assertEquals("Bad title", dialog()!!.findViewById<EditText>(R.id.clipTitle)!!.text.toString())
        assertEquals(12f, dialog()!!.findViewById<Slider>(R.id.clipDuration)!!.value)
    }

    @Test fun `opening the options after a failed attempt keeps the viewer's choices`() {
        whenever(model.lastClipRequest).thenReturn(ClipRequest("Big play", 12f))
        result.value = null
        activity.get().tokenPrefs().edit().putString(C.TOKEN, "token").commit()
        doReturn(Bundle().apply { putString("channelId", "channel") }).whenever(fragment).requireArguments()
        fragment.createClip()
        assertEquals("Big play", dialog()!!.findViewById<EditText>(R.id.clipTitle)!!.text.toString())
        assertEquals(12f, dialog()!!.findViewById<Slider>(R.id.clipDuration)!!.value)
    }

    @Test fun `a stream with no channel id offers no clip`() {
        doReturn(Bundle().apply { putString("channelLogin", "caster") }).whenever(fragment).requireArguments()
        assertFalse(fragment.canCreateClip())
        doReturn(Bundle().apply { putString("channelId", "channel") }).whenever(fragment).requireArguments()
        assertTrue(fragment.canCreateClip())
        ReflectionHelpers.setField(fragment, "videoType", PlayerFragment.VIDEO)
        assertFalse(fragment.canCreateClip())
    }

    private fun resumePending(pending: PendingClip): PendingClip? {
        val model = model
        var stored: PendingClip? = pending
        whenever(model.pendingClip).thenAnswer { stored }
        doAnswer { stored = it.getArgument(0); null }.whenever(model).pendingClip = anyOrNull()
        doReturn(Bundle().apply { putString("channelId", "channel") }).whenever(fragment).requireArguments()
        ReflectionHelpers.setField(fragment, "authRepository", mock<AuthRepository>())
        ReflectionHelpers.callInstanceMethod<Unit>(fragment, "resumePendingClip")
        return stored
    }

    @Test fun `coming back without authorizing does not create a clip`() {
        result.value = null
        activity.get().tokenPrefs().edit().putString(C.CLIP_CLIENT_ID, null).putString(C.CLIP_TOKEN, null).putString(C.TOKEN, null).commit()
        assertNull(resumePending(PendingClip("channel", ClipRequest(null, 30f), null)))
        verify(model, never()).createClip(anyOrNull(), any(), any(), any(), any(), any())
    }

    @Test fun `a new token resumes the clip the viewer asked for`() {
        result.value = null
        activity.get().tokenPrefs().edit().putString(C.CLIP_CLIENT_ID, "client").putString(C.CLIP_TOKEN, "approved").commit()
        try {
            fragment.isMaximized = true
            assertNull(resumePending(PendingClip("channel", ClipRequest("Big play", 20f), null)))
            verify(model).createClip(anyOrNull(), any(), eq("channel"), eq(ClipRequest("Big play", 20f)), any(), any())
        } finally {
            activity.get().tokenPrefs().edit().clear().commit()
        }
    }

    @Test fun `a new token opens the options when none were chosen yet`() {
        result.value = null
        activity.get().tokenPrefs().edit().putString(C.CLIP_CLIENT_ID, "client").putString(C.CLIP_TOKEN, "approved").commit()
        try {
            fragment.isMaximized = true
            assertNull(resumePending(PendingClip("channel", null, null)))
            assertNotNull(dialog()?.findViewById<Slider>(R.id.clipDuration))
            verify(model, never()).createClip(anyOrNull(), any(), any(), any(), any(), any())
        } finally {
            activity.get().tokenPrefs().edit().clear().commit()
        }
    }

    @Test fun `the result offers watching, copying, sharing and editing`() {
        show()
        val shown = dialog()!!
        listOf(R.id.clipWatch, R.id.clipCopyLink, R.id.clipShare, R.id.clipEdit).forEach {
            assertEquals(View.VISIBLE, shown.findViewById<View>(it)!!.visibility)
            // The icons are white vectors; without the tint they vanish on a light dialog.
            assertNotNull(TextViewCompat.getCompoundDrawableTintList(shown.findViewById<TextView>(it)!!))
        }
    }

    @Test fun `a clip without an editor link hides Edit`() {
        result.value = ClipCreationResult.Created("clip-id", null)
        show()
        assertEquals(View.GONE, dialog()!!.findViewById<View>(R.id.clipEdit)!!.visibility)
    }

    @Test fun `copying the link keeps the result open for another action`() {
        show()
        val shown = dialog()!!
        shown.findViewById<View>(R.id.clipCopyLink)!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val clipboard = activity.get().getSystemService(ClipboardManager::class.java)
        assertEquals("https://clips.twitch.tv/clip-id", clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertEquals(activity.get().getString(R.string.clip_link_copied), ShadowToast.getTextOfLatestToast())
        assertTrue(shown.isShowing)
        assertEquals(clip, result.value)
    }

    @Test fun `watching acknowledges the result and opens the clip`() {
        show()
        val shown = dialog()!!
        shown.findViewById<View>(R.id.clipWatch)!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        verify(fragment).watchCreatedClip("clip-id")
        assertFalse(shown.isShowing)
        assertNull(result.value)
        assertNull(dialog())
    }
}
