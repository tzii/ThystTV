package com.github.andreyasadchy.xtra.ui.login

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.model.id.TokenResponse
import com.github.andreyasadchy.xtra.util.C
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
) {
    val isSignedIn get() = !helixToken.isNullOrBlank() || !gqlToken.isNullOrBlank() || !gqlWebToken.isNullOrBlank()
}

/**
 * Reauthorizing adds Helix permissions without touching the current sign-in until a new
 * token is validated, so cancelling leaves everything as it was. Nothing is written or
 * revoked before [commit]; its single edit swaps the tokens atomically.
 */
internal object Reauthorization {
    data class Plan(
        /** The app restarts only when the signed-in account differs from the one on screen. */
        val accountChanged: Boolean,
        val revoke: List<RevokeTarget>,
    )

    fun plan(current: CurrentSignIn, newHelixToken: String, newUserId: String?, newLogin: String?): Plan {
        if (!current.isSignedIn) return Plan(accountChanged = true, revoke = emptyList())
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
            ).filter { it.token != newHelixToken },
        )
    }

    fun commit(tokenPrefs: SharedPreferences, plan: Plan, newHelixToken: String, newUserId: String?, newLogin: String?) {
        tokenPrefs.edit {
            putString(C.TOKEN, newHelixToken)
            putString(C.USER_ID, newUserId)
            putString(C.USERNAME, newLogin)
            if (plan.accountChanged) {
                putString(C.GQL_HEADERS, null)
                putLong(C.INTEGRITY_EXPIRATION, 0)
                putString(C.GQL_TOKEN2, null)
                putString(C.GQL_TOKEN_WEB, null)
            }
        }
    }

    private fun isSameAccount(current: CurrentSignIn, newUserId: String?, newLogin: String?): Boolean =
        if (!current.userId.isNullOrBlank() && !newUserId.isNullOrBlank()) current.userId == newUserId
        else !current.login.isNullOrBlank() && current.login.equals(newLogin, true)

    private fun target(clientId: String?, token: String?): RevokeTarget? =
        if (clientId.isNullOrBlank() || token.isNullOrBlank()) null else RevokeTarget(clientId, token)
}

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
     */
    suspend fun current(
        prefs: SharedPreferences,
        now: Long,
        refresh: suspend (ClipCredentials) -> TokenResponse,
    ): ClipCredentials? {
        val credentials = load(prefs) ?: return null
        if (now < credentials.expiresAt - REFRESH_MARGIN_MS) return credentials
        if (credentials.refreshToken.isNullOrBlank()) {
            if (now < credentials.expiresAt) return credentials
            clear(prefs)
            return null
        }
        val response = try {
            refresh(credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (now < credentials.expiresAt) return credentials
            throw failure
        }
        val renewed = fromGrant(credentials.clientId, response, now, credentials.refreshToken)
        if (renewed == null) {
            clear(prefs)
            return null
        }
        save(prefs, renewed)
        return renewed
    }
}
