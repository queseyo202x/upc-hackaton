package com.example.myapplication

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.launch

private val ReportBackground = Color(0xFFF9F7FF)
private val Ink = Color(0xFF101828)
private val MutedInk = Color(0xFF475467)
private val Purple = Color(0xFF49388F)
private val PurpleSoft = Color(0xFFEAE8FF)
private val Red = Color(0xFFC5161D)
private val CategoryColors = listOf(
    Color(0xFFC5161D), Color(0xFFB12650), Color(0xFF514092), Color(0xFF007A70)
)

class ReportsActivity : ComponentActivity() {
    private val repository by lazy { FirestoreEventRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReportsScreen(
                loadEvents = { onResult -> repository.loadEvents(onResult) },
                openDestination = { action, uri -> startActivity(Intent(action, Uri.parse(uri))) },
                goHome = {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReportsScreen(
    loadEvents: ((Result<List<EventRecord>>) -> Unit) -> Unit,
    openDestination: (String, String) -> Unit,
    goHome: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var report by remember { mutableStateOf<ReportData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadedEvents by remember { mutableStateOf<List<EventRecord>>(emptyList()) }
    var rangeStart by remember { mutableStateOf(defaultRangeStart()) }
    var rangeEnd by remember { mutableStateOf(endOfDay(System.currentTimeMillis())) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var pendingPdf by remember { mutableStateOf<ByteArray?>(null) }
    val createPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val pdf = pendingPdf
        pendingPdf = null
        if (uri == null || pdf == null) return@rememberLauncherForActivityResult
        try {
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("No se pudo abrir el archivo de destino")
            output.use { it.write(pdf) }
            scope.launch { snackbarHostState.showSnackbar("PDF exportado correctamente") }
        } catch (error: IOException) {
            scope.launch { snackbarHostState.showSnackbar("No se pudo guardar el PDF") }
        }
    }

    LaunchedEffect(Unit) {
        loadEvents { result ->
            result.onSuccess { events ->
                loadedEvents = events
                report = buildReport(events, rangeStart, rangeEnd)
                loading = false
            }.onFailure {
                loading = false
                scope.launch { snackbarHostState.showSnackbar("No se pudo cargar el resumen de eventos") }
            }
        }
    }

    Scaffold(
        containerColor = ReportBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavigation(selectedReports = true, onHome = goHome)
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Reportes", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                Button(
                    onClick = {
                        val data = report
                        if (data == null) {
                            scope.launch { snackbarHostState.showSnackbar("Espera a que cargue el informe") }
                        } else {
                            pendingPdf = createReportPdf(data, rangeStart, rangeEnd)
                            createPdfLauncher.launch("reporte_${formatFileDate(rangeStart)}_${formatFileDate(rangeEnd)}.pdf")
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple)
                ) { Text("Exportar PDF", fontSize = 13.sp) }
            }
            InfoCard(
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                onClick = { showDateRangePicker = true }
            )
            if (loading) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Purple)
                    }
                }
            } else {
                val data = report ?: ReportData(emptyList(), emptyList(), emptyMap())
                SectionTitle("Actividad reciente")
                SummaryCard(data, rangeStart, rangeEnd)
                SectionTitle("Evolución de alertas")
                TrendCard(data.daily)
                SectionTitle("Categorías de eventos")
                CategoryCard(data.categories)
            }
            SectionTitle("Protocolo de actuación inmediata")
            Text("Rutas oficiales de orientación y denuncia en Perú", color = MutedInk, fontSize = 14.sp)
            ProtocolCard("Línea 100", "Orientación, consejería y soporte emocional. Gratuita y disponible las 24 horas.", "Llamar al 100", Purple, Intent.ACTION_DIAL, "tel:100", openDestination)
            ProtocolCard("Centros Emergencia Mujer y Familia", "Atención legal, psicológica y social gratuita para personas afectadas por violencia.", "Ver información oficial", Color(0xFF007A70), Intent.ACTION_VIEW, "https://www.gob.pe/479-reportar-casos-de-violencia-contra-las-mujeres-e-integrantes-del-grupo-familiar", openDestination)
            ProtocolCard("Denuncia formal", "Conserva mensajes, capturas y fechas. Presenta la denuncia ante la Policía o Fiscalía.", "Radicar denuncia formal", Purple, Intent.ACTION_VIEW, "https://www.gob.pe/pnp", openDestination)
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showDateRangePicker) {
        val pickerState = androidx.compose.material3.rememberDateRangePickerState(
            initialSelectedStartDateMillis = pickerDateMillis(rangeStart),
            initialSelectedEndDateMillis = pickerDateMillis(rangeEnd)
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            rangeStart = startOfDayFromPickerMillis(start)
                            rangeEnd = endOfDayFromPickerMillis(end)
                            report = buildReport(loadedEvents, rangeStart, rangeEnd)
                            showDateRangePicker = false
                        }
                    },
                    enabled = pickerState.selectedStartDateMillis != null &&
                        pickerState.selectedEndDateMillis != null
                ) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancelar") }
            }
        ) {
            DateRangePicker(state = pickerState, showModeToggle = false)
        }
    }
}

