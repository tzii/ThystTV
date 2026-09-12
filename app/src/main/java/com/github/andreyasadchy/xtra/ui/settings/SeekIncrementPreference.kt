package com.github.andreyasadchy.xtra.ui.settings

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.EditTextPreference
import com.github.andreyasadchy.xtra.R

/** Edits whole seconds while preserving the existing millisecond preference contract. */
class SeekIncrementPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : EditTextPreference(context, attrs) {
    init {
        dialogMessage = context.getString(R.string.seek_increment_help)
        summaryProvider = SummaryProvider<SeekIncrementPreference> {
            context.getString(R.string.seconds_full, it.text ?: "10")
        }
        setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER
            it.setSelection(it.text.length)
        }
        setOnPreferenceChangeListener { _, value ->
            (milliseconds(value as? String) != null).also { valid ->
                if (!valid) Toast.makeText(context, R.string.seek_increment_invalid, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val savedMs = getPersistedString(defaultValue as? String)?.toLongOrNull()
            ?.takeIf { it in 1_000L..3_600_000L } ?: 10_000L
        text = (savedMs / 1000L).toString()
    }

    override fun persistString(value: String?): Boolean {
        val ms = milliseconds(value) ?: return false
        return super.persistString(ms.toString())
    }

    private fun milliseconds(value: String?): Long? = value?.trim()
        ?.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
        ?.toLongOrNull()?.takeIf { it in 1L..3600L }?.times(1000L)
}
