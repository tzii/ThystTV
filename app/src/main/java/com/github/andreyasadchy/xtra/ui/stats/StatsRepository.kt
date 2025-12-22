package com.github.andreyasadchy.xtra.ui.stats

import com.github.andreyasadchy.xtra.db.MonthlyWatchTime
import com.github.andreyasadchy.xtra.db.ScreenTimeDao
import com.github.andreyasadchy.xtra.db.StreamWatchStatsDao
import com.github.andreyasadchy.xtra.db.WatchSessionDao
import com.github.andreyasadchy.xtra.db.WatchStreakDao
import com.github.andreyasadchy.xtra.model.stats.CategoryWatchTime
import com.github.andreyasadchy.xtra.model.stats.HourlyWatchTime
import com.github.andreyasadchy.xtra.model.stats.ScreenTime
import com.github.andreyasadchy.xtra.model.stats.StreamWatchStats
import com.github.andreyasadchy.xtra.model.stats.StreamerLoyalty
import com.github.andreyasadchy.xtra.model.stats.WatchSession
import com.github.andreyasadchy.xtra.model.stats.WatchStreak
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StatsRepository @Inject constructor(
    private val screenTimeDao: ScreenTimeDao,
    private val streamWatchStatsDao: StreamWatchStatsDao,
    private val watchSessionDao: WatchSessionDao,
    private val watchStreakDao: WatchStreakDao
) {

    fun getScreenTimeFlow(date: String): Flow<ScreenTime?> = screenTimeDao.getScreenTimeFlow(date)

    suspend fun updateScreenTime(date: String, secondsToAdd: Long) {
        val current = screenTimeDao.getScreenTime(date)
        val newTime = (current?.totalSeconds ?: 0L) + secondsToAdd
        screenTimeDao.insert(ScreenTime(date, newTime))
    }

    fun getAllScreenTime(): Flow<List<ScreenTime>> = screenTimeDao.getAllScreenTime()

    fun getTopWatchedStreams(limit: Int): Flow<List<StreamWatchStats>> = streamWatchStatsDao.getTopWatchedStreams(limit)
    
    suspend fun updateStreamWatchStats(channelId: String, channelName: String, secondsToAdd: Long) {
        val current = streamWatchStatsDao.getStreamStats(channelId)
        val totalSeconds = (current?.totalSecondsWatched ?: 0L) + secondsToAdd
        streamWatchStatsDao.insert(StreamWatchStats(channelId, channelName, totalSeconds, System.currentTimeMillis()))
    }
    
    // Enhanced Analytics - Watch Sessions
    
    suspend fun recordWatchSession(session: WatchSession) {
        watchSessionDao.insert(session)
        updateWatchStreak(session.date)
    }
    
    suspend fun getCategoryBreakdown(startDate: String, endDate: String): List<CategoryWatchTime> {
        return watchSessionDao.getCategoryBreakdown(startDate, endDate)
    }
    
    fun getCategoryBreakdownFlow(startDate: String, endDate: String): Flow<List<CategoryWatchTime>> {
        return watchSessionDao.getCategoryBreakdownFlow(startDate, endDate)
    }
    
    suspend fun getHourlyBreakdown(startDate: String, endDate: String): List<HourlyWatchTime> {
        return watchSessionDao.getHourlyBreakdown(startDate, endDate)
    }
    
    fun getHourlyBreakdownFlow(startDate: String, endDate: String): Flow<List<HourlyWatchTime>> {
        return watchSessionDao.getHourlyBreakdownFlow(startDate, endDate)
    }
    
    suspend fun getMonthlySummary(limit: Int = 12): List<MonthlyWatchTime> {
        return watchSessionDao.getMonthlySummary(limit)
    }
    
    // Streamer Loyalty
    
    suspend fun getStreamerLoyalty(limit: Int = 20): List<StreamerLoyalty> {
        val rawData = watchSessionDao.getStreamerLoyaltyData(limit)
        if (rawData.isEmpty()) return emptyList()
        
        val maxWatchSeconds = rawData.maxOfOrNull { it.totalWatchSeconds } ?: 1L
        val maxSessionCount = rawData.maxOfOrNull { it.sessionCount } ?: 1
        val maxDistinctDays = rawData.maxOfOrNull { it.distinctDaysWatched } ?: 1
        
        return rawData.map { raw ->
            StreamerLoyalty(
                channelId = raw.channelId,
                channelName = raw.channelName,
                channelLogin = raw.channelLogin,
                totalWatchSeconds = raw.totalWatchSeconds,
                sessionCount = raw.sessionCount,
                distinctDaysWatched = raw.distinctDaysWatched,
                loyaltyScore = StreamerLoyalty.calculateScore(
                    raw.totalWatchSeconds, raw.sessionCount, raw.distinctDaysWatched,
                    maxWatchSeconds, maxSessionCount, maxDistinctDays
                )
            )
        }
    }
    
    // Watch Streak
    
    fun getWatchStreakFlow(): Flow<WatchStreak?> = watchStreakDao.getStreakFlow()
    
    private suspend fun updateWatchStreak(currentDate: String) {
        val streak = watchStreakDao.getStreak()
        if (streak == null) {
            watchStreakDao.upsert(WatchStreak(1, 1, 1, currentDate, currentDate))
        } else if (streak.lastWatchDate != currentDate) {
            // Check if consecutive day
            val isConsecutive = isConsecutiveDay(streak.lastWatchDate, currentDate)
            val newCurrent = if (isConsecutive) streak.currentStreakDays + 1 else 1
            val newLongest = maxOf(streak.longestStreakDays, newCurrent)
            val newStart = if (isConsecutive) streak.streakStartDate else currentDate
            watchStreakDao.updateStreak(newCurrent, newLongest, currentDate, newStart)
        }
    }
    
    private fun isConsecutiveDay(lastDate: String, currentDate: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val last = sdf.parse(lastDate) ?: return false
            val current = sdf.parse(currentDate) ?: return false
            val diffMs = current.time - last.time
            val diffDays = diffMs / (1000 * 60 * 60 * 24)
            diffDays == 1L
        } catch (e: Exception) {
            false
        }
    }
}
