package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.color.MaterialColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.lang.ref.WeakReference

/** The card or player fields Spoiler Shield needs, whatever the item type. */
internal data class SpoilerSubject(
    val key: String,
    val channelId: String? = null,
    val channelLogin: String? = null,
    val channelName: String? = null,
    val gameId: String? = null,
    val gameName: String? = null,
    val title: String? = null,
    val hasThumbnail: Boolean = false,
)

internal fun Stream.spoilerSubject() = SpoilerSubject(
    key = "stream:" + (id ?: channelId ?: channelLogin.orEmpty()),
    channelId, channelLogin, channelName, gameId, gameName, title, thumbnailURL != null,
)

internal fun Video.spoilerSubject() = SpoilerSubject(
    key = "video:" + (id ?: thumbnailURL.orEmpty()),
    channelId, channelLogin, channelName, gameId, gameName, title, thumbnailURL != null,
)

internal fun Clip.spoilerSubject() = SpoilerSubject(
    key = "clip:" + (id ?: thumbnailURL.orEmpty()),
    channelId, channelLogin, channelName, gameId, gameName, title, thumbnailURL != null,
)

internal fun Bookmark.spoilerSubject() = SpoilerSubject(
    key = "bookmark:" + (videoId ?: thumbnail.orEmpty()),
    userId, userLogin, userName, gameId, gameName, title, thumbnail != null,
)

internal fun OfflineVideo.spoilerSubject() = SpoilerSubject(
    key = "offline:$id",
    channelId, channelLogin, channelName, gameId, gameName, name, thumbnail != null,
)

@Serializable
internal data class ProtectedChannel(
    val id: String? = null,
    val login: String? = null,
    val name: String? = null,
    val expiresAt: Long? = null,
) {
    fun matches(subject: SpoilerSubject): Boolean =
        if (!id.isNullOrBlank() && !subject.channelId.isNullOrBlank()) id == subject.channelId
        else !login.isNullOrBlank() && login.equals(subject.channelLogin, true)

    fun sameChannel(other: ProtectedChannel): Boolean =
        if (!id.isNullOrBlank() && !other.id.isNullOrBlank()) id == other.id
        else !login.isNullOrBlank() && login.equals(other.login, true)

    fun isActive(now: Long) = expiresAt == null || expiresAt > now
}

@Serializable
internal data class ProtectedCategory(
    val id: String? = null,
    val name: String? = null,
    val expiresAt: Long? = null,
) {
    fun matches(subject: SpoilerSubject): Boolean =
        if (!id.isNullOrBlank() && !subject.gameId.isNullOrBlank()) id == subject.gameId
        else !name.isNullOrBlank() && name.equals(subject.gameName, true)

    fun sameCategory(other: ProtectedCategory): Boolean =
        if (!id.isNullOrBlank() && !other.id.isNullOrBlank()) id == other.id
        else !name.isNullOrBlank() && name.equals(other.name, true)

    fun isActive(now: Long) = expiresAt == null || expiresAt > now
}

@Serializable
internal data class SpoilerShieldRules(
    val channels: List<ProtectedChannel> = emptyList(),
    val categories: List<ProtectedCategory> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && categories.isEmpty()

    fun protectsChannel(subject: SpoilerSubject) = channels.any { it.matches(subject) }
    fun protectsCategory(subject: SpoilerSubject) = categories.any { it.matches(subject) }
    fun protects(subject: SpoilerSubject) = protectsChannel(subject) || protectsCategory(subject)

    /** Cards bind on every scroll step, so only allocate once something has expired. */
    fun active(now: Long): SpoilerShieldRules =
        if (channels.all { it.isActive(now) } && categories.all { it.isActive(now) }) this
        else SpoilerShieldRules(channels.filter { it.isActive(now) }, categories.filter { it.isActive(now) })

    fun togglingChannel(subject: SpoilerSubject, expiresAt: Long? = null) = copy(
        channels = if (protectsChannel(subject)) channels.filterNot { it.matches(subject) }
        else channels + ProtectedChannel(subject.channelId, subject.channelLogin, subject.channelName, expiresAt),
    )

    fun togglingCategory(subject: SpoilerSubject, expiresAt: Long? = null) = copy(
        categories = if (protectsCategory(subject)) categories.filterNot { it.matches(subject) }
        else categories + ProtectedCategory(subject.gameId, subject.gameName, expiresAt),
    )

    /** Adding an existing channel again replaces its rule, so it can change a time limit. */
    fun withChannel(channel: ProtectedChannel) = copy(
        channels = channels.filterNot { it.sameChannel(channel) } + channel,
    )

    fun withCategory(category: ProtectedCategory) = copy(
        categories = categories.filterNot { it.sameCategory(category) } + category,
    )

    fun without(removed: SpoilerShieldRules) = copy(
        channels = channels - removed.channels.toSet(),
        categories = categories - removed.categories.toSet(),
    )
}

