package com.example.myapplication

enum class Category {
    GROOMING,
    ACOSO_SEXUAL,
    CIBERBULYING,
    COACCION_INTIMIDACION
}

enum class Severity { BAJO, MEDIO, ALTO }

data class AnalysisResult(
    val category: Category,
    val severity: Severity,
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
    val category: Category,
    val severity: Severity,
    val contact: String,
    val hourMillis: Long?
)
