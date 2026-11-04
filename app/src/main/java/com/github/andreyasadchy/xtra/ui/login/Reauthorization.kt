package com.github.andreyasadchy.xtra.ui.login

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.model.id.TokenResponse
import com.github.andreyasadchy.xtra.util.C
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal data class RevokeTarget(val clientId: String, val token: String) {
    val body get() = "client_id=$clientId&token=$token"
}

/** The sign-in that is in place when a reauthorization finishes. */
internal data class CurrentSignIn(
    val helixClientId: String? = null,
    val helixToken: String? = null,
    val gqlClientId: String? = null,
    val gqlToken: String? = null,
    val gqlWebClientId: String? = null,
    val gqlWebToken: String? = null,
    val userId: String? = null,
    val login: String? = null,
    val clipClientId: String? = null,
    val clipToken: String? = null,
) {
    val isSignedIn get() = !helixToken.isNullOrBlank() || !gqlToken.isNullOrBlank() || !gqlWebToken.isNullOrBlank()
}

/**
 * Reauthorizing adds Helix permissions without touching the current sign-in until a new
 * token is validated, so cancelling leaves everything as it was. Nothing is written or
 * revoked before [commit]; its single edit swaps the tokens atomically.
 *
 * With the same account nothing restarts, and what is already on screen keeps sending the
 * old token because nothing tells it the token changed. So that token stays valid until no
 * screen from before can be using it: the next app start, or logging out.
 */
internal object Reauthorization {
    data class Plan(
        /** The app restarts only when the signed-in account differs from the one on screen. */
        val accountChanged: Boolean,
        val revoke: List<RevokeTarget>,
    ) {
        /** With an account change everything restarts, so nothing can still be using these. */
        val revokeNow get() = if (accountChanged) revoke else emptyList()
    }

    fun plan(current: CurrentSignIn, newHelixToken: String, newUserId: String?, newLogin: String?): Plan {
        val clip = target(current.clipClientId, current.clipToken)
        if (!current.isSignedIn) return Plan(accountChanged = true, revoke = listOfNotNull(clip))
        if (isSameAccount(current, newUserId, newLogin)) {
            return Plan(
                accountChanged = false,
                revoke = listOfNotNull(target(current.helixClientId, current.helixToken)).filter { it.token != newHelixToken },
            )
        }
        return Plan(
            accountChanged = true,
            revoke = listOfNotNull(
                target(current.helixClientId, current.helixToken),
                target(current.gqlClientId, current.gqlToken).takeIf { current.gqlToken != current.gqlWebToken },
                target(current.gqlWebClientId, current.gqlWebToken),
                clip,
            ).filter { it.token != newHelixToken },
        )
    }

    fun commit(tokenPrefs: SharedPreferences, plan: Plan, newHelixToken: String, newUserId: String?, newLogin: String?) {
        val pending = pendingRevocations(tokenPrefs) + plan.revoke.filter { !plan.accountChanged }.map { it.body }
        tokenPrefs.edit {
            putString(C.TOKEN, newHelixToken)
            putString(C.USER_ID, newUserId)
            putString(C.USERNAME, newLogin)
            if (plan.accountChanged) {
                putString(C.GQL_HEADERS, null)
                putLong(C.INTEGRITY_EXPIRATION, 0)
                putString(C.GQL_TOKEN2, null)
                putString(C.GQL_TOKEN_WEB, null)
                remove(C.CLIP_CLIENT_ID)
                remove(C.CLIP_TOKEN)
                remove(C.CLIP_REFRESH_TOKEN)
                remove(C.CLIP_TOKEN_EXPIRES_AT)
            }
            // A token issued again is back in use, so it must not be revoked later.
            putStringSet(C.PENDING_TOKEN_REVOCATIONS, pending.filterNot { it.endsWith("&token=$newHelixToken") }.toSet())
        }
    }

    /** Replaced tokens no screen can still be using. Each is handed out once. */
    fun takePendingRevocations(tokenPrefs: SharedPreferences): Set<String> {
        val pending = pendingRevocations(tokenPrefs)
        if (pending.isNotEmpty()) tokenPrefs.edit { remove(C.PENDING_TOKEN_REVOCATIONS) }
        return pending
    }

    private fun pendingRevocations(tokenPrefs: SharedPreferences): Set<String> =
        tokenPrefs.getStringSet(C.PENDING_TOKEN_REVOCATIONS, null).orEmpty().toSet()

    private fun isSameAccount(current: CurrentSignIn, newUserId: String?, newLogin: String?): Boolean =
        if (!current.userId.isNullOrBlank() && !newUserId.isNullOrBlank()) current.userId == newUserId
        else !current.login.isNullOrBlank() && current.login.equals(newLogin, true)