internal data class SpoilerPresentation(
    val hideTitle: Boolean = false,
    val hideThumbnail: Boolean = false,
    val canToggle: Boolean = false,
    val revealed: Boolean = false,
) {
    val hidesAnything get() = hideTitle || hideThumbnail
}

/**
 * Viewer-chosen channels and categories whose titles and thumbnails stay hidden in
 * stream, VoD, clip, bookmark and download lists, and whose titles stay hidden in
 * the player (issue #78). Channel and category names stay visible: they are how the
 * viewer recognizes what was hidden. This reduces accidental exposure but cannot
 * guarantee it, since a streamer can discuss a result under any category.
 */
internal object SpoilerShield {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedRaw: String? = null
    private var cachedRules = SpoilerShieldRules()

    fun decode(raw: String?): SpoilerShieldRules = try {
        if (raw.isNullOrBlank()) SpoilerShieldRules() else json.decodeFromString<SpoilerShieldRules>(raw)
    } catch (_: SerializationException) {
        SpoilerShieldRules()
    } catch (_: IllegalArgumentException) {
        SpoilerShieldRules()
    }

    fun encode(rules: SpoilerShieldRules): String = json.encodeToString(rules)

    /** Cards bind on every scroll step, so parse only when the stored rules change. */
    @Synchronized
    fun rules(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): SpoilerShieldRules {
        val raw = prefs.getString(C.SPOILER_SHIELD_RULES, null)
        if (raw != cachedRaw) {
            cachedRaw = raw
            cachedRules = decode(raw)
        }
        return cachedRules.active(now)
    }

    fun saveRules(prefs: SharedPreferences, rules: SpoilerShieldRules) {
        prefs.edit { putString(C.SPOILER_SHIELD_RULES, encode(rules)) }
    }

    /**
     * Apply dialog actions to the latest rules, preserving edits from other windows.
     * Reading through [rules] also drops expired rules from storage.
     */
    @Synchronized
    fun updateRules(prefs: SharedPreferences, update: (SpoilerShieldRules) -> SpoilerShieldRules) {
        saveRules(prefs, update(rules(prefs)))
    }

    fun presentation(
        rules: SpoilerShieldRules,
        subject: SpoilerSubject,
        hideTitles: Boolean,
        hideThumbnails: Boolean,
        revealed: Boolean,
    ): SpoilerPresentation {
        if (!rules.protects(subject)) return SpoilerPresentation()
        val titleHideable = hideTitles && !subject.title.isNullOrBlank()
        val thumbnailHideable = hideThumbnails && subject.hasThumbnail
        return SpoilerPresentation(
            hideTitle = titleHideable && !revealed,
            hideThumbnail = thumbnailHideable && !revealed,
            canToggle = titleHideable || thumbnailHideable,
            revealed = revealed,
        )
    }

    /** For surfaces without a reveal control of their own. */
    fun hidesTitle(prefs: SharedPreferences, subject: SpoilerSubject): Boolean =
        prefs.getBoolean(C.SPOILER_SHIELD_HIDE_TITLES, true) && rules(prefs).protects(subject)

    /** Ask how long a new rule lasts, then report when it expires (null means never). */
    fun chooseDuration(context: Context, now: () -> Long = System::currentTimeMillis, onChosen: (Long?) -> Unit) {
        val labels = arrayOf(
            context.getString(R.string.spoiler_shield_duration_forever),
            context.getString(R.string.spoiler_shield_duration_day),
            context.getString(R.string.spoiler_shield_duration_three_days),
            context.getString(R.string.spoiler_shield_duration_week),
        )
        context.getAlertDialogBuilder()
            .setTitle(R.string.spoiler_shield_duration_title)
            .setItems(labels) { _, which -> onChosen(DURATIONS_MS[which]?.let { now() + it }) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Matches the choices in [chooseDuration], in order. */
    private val DURATIONS_MS = listOf(null, DAY_MS, 3 * DAY_MS, 7 * DAY_MS)
}

private const val DAY_MS = 24L * 60L * 60L * 1000L

/** Reveal state and rule changes for one card list. Reveals end with the list. */
internal class SpoilerShieldCards(private val adapter: RecyclerView.Adapter<*>) {
    private val revealed = mutableSetOf<String>()
    private var recyclerView: WeakReference<RecyclerView>? = null
    private var signature: List<Any?>? = null

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ -> refresh(prefs) }
    private val windowListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            val prefs = view.context.prefs()
            prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
            refresh(prefs)
        }

