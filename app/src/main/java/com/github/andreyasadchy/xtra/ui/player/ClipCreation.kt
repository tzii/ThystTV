package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.helix.clip.CreateClipResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/** The viewer's choices in the clip dialog. */
internal data class ClipRequest(val title: String?, val durationSeconds: Float) {
    companion object {
        fun of(title: CharSequence?, durationSeconds: Float) = ClipRequest(
            title = title?.toString()?.trim()?.take(ClipCreation.MAX_TITLE_LENGTH)?.takeUnless(String::isEmpty),
            durationSeconds = durationSeconds.coerceIn(ClipCreation.MIN_DURATION_SECONDS, ClipCreation.MAX_DURATION_SECONDS),
        )
    }
}

/**
 * A clip waiting on the viewer to grant permission, kept with the token in use when they
 * left: a different token on return means they approved, the same one means they backed out.
 * With no [request] they had not chosen a title and length yet.
 */
internal data class PendingClip(val channelId: String, val request: ClipRequest?, val tokenAtLaunch: String?)

internal sealed interface ClipCreationResult {
    data class Created(val id: String, val editUrl: String?) : ClipCreationResult
    data object SignInRequired : ClipCreationResult
    data object NotAllowed : ClipCreationResult
    data object NotClippable : ClipCreationResult
    data object TitleRejected : ClipCreationResult
    data object NotLive : ClipCreationResult
    data object RateLimited : ClipCreationResult
    data object NotFinished : ClipCreationResult
    data object Unavailable : ClipCreationResult
    data object Failed : ClipCreationResult
}

/**
 * Twitch's Create Clip API captures the live broadcast around the moment of the
 * request, not the viewer's position, so recording replays do not offer it. Clips
 * are processed asynchronously. The endpoint reference allows 60 seconds for
 * confirmation; the separate Clips guide still says 15 seconds. It accepts 5 to 60
 * seconds and defaults to 30; a title can be refused by the channel's AutoMod.
 */
internal object ClipCreation {
    const val CONFIRM_TIMEOUT_MS = 60_000L
    const val CONFIRM_POLL_MS = 3_000L
    const val MIN_DURATION_SECONDS = 5f
    const val MAX_DURATION_SECONDS = 60f
    const val DEFAULT_DURATION_SECONDS = 30f
    const val MAX_TITLE_LENGTH = 100

    private val json = Json { ignoreUnknownKeys = true }

    fun parseResponse(status: Int, body: String): ClipCreationResult = when (status) {
        202 -> try {
            json.decodeFromString<CreateClipResponse>(body).data.firstOrNull()
                ?.takeUnless { it.id.isNullOrBlank() }
                ?.let { ClipCreationResult.Created(it.id!!, it.editUrl?.takeUnless(String::isBlank)) }
                ?: ClipCreationResult.Failed
        } catch (_: IllegalArgumentException) {
            ClipCreationResult.Failed
        }
        // Twitch answers 400 for several reasons; only its message tells a refused title apart.
        400 -> if (body.contains("automod", true) || body.contains("title", true)) ClipCreationResult.TitleRejected else ClipCreationResult.NotClippable
        401 -> ClipCreationResult.SignInRequired
        403 -> ClipCreationResult.NotAllowed
        404 -> ClipCreationResult.NotLive
        429 -> ClipCreationResult.RateLimited
        503 -> ClipCreationResult.Unavailable
        else -> ClipCreationResult.Failed
    }

    /** A failed check is not a failed clip: keep asking until the deadline. */
    suspend fun awaitConfirmation(exists: suspend () -> Boolean): Boolean = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) {
        var waitedMs = 0L
        while (waitedMs < CONFIRM_TIMEOUT_MS) {
            delay(CONFIRM_POLL_MS)
            waitedMs += CONFIRM_POLL_MS
            try {
                if (exists()) return@withTimeoutOrNull true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
        false
    } ?: false
}
