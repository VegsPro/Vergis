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

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey = BuildConfig.GEMINI_API_KEY
    // User requested flash 3.5 — use gemini-2.0-flash (or 2.5 if available)
    private val model = "gemini-2.0-flash"

    suspend fun generateQuestion(theme: String, round: Int, playerCount: Int): String = withContext(Dispatchers.IO) {
        val personalLevel = when {
            round <= 1 -> "лёгкие, весёлые, неличные вопросы для знакомства"
            round <= 3 -> "чуть более личные, но всё ещё комфортные вопросы"
            round <= 5 -> "достаточно личные вопросы, которые заставляют задуматься"
            else -> "очень личные, глубокие вопросы про чувства, отношения, мечты, страхи (в рамках темы)"
        }

        val prompt = """
            Ты ведущий вечериночной игры «Vergis». 
            Тема комнаты: "$theme".
            Сейчас раунд $round из игры (чем выше раунд — тем более личные вопросы).
            Количество игроков: $playerCount.
            
            Придумай ОДИН интересный вопрос на русском языке, который:
            - Подходит к теме "$theme"
            - Соответствует уровню: $personalLevel
            - Подходит для всех игроков сразу (не "ты vs другой")
            - Короткий (1-2 предложения максимум)
            - Провоцирует честные и интересные ответы
            
            Верни ТОЛЬКО текст вопроса, без кавычек, без нумерации, без пояснений.
        """.trimIndent()

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val bodyJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.9)
                    put("maxOutputTokens", 150)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext fallbackQuestion(theme, round)
                if (!response.isSuccessful) {
                    return@withContext fallbackQuestion(theme, round)
                }
                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text", "").trim()
                        if (text.isNotBlank()) return@withContext text
                    }
                }
                fallbackQuestion(theme, round)
            }
        } catch (e: Exception) {
            fallbackQuestion(theme, round)
        }
    }

    private fun fallbackQuestion(theme: String, round: Int): String {
        val easy = listOf(
            "Какой самый странный факт о себе ты готов рассказать?",
            "Если бы ты мог прожить один день чужой жизнью — чьей?",
            "Какая песня всегда поднимает тебе настроение?",
            "Что ты обычно делаешь, когда скучаешь?"
        )
        val medium = listOf(
            "О чём ты мечтал в детстве и сбылось ли это?",
            "Какой комплимент тебе запомнился больше всего?",
            "Есть ли у тебя привычка, которую ты хочешь изменить?",
            "Что для тебя значит настоящая дружба?"
        )
        val deep = listOf(
            "Кого ты любишь больше всего и почему?",
            "О чём ты больше всего жалеешь?",
            "Какой самый важный урок ты вынес из отношений?",
            "Чего ты боишься больше всего в жизни?",
            "Если бы завтра всё изменилось — что бы ты сделал сегодня?"
        )
        return when {
            round <= 2 -> easy.random()
            round <= 4 -> medium.random()
            else -> deep.random()
        } + " (тема: $theme)"
    }
}