    private fun target(clientId: String?, token: String?): RevokeTarget? =
        if (clientId.isNullOrBlank() || token.isNullOrBlank()) null else RevokeTarget(clientId, token)
}

/** Logging out or approving again while a refresh ran made the grant it renewed obsolete. */
internal class ClipGrantChanged : IOException("The clip grant changed during a refresh")

internal data class ClipCredentials(
    val clientId: String,
    val token: String,
    val refreshToken: String?,
    val expiresAt: Long,
)

/**
 * A separate device-code grant for creating clips on TV. Twitch device tokens last about
 * four hours, and the app treats a rejected main token as an expired sign-in, so these are
 * never stored as the main Helix token: an expired clip grant must not sign anyone out.
 */
internal object ClipAuthorization {
    const val SCOPES = "clips:edit"

    /** Refresh a little early so a token can't expire between the check and the request. */
    private const val REFRESH_MARGIN_MS = 60_000L
    private const val DEFAULT_LIFETIME_MS = 4L * 60L * 60L * 1000L

    fun load(prefs: SharedPreferences): ClipCredentials? {
        val clientId = prefs.getString(C.CLIP_CLIENT_ID, null)
        val token = prefs.getString(C.CLIP_TOKEN, null)
        if (clientId.isNullOrBlank() || token.isNullOrBlank()) return null
        return ClipCredentials(clientId, token, prefs.getString(C.CLIP_REFRESH_TOKEN, null), prefs.getLong(C.CLIP_TOKEN_EXPIRES_AT, 0L))
    }

    fun save(prefs: SharedPreferences, credentials: ClipCredentials) {
        prefs.edit {
            putString(C.CLIP_CLIENT_ID, credentials.clientId)
            putString(C.CLIP_TOKEN, credentials.token)
            putString(C.CLIP_REFRESH_TOKEN, credentials.refreshToken)
            putLong(C.CLIP_TOKEN_EXPIRES_AT, credentials.expiresAt)
        }
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit {
            remove(C.CLIP_CLIENT_ID)
            remove(C.CLIP_TOKEN)
            remove(C.CLIP_REFRESH_TOKEN)
            remove(C.CLIP_TOKEN_EXPIRES_AT)
        }
    }

    /** A rejection says nothing about a grant approved since that request was sent. */
    fun clearIfCurrent(prefs: SharedPreferences, credentials: ClipCredentials) {
        if (load(prefs) == credentials) clear(prefs)
    }

    /** Null when Twitch's response carries no access token. */
    fun fromGrant(clientId: String, response: TokenResponse, now: Long, previousRefreshToken: String? = null): ClipCredentials? {
        val token = response.token?.takeUnless { it.isBlank() } ?: return null
        return ClipCredentials(
            clientId = clientId,
            token = token,
            refreshToken = response.refreshToken?.takeUnless { it.isBlank() } ?: previousRefreshToken,
            expiresAt = now + (response.expiresInSeconds?.times(1000L) ?: DEFAULT_LIFETIME_MS),
        )
    }

    /**
     * A usable token, refreshed if needed, or null when the viewer must approve again. A
     * network failure while refreshing an expired token propagates, because the grant may
     * still be good: the caller reports a failed attempt instead of asking to sign in.
     *
     * A refresh can outlive the grant it renews, so its result is used only if that grant is
     * still the stored one. Otherwise this throws [ClipGrantChanged], and [discard] receives
     * the renewed token so it can be revoked. The checks and every change to the grant run on
     * the main thread, so nothing can slip in between a check and what follows it.
     */
    suspend fun current(
        prefs: SharedPreferences,
        now: () -> Long,
        discard: (ClipCredentials) -> Unit = {},
        refresh: suspend (ClipCredentials) -> TokenResponse,
    ): ClipCredentials? {
        val credentials = load(prefs) ?: return null
        val startedAt = now()
        if (startedAt < credentials.expiresAt - REFRESH_MARGIN_MS) return credentials
        if (credentials.refreshToken.isNullOrBlank()) {
            if (startedAt < credentials.expiresAt) return credentials
            clear(prefs)
            return null
        }
        val response = try {
            refresh(credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (load(prefs) != credentials) throw ClipGrantChanged()
            // The request may have outlasted the token, and using it then would lose the grant.
            if (now() < credentials.expiresAt) return credentials
            throw failure
        }
        val renewed = fromGrant(credentials.clientId, response, now(), credentials.refreshToken)
        if (load(prefs) != credentials) {
            renewed?.let(discard)
            throw ClipGrantChanged()
        }
        if (renewed == null) {
            clear(prefs)
            return null
        }
        save(prefs, renewed)
        return renewed
    }
}
