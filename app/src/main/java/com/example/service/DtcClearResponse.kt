package com.example.service

private val mode04ErrorMarkers = listOf("ERROR", "UNABLE TO CONNECT", "NO DATA", "CAN ERROR", "BUS ERROR", "STOPPED")
private val responseTokenSeparator = Regex("[\\s>]+")

internal fun isMode04ClearAcknowledged(raw: String): Boolean {
    val response = raw.uppercase()
    if (mode04ErrorMarkers.any(response::contains)) return false
    return response.split(responseTokenSeparator).any { it == "44" }
}

