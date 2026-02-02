package com.github.andreyasadchy.xtra.ui.view

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs

class GridRecyclerView : RecyclerView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val prefs = context.prefs()
    private val material3 = prefs.getBoolean(C.UI_THEME_MATERIAL3, true)
    private val portraitColumns = prefs.getString(C.PORTRAIT_COLUMN_COUNT, "1")!!.toInt()
    private val userLandscapeColumns = prefs.getString(C.LANDSCAPE_COLUMN_COUNT, "2")!!.toInt()

    private val gridLayoutManager: GridLayoutManager

    init {
        val columns = getColumnsForConfiguration(resources.configuration)
        gridLayoutManager = GridLayoutManager(context, columns)
        layoutManager = gridLayoutManager
        addItemDecoration(columns)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!material3) {
            removeItemDecorationAt(0)
        }
        val columns = getColumnsForConfiguration(newConfig)
        gridLayoutManager.spanCount = columns
        addItemDecoration(columns)
    }

    private fun getColumnsForConfiguration(configuration: Configuration): Int {
        return if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            portraitColumns
        } else {
            // For landscape, ensure at least 2 columns for better use of space
            // On tablets (sw600dp+), respect user setting or use at least 3
            val smallestWidth = configuration.smallestScreenWidthDp
            when {
                smallestWidth >= 600 -> maxOf(userLandscapeColumns, 3) // Tablets: at least 3 columns
                else -> maxOf(userLandscapeColumns, 2) // Phones: at least 2 columns in landscape
            }
        }
    }

    private fun addItemDecoration(columns: Int) {
        if (!material3) {
            addItemDecoration(
                if (columns <= 1) {
                    DividerItemDecoration(context, GridLayoutManager.VERTICAL)
                } else {
                    MarginItemDecoration(context.resources.getDimension(R.dimen.divider_margin).toInt(), columns)
                }
            )
        }
    }
}