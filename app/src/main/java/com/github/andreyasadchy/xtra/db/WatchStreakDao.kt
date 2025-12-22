 package com.github.andreyasadchy.xtra.db
 
 import androidx.room.Dao
 import androidx.room.Insert
 import androidx.room.OnConflictStrategy
 import androidx.room.Query
 import com.github.andreyasadchy.xtra.model.stats.WatchStreak
 import kotlinx.coroutines.flow.Flow
 
 @Dao
 interface WatchStreakDao {
     
     @Insert(onConflict = OnConflictStrategy.REPLACE)
     suspend fun upsert(streak: WatchStreak)
     
     @Query("SELECT * FROM watch_streak WHERE id = 1")
     suspend fun getStreak(): WatchStreak?
     
     @Query("SELECT * FROM watch_streak WHERE id = 1")
     fun getStreakFlow(): Flow<WatchStreak?>
     
     @Query("UPDATE watch_streak SET currentStreakDays = :currentStreak, longestStreakDays = :longestStreak, lastWatchDate = :lastDate, streakStartDate = :startDate WHERE id = 1")
     suspend fun updateStreak(currentStreak: Int, longestStreak: Int, lastDate: String, startDate: String?)
 }
