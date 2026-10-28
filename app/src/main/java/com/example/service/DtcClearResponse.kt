package com.example.service

private val mode04ErrorMarkers = listOf("ERROR", "UNABLE TO CONNECT", "NO DATA", "CAN ERROR", "BUS ERROR", "STOPPED")
private val responseTokenSeparator = Regex("[\\s>]+")

internal fun isMode04ClearAcknowledged(raw: String): Boolean {
    val response = raw.uppercase()
    if (mode04ErrorMarkers.any(response::contains)) return false
    val tokens = response.split(responseTokenSeparator).filter(String::isNotEmpty)
    val hexStream = tokens
        .filter { token -> token.all { character -> character in "0123456789ABCDEF" } }
        .joinToString("")
    if (hexStream.contains("7F04")) return false
    return tokens.any {
        it == "44" || (it.length > 2 && it.all { character -> character in "0123456789ABCDEF" } && it.endsWith("44"))
    }
}