        override fun onViewDetachedFromWindow(view: View) {
            view.context.prefs().unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
    }

    fun attach(view: RecyclerView) {
        detach()
        recyclerView = WeakReference(view)
        signature = signatureOf(view.context.prefs())
        view.addOnAttachStateChangeListener(windowListener)
        if (view.isAttachedToWindow) view.context.prefs().registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun detach() {
        recyclerView?.get()?.let { view ->
            view.removeOnAttachStateChangeListener(windowListener)
            view.context.prefs().unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        recyclerView = null
    }

    fun presentation(context: Context, subject: SpoilerSubject, showsThumbnail: Boolean): SpoilerPresentation {
        val prefs = context.prefs()
        return SpoilerShield.presentation(
            rules = SpoilerShield.rules(prefs),
            subject = subject,
            hideTitles = prefs.getBoolean(C.SPOILER_SHIELD_HIDE_TITLES, true),
            hideThumbnails = showsThumbnail && prefs.getBoolean(C.SPOILER_SHIELD_HIDE_THUMBNAILS, true),
            revealed = subject.key in revealed,
        )
    }

    fun bindReveal(button: ImageButton, subject: SpoilerSubject, presentation: SpoilerPresentation, position: () -> Int) {
        button.isVisible = presentation.canToggle
        if (!presentation.canToggle) {
            button.setOnClickListener(null)
            return
        }
        button.setImageResource(if (presentation.revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility_high)
        button.contentDescription = button.context.getString(
            if (presentation.revealed) R.string.spoiler_shield_hide else R.string.spoiler_shield_reveal
        )
        button.setOnClickListener {
            if (!revealed.remove(subject.key)) revealed.add(subject.key)
            position().takeIf { it != RecyclerView.NO_POSITION }?.let(adapter::notifyItemChanged)
        }
    }

    /** Whether [showActions] has anything to offer, for cards exposing it through a menu. */
    fun hasActions(subject: SpoilerSubject): Boolean =
        !(subject.channelName ?: subject.channelLogin).isNullOrBlank() || !subject.gameName.isNullOrBlank()

    fun showActions(context: Context, subject: SpoilerSubject) {
        val prefs = context.prefs()
        val rules = SpoilerShield.rules(prefs)
        val actions = buildList<Pair<String, () -> Unit>> {
            (subject.channelName ?: subject.channelLogin)?.takeIf { it.isNotBlank() }?.let { channel ->
                if (rules.protectsChannel(subject)) {
                    add(context.getString(R.string.spoiler_shield_stop_channel, channel) to {
                        SpoilerShield.updateRules(prefs) { current ->
                            if (current.protectsChannel(subject)) current.togglingChannel(subject) else current
                        }
                    })
                } else {
                    add(context.getString(R.string.spoiler_shield_add_channel, channel) to {
                        SpoilerShield.chooseDuration(context) { expiresAt ->
                            SpoilerShield.updateRules(prefs) { current ->
                                if (current.protectsChannel(subject)) current else current.togglingChannel(subject, expiresAt)
                            }
                        }
                    })
                }
            }
            subject.gameName?.takeIf { it.isNotBlank() }?.let { category ->
                if (rules.protectsCategory(subject)) {
                    add(context.getString(R.string.spoiler_shield_stop_category, category) to {
                        SpoilerShield.updateRules(prefs) { current ->
                            if (current.protectsCategory(subject)) current.togglingCategory(subject) else current
                        }
                    })
                } else {
                    add(context.getString(R.string.spoiler_shield_add_category, category) to {
                        SpoilerShield.chooseDuration(context) { expiresAt ->
                            SpoilerShield.updateRules(prefs) { current ->
                                if (current.protectsCategory(subject)) current else current.togglingCategory(subject, expiresAt)
                            }
                        }
                    })
                }
            }
        }
        if (actions.isEmpty()) return
        context.getAlertDialogBuilder()
            .setTitle(R.string.spoiler_shield)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refresh(prefs: SharedPreferences) {
        val current = signatureOf(prefs)
        if (current != signature) {
            signature = current
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    private fun signatureOf(prefs: SharedPreferences): List<Any?> = listOf(
        prefs.getString(C.SPOILER_SHIELD_RULES, null),
        prefs.getBoolean(C.SPOILER_SHIELD_HIDE_TITLES, true),
        prefs.getBoolean(C.SPOILER_SHIELD_HIDE_THUMBNAILS, true),
    )
}

/** Cancels any in-flight request too, so a recycled view cannot receive a protected image. */
internal fun ImageView.showSpoilerPlaceholder() {
    dispose()
    setImageDrawable(android.graphics.drawable.ColorDrawable(
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant)
    ))
}
