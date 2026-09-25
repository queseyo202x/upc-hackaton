package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.graphics.Rect
import java.security.MessageDigest

class WhatsappAccessibilityService : AccessibilityService() {
    private val geminiClient by lazy { GeminiClient(BuildConfig.GEMINI_API_KEY) }
    private val eventRepository by lazy { FirestoreEventRepository() }
    private var lastMessageHash: String? = null
    private var lastSubmittedAt = 0L
    private var requestInProgress = false

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        if (packageName != WHATSAPP_PACKAGE) return
        AccessibilityDiagnostics.record(
            this,
            "Evento WhatsApp recibido",
            packageName,
            event.eventType,
            event.text.joinToString(" ")
        )
        val message = extractMessage(event).trim()
        if (message.isBlank()) {
            AccessibilityDiagnostics.record(this, "WhatsApp recibió evento, pero el texto está vacío",
                packageName, event.eventType)
            return
        }
        if (message.length > MAX_MESSAGE_LENGTH) return

        val hash = sha256(message)
        val now = SystemClock.elapsedRealtime()
        if (requestInProgress || hash == lastMessageHash ||
            now - lastSubmittedAt < MIN_REQUEST_INTERVAL_MS
        ) {
            AccessibilityDiagnostics.record(this, "Texto detectado, bloqueado por duplicado o enfriamiento",
                packageName, event.eventType, message)
            return
        }
        lastMessageHash = hash
        lastSubmittedAt = now
        requestInProgress = true
        AccessibilityDiagnostics.record(this, "Texto reconocido; enviando a Gemini",
            packageName, event.eventType, message)
        AccessibilityMonitorState.publish(
            AccessibilityMonitorState.Update(message = message)
        )
        geminiClient.analyze(message, onModelAttempt = { attempt ->
            Log.i(TAG, "WhatsApp ${attempt.model}: ${attempt.httpCode} ${attempt.latencyMs}ms")
            AccessibilityMonitorState.publish(
                AccessibilityMonitorState.Update(message = message, attempt = attempt)
            )
        }) { result ->
            result.onSuccess { analysis ->
                AccessibilityMonitorState.publish(
                    AccessibilityMonitorState.Update(message = message, analysis = analysis)
                )
                eventRepository.saveEvent(message, analysis, "WhatsApp") { saveResult ->
                    AccessibilityMonitorState.publish(
                        AccessibilityMonitorState.Update(message = message, firestore = saveResult)
                    )
                    saveResult.exceptionOrNull()?.let {
                        Log.e(TAG, "No se pudo guardar el evento de WhatsApp", it)
                    }
                }
            }.onFailure {
                AccessibilityMonitorState.publish(
                    AccessibilityMonitorState.Update(
                        message = message,
                        error = it.message ?: "No se pudo analizar el mensaje de WhatsApp"
                    )
                )
                Log.e(TAG, "No se pudo analizar el mensaje de WhatsApp", it)
            }
            requestInProgress = false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityDiagnostics.record(this, "Servicio conectado y activo")
        Log.i(TAG, "AccessibilityService conectado")
    }

    override fun onInterrupt() = Unit

    private fun extractMessage(event: AccessibilityEvent): String {
        val candidates = mutableListOf<TextCandidate>()
        rootInActiveWindow?.let { root -> collectCandidates(root, candidates) }
        return candidates
            .asReversed()
            .filter { isMessageCandidate(it) }
            .maxByOrNull { it.score }
            ?.text
            .orEmpty()
    }

    private fun collectCandidates(
        node: AccessibilityNodeInfo,
        output: MutableList<TextCandidate>
    ) {
        val text = meaningfulText(node.text?.toString().orEmpty())
        val className = node.className?.toString().orEmpty()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (text != null) {
            output += TextCandidate(
                text = text,
                className = className,
                clickable = node.isClickable,
                contentDescription = node.contentDescription != null,
                bounds = bounds,
                score = scoreCandidate(text, className, node.isClickable, bounds)
            )
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                collectCandidates(child, output)
                child.recycle()
            }
        }
    }

    private fun isMessageCandidate(candidate: TextCandidate): Boolean {
        if (candidate.clickable || candidate.contentDescription) return false
        if (candidate.className != "android.widget.TextView" &&
            candidate.className != "android.view.View"
        ) return false
        if (candidate.text.matches(TIME_PATTERN)) return false
        if (NON_MESSAGE_TEXT.any { candidate.text.equals(it, ignoreCase = true) }) return false
        if (candidate.text.contains("Meta AI", ignoreCase = true)) return false
        if (candidate.text.contains("Grabadora", ignoreCase = true)) return false
        return candidate.score >= MIN_MESSAGE_SCORE
    }

    private fun scoreCandidate(
        text: String,
        className: String,
        clickable: Boolean,
        bounds: Rect
    ): Int {
        var score = 0
        if (className == "android.widget.TextView") score += 3
        if (!clickable) score += 3
        if (bounds.top > 120 && bounds.bottom < 2_400) score += 2
        if (text.length in 2..300) score += 2
        return score
    }

    private fun meaningfulText(value: String): String? {
        val text = value.replace(Regex("\\s+"), " ").trim()
        if (text.isBlank() || text.length > MAX_MESSAGE_LENGTH) return null
        if (IGNORED_UI_TEXT.any { text.equals(it, ignoreCase = true) }) return null
        if (text.contains("Inbox filters", ignoreCase = true) ||
            text.contains("Radio Group", ignoreCase = true) ||
            text.equals("Mensaje", ignoreCase = true)
        ) return null
        return text
    }

    private data class TextCandidate(
        val text: String,
        val className: String,
        val clickable: Boolean,
        val contentDescription: Boolean,
        val bounds: Rect,
        val score: Int
    )

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "WhatsappAccessibility"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val MAX_MESSAGE_LENGTH = 1_000
        private const val MIN_REQUEST_INTERVAL_MS = 8_000L
        private const val MIN_MESSAGE_SCORE = 8
        private val TIME_PATTERN = Regex(
            """^\d{1,2}:\d{2}\s*(a\.?\s*m\.?|p\.?\s*m\.?)?$""",
            RegexOption.IGNORE_CASE
        )
        private val IGNORED_UI_TEXT = setOf(
            "WhatsApp", "Chats", "Comunidades", "Llamadas", "Novedades",
            "Buscar", "Cámara", "Micrófono", "Adjuntar", "Enviar"
        )
        private val NON_MESSAGE_TEXT = setOf(
            "Preguntar a Meta AI",
            "Grabadora de mensajes de voz",
            "Transcribir",
            "Desliza hacia abajo para ver más acciones",
            "Inbox filters",
            "Radio Group"
        )
    }
}
