package com.github.andreyasadchy.xtra.ui.login

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.model.id.TokenResponse
import com.github.andreyasadchy.xtra.model.id.ValidationResponse
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.ui.main.MainViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.HttpTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
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

    @Test fun `what is on screen keeps a working token until nothing can be using it`() {
        storeSignIn()
        val plan = Reauthorization.plan(signIn, "new-helix", "1", "viewer")
        assertTrue("nothing is revoked while pages are still loading with it", plan.revokeNow.isEmpty())
        Reauthorization.commit(prefs, plan, "new-helix", "1", "viewer")
        assertEquals(setOf("client_id=helix-client&token=old-helix"), Reauthorization.pendingRevocations(prefs))
    }

    @Test fun `tokens replaced in turn all wait, and one issued again is never revoked`() {
        storeSignIn()
        Reauthorization.commit(prefs, Reauthorization.plan(signIn, "second", "1", "viewer"), "second", "1", "viewer")
        val second = signIn.copy(helixToken = "second")
        Reauthorization.commit(prefs, Reauthorization.plan(second, "third", "1", "viewer"), "third", "1", "viewer")
        val third = signIn.copy(helixToken = "third")
        Reauthorization.commit(prefs, Reauthorization.plan(third, "old-helix", "1", "viewer"), "old-helix", "1", "viewer")
        assertEquals(
            setOf("client_id=helix-client&token=second", "client_id=helix-client&token=third"),
            Reauthorization.pendingRevocations(prefs),
        )
    }

    @Test fun `a different account also drops the clip grant and revokes it at once`() {
        storeSignIn()
        ClipAuthorization.save(prefs, credentials)
        val current = signIn.copy(clipClientId = "client", clipToken = "access")
        val plan = Reauthorization.plan(current, "new-helix", "2", "other")
        assertTrue(RevokeTarget("client", "access") in plan.revokeNow)
        Reauthorization.commit(prefs, plan, "new-helix", "2", "other")
        assertNull(ClipAuthorization.load(prefs))
        assertTrue(Reauthorization.pendingRevocations(prefs).isEmpty())
    }

    @Test fun `the same account keeps its clip grant`() {
        storeSignIn()
        ClipAuthorization.save(prefs, credentials)
        val current = signIn.copy(clipClientId = "client", clipToken = "access")
        val plan = Reauthorization.plan(current, "new-helix", "1", "viewer")
        assertFalse(plan.revoke.any { it.token == "access" })
        Reauthorization.commit(prefs, plan, "new-helix", "1", "viewer")
        assertEquals(credentials, ClipAuthorization.load(prefs))
    }

    private fun mainViewModel(auth: AuthRepository) = MainViewModel(
        RuntimeEnvironment.getApplication(), mock(), mock(), mock(), mock(), auth, null, null, mock(), mock(), mock(), mock(),
    )

    @Test fun `a replaced token is revoked by the next set of screens, not the current one`() {
        val auth = mock<AuthRepository>()
        val running = mainViewModel(auth)
        running.revokePendingTokens("OkHttp", prefs)
        storeSignIn()
        Reauthorization.commit(prefs, Reauthorization.plan(signIn, "new-helix", "1", "viewer"), "new-helix", "1", "viewer")
        running.revokePendingTokens("OkHttp", prefs)
        verify(auth, never()).revokeInBackground(anyOrNull(), any(), any())
        mainViewModel(auth).revokePendingTokens("OkHttp", prefs)
        verify(auth).revokeInBackground(eq("OkHttp"), eq("client_id=helix-client&token=old-helix"), any())
    }

    @Test fun `a replaced token stays listed until Twitch answers, and is tried again at the next start`() {
        val auth = mock<AuthRepository>()
        val body = "client_id=helix-client&token=old-helix"
        storeSignIn()
        Reauthorization.commit(prefs, Reauthorization.plan(signIn, "new-helix", "1", "viewer"), "new-helix", "1", "viewer")
        mainViewModel(auth).revokePendingTokens("OkHttp", prefs)
        val answered = argumentCaptor<() -> Unit>()
        verify(auth).revokeInBackground(eq("OkHttp"), eq(body), answered.capture())
        assertEquals("a lost connection must not lose it", setOf(body), Reauthorization.pendingRevocations(prefs))
        mainViewModel(auth).revokePendingTokens("OkHttp", prefs)
        verify(auth, times(2)).revokeInBackground(eq("OkHttp"), eq(body), any())
        answered.firstValue.invoke()
        assertTrue(Reauthorization.pendingRevocations(prefs).isEmpty())
        mainViewModel(auth).revokePendingTokens("OkHttp", prefs)
        verify(auth, times(2)).revokeInBackground(eq("OkHttp"), eq(body), any())
    }

    @Test fun `a listed token signed in with again leaves the list unrevoked`() {
        prefs.edit()
            .putStringSet(C.PENDING_TOKEN_REVOCATIONS, setOf("client_id=helix-client&token=in-use"))
            .putString(C.TOKEN, "in-use")
            .apply()
        val revoked = mutableListOf<String>()
        Reauthorization.revokePending(prefs) { body, _ -> revoked += body }
        assertTrue(revoked.isEmpty())
        assertTrue(Reauthorization.pendingRevocations(prefs).isEmpty())
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

    @Test fun `logging out during a refresh keeps the grant gone and abandons the attempt`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        val held = CompletableDeferred<TokenResponse>()
        val discarded = ArrayList<ClipCredentials>()
        val attempt = async {
            runCatching { ClipAuthorization.current(prefs, { 950_000L }, discard = { discarded.add(it) }) { held.await() } }
        }
        runCurrent()
        ClipAuthorization.clear(prefs)
        held.complete(TokenResponse(token = "access2", refreshToken = "refresh2", expiresInSeconds = 14_000L))
        assertTrue(attempt.await().exceptionOrNull() is ClipGrantChanged)
        assertNull(ClipAuthorization.load(prefs))
        assertEquals("revocable", listOf("access2"), discarded.map { it.token })
    }

    @Test fun `a refresh finishing after a new approval leaves the new grant in place`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        val newer = ClipCredentials("client", "approved", "refresh-approved", 99_000_000L)
        val rejected = async {
            runCatching {
                ClipAuthorization.current(prefs, { 950_000L }) {
                    ClipAuthorization.save(prefs, newer)
                    TokenResponse(message = "Invalid refresh token")
                }
            }
        }
        assertTrue(rejected.await().exceptionOrNull() is ClipGrantChanged)
        assertEquals("a late refusal concerns the old grant only", newer, ClipAuthorization.load(prefs))
    }

    @Test fun `a refresh failing after logout doesn't bring back the old token`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        val failure = runCatching {
            ClipAuthorization.current(prefs, { 950_000L }) {
                ClipAuthorization.clear(prefs)
                throw IOException("offline")
            }
        }.exceptionOrNull()
        assertTrue(failure is ClipGrantChanged)
    }

    @Test fun `a rejected request only clears the grant it used`() {
        val newer = ClipCredentials("client", "approved", "refresh-approved", 99_000_000L)
        ClipAuthorization.save(prefs, newer)
        ClipAuthorization.clearIfCurrent(prefs, credentials)
        assertEquals(newer, ClipAuthorization.load(prefs))
        ClipAuthorization.clearIfCurrent(prefs, newer)
        assertNull(ClipAuthorization.load(prefs))
    }

    @Test fun `a token that expires while its refresh stalls is not used, and the grant stays`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        var now = 990_000L
        val failure = runCatching {
            ClipAuthorization.current(prefs, { now }) {
                now = 1_010_000L
                throw IOException("stalled")
            }
        }.exceptionOrNull()
        assertEquals("stalled", failure?.message)
        assertEquals("the refresh token can still work", credentials, ClipAuthorization.load(prefs))
    }

    @Test fun `a valid clip token is used without refreshing`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        val used = ClipAuthorization.current(prefs, { 100_000L }) { error("should not refresh") }
        assertEquals(credentials, used)
    }

    @Test fun `an expiring clip token refreshes and stores the rotated tokens`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        val renewed = ClipAuthorization.current(prefs, { 950_000L }) { old ->
            assertEquals("refresh", old.refreshToken)
            TokenResponse(token = "access2", refreshToken = "refresh2", expiresInSeconds = 14_000L)
        }
        val expected = ClipCredentials("client", "access2", "refresh2", 950_000L + 14_000_000L)
        assertEquals(expected, renewed)
        assertEquals(expected, ClipAuthorization.load(prefs))
    }

    @Test fun `a refresh Twitch rejects clears the grant and asks for approval`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        assertNull(ClipAuthorization.current(prefs, { 2_000_000L }) { TokenResponse(message = "Invalid refresh token") })
        assertNull(ClipAuthorization.load(prefs))
    }

    @Test fun `a network failure keeps the grant and is reported, even with time left on the token`() = runTest {
        ClipAuthorization.save(prefs, credentials)
        for (time in listOf(950_000L, 2_000_000L)) {
            val failure = runCatching { ClipAuthorization.current(prefs, { time }) { throw IOException("offline") } }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertEquals("kept for the next attempt", credentials, ClipAuthorization.load(prefs))
        }
    }

    @Test fun `cancelling a refresh is not treated as a network failure`() {
        ClipAuthorization.save(prefs, credentials)
        val failure = runCatching {
            runBlocking { ClipAuthorization.current(prefs, { 950_000L }) { throw CancellationException("left") } }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test fun `an expired grant without a refresh token asks for approval`() = runTest {
        ClipAuthorization.save(prefs, credentials.copy(refreshToken = null))
        assertEquals("still usable", credentials.copy(refreshToken = null), ClipAuthorization.current(prefs, { 950_000L }) { error("no refresh") })
        assertNull(ClipAuthorization.current(prefs, { 2_000_000L }) { error("no refresh") })
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

    @Test fun `only a clear answer rejects a clip approval, not a lost connection or an error page`() = runTest {
        val repository = mock<AuthRepository>()
        val activity = LoginActivity().apply { authRepository = repository }
        whenever(repository.getToken(anyOrNull(), any())).thenReturn(TokenResponse("device-access", refreshToken = "device-refresh", expiresInSeconds = 14_000L))
        whenever(repository.validate(anyOrNull(), any())).thenAnswer { throw HttpTimeoutException(20_000L) }
        assertEquals(
            DeviceCodeLoginController.Approval.Token("device-access"),
            activity.checkClipDeviceApproval("OkHttp", "client", "private-code"),
        )
        assertEquals("device-refresh", ReflectionHelpers.getField<ClipCredentials?>(activity, "clipCredentials")?.refreshToken)

        val unreadable = LoginActivity().apply { authRepository = repository }
        reset(repository)
        whenever(repository.getToken(anyOrNull(), any())).thenReturn(TokenResponse("device-access", refreshToken = "device-refresh", expiresInSeconds = 14_000L))
        whenever(repository.validate(anyOrNull(), any())).thenThrow(SerializationException("a 503 page"))
        assertEquals(
            DeviceCodeLoginController.Approval.Token("device-access"),
            unreadable.checkClipDeviceApproval("OkHttp", "client", "private-code"),
        )

        val refused = LoginActivity().apply { authRepository = repository }
        reset(repository)
        whenever(repository.getToken(anyOrNull(), any())).thenReturn(TokenResponse("device-access", refreshToken = "device-refresh", expiresInSeconds = 14_000L))
        whenever(repository.validate(anyOrNull(), any())).thenThrow(IllegalStateException("401"))
        val failure = runCatching { refused.checkClipDeviceApproval("OkHttp", "client", "private-code") }.exceptionOrNull()
        assertEquals(DeviceCodeLoginController.Failure.VALIDATION_FAILED, (failure as DeviceCodeLoginController.Rejected).reason)
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
