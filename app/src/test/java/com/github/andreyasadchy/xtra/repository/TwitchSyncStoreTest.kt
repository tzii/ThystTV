package com.github.andreyasadchy.xtra.repository

import android.app.Application
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class TwitchSyncStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val store = TwitchSyncStore(context)

    @Test fun `account settings and pending progress survive recreation without crossing accounts`() {
        store.enable("1", "vod", true)
        store.progress("1", "20") { it.checkpoint(0) }
        val reopened = TwitchSyncStore(context)
        assertTrue(reopened.enabled("1", "vod"))
        assertFalse(reopened.enabled("2", "vod"))
        assertTrue(reopened.read("2").progress.isEmpty())
        assertEquals(0L, reopened.read("1").progress.single().seconds)
        assertTrue(reopened.read("1").progress.single().pending)
    }

    @Test fun `retention never discards pending uploads`() {
        repeat(120) { n -> store.progress("1", "$n") { it.checkpoint(n.toLong()) } }
        repeat(120) { n -> store.progress("1", "clean$n") { it } }
        val data = store.read("1")
        assertEquals(120, data.progress.count { it.pending })
        assertEquals(100, data.progress.count { !it.pending })
    }

    @Test fun `newer lower streak replaces old count but duplicate and older notices do not`() {
        store.milestone("1", TwitchStreak("2", "channel", 10, 1000, "first"))
        store.milestone("1", TwitchStreak("2", "channel", 2, 2000, "second"))
        store.milestone("1", TwitchStreak("2", "channel", 20, 3000, "second"))
        store.milestone("1", TwitchStreak("2", "channel", 30, 500, "old"))
        assertEquals(2, store.read("1").streaks.single().count)
        assertEquals(2000L, store.read("1").streaks.single().timestamp)
        assertTrue(store.read("other").streaks.isEmpty())
    }

    @Test fun `full outbox rejects additions without discarding existing pending progress`() {
        store.update("1") { it.copy(progress = (1..500).map { id -> TwitchProgress("$id").checkpoint(0) }) }
        assertTrue(runCatching { store.progress("1", "501") { it.checkpoint(0) } }.exceptionOrNull() is TwitchSyncStore.OutboxFullException)
        assertEquals(500, store.read("1").progress.count { it.pending })
        store.progress("1", "1") { it.checkpoint(50) }
        assertEquals(50L, store.read("1").progress.find { it.videoId == "1" }?.seconds)
    }

    @Test fun `corrupt storage is preserved instead of silently clearing pending data`() {
        val prefs = context.getSharedPreferences("twitch_account_sync", 0)
        prefs.edit().putString("1:state", "broken-data").commit()
        assertTrue(runCatching { store.progress("1", "2") { it.checkpoint(10) } }.isFailure)
        assertEquals("broken-data", prefs.getString("1:state", null))
    }
}
