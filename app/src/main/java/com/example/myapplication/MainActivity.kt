package com.example.myapplication

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var messagesContainer: LinearLayout
    private lateinit var messagesScroll: ScrollView
    private lateinit var statusText: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var modelStatusContainer: LinearLayout
    private val geminiClient by lazy { GeminiClient(BuildConfig.GEMINI_API_KEY) }
    private val eventRepository by lazy { FirestoreEventRepository() }
    private val accessibilityObserver: (AccessibilityMonitorState.Update) -> Unit = ::handleAccessibilityUpdate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        messagesContainer = findViewById(R.id.messagesContainer)
        messagesScroll = findViewById(R.id.messagesScroll)
        statusText = findViewById(R.id.statusText)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        modelStatusContainer = findViewById(R.id.modelStatusContainer)
        sendButton.setOnClickListener { analyzeCurrentMessage() }
        findViewById<Button>(R.id.dashboardButton).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        addMessageBubble("Prueba el análisis escribiendo un mensaje de Diego_2025.", null)
        statusText.text = AccessibilityDiagnostics.summary(this)
    }

    override fun onStart() {
        super.onStart()
        AccessibilityMonitorState.observe(accessibilityObserver)
        statusText.text = AccessibilityDiagnostics.summary(this)
    }

    override fun onStop() {
        AccessibilityMonitorState.removeObserver(accessibilityObserver)
        super.onStop()
    }

    private fun analyzeCurrentMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) {
            messageInput.error = "Escribe un mensaje"
            return
        }
        messageInput.text.clear()
        sendButton.isEnabled = false
        statusText.text = "Analizando mensaje..."
        modelStatusContainer.removeAllViews()
        geminiClient.analyze(message, ::addModelAttempt) { result ->
            sendButton.isEnabled = true
            result.fold(
                onSuccess = { analysis -> handleAnalysis(message, analysis) },
                onFailure = { error ->
                    statusText.text = "No se pudo analizar el mensaje"
                    Snackbar.make(messagesContainer, error.message ?: "Error desconocido", Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun handleAccessibilityUpdate(update: AccessibilityMonitorState.Update) {
        when {
            update.attempt != null -> addModelAttempt(update.attempt)
            update.analysis != null -> {
                statusText.text = "WhatsApp reconocido: clasificación recibida"
                addMessageBubble(update.message, update.analysis)
            }
            update.firestore != null -> update.firestore.fold(
                onSuccess = {
                    statusText.text = "WhatsApp: confirmado, evento guardado en Firestore/events"
                    Snackbar.make(
                        messagesContainer,
                        "WhatsApp reconocido y guardado correctamente en Firestore",
                        Snackbar.LENGTH_LONG
                    ).show()
                },
                onFailure = {
                    statusText.text = "WhatsApp reconocido, pero Firestore rechazó el evento"
                    Snackbar.make(
                        messagesContainer,
                        "WhatsApp reconocido; Firestore falló: ${it.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            )
            update.error != null -> {
                statusText.text = "WhatsApp reconocido, análisis falló"
                Snackbar.make(messagesContainer, update.error, Snackbar.LENGTH_LONG).show()
            }
            else -> {
                statusText.text = "WhatsApp reconocido: enviando a los modelos..."
                addMessageBubble("WhatsApp\n${update.message}\n\nMensaje reconocido; analizando...", null)
            }
        }
    }

    private fun addModelAttempt(attempt: ModelAttempt) {
        val detail = attempt.error?.let { "\nHTTP ${attempt.httpCode ?: "N/D"}: $it" }
            ?: attempt.analysis?.let {
                "\nHTTP ${attempt.httpCode ?: "N/D"}: respuesta JSON recibida" +
                    "\nClasificación: ${categoryLabel(it.category)}" +
                    "\nSeveridad: ${it.severity.name.lowercase()}" +
                    "\nMensaje: ${it.fragment.ifBlank { "sin fragmento" }}"
            } ?: "\nHTTP ${attempt.httpCode ?: "N/D"}: respuesta recibida"
        val card = TextView(this).apply {
            text = "${if (attempt.success) "OK" else "ERROR"}  ${attempt.model}  " +
                "${attempt.latencyMs} ms$detail"
            textSize = 12f
            setTextColor(if (attempt.success) Color.rgb(20, 90, 40) else Color.rgb(150, 35, 35))
            setPadding(16, 12, 16, 12)
            setBackgroundColor(if (attempt.success) Color.rgb(232, 245, 233) else Color.rgb(255, 235, 238))
        }
        modelStatusContainer.addView(card)
    }

    private fun handleAnalysis(message: String, analysis: AnalysisResult) {
        eventRepository.saveEvent(message, analysis, getString(R.string.contact_name)) { saveResult ->
            saveResult.fold(
                onSuccess = {
                    statusText.text = "Confirmado: evento guardado correctamente en Firestore/events"
                    Snackbar.make(
                        messagesContainer,
                        "Guardado correctamente en Firestore, colección events",
                        Snackbar.LENGTH_LONG
                    ).show()
                    addMessageBubble(message, analysis)
                },
                onFailure = { error ->
                    statusText.text = "El análisis terminó, pero Firestore falló"
                    addMessageBubble(message, analysis)
                    Snackbar.make(messagesContainer, error.message ?: "Error guardando evento", Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun addMessageBubble(message: String, analysis: AnalysisResult?) {
        val bubble = TextView(this).apply {
            text = buildBubbleText(message, analysis)
            textSize = 16f
            setTextColor(Color.rgb(35, 31, 36))
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.WHITE)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 16)
            gravity = Gravity.START
        }
        messagesContainer.addView(bubble, params)
        messagesScroll.post { messagesScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun buildBubbleText(message: String, analysis: AnalysisResult?): String {
        if (analysis == null) return "Diego_2025\n$message"
        val category = categoryLabel(analysis.category)
        val severity = analysis.severity.name.lowercase()
        return "Diego_2025\n$message\n\nIA: $category | severidad: $severity\n" +
            "Modelo: ${analysis.model} | ${analysis.latencyMs} ms | streaming: ${analysis.streamed}\n" +
            "Fragmento: ${analysis.fragment}"
    }

    private fun categoryLabel(category: Category): String = when (category) {
        Category.OK -> "OK"
        Category.GROOMING -> "Grooming"
        Category.CONTENIDO_SEXUAL -> "Contenido sexual"
    }
}