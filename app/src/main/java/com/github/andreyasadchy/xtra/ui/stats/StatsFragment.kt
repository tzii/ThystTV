package com.github.andreyasadchy.xtra.ui.stats

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentStatsBinding
import com.github.andreyasadchy.xtra.ui.view.DailyBarChartView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class StatsFragment : Fragment(R.layout.fragment_stats) {

    private val viewModel: StatsViewModel by viewModels()
    private var binding: FragmentStatsBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentStatsBinding.bind(view)
        this.binding = binding

        val adapter = StreamStatsAdapter()
        binding.topStreamsRecyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.screenTime.collectLatest { screenTimes ->
                        updateScreenTimeDisplay(binding, screenTimes)
                    }
                }
                launch {
                    viewModel.topStreams.collectLatest { streams ->
                        adapter.submitList(streams)
                    }
                }
            }
        }
    }

    private fun updateScreenTimeDisplay(
        binding: FragmentStatsBinding,
        screenTimes: List<com.github.andreyasadchy.xtra.model.stats.ScreenTime>
    ) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())
        
        // Create map of date -> seconds for quick lookup
        val timeMap = screenTimes.associateBy { it.date }
        
        // Generate last 7 days data
        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val chartData = mutableListOf<DailyBarChartView.DayData>()
        
        var weekTotalSeconds = 0L
        
        // Go back 6 days and work forward to today
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        
        for (i in 0 until 7) {
            val dateStr = sdf.format(calendar.time)
            val seconds = timeMap[dateStr]?.totalSeconds ?: 0L
            weekTotalSeconds += seconds
            
            val label = if (dateStr == today) {
                "Today"
            } else {
                dayFormat.format(calendar.time)
            }
            
            chartData.add(DailyBarChartView.DayData(label, seconds))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        // Set chart data
        binding.dailyBarChart.setData(chartData)
        
        // Calculate daily average
        val avgSeconds = if (chartData.isNotEmpty()) weekTotalSeconds / 7 else 0L
        val avgHours = avgSeconds / 3600
        val avgMinutes = (avgSeconds % 3600) / 60
        binding.dailyAverageText.text = formatTime(avgHours, avgMinutes, "daily average")
        
        // Week change - compare with previous week (placeholder for now)
        binding.weekChangeText.text = "↑ from last week"
        
        // Today's time
        val todaySeconds = timeMap[today]?.totalSeconds ?: 0L
        val todayHours = todaySeconds / 3600
        val todayMinutes = (todaySeconds % 3600) / 60
        binding.todayTimeText.text = formatTimeShort(todayHours, todayMinutes)
        
        // Week total
        val weekHours = weekTotalSeconds / 3600
        val weekMinutes = (weekTotalSeconds % 3600) / 60
        binding.weekTotalText.text = formatTimeShort(weekHours, weekMinutes)
    }
    
    private fun formatTime(hours: Long, minutes: Long, suffix: String): String {
        return buildString {
            if (hours > 0) append("$hours hr ")
            append("$minutes min $suffix")
        }
    }
    
    private fun formatTimeShort(hours: Long, minutes: Long): String {
        return buildString {
            if (hours > 0) append("$hours hr ")
            if (minutes > 0 || hours == 0L) append("$minutes min")
        }.trim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
