package com.example.myapplication

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

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
            "mensaje" to message,
            "categoria" to analysis.category.firestoreValue,
            "severidad" to analysis.severity.firestoreValue,
            "contacto" to contact,
            "hora" to FieldValue.serverTimestamp()
        )
        firestore.collection("events").add(data)
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    fun loadEvents(onComplete: (Result<List<EventRecord>>) -> Unit) {
        firestore.collection("events")
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val events = snapshot.documents.mapNotNull { document ->
                    val category = document.firstString("categoria", "category")?.toCategory()
                        ?: return@mapNotNull null
                    val severity = document.firstString("severidad", "severity")?.toSeverity()
                        ?: return@mapNotNull null
                    EventRecord(
                        message = document.firstString("mensaje", "message").orEmpty(),
                        category = category,
                        severity = severity,
                        contact = document.firstString("contacto", "contact").orEmpty(),
                        hourMillis = document.getTimestamp("hora")?.toDate()?.time
                            ?: document.getTimestamp("createdAt")?.toDate()?.time
                    )
                }
                onComplete(Result.success(events.sortedByDescending { it.hourMillis ?: 0L }))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.firstString(vararg fields: String): String? =
        fields.asSequence().mapNotNull { getString(it) }.firstOrNull()

    private fun String.toCategory(): Category? = when (lowercase()) {
        "grooming" -> Category.GROOMING
        "acoso sexual" -> Category.ACOSO_SEXUAL
        "contenido_sexual" -> Category.ACOSO_SEXUAL
        "ciberbulying" -> Category.CIBERBULYING
        "coacción/intimidacion", "coaccion/intimidacion" -> Category.COACCION_INTIMIDACION
        else -> null
    }

    private fun String.toSeverity(): Severity? = when (lowercase()) {
        "bajo", "baja" -> Severity.BAJO
        "medio", "media" -> Severity.MEDIO
        "alto", "alta" -> Severity.ALTO
        else -> null
    }

    private val Category.firestoreValue: String
        get() = when (this) {
            Category.GROOMING -> "grooming"
            Category.ACOSO_SEXUAL -> "acoso sexual"
            Category.CIBERBULYING -> "ciberbulying"
            Category.COACCION_INTIMIDACION -> "coacción/intimidacion"
        }

    private val Severity.firestoreValue: String
        get() = when (this) {
            Severity.BAJO -> "bajo"
            Severity.MEDIO -> "medio"
            Severity.ALTO -> "alto"
        }
}
