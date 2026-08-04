package com.example.calls.models

data class Calls(
    val Id: String? = null,
    val Date: String? = null,
    val Number: String? = null,
    val Name: String? = null,
    val Type: String? = null,
    val Uploader: String? = null,
    val Names: List<String> = emptyList(),
    val Observation: String? = null
)