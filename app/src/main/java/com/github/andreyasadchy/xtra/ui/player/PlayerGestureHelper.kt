 package com.github.andreyasadchy.xtra.ui.player
 
 import android.content.Context
 import android.media.AudioManager
 import android.provider.Settings
 import android.view.WindowManager
 import kotlin.math.max
 import kotlin.math.min
 
 /**
  * Helper class for handling volume and brightness gestures in the player.
  * Extracted from PlayerFragment to improve code organization and testability.
  */
 class PlayerGestureHelper(private val context: Context) {
 
     private val audioManager: AudioManager by lazy {
         context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
     }
 
     private val maxVolume: Int by lazy {
         audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
     }
 
     /**
      * Gets the current system volume as a float between 0.0 and 1.0
      */
     fun getCurrentVolume(): Float {
         return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
     }
 
     /**
      * Sets the system volume from a float between 0.0 and 1.0
      */
     fun setVolume(volume: Float) {
         val clampedVolume = volume.coerceIn(0f, 1f)
         val newVolume = (clampedVolume * maxVolume).toInt()
         audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
     }
 
     /**
      * Adjusts volume by a delta value (positive or negative)
      * @param delta Volume change as a fraction of max volume (-1.0 to 1.0)
      * @return The new volume as a float between 0.0 and 1.0
      */
     fun adjustVolume(delta: Float): Float {
         val currentVolume = getCurrentVolume()
         val newVolume = (currentVolume + delta).coerceIn(0f, 1f)
         setVolume(newVolume)
         return newVolume
     }
 
     /**
      * Gets the current screen brightness as a float between 0.0 and 1.0
      * @param windowAttributes The current window attributes
      * @return Brightness value between 0.0 and 1.0
      */
     fun getCurrentBrightness(windowAttributes: WindowManager.LayoutParams): Float {
         return if (windowAttributes.screenBrightness < 0) {
             try {
                 Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
             } catch (e: Settings.SettingNotFoundException) {
                 0.5f
             }
         } else {
             windowAttributes.screenBrightness
         }
     }
 
     /**
      * Calculates the new brightness value after applying a delta
      * @param currentBrightness Current brightness (0.0 to 1.0)
      * @param delta Brightness change (-1.0 to 1.0)
      * @return New brightness value clamped between 0.0 and 1.0
      */
     fun calculateNewBrightness(currentBrightness: Float, delta: Float): Float {
         return (currentBrightness + delta).coerceIn(0f, 1f)
     }
 
     /**
      * Calculates seek position based on gesture distance
      * @param currentPosition Current playback position in milliseconds
      * @param duration Total duration in milliseconds
      * @param gestureDelta Horizontal gesture distance in pixels
      * @param screenWidth Screen width in pixels
      * @param seekMultiplier Multiplier for seek sensitivity (default 1.0)
      * @return New seek position in milliseconds
      */
     fun calculateSeekPosition(
         currentPosition: Long,
         duration: Long,
         gestureDelta: Float,
         screenWidth: Int,
         seekMultiplier: Float = 1f
     ): Long {
         val seekPercentage = gestureDelta / screenWidth
         val seekDelta = (duration * seekPercentage * seekMultiplier).toLong()
         return (currentPosition + seekDelta).coerceIn(0, duration)
     }
 
     /**
      * Determines if a touch event is a horizontal swipe (for seeking)
      * @param deltaX Horizontal distance moved
      * @param deltaY Vertical distance moved
      * @param threshold Minimum distance to consider as a swipe
      * @return true if horizontal swipe, false if vertical or no swipe
      */
     fun isHorizontalSwipe(deltaX: Float, deltaY: Float, threshold: Float): Boolean {
         return kotlin.math.abs(deltaX) > threshold && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.5f
     }
 
     /**
      * Determines if a touch event is a vertical swipe (for volume/brightness)
      * @param deltaX Horizontal distance moved
      * @param deltaY Vertical distance moved
      * @param threshold Minimum distance to consider as a swipe
      * @return true if vertical swipe, false if horizontal or no swipe
      */
     fun isVerticalSwipe(deltaX: Float, deltaY: Float, threshold: Float): Boolean {
         return kotlin.math.abs(deltaY) > threshold && kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) * 1.5f
     }
 
     /**
      * Formats a duration in milliseconds to a human-readable string (HH:MM:SS or MM:SS)
      * @param durationMs Duration in milliseconds
      * @return Formatted string
      */
     fun formatDuration(durationMs: Long): String {
         val totalSeconds = durationMs / 1000
         val hours = totalSeconds / 3600
         val minutes = (totalSeconds % 3600) / 60
         val seconds = totalSeconds % 60
         
         return if (hours > 0) {
             String.format("%d:%02d:%02d", hours, minutes, seconds)
         } else {
             String.format("%d:%02d", minutes, seconds)
         }
     }
 
     /**
      * Converts a percentage (0-100) to a volume icon resource level
      * @param volumePercent Volume as percentage (0-100)
      * @return Icon level (0=muted, 1=low, 2=medium, 3=high)
      */
     fun getVolumeIconLevel(volumePercent: Int): Int {
         return when {
             volumePercent == 0 -> 0
             volumePercent < 33 -> 1
             volumePercent < 66 -> 2
             else -> 3
         }
     }
 
     /**
      * Converts a percentage (0-100) to a brightness icon resource level
      * @param brightnessPercent Brightness as percentage (0-100)
      * @return Icon level (0=low, 1=medium, 2=high)
      */
    fun getBrightnessIconLevel(brightnessPercent: Int): Int {
        return when {
            brightnessPercent < 33 -> 0
            brightnessPercent < 66 -> 1
            else -> 2
        }
    }

    /**
     * Determines if a touch Y coordinate is in the top zone based on the split ratio.
     * @param y Touch Y coordinate
     * @param height Screen height
     * @param splitRatio Ratio of the screen defined as the top zone (0.0 to 1.0)
     * @return true if in top zone, false otherwise
     */
    fun isTopZone(y: Float, height: Float, splitRatio: Float): Boolean {
        return y < height * splitRatio
    }
}
