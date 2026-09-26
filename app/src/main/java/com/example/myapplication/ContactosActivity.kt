package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import java.util.Date

class ContactosActivity : AppCompatActivity() {
    private lateinit var root: View
    private lateinit var contactsContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var contactCount: TextView
    private val repository by lazy { FirestoreEventRepository() }
    private var events: List<EventRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_contactos)
        root = findViewById(R.id.contactsRoot)
        contactsContainer = findViewById(R.id.contactsContainer)
        searchInput = findViewById(R.id.contactSearchInput)
        contactCount = findViewById(R.id.contactCount)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            findViewById<View>(R.id.contactBottomNavigation).setPadding(0, 8, 0, 8 + bars.bottom)
            insets
        }
        searchInput.doAfterTextChanged { renderContacts(it?.toString().orEmpty()) }
        findViewById<View>(R.id.contactHomeNavigation).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.contactReportsNavigation).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        findViewById<View>(R.id.downloadHistoryButton).setOnClickListener {
            Snackbar.make(root, "Descarga del historial disponible próximamente (Mockup)", Snackbar.LENGTH_LONG).show()
        }
        loadContacts()
    }

    private fun loadContacts() {
        repository.loadEvents { result ->
            result.fold(
                onSuccess = {
                    events = it
                    renderContacts(searchInput.text.toString())
                },
                onFailure = {
                    Snackbar.make(root, "No se pudieron cargar los contactos: ${it.message}", Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun renderContacts(query: String) {
        val grouped = events
            .filter { it.contact.contains(query.trim(), ignoreCase = true) }
            .mapNotNull { event ->
                event.takeIf { it.severity == Severity.MEDIO || it.severity == Severity.ALTO }
            }
            .groupBy { it.contact.ifBlank { "Contacto sin nombre" } }
        contactsContainer.removeAllViews()
        contactCount.text = "${grouped.size} ${if (grouped.size == 1) "perfil" else "perfiles"} en observación"
        if (grouped.isEmpty()) {
            contactsContainer.addView(TextView(this).apply {
                text = "No hay perfiles que coincidan con la búsqueda."
                textSize = 14f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, 24, 0, 24)
            })
            return
        }
        val inflater = LayoutInflater.from(this)
        grouped.forEach { (contact, contactEvents) ->
            contactsContainer.addView(createContactCard(inflater, contact, contactEvents))
        }
    }

    private fun createContactCard(
        inflater: LayoutInflater,
        contact: String,
        contactEvents: List<EventRecord>
    ): View {
        val card = inflater.inflate(R.layout.item_contact_card, contactsContainer, false)
        val timelineEvents = contactEvents
            .sortedByDescending { it.hourMillis ?: 0L }
            .take(3)
        val latest = timelineEvents.first()
        card.findViewById<TextView>(R.id.contactHandle).text =
            if (contact.startsWith("@")) contact else "@$contact"
        card.findViewById<TextView>(R.id.contactSummary).text =
            "${contactEvents.size} ${if (contactEvents.size == 1) "evento registrado" else "eventos registrados"}"
        card.findViewById<TextView>(R.id.eventTimeText).text = latest.hourMillis?.let {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
        } ?: "Reciente"
        card.findViewById<TextView>(R.id.messageQuoteText).text = "“${latest.message}”"
        val timeline = card.findViewById<LinearLayout>(R.id.timelineContainer)
        timelineEvents.forEachIndexed { index, event ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.TOP
            }
            val marker = TextView(this).apply {
                text = if (index == 0) "●" else "○"
                textSize = 18f
                setTextColor(if (event.severity == Severity.ALTO) Color.parseColor("#C62828") else Color.parseColor("#D97706"))
                setPadding(0, 0, 10, 0)
            }
            val detail = TextView(this).apply {
                text = "${categoryLabel(event.category)} · ${event.message}"
                textSize = 13f
                setTextColor(Color.parseColor("#363247"))
                setBackgroundColor(if (event.severity == Severity.ALTO) Color.parseColor("#FCE8E8") else Color.parseColor("#FFF4E5"))
                setPadding(12, 10, 12, 10)
            }
            row.addView(marker)
            row.addView(detail, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                bottomMargin = 8
            })
            timeline.addView(row)
        }

        val (color, label) = when (latest.severity) {
            Severity.ALTO -> Color.parseColor("#C62828") to "Riesgo alto"
            Severity.MEDIO -> Color.parseColor("#D97706") to "Riesgo medio"
            Severity.BAJO -> Color.parseColor("#16826D") to "Riesgo bajo"
        }
        card.findViewById<View>(R.id.cardRiskBar).setBackgroundColor(color)
        card.findViewById<TextView>(R.id.severityBadge).apply {
            text = label
            setTextColor(color)
        }
        return card
    }

    private fun categoryLabel(category: Category): String = when (category) {
        Category.GROOMING -> "Grooming"
        Category.ACOSO_SEXUAL -> "Acoso sexual"
        Category.CIBERBULYING -> "Ciberbullying"
        Category.COACCION_INTIMIDACION -> "Coacción"
    }
}
