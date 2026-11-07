package com.github.andreyasadchy.xtra.util

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.random.Random

class WebSocketMaskTest {

    @Test
    fun `masks like the per-byte implementation and unmasks with the same key`() {
        val random = Random(7)
        for (size in listOf(0, 1, 3, 4, 5, 125, 126, 1024, 70_000)) {
            val data = random.nextBytes(size)
            val key = random.nextBytes(4)
            val reference = data.mapIndexed { index, byte -> (byte.toInt() xor key[index % 4].toInt()).toByte() }.toByteArray()
            val masked = WebSocket.mask(data, key)
            assertArrayEquals(reference, masked)
            assertArrayEquals(data, WebSocket.mask(masked, key))
        }
    }
}
