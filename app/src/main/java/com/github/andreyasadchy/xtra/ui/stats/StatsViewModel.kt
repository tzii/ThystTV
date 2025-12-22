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
    
    init {
        loadEnhancedAnalytics()
    }
    
    private fun loadEnhancedAnalytics() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val endDate = dateFormat.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, -30)
            val startDate = dateFormat.format(calendar.time)
            
            _categoryBreakdown.value = repository.getCategoryBreakdown(startDate, endDate)
            _hourlyBreakdown.value = repository.getHourlyBreakdown(startDate, endDate)
            _monthlySummary.value = repository.getMonthlySummary(12)
            _streamerLoyalty.value = repository.getStreamerLoyalty(10)
        }
    }
    
    fun refresh() {
        loadEnhancedAnalytics()
    }
}
