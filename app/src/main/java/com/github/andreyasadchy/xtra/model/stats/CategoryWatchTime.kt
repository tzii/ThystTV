 package com.github.andreyasadchy.xtra.model.stats
 
 /**
  * Aggregated watch time for a specific game/category.
  * Used for category breakdown pie charts.
  */
 data class CategoryWatchTime(
     val gameId: String?,
     val gameName: String?,
     val totalSeconds: Long,
     val sessionCount: Int
 )
