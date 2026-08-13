package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class BookmarkVideoRefreshGate {
    private var completed = false
    private val mutex = Mutex()

    suspend fun <T> load(
        videoIds: List<String>,
        loader: suspend (List<String>) -> List<T>,
    ): List<T> = load(videoIds, loader) {}

    suspend fun <T> load(
        videoIds: List<String>,
        loader: suspend (List<String>) -> List<T>,
        onLoaded: suspend (List<T>) -> Unit,
    ): List<T> = mutex.withLock {
        if (completed) return@withLock emptyList()

        val loaded = mutableListOf<T>()
        var allChunksLoaded = true
        for (ids in videoIds.chunked(100)) {
            try {
                loaded += loader(ids)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                allChunksLoaded = false
                break
            }
        }
        try {
            onLoaded(loaded)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return@withLock loaded
        }
        completed = allChunksLoaded
        loaded
    }
}
