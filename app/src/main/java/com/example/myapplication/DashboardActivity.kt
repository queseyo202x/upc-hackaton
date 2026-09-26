package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class DashboardActivity : AppCompatActivity() {

    private enum class EventFilter {
        ALL, HIGH_RISK, GROOMING, SEXUAL_HARASSMENT, CYBERBULLYING, COERCION, MEDIUM_RISK
    }

    private lateinit var root: View
    private lateinit var eventsContainer: LinearLayout
    private lateinit var statTodayCount: TextView
    private lateinit var statTodayDetail: TextView
    private lateinit var statRiskStatus: TextView

    private lateinit var chipAll: TextView
    private lateinit var chipHighRisk: TextView
    private lateinit var chipGrooming: TextView
    private lateinit var chipSexualContent: TextView
    private lateinit var chipCyberbullying: TextView
    private lateinit var chipCoercion: TextView
    private lateinit var chipMediumRisk: TextView

    private val repository by lazy { FirestoreEventRepository() }
    private var allEvents: List<EventRecord> = emptyList()
    private var activeFilter: EventFilter = EventFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        root = findViewById(R.id.dashboardRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            findViewById<View>(R.id.bottomNavigation).setPadding(
                0,
                8,
                0,
                8 + bars.bottom
            )
            insets
        }

        eventsContainer = findViewById(R.id.timelineContainer)
        statTodayCount = findViewById(R.id.statTodayCount)
        statTodayDetail = findViewById(R.id.statTodayDetail)
        statRiskStatus = findViewById(R.id.statRiskStatus)

        chipAll = findViewById(R.id.chipAll)
        chipHighRisk = findViewById(R.id.chipHighRisk)
        chipGrooming = findViewById(R.id.chipGrooming)
        chipSexualContent = findViewById(R.id.chipSexualContent)
        chipCyberbullying = findViewById(R.id.chipCyberbullying)
        chipCoercion = findViewById(R.id.chipCoercion)
        chipMediumRisk = findViewById(R.id.chipMediumRisk)

        setupFilterChips()

        findViewById<View>(R.id.roleHija).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
            finish()
        }
        findViewById<View>(R.id.reportsNavigation).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        findViewById<View>(R.id.contactNavigation).setOnClickListener {
            startActivity(Intent(this, ContactosActivity::class.java))
        }

        loadDashboard()
    }

    private fun setupFilterChips() {
        chipAll.setOnClickListener { setFilter(EventFilter.ALL) }
        chipHighRisk.setOnClickListener { setFilter(EventFilter.HIGH_RISK) }
        chipGrooming.setOnClickListener { setFilter(EventFilter.GROOMING) }
        chipSexualContent.setOnClickListener { setFilter(EventFilter.SEXUAL_HARASSMENT) }
        chipCyberbullying.setOnClickListener { setFilter(EventFilter.CYBERBULLYING) }
        chipCoercion.setOnClickListener { setFilter(EventFilter.COERCION) }
        chipMediumRisk.setOnClickListener { setFilter(EventFilter.MEDIUM_RISK) }
    }

    private fun setFilter(filter: EventFilter) {
        activeFilter = filter
        updateChipSelectionUI()
        renderEventsList()
    }

    private fun updateChipSelectionUI() {
        val chips = mapOf(
            EventFilter.ALL to chipAll,
            EventFilter.HIGH_RISK to chipHighRisk,
            EventFilter.GROOMING to chipGrooming,
            EventFilter.SEXUAL_HARASSMENT to chipSexualContent,
            EventFilter.CYBERBULLYING to chipCyberbullying,
            EventFilter.COERCION to chipCoercion,
            EventFilter.MEDIUM_RISK to chipMediumRisk
        )

        chips.forEach { (filter, view) ->
            if (filter == activeFilter) {
                view.setBackgroundColor(Color.parseColor("#311B92"))
                view.setTextColor(Color.WHITE)
            } else {
                view.setBackgroundColor(Color.WHITE)
                val textColor = when (filter) {
                    EventFilter.HIGH_RISK -> Color.parseColor("#991B1B")
                    EventFilter.GROOMING -> Color.parseColor("#C2410C")
                    EventFilter.SEXUAL_HARASSMENT -> Color.parseColor("#7C2D12")
                    EventFilter.CYBERBULLYING -> Color.parseColor("#1D4ED8")
                    EventFilter.COERCION -> Color.parseColor("#7E22CE")
                    EventFilter.MEDIUM_RISK -> Color.parseColor("#D97706")
                    else -> Color.parseColor("#1E293B")
                }
                view.setTextColor(textColor)
            }
        }
    }

    private fun loadDashboard() {
        repository.loadEvents { result ->
            result.fold(
                onSuccess = { events ->
                    allEvents = events
                    updateHeaderStats()
                    updateChipCounts()
                    renderEventsList()
                },
                onFailure = {
                    Snackbar.make(root, "Error al cargar eventos de Firestore: ${it.message}", Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun updateHeaderStats() {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayEvents = allEvents.filter { (it.hourMillis ?: 0L) >= todayStart }
        val criticalCount = todayEvents.count { it.severity == Severity.ALTO }
        val mediumCount = todayEvents.count { it.severity == Severity.MEDIO }

        statTodayCount.text = todayEvents.size.toString()
        statTodayDetail.text = "$criticalCount crítica · $mediumCount media"

        if (criticalCount > 0) {
            statRiskStatus.text = "Atención"
            statRiskStatus.setTextColor(Color.parseColor("#991B1B"))
        } else if (mediumCount > 0) {
            statRiskStatus.text = "Moderado"
            statRiskStatus.setTextColor(Color.parseColor("#D97706"))
        } else {
            statRiskStatus.text = "Sin riesgo"
            statRiskStatus.setTextColor(Color.parseColor("#166534"))
        }
    }

    private fun updateChipCounts() {
        val countAll = allEvents.size
        val countHigh = allEvents.count { it.severity == Severity.ALTO }
        val countGrooming = allEvents.count { it.category == Category.GROOMING }
        val countSexual = allEvents.count { it.category == Category.ACOSO_SEXUAL }
        val countCyberbullying = allEvents.count { it.category == Category.CIBERBULYING }
        val countCoercion = allEvents.count { it.category == Category.COACCION_INTIMIDACION }
        val countMedium = allEvents.count { it.severity == Severity.MEDIO }

        chipAll.text = "Todos ($countAll)"
        chipHighRisk.text = "🔴 Alto Riesgo ($countHigh)"
        chipGrooming.text = "⚠️ Grooming ($countGrooming)"
        chipSexualContent.text = "🚫 Acoso sexual ($countSexual)"
        chipCyberbullying.text = "💻 Ciberbulying ($countCyberbullying)"
        chipCoercion.text = "⚠️ Coacción ($countCoercion)"
        chipMediumRisk.text = "⚡ Riesgo Medio ($countMedium)"
    }

    private fun renderEventsList() {
        val filteredEvents = when (activeFilter) {
            EventFilter.ALL -> allEvents
            EventFilter.HIGH_RISK -> allEvents.filter { it.severity == Severity.ALTO }
            EventFilter.GROOMING -> allEvents.filter { it.category == Category.GROOMING }
            EventFilter.SEXUAL_HARASSMENT -> allEvents.filter { it.category == Category.ACOSO_SEXUAL }
            EventFilter.CYBERBULLYING -> allEvents.filter { it.category == Category.CIBERBULYING }
            EventFilter.COERCION -> allEvents.filter { it.category == Category.COACCION_INTIMIDACION }
            EventFilter.MEDIUM_RISK -> allEvents.filter { it.severity == Severity.MEDIO }
        }

        eventsContainer.removeAllViews()

        if (filteredEvents.isEmpty()) {
            addEmptyStateView()
        } else {
            val inflater = LayoutInflater.from(this)
            filteredEvents.forEach { event ->
                val cardView = createEventCardView(inflater, event)
                eventsContainer.addView(cardView)
            }
        }
    }

    private fun createEventCardView(inflater: LayoutInflater, event: EventRecord): View {
        val card = inflater.inflate(R.layout.item_event_card, eventsContainer, false)

        val riskBar = card.findViewById<View>(R.id.cardRiskBar)
        val contactHandle = card.findViewById<TextView>(R.id.contactHandle)
        val platformTag = card.findViewById<TextView>(R.id.platformTag)
        val categoryDesc = card.findViewById<TextView>(R.id.categoryDescription)
        val eventTime = card.findViewById<TextView>(R.id.eventTimeText)
        val severityBadge = card.findViewById<TextView>(R.id.severityBadge)
        val confidenceText = card.findViewById<TextView>(R.id.confidenceText)
        val messageQuote = card.findViewById<TextView>(R.id.messageQuoteText)
        val patternText = card.findViewById<TextView>(R.id.patternText)

        val btnGuide = card.findViewById<Button>(R.id.btnConversationGuide)

        val handleName = if (event.contact.isNotBlank()) event.contact else "desconocido"
        contactHandle.text = if (handleName.startsWith("@")) handleName else "@$handleName"

        platformTag.text = if (handleName.lowercase().contains("insta")) "INSTAGRAM DM" else "WHATSAPP"

        val formattedTime = event.hourMillis?.let {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
        } ?: "Reciente"
        eventTime.text = formattedTime

        when (event.severity) {
            Severity.ALTO -> {
                riskBar.setBackgroundColor(Color.parseColor("#D32F2F"))
                severityBadge.text = "✱ Alto Riesgo"
                severityBadge.setBackgroundColor(Color.parseColor("#D32F2F"))
            }
            Severity.MEDIO -> {
                riskBar.setBackgroundColor(Color.parseColor("#D97706"))
                severityBadge.text = "⚡ Riesgo Medio"
                severityBadge.setBackgroundColor(Color.parseColor("#D97706"))
            }
            Severity.BAJO -> {
                riskBar.setBackgroundColor(Color.parseColor("#EAB308"))
                severityBadge.text = "🟡 Riesgo Bajo"
                severityBadge.setBackgroundColor(Color.parseColor("#EAB308"))
            }
        }

        categoryDesc.text = when (event.category) {
            Category.GROOMING -> "Grooming"
            Category.ACOSO_SEXUAL -> "Acoso sexual"
            Category.CIBERBULYING -> "Ciberbulying"
            Category.COACCION_INTIMIDACION -> "Coacción/intimidación"
        }

        confidenceText.text = "Confianza IA: 98%"

        messageQuote.text = "“${event.message}”"

        patternText.text = when (event.category) {
            Category.GROOMING -> "Detección de patrones: secreto, aislamiento o manipulación."
            Category.ACOSO_SEXUAL -> "Detección de patrones: conducta o contenido sexual no deseado."
            Category.CIBERBULYING -> "Detección de patrones: humillación, insultos o persecución digital."
            Category.COACCION_INTIMIDACION -> "Detección de patrones: presión, chantaje o intimidación."
        }

        btnGuide.setOnClickListener {
            showConversationGuideDialog(handleName, event)
        }

        return card
    }

    private fun addEmptyStateView() {
        val emptyText = TextView(this).apply {
            text = "No se encontraron eventos detectados para este filtro."
            textSize = 14f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 32, 0, 32)
        }
        eventsContainer.addView(emptyText)
    }

    private fun showConversationGuideDialog(contact: String, event: EventRecord) {
        val message = """
            Recomendaciones para conversar sobre este evento detectado con $contact:
            
            1. Mantén la calma y aborda la charla en un momento de tranquilidad.
            2. Reafirma tu apoyo incondicional y recuérdale que no está en problemas.
            3. Explícale los riesgos del secreto y la presión en entornos digitales.
            4. Acuerden pautas de uso seguro en sus redes sociales.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("💬 Guía de conversación (Tutor)")
            .setMessage(message)
            .setPositiveButton("Cerrar") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
