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
 * Генерация вопросов через Gemini (тот же способ, что и в рабочем Python-скрипте).
 *
 * - Модель: gemini-2.5-flash
 * - Авторизация: заголовок x-goog-api-key (как в google-genai SDK)
 * - С VPN на телефоне работает так же, как терминальный чат
 * - Без VPN / при ошибке — качественный fallback по теме и раунду
 * - Опционально: GEMINI_BASE_URL (Cloudflare Worker) для обхода геоблока без VPN
 */
class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val baseUrl = BuildConfig.GEMINI_BASE_URL.trim().removeSuffix("/")

    // Модели в порядке предпочтения (как в рабочем скрипте)
    // Актуальные модели (ноябрь 2026). 2.5-flash ещё работает, но скоро редирект.
    // Пробуем от новых к старым — первый успешный ответ идёт в игру.
    private val models = listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash",
        "gemini-3-flash-preview",
        "gemini-2.5-flash",
        "gemini-2.0-flash"
    )

    suspend fun generateQuestion(theme: String, round: Int, playerCount: Int): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() && baseUrl.isBlank()) {
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

            // 1) Через Cloudflare Worker (если задан)
            if (baseUrl.isNotBlank()) {
                for (model in models) {
                    call(baseUrl, model, prompt, injectKey = false)?.let { return@withContext it }
                }
            }

            // 2) Прямой вызов (как в Python google-genai: x-goog-api-key)
            if (apiKey.isNotBlank()) {
                for (model in models) {
                    call("https://generativelanguage.googleapis.com", model, prompt, injectKey = true)
                        ?.let { return@withContext it }
                }
            }

            fallbackQuestion(theme, round)
        }

    private fun call(base: String, model: String, prompt: String, injectKey: Boolean): String? {
        return try {
            val url = "$base/v1beta/models/$model:generateContent"
            val bodyJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.95)
                    put("maxOutputTokens", 120)
                })
            }.toString()

            val builder = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")

            // Тот же способ, что использует google-genai Python SDK
            if (injectKey && apiKey.isNotBlank()) {
                builder.header("x-goog-api-key", apiKey)
            }

            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (!response.isSuccessful) return null
                extractText(body)
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