private data class DailyPoint(val label: String, val count: Int)
private data class ReportData(val recent: List<EventRecord>, val daily: List<DailyPoint>, val categories: Map<Category, Int>)

private fun buildReport(events: List<EventRecord>, start: Long, end: Long): ReportData {
    val recent = events.filter { timestamp ->
        timestamp.hourMillis != null && timestamp.hourMillis in start..end
    }
    val firstDay = Calendar.getInstance().apply { timeInMillis = start }
    val lastDay = Calendar.getInstance().apply { timeInMillis = end }
    val dayCount = ((lastDay.timeInMillis - firstDay.timeInMillis) / MILLIS_PER_DAY).toInt() + 1
    val daily = (0 until dayCount).map { offset ->
        val day = Calendar.getInstance().apply {
            timeInMillis = firstDay.timeInMillis
            add(Calendar.DAY_OF_YEAR, offset)
        }
        DailyPoint(formatDay(day), recent.count { sameDay(it.hourMillis, day) })
    }
    return ReportData(recent, daily, Category.values().associateWith { category ->
        recent.count { it.category == category }
    })
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

private fun defaultRangeStart() = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, -6)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfDayFromPickerMillis(timestamp: Long): Long {
    val pickerDate = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = timestamp
    }
    return Calendar.getInstance().apply {
        set(pickerDate.get(Calendar.YEAR), pickerDate.get(Calendar.MONTH), pickerDate.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun endOfDayFromPickerMillis(timestamp: Long) =
    startOfDayFromPickerMillis(timestamp) + MILLIS_PER_DAY - 1

private fun pickerDateMillis(timestamp: Long): Long {
    val date = Calendar.getInstance().apply { timeInMillis = timestamp }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun endOfDay(timestamp: Long): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    return calendar.timeInMillis
}

private fun formatDay(day: Calendar) = SimpleDateFormat("dd/MM", Locale.getDefault()).format(day.time)

private fun sameDay(timestamp: Long?, day: Calendar): Boolean {
    if (timestamp == null) return false
    val eventDay = Calendar.getInstance().apply { timeInMillis = timestamp }
    return eventDay.get(Calendar.YEAR) == day.get(Calendar.YEAR) &&
        eventDay.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun Header() {
    Text("🛡️  Escudo Digital", color = Purple, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun InfoCard(rangeStart: Long, rangeEnd: Long, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PurpleSoft),
        onClick = onClick
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("▣", color = Purple, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Rango evaluado", color = Purple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${formatDate(rangeStart)} - ${formatDate(rangeEnd)}", color = MutedInk, fontSize = 14.sp)
            }
        }
    }
}

private fun formatDate(timestamp: Long) =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(java.util.Date(timestamp))

private fun formatFileDate(timestamp: Long) =
    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(java.util.Date(timestamp))

private fun createReportPdf(data: ReportData, rangeStart: Long, rangeEnd: Long): ByteArray {
    val document = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(16, 24, 40) }
    val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(73, 56, 143)
        typeface = Typeface.DEFAULT_BOLD
    }
    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
    var canvas = page.canvas
    var y = 52f
    fun nextPage() {
        document.finishPage(page)
        pageNumber += 1
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        canvas = page.canvas
        y = 52f
    }
    fun line(text: String, size: Float = 12f, paintToUse: Paint = paint) {
        if (y > 800f) nextPage()
        paintToUse.textSize = size
        canvas.drawText(text, 40f, y, paintToUse)
        y += size + 12f
    }

    line("Escudo Digital - Reporte de eventos", 20f, titlePaint)
    line("Rango evaluado: ${formatDate(rangeStart)} - ${formatDate(rangeEnd)}", 12f, mutedPaint)
    y += 10f
    line("Actividad reciente", 16f, titlePaint)
    line("Total de eventos: ${data.recent.size}")
    line("Máximo de alertas en un día: ${data.daily.maxOfOrNull { it.count } ?: 0}")
    y += 10f
    line("Evolución de alertas", 16f, titlePaint)
    data.daily.forEach { line("${it.label}: ${it.count} ${if (it.count == 1) "alerta" else "alertas"}") }
    y += 10f
    line("Incidentes por categoría", 16f, titlePaint)
    val total = data.categories.values.sum()
    data.categories.forEach { (category, count) ->
        val percentage = if (total == 0) 0 else (count * 100 / total)
        line("${categoryLabel(category)}: $count (${percentage}%)")
    }
    document.finishPage(page)
    val output = ByteArrayOutputStream()
    document.writeTo(output)
    document.close()
    return output.toByteArray()
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SummaryCard(data: ReportData, rangeStart: Long, rangeEnd: Long) {
    val busiest = data.daily.maxOfOrNull { it.count } ?: 0
    val range = "${formatDate(rangeStart)} - ${formatDate(rangeEnd)}"
    val text = if (data.recent.isEmpty()) "Durante el periodo $range no se registraron eventos."
    else "Durante el periodo $range se registraron ${data.recent.size} eventos, con un máximo de $busiest alertas en un día."
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(text, modifier = Modifier.padding(18.dp), color = MutedInk, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun TrendCard(values: List<DailyPoint>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            Text("Alertas registradas por día", color = MutedInk, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            TrendChart(values)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                values.forEach {
                    Text(it.label, color = MutedInk, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<DailyPoint>) {
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val max = (points.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
        val horizontal = size.width / (points.size - 1).coerceAtLeast(1)
        val chartPoints = points.mapIndexed { index, point ->
            Offset(index * horizontal, size.height - 16.dp.toPx() - (size.height - 32.dp.toPx()) * point.count / max)
        }
        repeat(3) { row ->
            val y = 20.dp.toPx() + row * (size.height - 40.dp.toPx()) / 2
            drawLine(Color(0xFFE4E7EC), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val path = Path().apply {
            chartPoints.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        }
        drawPath(path, Red, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        chartPoints.forEach { drawCircle(Red, 6.dp.toPx(), it); drawCircle(Color.White, 2.5.dp.toPx(), it) }
    }
}

@Composable
private fun CategoryCard(values: Map<Category, Int>) {
    val total = values.values.sum()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Incidentes por categoría", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Text("Total: $total", modifier = Modifier.background(PurpleSoft, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp), color = Purple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            values.entries.forEachIndexed { index, (category, count) ->
                val ratio = if (total == 0) 0f else count.toFloat() / total
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(CategoryColors[index], RoundedCornerShape(5.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(categoryLabel(category), Modifier.weight(1f), color = CategoryColors[index], fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${(ratio * 100).toInt()}% · $count ${if (count == 1) "evento" else "eventos"}", color = MutedInk, fontSize = 14.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(11.dp).background(Color(0xFFE9ECFA), RoundedCornerShape(8.dp))) {
                        Box(Modifier.fillMaxWidth(ratio).height(11.dp).background(CategoryColors[index], RoundedCornerShape(8.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolCard(title: String, description: String, action: String, color: Color, intentAction: String, uri: String, open: (String, String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(description, color = MutedInk, fontSize = 14.sp, lineHeight = 20.sp)
            Button(onClick = { open(intentAction, uri) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = color)) {
                Text(action, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BottomNavigation(selectedReports: Boolean, onHome: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
        NavigationItem("🛡️", "Inicio", false, onHome)
        NavigationItem("📊", "Reportes", selectedReports) {}
        NavigationItem("👥", "Contactos", false) {}
        NavigationItem("⚙️", "Ajustes", false) {}
    }
}

@Composable
private fun NavigationItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 17.sp)
            Text(label, color = if (selected) Purple else MutedInk, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

private fun categoryLabel(category: Category) = when (category) {
    Category.GROOMING -> "Grooming"
    Category.ACOSO_SEXUAL -> "Acoso sexual"
    Category.CIBERBULYING -> "Ciberbullying"
    Category.COACCION_INTIMIDACION -> "Coacción / intimidación"
}
