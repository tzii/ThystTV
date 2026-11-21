package com.github.andreyasadchy.xtra.ui.settings

import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.github.andreyasadchy.xtra.R
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener

/** Controls an existing session only; opening Settings must never initialize Cast. */
internal class CastSessionPreference(
    private val preference: Preference,
    private val existingManager: () -> SessionManager? = {
        @Suppress("DEPRECATION")
        CastContext.getSharedInstance()?.sessionManager
    },
) {
    companion object {
        /** Runtime action, omitted from XML so the static settings search never indexes it. */
        fun attach(screen: PreferenceScreen): CastSessionPreference {
            val row = screen.findPreference<Preference>("stop_casting") ?: Preference(screen.context).apply {
                key = "stop_casting"
                order = -1
                setTitle(R.string.cast_stop)
                setSummary(R.string.cast_stop_summary)
                setIcon(R.drawable.ic_cast_24)
                isVisible = false
                isSingleLineTitle = false
                screen.addPreference(this)
            }
            return CastSessionPreference(row)
        }
    }

    private var manager: SessionManager? = null
    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, id: String) = refresh()
        override fun onSessionResumed(session: CastSession, suspended: Boolean) = refresh()
        override fun onSessionSuspended(session: CastSession, reason: Int) = refresh()
        override fun onSessionStarting(session: CastSession) = refresh()
        override fun onSessionResuming(session: CastSession, id: String) = refresh()
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) { preference.isVisible = false }
        override fun onSessionStartFailed(session: CastSession, error: Int) { preference.isVisible = false }
        override fun onSessionResumeFailed(session: CastSession, error: Int) { preference.isVisible = false }
    }

    init {
        preference.isVisible = false
        preference.setOnPreferenceClickListener {
            // Re-read at the moment of the tap; the receiver may have disconnected.
            manager?.takeIf { it.currentCastSession != null }?.endCurrentSession(true)
            refresh()
            true
        }
    }

    fun start() {
        stop()
        manager = try { existingManager() } catch (_: RuntimeException) { null }
        manager?.addSessionManagerListener(listener, CastSession::class.java)
        refresh()
    }

    fun stop() {
        manager?.removeSessionManagerListener(listener, CastSession::class.java)
        manager = null
        preference.isVisible = false
    }

    private fun refresh() {
        preference.isVisible = manager?.currentCastSession != null
    }
}
