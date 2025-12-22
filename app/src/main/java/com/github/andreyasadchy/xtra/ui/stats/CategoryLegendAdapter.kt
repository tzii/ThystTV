 package com.github.andreyasadchy.xtra.ui.stats
 
 import android.graphics.drawable.GradientDrawable
 import android.view.LayoutInflater
 import android.view.ViewGroup
 import androidx.recyclerview.widget.DiffUtil
 import androidx.recyclerview.widget.ListAdapter
 import androidx.recyclerview.widget.RecyclerView
 import com.github.andreyasadchy.xtra.databinding.ItemCategoryLegendBinding
 import com.github.andreyasadchy.xtra.ui.view.CategoryPieChartView
 
 class CategoryLegendAdapter : ListAdapter<CategoryPieChartView.Slice, CategoryLegendAdapter.ViewHolder>(DIFF_CALLBACK) {
 
     override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
         val binding = ItemCategoryLegendBinding.inflate(
             LayoutInflater.from(parent.context), parent, false
         )
         return ViewHolder(binding)
     }
 
     override fun onBindViewHolder(holder: ViewHolder, position: Int) {
         holder.bind(getItem(position))
     }
 
     class ViewHolder(private val binding: ItemCategoryLegendBinding) : RecyclerView.ViewHolder(binding.root) {
         fun bind(slice: CategoryPieChartView.Slice) {
             val drawable = GradientDrawable().apply {
                 shape = GradientDrawable.OVAL
                 setColor(slice.color)
             }
             binding.colorDot.background = drawable
             binding.categoryName.text = slice.label.ifEmpty { "Unknown" }
             val percentage = (slice.value / 360f * 100).toInt()
             binding.categoryPercentage.text = "$percentage%"
         }
     }
 
     companion object {
         private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CategoryPieChartView.Slice>() {
             override fun areItemsTheSame(oldItem: CategoryPieChartView.Slice, newItem: CategoryPieChartView.Slice) =
                 oldItem.label == newItem.label
             override fun areContentsTheSame(oldItem: CategoryPieChartView.Slice, newItem: CategoryPieChartView.Slice) =
                 oldItem == newItem
         }
     }
 }
