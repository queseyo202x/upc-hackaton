package com.example.myapplication

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class FirestoreEventRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun saveEvent(
        message: String,
        analysis: AnalysisResult,
        contact: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val data = hashMapOf(
            "message" to message,
            "fragment" to analysis.fragment,
            "category" to analysis.category.name.lowercase(),
            "severity" to analysis.severity.name.lowercase(),
            "contact" to contact,
            "model" to analysis.model,
            "latencyMs" to analysis.latencyMs,
            "streamed" to analysis.streamed,
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("events").add(data)
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun loadEvents(onComplete: (Result<List<EventRecord>>) -> Unit) {
        firestore.collection("events")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val events = snapshot.documents.mapNotNull { document ->
                    val category = document.getString("category")?.toCategory() ?: return@mapNotNull null
                    val severity = document.getString("severity")?.toSeverity() ?: return@mapNotNull null
                    EventRecord(
                        message = document.getString("message").orEmpty(),
                        fragment = document.getString("fragment").orEmpty(),
                        category = category,
                        severity = severity,
                        contact = document.getString("contact").orEmpty(),
                        createdAtMillis = document.getTimestamp("createdAt")?.toDate()?.time
                    )
                }
                onComplete(Result.success(events))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    private fun String.toCategory(): Category? = when (lowercase()) {
        "ok" -> Category.OK
        "grooming" -> Category.GROOMING
        "contenido_sexual" -> Category.CONTENIDO_SEXUAL
        else -> null
    }

    private fun String.toSeverity(): Severity? = when (lowercase()) {
        "ninguna" -> Severity.NINGUNA
        "baja" -> Severity.BAJA
        "media" -> Severity.MEDIA
        "alta" -> Severity.ALTA
        else -> null
    }
}
