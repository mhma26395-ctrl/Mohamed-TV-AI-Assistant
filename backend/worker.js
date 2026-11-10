const SYSTEM_INSTRUCTION = `You are Mohamed TV AI Assistant, a careful assistant for Android TV and Google TV.
Understand Egyptian Arabic, Modern Standard Arabic, and English. Reply in the user's language.
Use only the declared tools for device actions. Never claim an action succeeded unless the device later confirms it.
If a request is unsupported or needs user approval, explain that clearly. Ask a short clarification when required.
Never invent installed apps, device capabilities, settings, or action results.
`;

const TOOLS = [{
  functionDeclarations: [
    { name: "list_installed_apps", description: "List apps that this TV exposes as launchable.", parameters: { type: "OBJECT", properties: {}, required: [] } },
    { name: "launch_app", description: "Launch an installed app by its exact package name from the app list.", parameters: { type: "OBJECT", properties: { packageName: { type: "STRING" }, appName: { type: "STRING" } }, required: ["packageName"] } },
    { name: "adjust_volume", description: "Adjust media volume by a relative number of steps.", parameters: { type: "OBJECT", properties: { direction: { type: "STRING", enum: ["up", "down"] }, steps: { type: "INTEGER", minimum: 1, maximum: 5 } }, required: ["direction"] } },
    { name: "open_settings", description: "Open a supported Android TV settings page. This opens a page but does not itself change protected system settings.", parameters: { type: "OBJECT", properties: { page: { type: "STRING", enum: ["general", "wifi", "vpn", "language", "display", "screensaver", "developer"] } }, required: ["page"] } },
    { name: "get_device_info", description: "Read non-sensitive device details such as Android version, manufacturer, and model.", parameters: { type: "OBJECT", properties: {}, required: [] } }
  ]
}];

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });
}

function validRequest(body) {
  return body && typeof body.message === "string" &&
    body.message.trim().length > 0 && body.message.length <= 4000;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/v1/health") {
      return json({ ok: true, service: "mohamed-tv-ai", apiVersion: 1 });
    }
    if (request.method !== "POST" || url.pathname !== "/v1/assistant") {
      return json({ error: "not_found" }, 404);
    }

    // Do not deploy publicly until device enrollment/authentication is configured.
    if (!env.APP_ACCESS_TOKEN || !env.GEMINI_API_KEY) {
      return json({ error: "server_not_configured" }, 503);
    }
    const auth = request.headers.get("authorization") || "";
    if (auth !== `Bearer ${env.APP_ACCESS_TOKEN}`) {
      return json({ error: "unauthorized" }, 401);
    }

    let body;
    try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
    if (!validRequest(body)) return json({ error: "invalid_request", maxMessageLength: 4000 }, 400);

    // Cloudflare Rate Limiting binding; configure it in the deployed Worker.
    if (env.RATE_LIMITER) {
      const key = request.headers.get("cf-connecting-ip") || "unknown";
      const decision = await env.RATE_LIMITER.limit({ key });
      if (!decision.success) return json({ error: "rate_limited" }, 429);
    }

    const model = env.GEMINI_MODEL || "gemini-2.5-flash";
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
    const payload = {
      systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
      contents: [{ role: "user", parts: [{ text: body.message.trim() }] }],
      tools: TOOLS,
      toolConfig: { functionCallingConfig: { mode: "AUTO" } },
      generationConfig: { temperature: 0.2, maxOutputTokens: 700 }
    };

    let upstream;
    try {
      upstream = await fetch(endpoint, {
        method: "POST",
        headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
        body: JSON.stringify(payload)
      });
    } catch {
      return json({ error: "provider_unavailable" }, 502);
    }
    const raw = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      // Avoid returning provider diagnostics that may expose internal details.
      return json({ error: "provider_error", providerStatus: upstream.status }, 502);
    }

    const parts = raw.candidates?.[0]?.content?.parts || [];
    const text = parts.filter(p => typeof p.text === "string").map(p => p.text).join("\n").trim();
    const toolCalls = parts.filter(p => p.functionCall?.name).map(p => ({
      name: p.functionCall.name,
      args: p.functionCall.args || {}
    }));
    return json({
      ok: true,
      provider: "gemini",
      model,
      text,
      toolCalls,
      finishReason: raw.candidates?.[0]?.finishReason || null
    });
  }
};
