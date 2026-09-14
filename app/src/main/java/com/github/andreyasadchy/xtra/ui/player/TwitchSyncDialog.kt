package com.github.andreyasadchy.xtra.ui.player

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.TwitchSyncManager
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TwitchSyncDialog : DialogFragment() {
    @Inject lateinit var sync: TwitchSyncManager
    private var content: LinearLayout? = null
    private var accountId = ""
    private var displayed = ""
    private var observer: Job? = null
    private var refreshJob: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        accountId = sync.account().id
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        root.addView(TextView(context).apply {
            text = getString(if (accountId.isBlank()) R.string.twitch_sync_sign_in else R.string.twitch_sync_intro)
        })
        if (accountId.isNotBlank()) {
            fun toggle(title: Int, kind: String, checked: Boolean) {
                root.addView(SwitchCompat(context).apply {
                    setText(title)
                    isChecked = checked
                    minHeight = (56 * resources.displayMetrics.density).toInt()
                    setOnCheckedChangeListener { _, value -> sync.configure(accountId, kind, value) }
                })
            }
            toggle(R.string.twitch_sync_vod_option, "vod", sync.vodEnabled(accountId))
            toggle(R.string.twitch_sync_live_option, "live", sync.liveEnabled(accountId))
            root.addView(MaterialButton(context).apply {
                setText(R.string.twitch_sync_refresh)
                setOnClickListener {
                    isEnabled = false
                    refreshJob?.cancel()
                    refreshJob = lifecycleScope.launch {
                        try { sync.refresh(); render() } finally { isEnabled = true }
                    }
                }
            })
        }
        content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; root.addView(this) }
        return context.getAlertDialogBuilder().setTitle(R.string.twitch_sync_title)
            .setView(NestedScrollView(context).apply { addView(root) })
            .setNegativeButton(R.string.close, null).create()
    }

    override fun onStart() {
        super.onStart()
        observer?.cancel()
        refreshJob?.cancel()
        if (accountId.isNotBlank()) refreshJob = lifecycleScope.launch { sync.refresh() }
        observer = lifecycleScope.launch {
            while (content != null) {
                if (sync.account().id != accountId) { dismissAllowingStateLoss(); break }
                render()
                delay(1_000)
            }
        }
    }

    private fun render() {
        val container = content ?: return
        if (accountId.isBlank()) return
        val state = try { sync.store.read(accountId) } catch (_: Exception) {
            container.removeAllViews()
            container.addView(TextView(requireContext()).apply { setText(R.string.twitch_sync_storage_error) })
            return
        }
        val historyStatus = sync.historyStatus(accountId)
        val signature = "${state.recent}:${state.streaks}:${state.progress.count { it.pending }}:${state.progress.filter { it.conflict || it.unavailable }}:${sync.status(accountId)}:${sync.status(accountId, true)}:$historyStatus"
        if (signature == displayed) return
        displayed = signature
        container.removeAllViews()
        fun text(value: String) = container.addView(TextView(requireContext()).apply {
            text = value
            val pad = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, pad, 0, pad)
        })
        fun action(label: String, block: () -> Unit) = container.addView(MaterialButton(requireContext()).apply {
            text = label
            setOnClickListener { if (sync.account().id == accountId) block() }
        })
        fun position(seconds: Long?) = seconds?.let(DateUtils::formatElapsedTime) ?: getString(R.string.resume_probe_no_position)
        text(getString(R.string.twitch_sync_account, requireContext().tokenPrefs().getString(C.USERNAME, null)?.takeIf { it.isNotBlank() } ?: accountId))
        text(getString(R.string.twitch_sync_vod_status, getString(sync.status(accountId))))
        text(getString(R.string.twitch_sync_live_status, getString(sync.status(accountId, true))))
        text(getString(R.string.twitch_sync_pending, state.progress.count { it.pending }))
        state.progress.filter { it.conflict }.forEach { item ->
            text(getString(R.string.twitch_sync_conflict_detail, item.title.ifBlank { item.videoId }, position(item.seconds), position(item.remote)))
            action(getString(R.string.twitch_sync_keep_local)) { sync.resolve(item.videoId, true); render() }
            action(getString(R.string.twitch_sync_keep_remote)) { sync.resolve(item.videoId, false); render() }
        }
        state.progress.filter { it.unavailable }.forEach { item ->
            text(item.title.ifBlank { item.videoId } + "\n" + getString(R.string.twitch_sync_video_unavailable))
            action(getString(R.string.twitch_sync_retry_video)) { sync.unavailableVideo(item.videoId, true); render() }
            action(getString(R.string.twitch_sync_dismiss_upload)) { sync.unavailableVideo(item.videoId, false); render() }
        }
        text(getString(R.string.twitch_sync_recent))
        text(getString(historyStatus))
        if (state.recent.isNotEmpty() && historyStatus != R.string.twitch_sync_refreshed) text(getString(R.string.twitch_sync_history_cached))
        state.recent.forEach { video ->
            action("${video.title.ifBlank { video.videoId }} · ${position(video.seconds)}") {
                startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                    this.action = Intent.ACTION_VIEW
                    data = "https://www.twitch.tv/videos/${video.videoId}?t=${video.seconds}s".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                dismiss()
            }
        }
        text(getString(R.string.twitch_sync_streaks))
        if (state.streaks.isEmpty()) text(getString(R.string.twitch_sync_no_streaks))
        state.streaks.asReversed().forEach {
            text(getString(R.string.twitch_sync_streak_detail, it.channel.ifBlank { it.channelId }, it.count,
                DateUtils.formatDateTime(requireContext(), it.timestamp, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)))
        }
    }

    override fun onDestroyView() { content = null; displayed = ""; super.onDestroyView() }

    override fun onStop() {
        observer?.cancel(); refreshJob?.cancel()
        super.onStop()
    }

    companion object { const val TAG = "twitch_account_sync" }
}
