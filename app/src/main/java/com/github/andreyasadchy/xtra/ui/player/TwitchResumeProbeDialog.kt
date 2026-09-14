package com.github.andreyasadchy.xtra.ui.player

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.Account
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.Failure
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.ProbeException
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-session manual experiment. Closing/backgrounding cancels work; nothing auto-resumes. */
@AndroidEntryPoint
class TwitchResumeProbeDialog : DialogFragment() {
    @Inject lateinit var repository: TwitchResumeProbeRepository
    private var status: TextView? = null
    private val buttons = mutableListOf<Button>()
    private var seekButton: Button? = null
    private var job: Job? = null
    private var remote: TwitchResumeProbeRepository.Position? = null
    private var confirmation: Dialog? = null
    @Volatile private var sessionActive = false
    private lateinit var account: Account
    private lateinit var appContext: Context
    private val videoId get() = requireArguments().getString(VIDEO_ID).orEmpty()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        appContext = context.applicationContext
        account = currentAccount()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        content.addView(TextView(context).apply {
            text = getString(R.string.resume_probe_intro, account.id, videoId)
        })
        fun action(label: Int, onClick: () -> Unit): Button = MaterialButton(context).also {
            it.setText(label)
            it.setOnClickListener { onClick() }
            content.addView(it, LinearLayout.LayoutParams(-1, -2))
            buttons.add(it)
        }
        action(R.string.resume_probe_read) {
            runProbe {
                val result = repository.read(account, videoId, ::isCurrent)
                remote = result
                getString(R.string.resume_probe_read_result, format(result.seconds))
            }
        }
        seekButton = action(R.string.resume_probe_seek) {
            val player = currentPlayer() ?: return@action
            val seconds = remote?.seconds ?: return@action
            if (!isCurrent() || seconds * 1000L > player.getDuration()) {
                status?.text = getString(R.string.resume_probe_session_changed)
                return@action
            }
            // Explicit user action only. Reading remote progress never seeks the player.
            player.seek(seconds * 1000L)
            dismiss()
        }.apply { isEnabled = false }
        action(R.string.resume_probe_recent) {
            runProbe {
                val recent = repository.recent(account, ::isCurrent)
                if (recent.isEmpty()) getString(R.string.resume_probe_empty) else recent.joinToString("\n\n") {
                    "${it.title}\n${it.videoId}: ${format(it.seconds)}\n${it.updatedAt.orEmpty()}"
                }
            }
        }
        action(R.string.resume_probe_send) { confirmPosition() }
        status = TextView(context).apply {
            setText(R.string.resume_probe_ready)
            setTextIsSelectable(true)
            content.addView(this)
        }
        return context.getAlertDialogBuilder()
            .setTitle(R.string.resume_probe_title)
            .setView(NestedScrollView(context).apply { addView(content) })
            .setNegativeButton(R.string.close, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        sessionActive = true
        if (!appContext.prefs().getBoolean(PREFERENCE, false) || currentPlayer() == null) dismiss()
    }

