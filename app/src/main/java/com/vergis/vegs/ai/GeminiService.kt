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
 * Почему раньше «ломалась» тема:
 * 1) Gemini часто недоступен (гео) → включался fallback.
 * 2) Для неизвестных тем fallback брал deepQ и клеил «(в контексте: тема)» —
 *    на экране получалось «Кого ты любишь… (в контексте: Смешные вопросы)».
 * 3) Тема «Смешные вопросы» не имела своего пула.
 *
 * Сейчас: отдельный пул на каждую тему, валидация ответа Gemini, без суффиксов.
 */
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
        val n = playerCount.coerceAtLeast(1)
        val audience = when {
            n <= 2 -> (
                "В комнате РОВНО $n игрока (только двое). " +
                "Обращайся на «ты» или «вы двое». " +
                "СТРОГО ЗАПРЕЩЕНО: формулировки для компании 3+, " +
                "«все», «каждый из вас», «кто-нибудь из присутствующих», «кто из компании», " +
                "«поднимите руки», обращение к группе."
            )
            else -> "В комнате $n игроков. Можно обращаться ко всем присутствующим."
        }

        val themeRules = themeRules(theme)
        val avoidN = if (round > 15) 40 else 20
        val avoidBlock = if (usedQuestions.isNotEmpty()) {
            val last = usedQuestions.takeLast(avoidN).joinToString("\n- ")
            "\nУже были — НЕ повторяй и не перефразируй:\n- $last\n"
        } else ""

        val prompt = """
            Придумай ОДИН короткий вопрос на русском для игры Vergis.

            ТЕМА КОМНАТЫ: «$theme»
            $themeRules

            $audience
            Раунд: $round (чем выше — тем можно чуть личнее, но ТОЛЬКО в рамках темы «$theme»).

            Сначала мысленно проверь: вопрос строго по теме «$theme» и не повторяет / не перефразирует ни один вопрос из списка уже заданных (если список не пуст). Проверку в ответ не выводи.

            ЗАПРЕЩЕНО:
            - уходить в другую тему
            - повторять или перефразировать уже заданные вопросы
            - писать «(тема: …)», «(в контексте: …)»
            - нумерацию, кавычки, пояснения
            - шаблоны не по теме (например «кого любишь» при теме «Смешные вопросы»)
            - вопросы, рассчитанные на компанию из 3+ человек, если в комнате только 2
            $avoidBlock
            СТРОГОЕ НАПОМИНАНИЕ: вопрос должен быть на русском, коротким, в 1 предложение, строго по теме «$theme» и семантически не похож на уже заданные.

            Ответ: только текст вопроса, 1 предложение.
        """.trimIndent()

        if (baseUrl.isNotBlank() || apiKey.isNotBlank()) {
            if (baseUrl.isNotBlank()) {
                for (model in models) {
                    call(baseUrl, model, prompt, false)
                        ?.let { clean(it) }
                        ?.takeIf { isFresh(it, usedQuestions) && matchesTheme(it, theme) }
                        ?.let { return@withContext it }
                }
            }
            if (apiKey.isNotBlank()) {
                for (model in models) {
                    call("https://generativelanguage.googleapis.com", model, prompt, true)
                        ?.let { clean(it) }
                        ?.takeIf { isFresh(it, usedQuestions) && matchesTheme(it, theme) }
                        ?.let { return@withContext it }
                }
            }
        }

        fallbackQuestion(theme, round, n, usedQuestions)
    }

    private fun themeRules(theme: String): String {
        val t = theme.lowercase()
        return when {
            t.contains("смеш") || t.contains("юмор") || t.contains("прикол") ->
                "Тема = ЮМОР. Только смешные, абсурдные, лёгкие вопросы. Без философии, любви и страхов."
            t.contains("пар") || t.contains("любов") || t.contains("отношен") ->
                "Тема = ПАРА / отношения. Только про партнёра, чувства, быт вдвоём."
            t.contains("друз") ->
                "Тема = ДРУЖБА. Только про друзей, компанию, совместные истории."
            t.contains("вечер") || t.contains("party") || t.contains("тусов") ->
                "Тема = ВЕЧЕРИНКА. Только про тусовки, танцы, тосты, приколы на вечеринке."
            t.contains("семь") ->
                "Тема = СЕМЬЯ. Только про родственников, традиции, детство в семье."
            t.contains("конкур") || t.contains("соревн") ->
                "Тема = СОРЕВНОВАНИЕ. Только про победу, проигрыш, рекорды, кто круче."
            t.contains("расслаб") || t.contains("лёгк") ->
                "Тема = РЕЛАКС. Лёгкие спокойные вопросы без давления."
            t.contains("глубок") ->
                "Тема = ГЛУБОКИЙ РАЗГОВОР. Можно личные, но осмысленные вопросы."
            else ->
                "Тема = «$theme». Каждый вопрос обязан явно опираться на эту тему словами или смыслом. Не уходи в общие темы."
        }
    }

    private fun matchesTheme(q: String, theme: String): Boolean {
        val t = theme.lowercase()
        val ql = q.lowercase()
        // reject meta suffixes
        if (ql.contains("в контексте") || ql.contains("тема:")) return false
        // funny theme: reject heavy romance/fear templates
        if (t.contains("смеш") || t.contains("юмор")) {
            val banned = listOf(
                "любишь больше всего", "жалеешь", "боишься", "прожить жизнь не зря",
                "никогда никому не рассказывал", "смысл жизни"
            )
            if (banned.any { it in ql }) return false
        }
        return true
    }

    private fun clean(q: String): String {
        return q.trim()
            .trim('"', '«', '»', '\'')
            .replace(Regex("""\s*\((тема|в контексте)[^)]*\)\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
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
        return wa.intersect(wb).size.toDouble() / minOf(wa.size, wb.size) >= 0.7
    }

    private fun call(base: String, model: String, prompt: String, injectKey: Boolean): String? {
        return try {
            val url = "$base/v1beta/models/$model:generateContent"
            val bodyJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 1.05)
                    put("maxOutputTokens", 100)
                })
            }.toString()
            val builder = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
            if (injectKey && apiKey.isNotBlank()) builder.header("x-goog-api-key", apiKey)
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

    private fun fallbackQuestion(theme: String, round: Int, playerCount: Int, used: List<String>): String {
        val pool = themePool(theme, round, playerCount)
        val fresh = pool.filter { isFresh(it, used) && matchesTheme(it, theme) }
        if (fresh.isNotEmpty()) return fresh.random()
        // last resort: mutate so never 1:1 repeat
        val base = pool[(round + used.size) % pool.size].trimEnd('?', '!')
        val twists = listOf(
            "и почему", "без шуток", "если честно", "прямо сейчас",
            "в двух словах", "с примером", "как в кино"
        )
        var candidate = "$base — ${twists[(round + used.size) % twists.size]}?"
        var i = 0
        while (!isFresh(candidate, used) && i < twists.size) {
            candidate = "$base — ${twists[(round + used.size + i) % twists.size]}?"
            i++
        }
        return candidate
    }

    private fun themePool(theme: String, round: Int, playerCount: Int): List<String> {
        val t = theme.lowercase()
        val duo = playerCount <= 2

        val funny = listOf(
            "Какой самый глупый факт о себе ты готов признать?",
            "Если бы твоя жизнь была мемом — каким?",
            "Какую суперспособность ты бы взял только ради троллинга друзей?",
            "Что ты делаешь, когда притворяешься занятым?",
            "Какой странный скилл у тебя есть, но стыдно им хвастаться?",
            "Если бы еда умела говорить — какое блюдо тебя бы сдало?",
            "Какой самый нелепый поиск ты вбивал в Google?",
            "На какой глупости ты попался, хотя «точно знал»?",
            "Какой звук бесит тебя сильнее всего?",
            "Если бы ты был NPC в игре — какую фразу повторял бы?",
            "Какой фильм ты защищаешь, хотя все говорят, что он плохой?",
            "Что бы написали на твоей кружке на работе?",
            "Какая привычка выдаёт тебя с головой?",
            "Если бы ты озвучивал мультфильм — кого?",
            "Какой самый странный комплимент тебе делали?",
            "Что ты бы запретил в своей стране на один день ради прикола?",
            "Какая песня стыдно нравится, но нравится?",
            "Если бы подушка умела рассказывать секреты — что бы слила?",
            "Какой жест ты делаешь автоматически и не замечаешь?",
            "Что из детства до сих пор кажется тебе смешным?"
        )

        val couples = listOf(
            "Что тебя больше всего цепляет в партнёре прямо сейчас?",
            "Какой ваш самый тёплый совместный ритуал?",
            "Какой комплимент от партнёра ты запомнил навсегда?",
            "В чём вы идеально дополняете друг друга?",
            "За что ты благодарен партнёру прямо сейчас?",
            "Какая мелочь в партнёре тебя каждый раз умиляет?",
            "О каком совместном приключении ты мечтаешь?",
            "Как вы обычно миритесь после ссоры?",
            "Какой день из вашей истории хочется прожить заново?",
            "Что бы ты хотел(а) услышать от партнёра сегодня вечером?",
            if (duo) "Что тебе нравится в том, как вы общаетесь вдвоём?" else "Что тебе нравится в динамике вашей пары?",
            "Какой жест партнёра говорит «я рядом» без слов?"
        )

        val friends = listOf(
            "Какой поступок друга ты никогда не забудешь?",
            "С кем из друзей ты бы уехал в спонтанную поездку завтра?",
            "Что отличает настоящего друга от просто знакомого?",
            "Какую тайну ты доверил бы только лучшему другу?",
            "Кто из друзей лучше всех поднимает настроение?",
            "Какой самый безумный план вы придумывали вместе?",
            "Какая шутка внутри вашей компании всегда работает?",
            if (duo) "Что тебе больше всего нравится в общении с этим человеком?" else "С кем из присутствующих ты чувствуешь наибольшую близость?",
            "Какой совместный момент сделал вас ближе?",
            "Кому из друзей ты обязан самым важным советом?"
        )

        val party = listOf(
            "Самый эпичный момент твоей жизни на вечеринке?",
            "Какой тост ты бы произнёс прямо сейчас?",
            "Какой трек должен играть, когда ты входишь?",
            "Что делает вечеринку удачной лично для тебя?",
            "Самая смешная история с вечеринки — в двух предложениях.",
            "Какой челлендж ты бы предложил компании?",
            "С кем из присутствующих устроил бы импровизированный дуэт?",
            "Кого назначил бы ведущим следующей игры и почему?"
        )

        val family = listOf(
            "Какая семейная традиция тебе дороже всего?",
            "Чему тебя научили родители на всю жизнь?",
            "Какой семейный ужин вспоминаешь с теплом?",
            "С кем из семьи легче всего по душам?",
            "Какую семейную историю рассказываешь чаще всего?",
            "Какой совет от старших оказался самым ценным?"
        )

        val competitive = listOf(
            "В чём ты точно победишь любого из присутствующих?",
            "Какой проигрыш научил тебя больше, чем победа?",
            "Какая твоя скрытая суперсила в играх?",
            "С кем соревноваться интереснее всего?",
            "Какой вызов ты готов принять прямо сейчас?",
            "В какой дисциплине ты недооценён?",
            "Что важнее — победить или хорошо провести время?",
            "Какой рекорд хочешь поставить в этой компании?"
        )

        val casual = listOf(
            "Какая мелочь сделала тебя счастливым на этой неделе?",
            "Что ты делаешь, когда совсем скучно?",
            "Какой навык хочешь освоить за месяц?",
            "Какая песня всегда поднимает настроение?",
            "Какой фильм пересматриваешь, когда плохо?",
            "Если бы день прошёл идеально — что в нём было бы?"
        )

        val deep = listOf(
            "О чём ты больше всего жалеешь — и почему?",
            "Чего боишься сильнее всего в ближайшие годы?",
            "Какой урок о себе дался дороже всего?",
            "Какая мечта до сих пор не отпущена?",
            "Что для тебя значит «прожить жизнь не зря»?",
            "Если бы завтра всё изменилось — что сделал бы сегодня?"
        )

        return when {
            t.contains("смеш") || t.contains("юмор") || t.contains("прикол") -> funny
            t.contains("пар") || t.contains("любов") || t.contains("отношен") -> couples
            t.contains("друз") -> friends
            t.contains("вечер") || t.contains("party") || t.contains("тусов") -> party
            t.contains("семь") -> family
            t.contains("конкур") || t.contains("соревн") -> competitive
            t.contains("расслаб") || t.contains("лёгк") -> casual
            t.contains("глубок") -> if (round >= 4) deep else casual + deep.take(3)
            else -> listOf(
                "Что для тебя лично значит «$theme»?",
                "Какой момент, связанный с «$theme», запомнился сильнее всего?",
                "Какой совет по теме «$theme» дал бы новичкам?",
                "Что в «$theme» тебя бесит, а что вдохновляет?",
                "Какой твой опыт лучше всего иллюстрирует «$theme»?",
                "Что ты понял про «$theme» только недавно?",
                "Какая деталь в «$theme» обычно остаётся незамеченной?",
                if (duo) "О чём вам двоим интереснее всего говорить в рамках «$theme»?"
                else "С кем из присутствующих интереснее обсуждать «$theme»?"
            )
        }
    }
}
