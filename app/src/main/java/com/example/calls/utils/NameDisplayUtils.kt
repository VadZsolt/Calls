package com.example.calls.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import android.widget.TextView

/**
 * Binds a primary name + optional "+N" badge to the given TextViews.
 * Tapping the badge shows a dialog listing every known name for that number.
 */
fun bindNames(context: Context, tvPrimaryName: TextView, tvBadge: TextView, names: List<String>) {
    val distinctNames = names.filter { it.isNotBlank() }.distinct()
    val primary = distinctNames.firstOrNull() ?: "Unknown"
    val extraCount = distinctNames.size - 1

    tvPrimaryName.text = primary

    if (extraCount > 0) {
        tvBadge.visibility = android.view.View.VISIBLE
        tvBadge.text = "+$extraCount"
        tvBadge.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Known names for this number")
                .setItems(distinctNames.toTypedArray(), null)
                .setPositiveButton("Close", null)
                .show()
        }
    } else {
        tvBadge.visibility = android.view.View.GONE
        tvBadge.setOnClickListener(null)
    }
}