 package com.github.andreyasadchy.xtra.model.stats
 
 /**
  * Aggregated watch time for a specific hour of the day.
  * Used for peak viewing times heatmap.
  */
 data class HourlyWatchTime(
     val hourOfDay: Int,
     val totalSeconds: Long,
     val sessionCount: Int
 )
