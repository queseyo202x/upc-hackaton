package com.example.myapplication

object AccessibilityMonitorState {
    data class Update(
        val message: String,
        val attempt: ModelAttempt? = null,
        val analysis: AnalysisResult? = null,
        val firestore: Result<Unit>? = null,
        val error: String? = null
    )

    private val listeners = mutableSetOf<(Update) -> Unit>()

    @Synchronized
    fun observe(listener: (Update) -> Unit) {
        listeners += listener
    }

    @Synchronized
    fun removeObserver(listener: (Update) -> Unit) {
        listeners -= listener
    }

    @Synchronized
    fun publish(update: Update) {
        listeners.toList().forEach { it(update) }
    }
}
