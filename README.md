# Vergis

Многопользовательская вечериночная игра с вопросами от Gemini AI.

## Как играть

1. Регистрация / вход по email
2. Создай комнату: выбери 2–5 игроков, тему (или свою), при желании пароль
3. Имя комнаты = твоё имя
4. Другие игроки видят комнату в списке и подключаются
5. Когда все собрались — Gemini генерирует вопрос 1-го раунда
6. Каждый пишет ответ локально → после всех ответы открываются
7. «Следующий раунд» → когда все готовы, новый (более личный) вопрос

Все данные Firebase: путь `vergis/...`

## Сборка

- applicationId: `com.vergis.vegs`
- Подпись: выделенный keystore (обновления поверх старых APK без конфликта)
- SHA-1: `43:1A:E0:E4:E5:B5:76:50:C3:75:17:69:4D:44:06:BF:33:36:97:A9`
- SHA-256: `51:72:5F:45:12:76:40:86:4E:AD:0C:B2:C3:75:E9:71:80:BA:A9:75:64:2F:D6:B2:CB:2C:3F:C6:76:21:8B:8B`

### Важно перед первым запуском

1. В Firebase Console (проект `vix-hub-3caca`):
   - Добавь Android-приложение с package `com.vergis.vegs`
   - Добавь SHA-1 выше в настройки приложения
   - Скачай `google-services.json` и замени файл в `app/`
   - Включи **Authentication → Email/Password**
   - Включи **Realtime Database** и поставь правила (для теста):
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

2. Secrets в GitHub Actions:
   - `KEYSTORE_BASE64`
   - `KEYSTORE_PASSWORD` = `VergisSecure2026!`
   - `KEY_PASSWORD` = `VergisSecure2026!`
   - `GEMINI_API_KEY`

## Gemini

Используется `gemini-2.0-flash`. Ключ передаётся через BuildConfig / secret.
