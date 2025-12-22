 package com.github.andreyasadchy.xtra.model.stats
 
 import androidx.room.Entity
 import androidx.room.Index
 import androidx.room.PrimaryKey
 
 /**
  * Represents a single watch session for detailed analytics.
  * Each session tracks when a user watched a specific channel/game.
  */
 @Entity(
     tableName = "watch_sessions",
     indices = [
         Index(value = ["channelId"]),
         Index(value = ["gameId"]),
         Index(value = ["startTime"]),
         Index(value = ["date"])
     ]
 )
 data class WatchSession(
     @PrimaryKey(autoGenerate = true)
     val id: Long = 0,
     
     val channelId: String?,
     val channelName: String?,
     val channelLogin: String?,
     
     val gameId: String?,
     val gameName: String?,
     
     val startTime: Long,
     val endTime: Long,
     val durationSeconds: Long,
     
     // Date in format yyyy-MM-dd for easy grouping
     val date: String,
     
     // Hour of day (0-23) for peak time analysis
     val hourOfDay: Int
 )
