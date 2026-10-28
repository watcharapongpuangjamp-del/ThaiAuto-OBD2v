package com.example.data.remote

import android.util.Log
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

/**
 * Log only non-sensitive failure metadata. Never include auth identifiers,
 * email addresses, provider metadata, raw exception messages, or document paths.
 *
 * Callers must still propagate/handle the original exception; logging is not
 * a substitute for reporting a failed operation to the UI.
 */
fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val errorInfoJson = JSONObject().apply {
        put("errorType", exception::class.java.simpleName)
        put("operationType", operationType.value)
        put("resource", path?.substringAfterLast('/')?.takeIf { it.matches(Regex("[a-zA-Z_]+")) } ?: "redacted")
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore operation failed: $jsonString")
    return jsonString
}
