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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val baseUrl = BuildConfig.GEMINI_BASE_URL.trim().removeSuffix("/")

    private val models = listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash",
        "gemini-3-flash-preview",
        "gemini-2.5-flash",
        "gemini-2.0-flash"
    )

    suspend fun generateQuestion(
        theme: String,
        round: Int,
        playerCount: Int,
        usedQuestions: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val personalLevel = when {
            round <= 1 -> "лёгкие, весёлые, неличные"
            round <= 3 -> "чуть более личные, но комфортные"
            round <= 5 -> "достаточно личные, заставляющие задуматься"
            else -> "очень личные, глубокие (чувства, отношения, мечты, страхи)"
        }

        val avoidBlock = if (usedQuestions.isNotEmpty()) {
            val last = usedQuestions.takeLast(12).joinToString("\n- ")
            "\nУже были такие вопросы — НЕ ПОВТОРЯЙ их и не перефразируй:\n- $last\n"
        } else ""

        val prompt = """
            Ты ведущий игры «Vergis». Придумай ОДИН новый вопрос.
            
            ОБЯЗАТЕЛЬНО:
            - Тема комнаты: «$theme». Вопрос ДОЛЖЕН быть напрямую про эту тему.
              Если тема «Для пар» — про отношения/партнёра. Если «Для друзей» — про дружбу.
              Если своя тема — строго в её рамках. Не уходи в общие вопросы «о жизни».
            - Уровень личных: $personalLevel (раунд $round)
            - Игроков: $playerCount
            - На русском, 1–2 предложения
            - Для всех игроков сразу
            $avoidBlock
            Верни ТОЛЬКО текст вопроса. Без кавычек, без нумерации, без пояснений.
        """.trimIndent()

        if (baseUrl.isNotBlank() || apiKey.isNotBlank()) {
            if (baseUrl.isNotBlank()) {
                for (model in models) {
                    call(baseUrl, model, prompt, injectKey = false)
                        ?.takeIf { isFresh(it, usedQuestions) }
                        ?.let { return@withContext it }
                }
            }
            if (apiKey.isNotBlank()) {
                for (model in models) {
                    call("https://generativelanguage.googleapis.com", model, prompt, injectKey = true)
                        ?.takeIf { isFresh(it, usedQuestions) }
                        ?.let { return@withContext it }
                }
            }
        }

        fallbackQuestion(theme, round, usedQuestions)
    }

    private fun isFresh(q: String, used: List<String>): Boolean {
        val norm = normalize(q)
        if (norm.length < 8) return false
        return used.none { normalize(it) == norm || similar(normalize(it), norm) }
    }

    private fun normalize(s: String) = s.lowercase()
        .replace(Regex("[«»\"'?.!,;:—\\-\\s]+"), " ")
        .trim()

    private fun similar(a: String, b: String): Boolean {
        if (a == b) return true
        val wa = a.split(" ").filter { it.length > 3 }.toSet()
        val wb = b.split(" ").filter { it.length > 3 }.toSet()
        if (wa.isEmpty() || wb.isEmpty()) return false
        val inter = wa.intersect(wb).size.toDouble()
        return inter / minOf(wa.size, wb.size) >= 0.7
    }

    private fun call(base: String, model: String, prompt: String, injectKey: Boolean): String? {
        return try {
            val url = "$base/v1beta/models/$model:generateContent"
            val bodyJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 1.0)
                    put("maxOutputTokens", 120)
                })
            }.toString()

            val builder = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
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

    private fun fallbackQuestion(theme: String, round: Int, used: List<String>): String {
        val pool = themePool(theme, round)
        val fresh = pool.filter { isFresh(it, used) }
        if (fresh.isNotEmpty()) return fresh.random()
        // если всё исчерпано — вариации с темой
        val base = pool.random()
        return "$base (тема: $theme)"
    }

    private fun themePool(theme: String, round: Int): List<String> {
        val t = theme.lowercase()
        val deep = round >= 4

        val couples = listOf(
            "Что тебя больше всего цепляет в партнёре прямо сейчас?",
            "Какой ваш самый тёплый совместный ритуал?",
            "О чём вы мечтаете вместе, но ещё не обсуждали вслух?",
            "Какой комплимент от партнёра ты запомнил навсегда?",
            "В чём вы идеально дополняете друг друга?",
            "Какой момент в отношениях изменил тебя сильнее всего?",
            "Чего тебе больше всего хочется от ваших отношений в ближайший год?",
            "За что ты благодарен партнёру прямо сейчас?",
            "Какая мелочь в партнёре тебя каждый раз умиляет?",
            "О каком совместном приключении ты мечтаешь?",
            "Что для тебя значит «настоящая близость» в паре?",
            "Какой ваш самый честный разговор за последнее время?"
        )
        val friends = listOf(
            "Какой поступок друга ты никогда не забудешь?",
            "С кем из друзей ты бы отправился в спонтанную поездку завтра?",
            "Что для тебя отличает настоящего друга от просто знакомого?",
            "Какую тайну ты доверил бы только лучшему другу?",
            "Кто из друзей лучше всех поднимает тебе настроение?",
            "За что ты больше всего ценишь свою компанию?",
            "Какой самый безумный план вы придумывали вместе?",
            "Кому из друзей ты обязан самым важным советом?",
            "Что бы ты хотел изменить в том, как вы общаетесь?",
            "Какая шутка внутри вашей компании всегда работает?",
            "С кем из присутствующих ты чувствуешь наибольшую близость?",
            "Какой совместный момент сделал вас ближе?"
        )
        val party = listOf(
            "Самый эпичный момент твоей жизни на вечеринке?",
            "С кем из присутствующих ты бы устроил импровизированный дуэт?",
            "Какой тост ты бы произнёс прямо сейчас?",
            "Самая смешная история с вечеринки — в двух предложениях.",
            "Кто здесь король/королева атмосферы и почему?",
            "Какой трек должен играть, когда ты входишь в комнату?",
            "Самый неожиданный человек, с которым ты познакомился на вечеринке?",
            "Что делает вечеринку по-настоящему удачной для тебя?",
            "Какой челлендж ты бы предложил компании прямо сейчас?",
            "Кого бы ты назначил ведущим следующей игры и почему?"
        )
        val family = listOf(
            "Какая семейная традиция тебе дороже всего?",
            "Чему тебя научили родители, что ты пронёс через всю жизнь?",
            "Какой семейный ужин ты вспоминаешь с теплом?",
            "С кем из семьи тебе легче всего говорить по душам?",
            "Какую семейную историю ты рассказываешь чаще всего?",
            "Что ты хотел бы сохранить из своей семьи для будущего?",
            "Какой совет от старших оказался самым ценным?",
            "За что ты благодарен своей семье прямо сейчас?"
        )
        val competitive = listOf(
            "В чём ты точно победишь любого из присутствующих?",
            "Какой проигрыш научил тебя больше, чем победа?",
            "Какая твоя скрытая суперсила в спорах или играх?",
            "С кем из компании соревноваться интереснее всего?",
            "Какой вызов ты готов принять прямо сейчас?",
            "Что для тебя важнее — победить или хорошо провести время?",
            "В какой дисциплине ты недооценён?",
            "Какой рекорд ты хочешь поставить в этой компании?"
        )
        val casual = listOf(
            "Какой самый странный факт о себе ты готов рассказать?",
            "Если бы день прошёл идеально — что бы в нём было?",
            "Какая мелочь сделала тебя счастливым на этой неделе?",
            "Что ты обычно делаешь, когда совсем скучно?",
            "Какой навык ты хотел бы освоить за месяц?",
            "Какая песня всегда поднимает тебе настроение?",
            "О чём ты недавно случайно задумался и не можешь отпустить?",
            "Какой фильм или сериал ты пересматриваешь, когда плохо?"
        )
        val deepQ = listOf(
            "О чём ты больше всего жалеешь — и почему?",
            "Чего ты боишься сильнее всего в ближайшие годы?",
            "Какой урок о себе дался тебе дороже всего?",
            "Что ты никогда никому не рассказывал, но готов сказать сейчас?",
            "Кого ты любишь больше всего и почему именно этого человека?",
            "Если бы завтра всё изменилось — что бы ты сделал сегодня?",
            "Какая мечта до сих пор не отпущена?",
            "Что для тебя значит «прожить жизнь не зря»?"
        )

        val base = when {
            t.contains("пар") || t.contains("любов") || t.contains("отношен") -> couples
            t.contains("друз") || t.contains("friend") -> friends
            t.contains("вечер") || t.contains("party") || t.contains("тусов") -> party
            t.contains("семь") || t.contains("family") -> family
            t.contains("конкур") || t.contains("соревн") || t.contains("compet") -> competitive
            t.contains("расслаб") || t.contains("casual") || t.contains("лёгк") -> casual
            t.contains("глубок") || t.contains("разговор") -> if (deep) deepQ else friends + casual
            else -> {
                // своя тема — вшиваем тему в шаблоны
                listOf(
                    "Что для тебя значит «$theme» в реальной жизни?",
                    "Какой момент, связанный с «$theme», запомнился сильнее всего?",
                    "С кем из присутствующих тебе интереснее всего обсуждать «$theme»?",
                    "Какой совет по теме «$theme» ты бы дал новичкам?",
                    "Что в «$theme» тебя бесит, а что вдохновляет?",
                    "Какой твой личный опыт лучше всего иллюстрирует «$theme»?",
                    "Если бы «$theme» была человеком — что бы ты ей сказал?",
                    "Какой вопрос про «$theme» ты бы задал всем в комнате?",
                    "Что ты понял про «$theme» только недавно?",
                    "Какая деталь в «$theme» обычно остаётся незамеченной?"
                ) + if (deep) deepQ.map { "$it (в контексте: $theme)" } else emptyList()
            }
        }

        return if (deep) (base + deepQ).distinct() else base
    }
}
