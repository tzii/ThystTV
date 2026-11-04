package com.github.andreyasadchy.xtra.ui.login

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.model.id.TokenResponse
import com.github.andreyasadchy.xtra.model.id.ValidationResponse
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ReauthorizationTest {
    private lateinit var prefs: SharedPreferences
    private val signIn = CurrentSignIn(
        helixClientId = "helix-client", helixToken = "old-helix",
        gqlClientId = "gql-client", gqlToken = "old-gql",
        gqlWebClientId = "web-client", gqlWebToken = "old-web",
        userId = "1", login = "viewer",
    )

    @Before fun setup() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("test", Context.MODE_PRIVATE)
        prefs.edit { clear() }
    }

    private fun storeSignIn() = prefs.edit {
        putString(C.TOKEN, "old-helix")
        putString(C.GQL_TOKEN2, "old-gql")
        putString(C.GQL_TOKEN_WEB, "old-web")
        putString(C.USER_ID, "1")
        putString(C.USERNAME, "viewer")
    }

    @Test fun `the same account replaces only the Helix token and keeps the app running`() {
        storeSignIn()
        val plan = Reauthorization.plan(signIn, "new-helix", "1", "viewer")
        assertFalse(plan.accountChanged)
        assertEquals(listOf(RevokeTarget("helix-client", "old-helix")), plan.revoke)
        Reauthorization.commit(prefs, plan, "new-helix", "1", "viewer")
        assertEquals("new-helix", prefs.getString(C.TOKEN, null))
        assertEquals("old-gql", prefs.getString(C.GQL_TOKEN2, null))
        assertEquals("old-web", prefs.getString(C.GQL_TOKEN_WEB, null))
        assertEquals("1", prefs.getString(C.USER_ID, null))
    }

    @Test fun `a different account replaces everything and restarts`() {
        storeSignIn()
        val plan = Reauthorization.plan(signIn, "new-helix", "2", "other")
        assertTrue(plan.accountChanged)
        assertEquals(
            listOf(RevokeTarget("helix-client", "old-helix"), RevokeTarget("gql-client", "old-gql"), RevokeTarget("web-client", "old-web")),
            plan.revoke,
        )
        Reauthorization.commit(prefs, plan, "new-helix", "2", "other")
        assertEquals("new-helix", prefs.getString(C.TOKEN, null))
        assertNull(prefs.getString(C.GQL_TOKEN2, null))
        assertNull(prefs.getString(C.GQL_TOKEN_WEB, null))
        assertEquals("other", prefs.getString(C.USERNAME, null))
    }

    @Test fun `accounts compare by id, then by login in any case`() {
        assertFalse(Reauthorization.plan(signIn.copy(userId = null), "new", null, "VIEWER").accountChanged)
        assertFalse(Reauthorization.plan(signIn, "new", "1", "renamed").accountChanged)
        assertTrue(Reauthorization.plan(signIn, "new", "2", "viewer").accountChanged)
        assertTrue("nothing to compare", Reauthorization.plan(signIn.copy(userId = null, login = null), "new", "1", "viewer").accountChanged)
    }

    @Test fun `a shared gql and web token is revoked once and a reissued token never`() {
        val shared = signIn.copy(gqlToken = "old-web")
        assertEquals(
            listOf(RevokeTarget("helix-client", "old-helix"), RevokeTarget("web-client", "old-web")),
            Reauthorization.plan(shared, "new", "2", "other").revoke,
        )
        assertEquals(emptyList<RevokeTarget>(), Reauthorization.plan(signIn, "old-helix", "1", "viewer").revoke)
    }

    @Test fun `signing in from signed out restarts and revokes nothing`() {
        val plan = Reauthorization.plan(CurrentSignIn(helixClientId = "helix-client"), "new", "1", "viewer")
        assertTrue(plan.accountChanged)
        assertEquals(emptyList<RevokeTarget>(), plan.revoke)
    }

    @Test fun `a helix-only sign in with no account details still counts as signed in`() {
        val plan = Reauthorization.plan(CurrentSignIn(helixClientId = "helix-client", helixToken = "old"), "new", "1", "viewer")
        assertTrue(plan.accountChanged)
        assertEquals(listOf(RevokeTarget("helix-client", "old")), plan.revoke)
    }

    private val credentials = ClipCredentials("client", "access", "refresh", 1_000_000L)

    @Test fun `clip credentials survive storage and clearing removes them`() {
        assertNull(ClipAuthorization.load(prefs))
        ClipAuthorization.save(prefs, credentials)
        assertEquals(credentials, ClipAuthorization.load(prefs))
        ClipAuthorization.clear(prefs)
        assertNull(ClipAuthorization.load(prefs))
        assertFalse(prefs.contains(C.CLIP_REFRESH_TOKEN))
    }

    @Test fun `clip credentials never touch the sign-in`() {
        storeSignIn()
        ClipAuthorization.save(prefs, credentials)
        ClipAuthorization.clear(prefs)
        assertEquals("old-helix", prefs.getString(C.TOKEN, null))
        assertEquals("1", prefs.getString(C.USER_ID, null))
    }

    @Test fun `a valid clip token is used without refreshing`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        val used = ClipAuthorization.current(prefs, 100_000L) { error("should not refresh") }
        assertEquals(credentials, used)
    }

    @Test fun `an expiring clip token refreshes and stores the rotated tokens`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        val renewed = ClipAuthorization.current(prefs, 950_000L) { old ->
            assertEquals("refresh", old.refreshToken)
            TokenResponse(token = "access2", refreshToken = "refresh2", expiresInSeconds = 14_000L)
        }
        val expected = ClipCredentials("client", "access2", "refresh2", 950_000L + 14_000_000L)
        assertEquals(expected, renewed)
        assertEquals(expected, ClipAuthorization.load(prefs))
    }

    @Test fun `a refresh Twitch rejects clears the grant and asks for approval`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        assertNull(ClipAuthorization.current(prefs, 2_000_000L) { TokenResponse(message = "Invalid refresh token") })
        assertNull(ClipAuthorization.load(prefs))
    }

    @Test fun `a network failure keeps the grant and uses the token if it still works`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        assertEquals(credentials, ClipAuthorization.current(prefs, 950_000L) { throw IOException("offline") })
        val failure = runCatching { ClipAuthorization.current(prefs, 2_000_000L) { throw IOException("offline") } }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals("kept for the next attempt", credentials, ClipAuthorization.load(prefs))
    }

    @Test fun `cancelling a refresh is not treated as a network failure`() {
        ClipAuthorization.save(prefs, credentials)
        val failure = runCatching {
            runBlocking { ClipAuthorization.current(prefs, 950_000L) { throw CancellationException("left") } }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test fun `an expired grant without a refresh token asks for approval`() = runTest {
        ClipAuthorization.save(prefs, credentials.copy(refreshToken = null))
        assertEquals("still usable", credentials.copy(refreshToken = null), ClipAuthorization.current(prefs, 950_000L) { error("no refresh") })
        assertNull(ClipAuthorization.current(prefs, 2_000_000L) { error("no refresh") })
        assertNull(ClipAuthorization.load(prefs))
    }

    @Test fun `a refresh without a new refresh token keeps the old one`() {
        assertEquals(
            ClipCredentials("client", "access2", "refresh", 5_000L),
            ClipAuthorization.fromGrant("client", TokenResponse(token = "access2", expiresInSeconds = 4L), 1_000L, previousRefreshToken = "refresh"),
        )
        assertNull(ClipAuthorization.fromGrant("client", TokenResponse(message = "nope"), 1_000L))
    }

    @Test fun `a clip device approval must validate against the same client and keeps the refresh token`() = runTest {
        val repository = mock<AuthRepository>()
        val activity = LoginActivity().apply { authRepository = repository }
        whenever(repository.getToken(anyOrNull(), any())).thenReturn(TokenResponse("device-access", refreshToken = "device-refresh", expiresInSeconds = 14_000L))
        whenever(repository.validate(anyOrNull(), any())).thenReturn(ValidationResponse("client", "viewer", "1"))
        assertEquals(
            DeviceCodeLoginController.Approval.Token("device-access"),
            activity.checkClipDeviceApproval("OkHttp", "client", "private-code"),
        )
        val stored = ReflectionHelpers.getField<ClipCredentials?>(activity, "clipCredentials")
        assertEquals("device-refresh", stored?.refreshToken)
        verify(repository).validate(anyOrNull(), eq("Bearer device-access"))

        val other = LoginActivity().apply { authRepository = repository }
        whenever(repository.validate(anyOrNull(), any())).thenReturn(ValidationResponse("different-client"))
        val failure = runCatching { other.checkClipDeviceApproval("OkHttp", "client", "private-code") }.exceptionOrNull()
        assertEquals(
            DeviceCodeLoginController.Failure.VALIDATION_FAILED,
            (failure as DeviceCodeLoginController.Rejected).reason,
        )
        assertNull(ReflectionHelpers.getField<ClipCredentials?>(other, "clipCredentials"))
    }

    @Test fun `a pending clip device approval reports the same states as sign-in`() = runTest {
        val repository = mock<AuthRepository>()
        val activity = LoginActivity().apply { authRepository = repository }
        whenever(repository.getToken(anyOrNull(), any())).thenReturn(TokenResponse(message = "authorization_pending"))
        assertEquals(
            DeviceCodeLoginController.Approval.Pending,
            activity.checkClipDeviceApproval("OkHttp", "client", "private-code"),
        )
        verify(repository).getToken(anyOrNull(), argThat { contains("scopes=clips%3Aedit") })
    }
}
