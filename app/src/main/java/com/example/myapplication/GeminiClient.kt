package com.example.myapplication

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.util.regex.Pattern
import java.net.URL
import java.util.concurrent.Executors

class GeminiClient(private val apiKey: String) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestExecutor = Executors.newFixedThreadPool(3)

    fun analyze(
        message: String,
        onModelAttempt: (ModelAttempt) -> Unit,
        onResult: (Result<AnalysisResult>) -> Unit
    ) {
        requestExecutor.execute {
            val result = runCatching {
                analyzeBlocking(message) { attempt ->
                    mainHandler.post { onModelAttempt(attempt) }
                }
            }
            mainHandler.post { onResult(result) }
        }
    }

    private fun analyzeBlocking(
        message: String,
        onModelAttempt: (ModelAttempt) -> Unit
    ): AnalysisResult {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Configura geminiApiKey en local.properties para analizar mensajes.")
        }
        val prompt = """
            Clasifica este mensaje dirigido a una menor. Responde SOLO JSON válido:
            {"categoria":"grooming|acoso sexual|ciberbulying|coacción/intimidacion","severidad":"bajo|medio|alto"}
            Usa obligatoriamente una sola de estas categorías, sin crear variantes:
            grooming: secreto, aislamiento o manipulación;
            acoso sexual: insinuaciones, solicitudes o contenido sexual no deseado;
            ciberbulying: insultos, humillación, amenazas o persecución digital;
            coacción/intimidacion: presión, chantaje, control o intimidación.
            Usa obligatoriamente una sola severidad: bajo, medio o alto.
            Todo mensaje debe clasificarse en una de las cuatro categorías y una severidad.
            Mensaje recibido: ${JSONObject.quote(message)}
        """.trimIndent()
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject()
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject()
                .put("temperature", 0)
                .put("responseMimeType", "application/json"))
        val deadline = System.nanoTime() + TOTAL_TIMEOUT_MS * 1_000_000L
        val models = listOf(
            "gemini-2.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.6-flash"
        )
        var firstSuccessful: AnalysisResult? = null
        for (model in models) {
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000L
            if (remainingMs <= 0) {
                onModelAttempt(ModelAttempt(model, TOTAL_TIMEOUT_MS, error = "Tiempo total agotado"))
                continue
            }
            val startedAt = System.nanoTime()
            try {
                val analysis = request(body, model, remainingMs)
                val latency = (System.nanoTime() - startedAt) / 1_000_000L
                onModelAttempt(ModelAttempt(model, latency, 200, analysis = analysis))
                if (firstSuccessful == null) firstSuccessful = analysis
            } catch (error: IOException) {
                val latency = (System.nanoTime() - startedAt) / 1_000_000L
                val httpCode = (error as? GeminiHttpException)?.code
                onModelAttempt(ModelAttempt(model, latency, httpCode, error.message))
                Log.w(TAG, "Gemini request failed: ${error.message}")
            } catch (error: IllegalArgumentException) {
                val latency = (System.nanoTime() - startedAt) / 1_000_000L
                onModelAttempt(ModelAttempt(model, latency, 200, error.message))
            } catch (error: JSONException) {
                val latency = (System.nanoTime() - startedAt) / 1_000_000L
                onModelAttempt(ModelAttempt(model, latency, 200, "JSON inválido: ${error.message}"))
            }
        }
        return firstSuccessful ?: throw IOException(
            "Ningún modelo respondió correctamente. Revisa los cuadros de diagnóstico."
        )
    }

    private fun request(body: JSONObject, model: String, remainingMs: Long): AnalysisResult {
        val startedAt = System.nanoTime()
        val connection = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        ).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = minOf(CONNECT_TIMEOUT_MS, remainingMs.toInt())
            connection.readTimeout = minOf(READ_TIMEOUT_MS, remainingMs.toInt())
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                throw GeminiHttpException(
                    model,
                    responseCode,
                    responseBody,
                    responseCode == 429 || responseCode == 408 || responseCode >= 500
                )
            }
            val streamedChunks = responseBody.lineSequence()
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .filter { it.isNotEmpty() && it != "[DONE]" }
                .toList()
            val generatedText = extractStreamedText(streamedChunks)
                .replace("```json", "").replace("```", "").trim()
            return parseAnalysis(generatedText).copy(
                model = model,
                latencyMs = (System.nanoTime() - startedAt) / 1_000_000L,
                streamed = true
            )
        } catch (error: SocketTimeoutException) {
            throw GeminiHttpException(model, 408, "Tiempo de espera agotado", true)
        } catch (error: GeminiHttpException) {
            throw error
        } catch (error: IOException) {
            throw GeminiHttpException(model, 0, error.message ?: "Error de red", true)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private fun extractStreamedText(chunks: List<String>): String {
            return chunks.joinToString("") { chunk ->
                runCatching {
                    JSONObject(chunk).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).optString("text")
                }.getOrDefault("")
            }
        }

        private fun detectHighConfidenceRisk(message: String): AnalysisResult? {
            val normalized = message.lowercase()
                .replace(Regex("[áàäâ]"), "a")
                .replace(Regex("[éèëê]"), "e")
                .replace(Regex("[íìïî]"), "i")
                .replace(Regex("[óòöô]"), "o")
                .replace(Regex("[úùüû]"), "u")
                .trim()
            val asksForPhoto = normalized.contains("foto") ||
                normalized.contains("selfie") ||
                normalized.contains("imagen")
            if (!asksForPhoto) return null
            val asksForIntimatePhoto = normalized.contains("intim") ||
                normalized.contains("desnuda") ||
                normalized.contains("desnudo") ||
                normalized.contains("sin ropa")
            val requestsPhoto = normalized.contains("pasame") ||
                normalized.contains("mandame") ||
                normalized.contains("muestrame") ||
                normalized.contains("envia") ||
                normalized.contains("dame")
            if (!requestsPhoto) return null
            return if (asksForIntimatePhoto) {
                AnalysisResult(Category.ACOSO_SEXUAL, Severity.ALTO)
            } else {
                AnalysisResult(Category.GROOMING, Severity.MEDIO)
            }
        }

        fun parseAnalysis(json: String): AnalysisResult {
            fun field(name: String): String {
                val match = Pattern.compile("\"$name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                    .matcher(json)
                if (!match.find()) throw IllegalArgumentException("Campo $name ausente")
                return match.group(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
                    ?: throw IllegalArgumentException("Campo $name vacío")
            }
            val category = when (field("categoria").lowercase()) {
                "grooming" -> Category.GROOMING
                "acoso sexual" -> Category.ACOSO_SEXUAL
                "ciberbulying" -> Category.CIBERBULYING
                "coacción/intimidacion", "coaccion/intimidacion" -> Category.COACCION_INTIMIDACION
                else -> throw IllegalArgumentException("Categoría de Gemini no válida")
            }
            val severity = when (field("severidad").lowercase()) {
                "bajo" -> Severity.BAJO
                "medio" -> Severity.MEDIO
                "alto" -> Severity.ALTO
                else -> throw IllegalArgumentException("Severidad de Gemini no válida")
            }
            return AnalysisResult(category, severity)
        }

        private const val TAG = "GeminiClient"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val TOTAL_TIMEOUT_MS = 45_000L
        private const val RETRY_DELAY_MS = 250L

        private class GeminiHttpException(
            model: String,
            val code: Int,
            response: String,
            val isTransient: Boolean
        ) : IOException(
            if (code == 0) {
                "Error de red con Gemini ($model): $response"
            } else {
                "Gemini ($model) respondió HTTP $code: ${response.take(240)}"
            }
        )
    }
}
