package com.github.andreyasadchy.xtra.repository

import android.app.Application
import org.robolectric.RuntimeEnvironment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.Failure
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.Position
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.ProbeException
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.Recent
import com.github.andreyasadchy.xtra.repository.TwitchResumeProbeRepository.WriteResult
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class TwitchSyncManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = RuntimeEnvironment.getApplication()
    private val repository: TwitchResumeProbeRepository = mock()
    private val live: TwitchLiveWatchRepository = mock()
    private lateinit var store: TwitchSyncStore
    private lateinit var manager: TwitchSyncManager

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        context.tokenPrefs().edit().clear().putString(C.USER_ID, "1").putString(C.GQL_TOKEN2, "fixture-token").commit()
        store = TwitchSyncStore(context)
        manager = TwitchSyncManager(context, store, repository, live)
    }

    @After fun cleanup() {
        manager.configure(manager.account().id, "vod", false)
        manager.detach("player")
        Dispatchers.resetMain()
    }

    private fun pending(position: Long = 200, baseline: Long? = 100) {
        store.enable("1", "vod", true)
        store.progress("1", "2") { it.copy(baseline = baseline, baselineKnown = true).checkpoint(position) }
    }
    private fun item() = store.read("1").progress.single()

    private fun syncTest(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally {
            manager.configure(manager.account().id, "vod", false)
            manager.detach("player")
            runCurrent()
        }
    }

    @Test fun `empty local history does not claim account verification`() = syncTest {
        assertTrue(store.read("1").recent.isEmpty())
        assertEquals(R.string.twitch_sync_history_not_loaded, manager.historyStatus("1"))
        verifyNoInteractions(repository)
    }

    @Test fun `history authentication failures stay specific and preserve pending uploads`() = syncTest {
        pending()
        for ((failure, message) in listOf(
            Failure.SIGN_IN_REQUIRED to R.string.resume_probe_sign_in,
            Failure.ACCOUNT_MISMATCH to R.string.resume_probe_account_mismatch,
            Failure.CLIENT_MISMATCH to R.string.resume_probe_client_mismatch,
            Failure.TOKEN_REJECTED to R.string.resume_probe_token_rejected,
            Failure.AUTHENTICATION to R.string.resume_probe_authentication,
        )) {
            doAnswer { throw ProbeException(failure) }.whenever(repository).recent(any(), any())
            assertTrue(manager.refresh().isEmpty())
            assertEquals(message, manager.historyStatus("1"))
            assertEquals(message, manager.status("1"))
            assertTrue(item().pending)
        }
        verify(repository, never()).writeAndReadBack(any(), any(), any(), any())
    }

    @Test fun `verified empty history is shown only after successful response`() = syncTest {
        val gate = CompletableDeferred<List<Recent>>()
        whenever(repository.recent(any(), any())).doSuspendableAnswer { gate.await() }
        val refresh = async { manager.refresh() }
        runCurrent()
        assertEquals(R.string.twitch_sync_history_loading, manager.historyStatus("1"))
        gate.complete(emptyList())
        assertTrue(refresh.await().isEmpty())
        assertEquals(R.string.resume_probe_empty, manager.historyStatus("1"))
    }

    @Test fun `failed history refresh preserves cached entries without claiming refresh success`() = syncTest {
        whenever(repository.recent(any(), any())).thenReturn(listOf(Recent("2", 100, null, "Saved VoD")))
        val saved = manager.refresh()
        assertEquals(R.string.twitch_sync_refreshed, manager.historyStatus("1"))
        doAnswer { throw ProbeException(Failure.NETWORK) }.whenever(repository).recent(any(), any())
        assertEquals(saved, manager.refresh())
        assertEquals(saved, store.read("1").recent)
        assertEquals(R.string.resume_probe_network, manager.historyStatus("1"))
    }

    @Test fun `cancelled history request stops showing loading without claiming verification`() = syncTest {
        val gate = CompletableDeferred<List<Recent>>()
        whenever(repository.recent(any(), any())).doSuspendableAnswer { gate.await() }
        val refresh = async { manager.refresh() }
        runCurrent()
        refresh.cancel()
        refresh.join()
        assertEquals(R.string.twitch_sync_history_not_loaded, manager.historyStatus("1"))
        assertTrue(store.read("1").recent.isEmpty())
    }

    @Test fun `older history request cannot overwrite a newer refresh result`() = syncTest {
        val gate = CompletableDeferred<List<Recent>>()
        whenever(repository.recent(any(), any())).doSuspendableAnswer { gate.await() }
        val old = async { manager.refresh() }
        runCurrent()
        doReturn(emptyList<Recent>()).whenever(repository).recent(any(), any())
        manager.refresh()
        gate.complete(listOf(Recent("2", 100, null, "Old response")))
        assertTrue(old.await().isEmpty())
        assertEquals(R.string.resume_probe_empty, manager.historyStatus("1"))
        assertTrue(store.read("1").recent.isEmpty())
    }

    @Test fun `credential change invalidates history status and ignores in-flight result`() = syncTest {
        whenever(repository.recent(any(), any())).thenReturn(emptyList())
        manager.refresh()
        val gate = CompletableDeferred<List<Recent>>()
        whenever(repository.recent(any(), any())).doSuspendableAnswer { gate.await() }
        val old = async { manager.refresh() }
        runCurrent()
        context.tokenPrefs().edit().putString(C.GQL_TOKEN2, "replacement-fixture").commit()
        runCurrent()
        assertEquals(R.string.twitch_sync_history_not_loaded, manager.historyStatus("1"))
        gate.complete(listOf(Recent("2", 100, null, "Old credentials")))
        assertTrue(old.await().isEmpty())
        assertTrue(store.read("1").recent.isEmpty())
    }

    @Test fun `upload authentication error does not replace the actual history outcome`() = syncTest {
        pending()
        whenever(repository.recent(any(), any())).thenReturn(emptyList())
        whenever(repository.read(any(), eq("2"), any())).thenAnswer { throw ProbeException(Failure.CLIENT_MISMATCH) }
        manager.refresh()
        advanceTimeBy(1100); runCurrent()
        assertEquals(R.string.resume_probe_client_mismatch, manager.status("1"))
        assertEquals(R.string.resume_probe_empty, manager.historyStatus("1"))
        assertTrue(item().pending)
        verify(repository, never()).writeAndReadBack(any(), any(), any(), any())
    }

    @Test fun `closing immediately after rewind checkpoints the final position`() = syncTest {
        pending()
        manager.attach("player")
        val sample = TwitchSyncManager.Sample(videoId = "2", positionMs = 1000, durationMs = 100_000, playing = true)
        manager.sample("player", sample)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        manager.sample("player", sample.copy(positionMs = 7000))
        manager.checkpointNow("player", "2", 0, 100_000)
        assertEquals(0L, item().seconds)
        assertTrue(item().pending)
    }

    @Test fun `off is inert and does not upload legacy resume`() = syncTest {
        assertEquals(4000L, manager.resume("2", 4000))
        manager.attach("player")
        advanceTimeBy(2000); runCurrent()
        verifyNoInteractions(repository, live)
        assertTrue(store.read("1").progress.isEmpty())
    }

    @Test fun `remote zero overrides local progress before playback`() = syncTest {
        store.enable("1", "vod", true)
        whenever(repository.read(any(), eq("2"), any())).thenReturn(Position("2", 0))
        assertEquals(0L, manager.resume("2", 50_000))
        assertFalse(item().pending)
        assertEquals(0L, item().baseline)
    }

    @Test fun `offline startup uses account-owned pending position`() = syncTest {
        pending(position = 0)
        whenever(repository.read(any(), eq("2"), any())).thenAnswer { throw ProbeException(Failure.NETWORK) }
        assertEquals(0L, manager.resume("2", 50_000))
        assertTrue(item().pending)
    }

    @Test fun `rewind is written and acknowledged only after matching readback`() = syncTest {
        pending(position = 0)
        whenever(repository.read(any(), eq("2"), any())).thenReturn(Position("2", 100))
        whenever(repository.writeAndReadBack(any(), eq("2"), eq(0L), any())).thenReturn(WriteResult(0, 0))
        manager.attach("player")
        advanceTimeBy(1100); runCurrent()
        verify(repository).writeAndReadBack(any(), eq("2"), eq(0L), any())
        assertFalse(item().pending)
        assertEquals(0L, item().baseline)
    }

    @Test fun `changed Twitch position stops writes until user resolves conflict`() = syncTest {
        pending()
        whenever(repository.read(any(), eq("2"), any())).thenReturn(Position("2", 300))
        manager.attach("player")
        advanceTimeBy(1100); runCurrent()
        assertTrue(item().conflict)
        verify(repository, never()).writeAndReadBack(any(), any(), any(), any())
        manager.resolve("2", false)
        assertFalse(item().pending)
        assertEquals(300L, item().seconds)
    }

    @Test fun `lost write response is recognized by preflight without duplicate mutation`() = syncTest {
        pending()
        whenever(repository.read(any(), eq("2"), any())).thenReturn(Position("2", 200))
        manager.attach("player")
        advanceTimeBy(1100); runCurrent()
        assertFalse(item().pending)
        verify(repository, never()).writeAndReadBack(any(), any(), any(), any())
    }

    @Test fun `new checkpoint arriving during write stays pending`() = syncTest {
        pending()
        whenever(repository.read(any(), eq("2"), any())).thenReturn(Position("2", 100))
        whenever(repository.writeAndReadBack(any(), any(), any(), any())).thenAnswer {
            store.progress("1", "2") { it.checkpoint(0) }
            WriteResult(200, 200)
        }
        manager.attach("player")
        advanceTimeBy(1100); runCurrent()
        assertTrue(item().pending)
        assertEquals(0L, item().seconds)
        assertEquals(200L, item().baseline)
    }

    @Test fun `deleted VoD does not block another queued upload`() = syncTest {
        pending()
        store.progress("1", "3") { it.copy(baseline = 10, baselineKnown = true).checkpoint(20) }
        whenever(repository.read(any(), eq("2"), any())).thenAnswer { throw ProbeException(Failure.UNAVAILABLE_VIDEO) }
        whenever(repository.read(any(), eq("3"), any())).thenReturn(Position("3", 20))
        manager.attach("player")
        advanceTimeBy(1100); runCurrent()
        assertTrue(store.read("1").progress.find { it.videoId == "2" }!!.unavailable)
        advanceTimeBy(30_000); runCurrent()
        assertFalse(store.read("1").progress.find { it.videoId == "3" }!!.pending)
        manager.unavailableVideo("2", false)
        assertFalse(store.read("1").progress.find { it.videoId == "2" }!!.pending)
    }

    @Test fun `choosing absent Twitch history restarts at zero instead of old local progress`() = syncTest {
        pending()
        store.progress("1", "2") { it.copy(conflict = true, remote = null) }
        manager.resolve("2", false)
        whenever(repository.read(any(), eq("2"), any())).thenReturn(Position("2", null))
        assertEquals(0L, manager.resume("2", 50_000))
    }

    @Test fun `Twitch choice survives player recreation and offline reopen`() = syncTest {
        pending()
        store.progress("1", "2") { it.copy(conflict = true, remote = 300) }
        manager.resolve("2", false)
        manager.attach("player")
        val sample = TwitchSyncManager.Sample(videoId = "2", positionMs = 1000, durationMs = 1_000_000, playing = true)
        manager.sample("player", sample)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        manager.sample("player", sample.copy(positionMs = 7000))
        assertFalse(item().pending)
        whenever(repository.read(any(), eq("2"), any())).thenAnswer { throw ProbeException(Failure.NETWORK) }
        assertEquals(300_000L, manager.resume("2", 50_000))
        assertFalse(item().remoteChosen)
    }

    @Test fun `turning sync off cancels a queued mutation`() = syncTest {
        pending()
        manager.attach("player")
        manager.configure("1", "vod", false)
        advanceTimeBy(2000); runCurrent()
        assertTrue(item().pending)
        verifyNoInteractions(repository)
    }

    @Test fun `transient network failure retains outbox and retries`() = syncTest {
        pending()
        whenever(repository.read(any(), eq("2"), any())).thenAnswer { throw ProbeException(Failure.NETWORK) }.thenReturn(Position("2", 200))
        manager.attach("player")
        advanceTimeBy(1100); runCurrent()
        assertTrue(item().pending)
        advanceTimeBy(60_000); runCurrent()
        assertFalse(item().pending)
    }

    @Test fun `account switch prevents pending account upload`() = syncTest {
        pending()
        manager.attach("player")
        context.tokenPrefs().edit().putString(C.USER_ID, "other").commit()
        advanceTimeBy(2000); runCurrent()
        verifyNoInteractions(repository)
        assertTrue(item().pending)
        assertTrue(store.read("other").progress.isEmpty())
    }

    @Test fun `resume timeout cancels the lookup and never seeks later`() = syncTest {
        store.enable("1", "vod", true)
        val gate = CompletableDeferred<Position>()
        whenever(repository.read(any(), eq("2"), any())).doSuspendableAnswer { gate.await() }
        assertEquals(50_000L, manager.resume("2", 50_000))
        gate.complete(Position("2", 5))
        runCurrent()
        assertTrue(store.read("1").progress.isEmpty())
    }

    @Test fun `only actual live playback sends a minute and displaced owner is ignored`() = syncTest {
        manager.attach("player")
        val sample = TwitchSyncManager.Sample(broadcastId = "3", channelId = "2", channelLogin = "channel", playing = true)
        for (second in 0L..60) {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            manager.sample("stale-player", sample.copy(positionMs = second * 1000))
            runCurrent()
        }
        verifyNoInteractions(live)
        for (second in 0L..60) {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            manager.sample("player", sample.copy(positionMs = second * 1000))
            runCurrent()
        }
        verify(live).send(eq("1"), eq("3"), eq("2"), eq("channel"), any())
    }
}
