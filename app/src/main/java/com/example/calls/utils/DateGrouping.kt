package com.example.calls.utils

import com.example.calls.models.CallListItem
import com.example.calls.models.Calls

/**
 * Takes a list of calls (assumed newest-first, same order ReadActivity already uses)
 * and inserts a Header item before the first call of each new day.
 */
fun groupCallsByDay(calls: List<Calls>): List<CallListItem> {
    val result = mutableListOf<CallListItem>()
    var lastDay: String? = null

    for (call in calls) {
        val day = call.Date?.split(" ")?.getOrNull(0) // "MM/dd" part

        if (day != null && day != lastDay) {
            result.add(CallListItem.Header(day))
            lastDay = day
        }

        result.add(CallListItem.CallRow(call))
    }

    return result
}