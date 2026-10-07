package com.example.calls.models

sealed class CallListItem {
    data class Header(val dayLabel: String) : CallListItem()
    data class CallRow(val call: Calls) : CallListItem()
}