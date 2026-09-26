package com.example.myapplication

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar

class ChatSimulatorActivity : AppCompatActivity() {
    private data class SimulatedMessage(
        val text: String,
        val category: Category,
        val severity: Severity
    )

    private val conversations = linkedMapOf(
        "@Diego_2025" to listOf(
            SimulatedMessage("Te vamos a pegar", Category.CIBERBULYING, Severity.ALTO),
            SimulatedMessage("No le cuentes a tu papá", Category.GROOMING, Severity.MEDIO),
            SimulatedMessage("Pásame una foto tuya", Category.GROOMING, Severity.MEDIO)
        ),
        "@Sofi_redes" to listOf(
            SimulatedMessage("Si no haces lo que digo, lo publico", Category.COACCION_INTIMIDACION, Severity.ALTO),
            SimulatedMessage("Tienes que responderme ahora", Category.COACCION_INTIMIDACION, Severity.MEDIO),
            SimulatedMessage("Nadie te va a creer", Category.CIBERBULYING, Severity.MEDIO)
        ),
        "@Alex_urbano" to listOf(
            SimulatedMessage("Mándame una foto íntima", Category.ACOSO_SEXUAL, Severity.ALTO),
            SimulatedMessage("Es nuestro secreto", Category.GROOMING, Severity.MEDIO),
            SimulatedMessage("No seas aburrida", Category.ACOSO_SEXUAL, Severity.MEDIO)
        ),
        "@Vale_play" to listOf(
            SimulatedMessage("Todos se están riendo de ti", Category.CIBERBULYING, Severity.ALTO),
            SimulatedMessage("Borra ese mensaje", Category.COACCION_INTIMIDACION, Severity.MEDIO),
            SimulatedMessage("No vuelvas al grupo", Category.CIBERBULYING, Severity.MEDIO)
        )
    )

    private lateinit var root: View
    private lateinit var tabs: LinearLayout
    private lateinit var messages: LinearLayout
    private lateinit var status: TextView
    private val repository by lazy { FirestoreEventRepository() }
    private var selectedContact = conversations.keys.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_simulator)
        root = findViewById(R.id.chatSimulatorRoot)
        tabs = findViewById(R.id.contactTabs)
        messages = findViewById(R.id.chatMessages)
        status = findViewById(R.id.uploadStatus)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.uploadAllButton).setOnClickListener { uploadAllConversations() }

        conversations.keys.forEach { contact ->
            tabs.addView(createContactTab(contact))
        }
        renderConversation(selectedContact)
    }

    private fun createContactTab(contact: String): TextView = TextView(this).apply {
        text = contact
        textSize = 13f
        setTextColor(Color.parseColor("#4B388F"))
        setBackgroundColor(Color.WHITE)
        setPadding(18, 12, 18, 12)
        setOnClickListener {
            selectedContact = contact
            renderConversation(contact)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = 8
        layoutParams = params
    }

    private fun renderConversation(contact: String) {
        messages.removeAllViews()
        messages.addView(TextView(this).apply {
            text = "$contact · ${conversations.getValue(contact).size} mensajes de prueba"
            textSize = 14f
            setTextColor(Color.parseColor("#4B388F"))
            setPadding(0, 0, 0, 12)
        })
        conversations.getValue(contact).forEach { message ->
            val messageCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
                setBackgroundResource(
                    if (message.severity == Severity.ALTO) R.drawable.bg_chat_message_high
                    else R.drawable.bg_chat_message_medium
                )
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 8)
                layoutParams = params
            }
            val metadata = TextView(this).apply {
                text = "${categoryLabel(message.category)}  ·  ${severityLabel(message.severity)}"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(
                    if (message.severity == Severity.ALTO) Color.parseColor("#B0003A")
                    else Color.parseColor("#A66300")
                )
            }
            val text = TextView(this).apply {
                this.text = message.text
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#17152B"))
                setPadding(0, 6, 0, 0)
            }
            messageCard.addView(metadata)
            messageCard.addView(text)
            messages.addView(messageCard)
        }
    }

    private fun severityLabel(severity: Severity): String = when (severity) {
        Severity.ALTO -> "Riesgo alto"
        Severity.MEDIO -> "Riesgo medio"
        Severity.BAJO -> "Riesgo bajo"
    }

    private fun categoryLabel(category: Category): String = when (category) {
        Category.GROOMING -> "Grooming"
        Category.ACOSO_SEXUAL -> "Acoso sexual"
        Category.CIBERBULYING -> "Ciberbullying"
        Category.COACCION_INTIMIDACION -> "Coacción"
    }

    private fun uploadAllConversations() {
        val pending = conversations.values.sumOf { it.size }
        var completed = 0
        var failures = 0
        findViewById<Button>(R.id.uploadAllButton).isEnabled = false
        status.text = "Cargando $pending mensajes en las estadísticas..."

        conversations.forEach { (contact, messages) ->
            messages.forEach { message ->
                repository.saveEvent(
                    message = message.text,
                    analysis = AnalysisResult(message.category, message.severity, model = "simulator"),
                    contact = contact
                ) { result ->
                    completed++
                    if (result.isFailure) failures++
                    if (completed == pending) {
                        findViewById<Button>(R.id.uploadAllButton).isEnabled = true
                        if (failures == 0) {
                            status.text = "$pending mensajes cargados. Ya aparecen en Inicio y Contactos."
                            Snackbar.make(root, "Conversaciones agregadas a las estadísticas", Snackbar.LENGTH_LONG).show()
                        } else {
                            status.text = "$failures mensajes no pudieron cargarse."
                            Snackbar.make(root, "Algunos mensajes no se pudieron guardar", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
