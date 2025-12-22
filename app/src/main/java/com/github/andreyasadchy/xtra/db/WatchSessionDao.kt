 package com.github.andreyasadchy.xtra.db
 
 import androidx.room.Dao
 import androidx.room.Insert
 import androidx.room.Query
 import com.github.andreyasadchy.xtra.model.stats.CategoryWatchTime
 import com.github.andreyasadchy.xtra.model.stats.HourlyWatchTime
 import com.github.andreyasadchy.xtra.model.stats.WatchSession
 import kotlinx.coroutines.flow.Flow
 
 @Dao
 interface WatchSessionDao {
     
     @Insert
     suspend fun insert(session: WatchSession)
     
     @Query("SELECT * FROM watch_sessions ORDER BY startTime DESC LIMIT :limit")
     suspend fun getRecentSessions(limit: Int = 100): List<WatchSession>
     
     @Query("SELECT * FROM watch_sessions WHERE date = :date ORDER BY startTime DESC")
     suspend fun getSessionsForDate(date: String): List<WatchSession>
     
     @Query("SELECT * FROM watch_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY startTime DESC")
     suspend fun getSessionsBetweenDates(startDate: String, endDate: String): List<WatchSession>
     
     // Category breakdown - aggregate watch time by game
     @Query("""
         SELECT gameId, gameName, 
                SUM(durationSeconds) as totalSeconds, 
                COUNT(*) as sessionCount 
         FROM watch_sessions 
         WHERE date BETWEEN :startDate AND :endDate
         GROUP BY gameId, gameName 
         ORDER BY totalSeconds DESC
     """)
     suspend fun getCategoryBreakdown(startDate: String, endDate: String): List<CategoryWatchTime>
     
     // Peak viewing times - aggregate by hour of day
     @Query("""
         SELECT hourOfDay, 
                SUM(durationSeconds) as totalSeconds, 
                COUNT(*) as sessionCount 
         FROM watch_sessions 
         WHERE date BETWEEN :startDate AND :endDate
         GROUP BY hourOfDay 
         ORDER BY hourOfDay ASC
     """)
     suspend fun getHourlyBreakdown(startDate: String, endDate: String): List<HourlyWatchTime>
     
     // Total watch time for a date range
     @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM watch_sessions WHERE date BETWEEN :startDate AND :endDate")
     suspend fun getTotalWatchTime(startDate: String, endDate: String): Long
     
     // Monthly summary - total seconds per month
     @Query("""
         SELECT SUBSTR(date, 1, 7) as month, SUM(durationSeconds) as totalSeconds 
         FROM watch_sessions 
         GROUP BY month 
         ORDER BY month DESC 
         LIMIT :limit
     """)
     suspend fun getMonthlySummary(limit: Int = 12): List<MonthlyWatchTime>
     
     // Flow version for reactive UI
     @Query("""
         SELECT gameId, gameName, 
                SUM(durationSeconds) as totalSeconds, 
                COUNT(*) as sessionCount 
         FROM watch_sessions 
         WHERE date BETWEEN :startDate AND :endDate
         GROUP BY gameId, gameName 
         ORDER BY totalSeconds DESC
     """)
     fun getCategoryBreakdownFlow(startDate: String, endDate: String): Flow<List<CategoryWatchTime>>
     
     @Query("""
         SELECT hourOfDay, 
                SUM(durationSeconds) as totalSeconds, 
                COUNT(*) as sessionCount 
         FROM watch_sessions 
         WHERE date BETWEEN :startDate AND :endDate
         GROUP BY hourOfDay 
         ORDER BY hourOfDay ASC
     """)
     fun getHourlyBreakdownFlow(startDate: String, endDate: String): Flow<List<HourlyWatchTime>>
     
     // Get distinct watch dates for streak calculation
     @Query("SELECT DISTINCT date FROM watch_sessions ORDER BY date DESC")
     suspend fun getDistinctWatchDates(): List<String>
     
     // Delete old sessions (cleanup)
     @Query("DELETE FROM watch_sessions WHERE date < :beforeDate")
     suspend fun deleteOldSessions(beforeDate: String)
    
    // Streamer loyalty - aggregate by channel
    @Query("""
        SELECT channelId, channelName, channelLogin,
               SUM(durationSeconds) as totalWatchSeconds,
               COUNT(*) as sessionCount,
               COUNT(DISTINCT date) as distinctDaysWatched
        FROM watch_sessions 
        WHERE channelId IS NOT NULL
        GROUP BY channelId, channelName, channelLogin
        ORDER BY totalWatchSeconds DESC
        LIMIT :limit
    """)
    suspend fun getStreamerLoyaltyData(limit: Int = 20): List<RawStreamerLoyalty>
    
    // Flow version for streamer loyalty
    @Query("""
        SELECT channelId, channelName, channelLogin,
               SUM(durationSeconds) as totalWatchSeconds,
               COUNT(*) as sessionCount,
               COUNT(DISTINCT date) as distinctDaysWatched
        FROM watch_sessions 
        WHERE channelId IS NOT NULL
        GROUP BY channelId, channelName, channelLogin
        ORDER BY totalWatchSeconds DESC
        LIMIT :limit
    """)
    fun getStreamerLoyaltyDataFlow(limit: Int = 20): Flow<List<RawStreamerLoyalty>>
 }
 
 data class MonthlyWatchTime(
     val month: String,
     val totalSeconds: Long
 )

data class RawStreamerLoyalty(
    val channelId: String?,
    val channelName: String?,
    val channelLogin: String?,
    val totalWatchSeconds: Long,
    val sessionCount: Int,
    val distinctDaysWatched: Int
)
