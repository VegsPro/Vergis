package com.vergis.vegs.ai

import com.vergis.vegs.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Генерация вопросов.
 *
 * Gemini из РФ часто блокируется даже с VPN.
 * Решения:
 * 1. Получи нормальный ключ AIza... на https://aistudio.google.com/apikey
 * 2. Положи его в GitHub Secret GEMINI_API_KEY и пересобери
 * 3. Если всё равно блок — используй proxy (см. PROXY_URL ниже) или
 *    игра продолжит работать на встроенных вопросах (fallback).
 */
class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val apiKey = BuildConfig.GEMINI_API_KEY

    // Если нужно обойти геоблок: подними Cloudflare Worker / Vercel / свой сервер
    // который принимает {prompt} и дергает Gemini, и пропиши URL сюда или в BuildConfig.
    // Пример Worker: https://github.com/ (простой proxy на 20 строк)
    private val proxyUrl: String? = null // например "https://your-worker.workers.dev/gemini"

    private val models = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-1.5-flash-latest"
    )

    suspend fun generateQuestion(theme: String, round: Int, playerCount: Int): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext fallbackQuestion(theme, round)
            }

            val personalLevel = when {
                round <= 1 -> "лёгкие, весёлые, неличные вопросы для знакомства"
                round <= 3 -> "чуть более личные, но всё ещё комфортные вопросы"
                round <= 5 -> "достаточно личные вопросы, которые заставляют задуматься"
                else -> "очень личные, глубокие вопросы про чувства, отношения, мечты, страхи (в рамках темы)"
            }

            val prompt = """
                Ты ведущий вечериночной игры «Vergis». 
                Тема комнаты: "$theme".
                Сейчас раунд $round (чем выше — тем личнее).
                Игроков: $playerCount.
                
                Придумай ОДИН короткий интересный вопрос на русском:
                - по теме "$theme"
                - уровень: $personalLevel
                - для всех сразу
                - 1-2 предложения максимум
                
                Верни ТОЛЬКО текст вопроса, без кавычек и пояснений.
            """.trimIndent()

            // 1) Пробуем proxy (если задан)
            proxyUrl?.let { proxy ->
                tryProxy(proxy, prompt)?.let { return@withContext it }
            }

            // 2) Прямой вызов Gemini (несколько моделей)
            for (model in models) {
                tryDirect(model, prompt)?.let { return@withContext it }
            }

            // 3) Всегда есть fallback — игра не ломается
            fallbackQuestion(theme, round)
        }

    private fun tryDirect(model: String, prompt: String): String? {
        return try {
            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val bodyJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.95)
                    put("maxOutputTokens", 120)
                })
            }
            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (!response.isSuccessful) return null
                extractText(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryProxy(proxy: String, prompt: String): String? {
        return try {
            val bodyJson = JSONObject().put("prompt", prompt).put("key", apiKey)
            val request = Request.Builder()
                .url(proxy)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (!response.isSuccessful) return null
                // ожидаем либо чистый текст, либо { "text": "..." }
                try {
                    val json = JSONObject(body)
                    json.optString("text").ifBlank { json.optString("question") }
                        .ifBlank { extractText(body) }
                } catch (_: Exception) {
                    body.trim().takeIf { it.length in 5..300 }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractText(body: String): String? {
        return try {
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null
            parts.getJSONObject(0).optString("text", "").trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackQuestion(theme: String, round: Int): String {
        val t = theme.lowercase()

        val easy = listOf(
            "Какой самый странный факт о себе ты готов рассказать прямо сейчас?",
            "Если бы ты мог прожить один день чужой жизнью — чьей и почему?",
            "Какая песня всегда поднимает тебе настроение?",
            "Что ты обычно делаешь, когда совсем скучно?",
            "Какой суперспособностью ты бы хотел обладать на один день?",
            "Самое глупое, что ты делал «ради прикола»?"
        )

        val medium = listOf(
            "О чём ты мечтал в детстве — и сбылось ли это?",
            "Какой комплимент тебе запомнился сильнее всего?",
            "Есть ли привычка, от которой ты хочешь избавиться?",
            "Что для тебя значит настоящая дружба?",
            "Какой момент за последние полгода ты считаешь самым важным?",
            "Кому из присутствующих ты бы доверил секрет?"
        )

        val deep = listOf(
            "Кого ты любишь больше всего и почему именно этого человека?",
            "О чём ты больше всего жалеешь?",
            "Какой самый важный урок ты вынес из отношений?",
            "Чего ты боишься больше всего в жизни?",
            "Если бы завтра всё изменилось — что бы ты сделал сегодня?",
            "Что ты никогда никому не рассказывал, но готов сказать сейчас?"
        )

        val couples = listOf(
            "Что тебя больше всего привлекает в партнёре?",
            "Какой ваш самый милый совместный ритуал?",
            "О чём вы мечтаете вместе?",
            "Что ты ценишь в ваших отношениях больше всего?"
        )

        val party = listOf(
            "Самый эпичный момент твоей жизни на вечеринке?",
            "С кем из присутствующих ты бы пошёл в приключение прямо сейчас?",
            "Какой тост ты бы произнёс прямо сейчас?",
            "Самая смешная история из твоей жизни — в двух предложениях."
        )

        val pool = when {
            t.contains("пар") || t.contains("любов") || t.contains("отношен") ->
                if (round <= 2) easy + couples else couples + deep
            t.contains("вечер") || t.contains("party") || t.contains("тусов") ->
                if (round <= 2) easy + party else party + medium
            round <= 2 -> easy
            round <= 4 -> medium
            else -> deep
        }

        return pool.random()
    }
}
