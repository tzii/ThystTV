package com.github.andreyasadchy.xtra.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.db.MonthlyWatchTime
import com.github.andreyasadchy.xtra.model.stats.CategoryWatchTime
import com.github.andreyasadchy.xtra.model.stats.HourlyWatchTime
import com.github.andreyasadchy.xtra.model.stats.ScreenTime
import com.github.andreyasadchy.xtra.model.stats.StreamWatchStats
import com.github.andreyasadchy.xtra.model.stats.StreamerLoyalty
import com.github.andreyasadchy.xtra.model.stats.WatchStreak
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

enum class StatsTimeRange(val days: Int?) {
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    ALL_TIME(null),
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: StatsRepository
) : ViewModel() {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val screenTime: StateFlow<List<ScreenTime>> = repository.getAllScreenTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topStreams: StateFlow<List<StreamWatchStats>> = repository.getTopWatchedStreams(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val watchStreak: StateFlow<WatchStreak?> = repository.getWatchStreakFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    // Category breakdown for last 30 days
    private val _categoryBreakdown = MutableStateFlow<List<CategoryWatchTime>>(emptyList())
    val categoryBreakdown: StateFlow<List<CategoryWatchTime>> = _categoryBreakdown.asStateFlow()
    
    // Hourly breakdown (heatmap data)
    private val _hourlyBreakdown = MutableStateFlow<List<HourlyWatchTime>>(emptyList())
    val hourlyBreakdown: StateFlow<List<HourlyWatchTime>> = _hourlyBreakdown.asStateFlow()
    
    // Monthly summaries
    private val _monthlySummary = MutableStateFlow<List<MonthlyWatchTime>>(emptyList())
    val monthlySummary: StateFlow<List<MonthlyWatchTime>> = _monthlySummary.asStateFlow()
    
    // Streamer loyalty
    private val _streamerLoyalty = MutableStateFlow<List<StreamerLoyalty>>(emptyList())
    val streamerLoyalty: StateFlow<List<StreamerLoyalty>> = _streamerLoyalty.asStateFlow()

    private val _timeRange = MutableStateFlow(StatsTimeRange.LAST_7_DAYS)
    val timeRange: StateFlow<StatsTimeRange> = _timeRange.asStateFlow()
    
    init {
        loadEnhancedAnalytics()
    }
    
    private fun loadEnhancedAnalytics() {
        viewModelScope.launch {
            val (startDate, endDate) = getDateRange(_timeRange.value)
            
            _categoryBreakdown.value = repository.getCategoryBreakdown(startDate, endDate)
            _hourlyBreakdown.value = repository.getHourlyBreakdown(startDate, endDate)
            _monthlySummary.value = repository.getMonthlySummary(12)
            _streamerLoyalty.value = if (_timeRange.value == StatsTimeRange.ALL_TIME) {
                repository.getStreamerLoyalty(10)
            } else {
                repository.getStreamerLoyalty(10, startDate, endDate)
            }
        }
    }

    fun setTimeRange(timeRange: StatsTimeRange) {
        if (_timeRange.value == timeRange) return
        _timeRange.value = timeRange
        loadEnhancedAnalytics()
    }
    
    fun refresh() {
        loadEnhancedAnalytics()
    }

    private fun getDateRange(timeRange: StatsTimeRange): Pair<String, String> {
        val calendar = Calendar.getInstance()
        val endDate = dateFormat.format(calendar.time)
        val days = timeRange.days
        return if (days != null) {
            calendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
            dateFormat.format(calendar.time) to endDate
        } else {
            "0000-01-01" to endDate
        }
    }
}
