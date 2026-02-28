package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PlayerGestureHelperTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var helper: PlayerGestureHelper

    @Before
    fun setup() {
        audioManager = mock()
        context = mock {
            on { getSystemService(Context.AUDIO_SERVICE) } doReturn audioManager
        }
        // Helper instantiates AudioManager lazily, so this is safe
        helper = PlayerGestureHelper(context)
    }

    @Test
    fun `formatDuration formats seconds correctly`() {
        assertEquals("0:45", helper.formatDuration(45_000))
    }

    @Test
    fun `formatDuration formats minutes and seconds correctly`() {
        assertEquals("5:30", helper.formatDuration(330_000))
    }

    @Test
    fun `formatDuration formats hours correctly`() {
        assertEquals("1:23:45", helper.formatDuration(5_025_000))
    }

    @Test
    fun `formatDuration handles zero`() {
        assertEquals("0:00", helper.formatDuration(0))
    }

    @Test
    fun `calculateNewBrightness clamps to valid range`() {
        assertEquals(0f, helper.calculateNewBrightness(0.1f, -0.5f), 0.001f)
        assertEquals(1f, helper.calculateNewBrightness(0.9f, 0.5f), 0.001f)
        assertEquals(0.7f, helper.calculateNewBrightness(0.5f, 0.2f), 0.001f)
    }

    @Test
    fun `calculateSeekPosition calculates correctly`() {
        val duration = 3600_000L // 1 hour
        val currentPosition = 1800_000L // 30 minutes
        val screenWidth = 1000
        
        // Swipe 10% of screen width to the right
        val newPosition = helper.calculateSeekPosition(currentPosition, duration, 100f, screenWidth)
        assertEquals(2160_000L, newPosition) // 36 minutes (30 + 10% of 60)
    }

    @Test
    fun `calculateSeekPosition with sensitivity`() {
        val duration = 3600_000L
        val currentPosition = 1800_000L
        val screenWidth = 1000
        val sensitivity = 2.0f // Double sensitivity
        
        // Swipe 10% * 2 = 20%
        val newPosition = helper.calculateSeekPosition(currentPosition, duration, 100f, screenWidth, sensitivity)
        assertEquals(2520_000L, newPosition) // 42 minutes (30 + 20% of 60)
    }

    @Test
    fun `calculateSeekPosition clamps to valid range`() {
        val duration = 3600_000L
        
        val pastEnd = helper.calculateSeekPosition(3500_000L, duration, 500f, 1000)
        assertEquals(duration, pastEnd)
        
        val beforeStart = helper.calculateSeekPosition(100_000L, duration, -500f, 1000)
        assertEquals(0L, beforeStart)
    }

    @Test
    fun `isHorizontalSwipe detects horizontal swipes`() {
        assertTrue(helper.isHorizontalSwipe(100f, 20f, 50f))
        assertFalse(helper.isHorizontalSwipe(30f, 20f, 50f)) // Below threshold
        assertFalse(helper.isHorizontalSwipe(100f, 100f, 50f)) // Too vertical
    }

    @Test
    fun `isVerticalSwipe detects vertical swipes`() {
        assertTrue(helper.isVerticalSwipe(20f, 100f, 50f))
        assertFalse(helper.isVerticalSwipe(20f, 30f, 50f)) // Below threshold
        assertFalse(helper.isVerticalSwipe(100f, 100f, 50f)) // Too horizontal
    }

    @Test
    fun `getVolumeIconLevel returns correct levels`() {
        assertEquals(0, helper.getVolumeIconLevel(0))
        assertEquals(1, helper.getVolumeIconLevel(20))
        assertEquals(2, helper.getVolumeIconLevel(50))
        assertEquals(3, helper.getVolumeIconLevel(80))
        assertEquals(3, helper.getVolumeIconLevel(100))
    }

    @Test
    fun `getBrightnessIconLevel returns correct levels`() {
        assertEquals(0, helper.getBrightnessIconLevel(10))
        assertEquals(1, helper.getBrightnessIconLevel(50))
        assertEquals(2, helper.getBrightnessIconLevel(80))
        assertEquals(2, helper.getBrightnessIconLevel(100))
    }
    
    @Test
    fun `getCurrentVolume returns correct fraction`() {
        whenever(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(100)
        whenever(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(50)
        
        assertEquals(0.5f, helper.getCurrentVolume(), 0.001f)
    }

    @Test
    fun `isTopZone respects split ratio`() {
        // 50% split
        assertTrue(helper.isTopZone(400f, 1000f, 0.5f))
        assertFalse(helper.isTopZone(600f, 1000f, 0.5f))
        
        // 40% split (top is smaller)
        assertTrue(helper.isTopZone(300f, 1000f, 0.4f))
        assertFalse(helper.isTopZone(500f, 1000f, 0.4f))
        
        // 60% split (top is larger)
        assertTrue(helper.isTopZone(500f, 1000f, 0.6f))
        assertFalse(helper.isTopZone(700f, 1000f, 0.6f))
    }
}
