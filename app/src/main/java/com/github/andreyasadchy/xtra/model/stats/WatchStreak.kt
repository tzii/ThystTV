 package com.github.andreyasadchy.xtra.model.stats
 
 import androidx.room.Entity
 import androidx.room.PrimaryKey
 
 /**
  * Tracks the user's watch streak (consecutive days of watching).
  */
 @Entity(tableName = "watch_streak")
 data class WatchStreak(
     @PrimaryKey
     val id: Int = 1, // Single row table
     
     val currentStreakDays: Int,
     val longestStreakDays: Int,
     val lastWatchDate: String, // yyyy-MM-dd format
     val streakStartDate: String? // When current streak started
 )
