 package com.github.andreyasadchy.xtra.model.stats
 
 /**
  * Represents a loyalty score for a specific streamer.
  * Calculated based on watch time, frequency, and consistency.
  */
 data class StreamerLoyalty(
     val channelId: String?,
     val channelName: String?,
     val channelLogin: String?,
     val totalWatchSeconds: Long,
     val sessionCount: Int,
     val distinctDaysWatched: Int,
     val loyaltyScore: Float // 0-100 score based on engagement
 ) {
     companion object {
         /**
          * Calculates loyalty score based on:
          * - Total watch time (40% weight)
          * - Session frequency (30% weight)  
          * - Consistency/distinct days (30% weight)
          */
         fun calculateScore(
             totalWatchSeconds: Long,
             sessionCount: Int,
             distinctDaysWatched: Int,
             maxWatchSeconds: Long,
             maxSessionCount: Int,
             maxDistinctDays: Int
         ): Float {
             if (maxWatchSeconds == 0L && maxSessionCount == 0 && maxDistinctDays == 0) return 0f
             
             val timeScore = if (maxWatchSeconds > 0) {
                 (totalWatchSeconds.toFloat() / maxWatchSeconds) * 40f
             } else 0f
             
             val frequencyScore = if (maxSessionCount > 0) {
                 (sessionCount.toFloat() / maxSessionCount) * 30f
             } else 0f
             
             val consistencyScore = if (maxDistinctDays > 0) {
                 (distinctDaysWatched.toFloat() / maxDistinctDays) * 30f
             } else 0f
             
             return (timeScore + frequencyScore + consistencyScore).coerceIn(0f, 100f)
         }
     }
 }
