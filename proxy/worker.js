/**
 * Vergis Gemini Proxy — Cloudflare Worker
 *
 * 1. Создай Worker в Cloudflare Dashboard → Workers & Pages → Create
 * 2. Вставь этот код
 * 3. Settings → Variables → Add secret:
 *      GEMINI_API_KEY = твой ключ AIza... с https://aistudio.google.com/apikey
 * 4. Deploy
 * 5. Скопируй URL вида https://vergis-gemini.XXXX.workers.dev
 *    и вставь его в BuildConfig / secrets приложения
 *
 * Клиент вызывает:
 *   POST https://твой-worker.workers.dev/v1beta/models/gemini-2.0-flash:generateContent
 *   (ключ НЕ нужен в запросе — Worker сам подставляет)
 */

const UPSTREAM = "https://generativelanguage.googleapis.com";

export default {
  async fetch(request, env) {
    // CORS
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
        },
      });
    }

    const url = new URL(request.url);

    // Только Gemini-пути
    if (!url.pathname.startsWith("/v1beta/") && !url.pathname.startsWith("/v1/")) {
      return new Response(JSON.stringify({ error: "Use /v1beta/models/..." }), {
        status: 400,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
      });
    }

    const apiKey = env.GEMINI_API_KEY;
    if (!apiKey) {
      return new Response(JSON.stringify({ error: "GEMINI_API_KEY secret not set" }), {
        status: 500,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
      });
    }

    // Собираем upstream URL, подставляем ключ
    const upstreamUrl = new URL(UPSTREAM + url.pathname + url.search);
    upstreamUrl.searchParams.set("key", apiKey);

    const headers = new Headers(request.headers);
    headers.delete("host");
    headers.delete("authorization"); // ключ только из secret

    const init = {
      method: request.method,
      headers,
    };
    if (request.method !== "GET" && request.method !== "HEAD") {
      init.body = request.body;
    }

    try {
      const resp = await fetch(upstreamUrl.toString(), init);
      const newHeaders = new Headers(resp.headers);
      newHeaders.set("Access-Control-Allow-Origin", "*");
      return new Response(resp.body, {
        status: resp.status,
        statusText: resp.statusText,
        headers: newHeaders,
      });
    } catch (e) {
      return new Response(JSON.stringify({ error: String(e) }), {
        status: 502,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
      });
    }
  },
};
