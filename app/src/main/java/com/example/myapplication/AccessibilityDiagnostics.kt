package com.example.myapplication

import android.content.Context

object AccessibilityDiagnostics {
    private const val PREFS = "accessibility_diagnostics"
    private const val LAST_STATUS = "last_status"
    private const val LAST_PACKAGE = "last_package"
    private const val LAST_EVENT_TYPE = "last_event_type"
    private const val LAST_TEXT = "last_text"
    private const val LAST_TIME = "last_time"

    fun record(
        context: Context,
        status: String,
        packageName: String? = null,
        eventType: Int? = null,
        text: String? = null
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(LAST_STATUS, status)
            .putString(LAST_PACKAGE, packageName ?: "")
            .putInt(LAST_EVENT_TYPE, eventType ?: 0)
            .putString(LAST_TEXT, text ?: "")
            .putLong(LAST_TIME, System.currentTimeMillis())
            .apply()
    }

    fun summary(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val status = prefs.getString(LAST_STATUS, "Nunca se recibió una señal") ?: ""
        val packageName = prefs.getString(LAST_PACKAGE, "").orEmpty()
        val eventType = prefs.getInt(LAST_EVENT_TYPE, 0)
        val text = prefs.getString(LAST_TEXT, "").orEmpty()
        val time = prefs.getLong(LAST_TIME, 0L)
        return "Diagnóstico AccessibilityService\n" +
            "Estado: $status\n" +
            "Paquete: ${packageName.ifBlank { "ninguno" }}\n" +
            "Tipo de evento: $eventType\n" +
            "Texto capturado: ${text.ifBlank { "(vacío)" }}\n" +
            "Última señal: ${if (time == 0L) "nunca" else java.text.DateFormat.getDateTimeInstance().format(time)}"
    }
}
