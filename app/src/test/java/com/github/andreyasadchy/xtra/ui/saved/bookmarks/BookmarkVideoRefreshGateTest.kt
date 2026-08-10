package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkVideoRefreshGateTest {

    @Test
    fun `successful refresh loads one hundred item chunks only once`() = runTest {
        val gate = BookmarkVideoRefreshGate()
        val chunks = mutableListOf<List<String>>()
        val ids = (1..201).map(Int::toString)

        val first = gate.load(ids) { chunk ->
            chunks += chunk
            chunk.map { "video-$it" }
        }
        val second = gate.load<String>(ids) { error("completed refresh must not load again") }

        assertEquals(listOf(100, 100, 1), chunks.map { it.size })
        assertEquals(ids.map { "video-$it" }, first)
        assertTrue(second.isEmpty())
    }

    @Test
    fun `ordinary failure preserves earlier results and retries from the first chunk`() = runTest {
        val gate = BookmarkVideoRefreshGate()
        val ids = (1..101).map(Int::toString)
        var attempts = 0

        val partial = gate.load(ids) { chunk ->
            attempts += 1
            if (attempts == 2) throw IllegalStateException("offline")
            chunk.map { "video-$it" }
        }
        val retried = gate.load(ids) { chunk ->
            attempts += 1
            chunk.map { "video-$it" }
        }
        val afterSuccess = gate.load<String>(ids) { error("successful retry must complete the gate") }

        assertEquals(100, partial.size)
        assertEquals(ids.map { "video-$it" }, retried)
        assertTrue(afterSuccess.isEmpty())
        assertEquals(4, attempts)
    }

    @Test
    fun `empty input completes without loading`() = runTest {
        val gate = BookmarkVideoRefreshGate()
        var loads = 0

        assertTrue(gate.load<String>(emptyList()) { loads += 1; emptyList() }.isEmpty())
        assertTrue(gate.load(listOf("later")) { loads += 1; it }.isEmpty())
        assertEquals(0, loads)
    }

    @Test
    fun `overlapping calls serialize and load only once after success`() = runTest {
        val gate = BookmarkVideoRefreshGate()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var loads = 0

        val first = async {
            gate.load(listOf("1")) { ids ->
                loads += 1
                firstStarted.complete(Unit)
                releaseFirst.await()
                ids
            }
        }
        firstStarted.await()
        val second = async {
            gate.load(listOf("1")) { ids ->
                loads += 1
                ids
            }
        }
        releaseFirst.complete(Unit)

        assertEquals(listOf("1"), first.await())
        assertTrue(second.await().isEmpty())
        assertEquals(1, loads)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown`() = runTest {
        BookmarkVideoRefreshGate().load<String>(listOf("1")) {
            throw CancellationException("view model cleared")
        }
    }
}
