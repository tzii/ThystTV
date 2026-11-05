package com.github.andreyasadchy.xtra.ui.settings

import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.github.andreyasadchy.xtra.R
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class SpoilerShieldSearchTest {
    class Host : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = View(inflater.context)
    }

    private val fragment = Host()
    private val gate = CompletableDeferred<List<String>>()
    private var searchStarted = false

    @Before fun setup() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.BaseDarkTheme)
        }.setup().get()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
    }

    /** Twitch's search sources catch every exception, which turns a cancellation into no results. */
    private fun startSearch(): AlertDialog {
        fragment.showSpoilerShieldSearch(
            hint = R.string.spoiler_shield_search,
            search = {
                searchStarted = true
                try {
                    gate.await()
                } catch (_: Exception) {
                    emptyList()
                }
            },
            label = { it },
            typed = { it },
            chosen = {},
        )
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        // The dialog wires its Search button when shown, and that is posted.
        shadowOf(Looper.getMainLooper()).idle()
        dialog.findViewById<EditText>(R.id.input)!!.setText("chess")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("the search is running", searchStarted)
        return dialog
    }

    @Test fun `a finished search offers its results`() {
        val search = startSearch()
        gate.complete(listOf("Chess"))
        shadowOf(Looper.getMainLooper()).idle()
        assertNotSame(search, ShadowDialog.getLatestDialog())
    }

    @Test fun `cancelling a running search never opens results afterwards`() {
        val search = startSearch()
        search.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        gate.complete(listOf("Chess"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, ShadowDialog.getShownDialogs().size)
    }
}
