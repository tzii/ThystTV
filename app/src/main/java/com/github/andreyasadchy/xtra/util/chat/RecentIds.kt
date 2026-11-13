package com.github.andreyasadchy.xtra.util.chat

/**
 * The last [capacity] message ids a socket handled, to drop redelivered notifications.
 * Checking and recording an id is constant time; the oldest id is evicted first.
 * Not thread-safe: each socket calls it from its single read loop.
 */
internal class RecentIds(private val capacity: Int) {
    private val ids = LinkedHashSet<String>()

    /** Records [id]. Returns false when it is already among the recorded ids. */
    fun add(id: String): Boolean {
        if (!ids.add(id)) return false
        if (ids.size > capacity) {
            val oldest = ids.iterator()
            oldest.next()
            oldest.remove()
        }
        return true
    }
}