    private fun confirmPosition() {
        val player = currentPlayer() ?: return
        val position = player.getCurrentPosition()
        if (position == null || position < 0 || player.getDuration() <= 0 || position > player.getDuration()) {
            status?.text = getString(R.string.resume_probe_wait_for_player)
            return
        }
        if (!isCurrent()) {
            status?.text = getString(R.string.resume_probe_session_changed)
            return
        }
        // Freeze the actual player position shown for approval, not a manually entered value.
        confirmation = requireContext().getAlertDialogBuilder()
            .setTitle(R.string.resume_probe_send)
            .setMessage(getString(R.string.resume_probe_confirm, format(position / 1000L), account.id, videoId))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.resume_probe_send_confirm) { _, _ ->
                runProbe(writing = true) {
                    val result = repository.writeAndReadBack(account, videoId, position, ::isCurrent)
                    remote = if (result.readbackFailure == null) TwitchResumeProbeRepository.Position(videoId, result.observed) else null
                    when {
                        result.matched -> getString(R.string.resume_probe_verified, format(result.seconds))
                        result.readbackFailure != null -> getString(R.string.resume_probe_accepted_unverified, failureText(result.readbackFailure))
                        else -> getString(R.string.resume_probe_different, format(result.seconds), format(result.observed))
                    }
                }
            }.show()
    }

    private fun runProbe(writing: Boolean = false, operation: suspend () -> String) {
        if (job?.isActive == true) return
        if (!isCurrent() || currentPlayer() == null) {
            status?.text = getString(R.string.resume_probe_session_changed)
            return
        }
        remote = null
        buttons.forEach { it.isEnabled = false }
        status?.text = getString(R.string.resume_probe_loading)
        job = lifecycleScope.launch {
            try {
                val message = operation()
                if (isCurrent() && currentPlayer() != null) status?.text = message
            } catch (error: CancellationException) {
                throw error
            } catch (error: ProbeException) {
                if (sessionActive) {
                    status?.text = failureText(error.failure) + if (writing) "\n\n" + getString(R.string.resume_probe_write_uncertain) else ""
                }
            } finally {
                if (sessionActive) {
                    buttons.forEach { it.isEnabled = true }
                    seekButton?.isEnabled = remote?.seconds != null && isCurrent()
                }
            }
        }
    }

    private fun currentAccount() = Account(
        appContext.tokenPrefs().getString(C.USER_ID, null).orEmpty(),
        TwitchApiHelper.getGQLHeaders(appContext, true),
    )

    // Called from both UI and network threads. SharedPreferences is thread-safe; no Views here.
    private fun isCurrent(): Boolean {
        if (!sessionActive || !appContext.prefs().getBoolean(PREFERENCE, false)) return false
        val current = currentAccount()
        return current.id == account.id && current.headers == account.headers
    }

    private fun currentPlayer(): PlayerFragment? = (parentFragment as? PlayerFragment)?.takeIf {
        it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && it.resumeProbeVideoId() == videoId
    }

    private fun format(seconds: Long?) = seconds?.let(DateUtils::formatElapsedTime) ?: getString(R.string.resume_probe_no_position)

    private fun failureText(failure: Failure): String = getString(when (failure) {
        Failure.SIGN_IN_REQUIRED -> R.string.resume_probe_sign_in
        Failure.ACCOUNT_MISMATCH -> R.string.resume_probe_account_mismatch
        Failure.CLIENT_MISMATCH -> R.string.resume_probe_client_mismatch
        Failure.SESSION_CHANGED -> R.string.resume_probe_session_changed
        Failure.TOKEN_REJECTED -> R.string.resume_probe_token_rejected
        Failure.AUTHENTICATION -> R.string.resume_probe_authentication
        Failure.UNSUPPORTED_OPERATION -> R.string.resume_probe_unsupported
        Failure.GRAPHQL -> R.string.resume_probe_graphql
        Failure.HTTP -> R.string.resume_probe_http
        Failure.NETWORK -> R.string.resume_probe_network
        Failure.INVALID_RESPONSE -> R.string.resume_probe_invalid_response
        Failure.UNAVAILABLE_VIDEO -> R.string.resume_probe_unavailable
        Failure.INVALID_POSITION -> R.string.resume_probe_wait_for_player
    })

    override fun onStop() {
        sessionActive = false
        job?.cancel()
        confirmation?.dismiss()
        confirmation = null
        super.onStop()
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        status = null
        seekButton = null
        buttons.clear()
        super.onDestroyView()
    }

    companion object {
        const val PREFERENCE = "debug_twitch_resume_probe"
        const val TAG = "twitch_resume_probe"
        private const val VIDEO_ID = "videoId"
        fun newInstance(videoId: String) = TwitchResumeProbeDialog().apply {
            arguments = Bundle().apply { putString(VIDEO_ID, videoId) }
        }
    }
}
