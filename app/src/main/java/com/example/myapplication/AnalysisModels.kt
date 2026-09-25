package com.example.myapplication

enum class Category { OK, GROOMING, CONTENIDO_SEXUAL }
enum class Severity { NINGUNA, BAJA, MEDIA, ALTA }

data class AnalysisResult(
    val category: Category,
    val severity: Severity,
    val fragment: String,
    val model: String = "rules",
    val latencyMs: Long = 0,
    val streamed: Boolean = false
) {
    val shouldPersist: Boolean = true
}

data class ModelAttempt(
    val model: String,
    val latencyMs: Long,
    val httpCode: Int? = null,
    val error: String? = null,
    val analysis: AnalysisResult? = null,
    val success: Boolean = error == null
)

data class ChatMessage(
    val text: String,
    val analysis: AnalysisResult? = null,
    val error: String? = null
)

data class EventRecord(
    val message: String,
    val fragment: String,
    val category: Category,
    val severity: Severity,
    val contact: String,
    val createdAtMillis: Long?
)
