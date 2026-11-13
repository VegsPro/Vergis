# Vergis

Многопользовательская вечериночная игра с вопросами от Gemini AI.

## Как играть

1. Регистрация / вход по email  
2. Создай комнату: 2–5 игроков, тема (или своя), пароль по желанию  
3. Имя комнаты = твоё имя  
4. Когда все собрались — Gemini генерирует вопрос  
5. Каждый пишет ответ → после всех ответы открываются  
6. «Следующий раунд» → когда все готовы (N/N) — новый, более личный вопрос  

Все данные Firebase: путь `vergis/...`

---

## Gemini из РФ (обязательно)

Ключ `AQ....` **не работает**. Нужен обычный ключ `AIza...`.

### Шаги (5 минут)

1. Открой https://aistudio.google.com/apikey → **Create API key** → скопируй `AIza...`

2. Задеплой прокси (обход геоблока):
   - Cloudflare Dashboard → **Workers & Pages** → Create Worker
   - Вставь код из файла `proxy/worker.js`
   - Deploy
   - Settings → Variables and Secrets → **Add** → Secret name: `GEMINI_API_KEY`, value = твой `AIza...`
   - Сохрани URL вида `https://vergis-gemini.XXXX.workers.dev`

3. В GitHub репозитории Vergis → Settings → Secrets and variables → Actions:
   - `GEMINI_BASE_URL` = `https://vergis-gemini.XXXX.workers.dev`  
   - `GEMINI_API_KEY` можно оставить пустым (ключ только на Worker)

4. Actions → Run workflow (или push) → скачай новый APK

После этого Gemini будет вызываться через Cloudflare (IP не российский) и работать стабильно.

---

## Сборка

- applicationId: `com.vergis.vegs`
- Подпись: выделенный keystore (обновления без конфликта)
- SHA-1: `43:1A:E0:E4:E5:B5:76:50:C3:75:17:69:4D:44:06:BF:33:36:97:A9`
- SHA-256: `51:72:5F:45:12:76:40:86:4E:AD:0C:B2:C3:75:E9:71:80:BA:A9:75:64:2F:D6:B2:CB:2C:3F:C6:76:21:8B:8B`

### Firebase (один раз)

1. Console → Project `vix-hub-3caca` → Add Android app  
   Package: `com.vergis.vegs`  
   SHA-1: указан выше  
2. Authentication → Email/Password → Enable  
3. Realtime Database → Rules:
```json
{
  "rules": {
    "vergis": {
      ".read": "auth != null",
      ".write": "auth != null"
    }
  }
}
```

---

## Secrets для Actions

| Secret | Значение |
|--------|----------|
| KEYSTORE_BASE64 | (уже стоит) |
| KEYSTORE_PASSWORD | VergisSecure2026! |
| KEY_PASSWORD | VergisSecure2026! |
| GEMINI_BASE_URL | https://твой-worker.workers.dev |
| GEMINI_API_KEY | (опционально, если не используешь Worker) |
