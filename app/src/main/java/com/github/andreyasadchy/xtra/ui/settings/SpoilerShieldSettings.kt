package com.github.andreyasadchy.xtra.ui.settings

import android.app.Dialog
import android.content.DialogInterface
import android.text.format.DateUtils
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogSpoilerShieldSearchBinding
import com.github.andreyasadchy.xtra.ui.common.ProtectedCategory
import com.github.andreyasadchy.xtra.ui.common.ProtectedChannel
import com.github.andreyasadchy.xtra.ui.common.SpoilerShield
import com.github.andreyasadchy.xtra.ui.common.SpoilerShieldRules
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.launch

/** The Settings > UI list of protected channels and categories. */
internal fun Fragment.showSpoilerShieldManager(viewModel: SettingsViewModel, changed: () -> Unit) {
    val context = requireContext()
    val prefs = context.prefs()
    val rules = SpoilerShield.rules(prefs)
    if (rules.isEmpty) {
        showSpoilerShieldAdd(viewModel, changed)
        return
    }
    val labels = rules.channels.map {
        entryLabel(getString(R.string.spoiler_shield_channel_entry, it.name ?: it.login.orEmpty()), it.expiresAt)
    } + rules.categories.map {
        entryLabel(getString(R.string.spoiler_shield_category_entry, it.name.orEmpty()), it.expiresAt)
    }
    val checked = BooleanArray(labels.size) { true }
    val applyRemovals = {
        val removed = SpoilerShieldRules(
            channels = rules.channels.filterIndexed { index, _ -> !checked[index] },
            categories = rules.categories.filterIndexed { index, _ -> !checked[rules.channels.size + index] },
        )
        SpoilerShield.updateRules(prefs) { current -> current.without(removed) }
        changed()
    }
    context.getAlertDialogBuilder()
        .setTitle(R.string.spoiler_shield_manage)
        .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
        .setNeutralButton(R.string.spoiler_shield_add) { _, _ ->
            applyRemovals()
            showSpoilerShieldAdd(viewModel, changed)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { _, _ -> applyRemovals() }
        .show()
}

private fun Fragment.entryLabel(label: String, expiresAt: Long?): String =
    if (expiresAt == null) label else getString(
        R.string.spoiler_shield_entry_until,
        label,
        DateUtils.formatDateTime(
            requireContext(),
            expiresAt,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        ),
    )

private fun Fragment.showSpoilerShieldAdd(viewModel: SettingsViewModel, changed: () -> Unit) {
    val labels = arrayOf(
        getString(R.string.spoiler_shield_add_kind_channel),
        getString(R.string.spoiler_shield_add_kind_category),
    )
    requireContext().getAlertDialogBuilder()
        .setTitle(R.string.spoiler_shield_add)
        .setItems(labels) { _, which ->
            if (which == 0) {
                showSpoilerShieldSearch(
                    hint = R.string.spoiler_shield_search_channel,
                    search = viewModel::searchSpoilerChannels,
                    label = { it.name ?: it.login.orEmpty() },
                    typed = { ProtectedChannel(login = it.lowercase(), name = it) },
                ) { channel ->
                    SpoilerShield.chooseDuration(requireContext()) { expiresAt ->
                        SpoilerShield.updateRules(requireContext().prefs()) { it.withChannel(channel.copy(expiresAt = expiresAt)) }
                        changed()
                    }
                }
            } else {
                showSpoilerShieldSearch(
                    hint = R.string.spoiler_shield_search_category,
                    search = viewModel::searchSpoilerCategories,
                    label = { it.name.orEmpty() },
                    typed = { ProtectedCategory(name = it) },
                ) { category ->
                    SpoilerShield.chooseDuration(requireContext()) { expiresAt ->
                        SpoilerShield.updateRules(requireContext().prefs()) { it.withCategory(category.copy(expiresAt = expiresAt)) }
                        changed()
                    }
                }
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/** Search online, but never require it: a failed search still offers the typed text. */
private fun <T> Fragment.showSpoilerShieldSearch(
    hint: Int,
    search: suspend (String) -> List<T>,
    label: (T) -> String,
    typed: (String) -> T,
    chosen: (T) -> Unit,
) {
    val binding = DialogSpoilerShieldSearchBinding.inflate(layoutInflater)
    binding.inputLayout.hint = getString(hint)
    val dialog = requireContext().getAlertDialogBuilder()
        .setView(binding.root)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.spoiler_shield_search, null)
        .create()
    var searching: kotlinx.coroutines.Job? = null
    val start = start@{
        val query = binding.input.text?.toString()?.trim().orEmpty()
        if (query.isEmpty() || searching?.isActive == true) return@start
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = false
        binding.progress.isVisible = true
        searching = viewLifecycleOwner.lifecycleScope.launch {
            val results = search(query)
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            showSpoilerShieldResults(query, results, label, typed, chosen)
        }
    }
    dialog.setOnShowListener {
        val button = (it as AlertDialog).getButton(Dialog.BUTTON_POSITIVE)
        button.isEnabled = false
        button.setOnClickListener { start() }
        binding.input.doAfterTextChanged { text ->
            button.isEnabled = !text.isNullOrBlank() && searching?.isActive != true
        }
        binding.input.requestFocus()
    }
    dialog.setOnDismissListener { searching?.cancel() }
    binding.input.setOnEditorActionListener { _, action, _ ->
        if (action == EditorInfo.IME_ACTION_SEARCH) {
            start()
            true
        } else {
            false
        }
    }
    dialog.show()
}

private fun <T> Fragment.showSpoilerShieldResults(
    query: String,
    results: List<T>,
    label: (T) -> String,
    typed: (String) -> T,
    chosen: (T) -> Unit,
) {
    val labels = results.map(label) + getString(R.string.spoiler_shield_use_typed, query)
    requireContext().getAlertDialogBuilder()
        .setTitle(R.string.spoiler_shield_results_title)
        .setItems(labels.toTypedArray()) { _, which ->
            chosen(results.getOrNull(which) ?: typed(query))
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
