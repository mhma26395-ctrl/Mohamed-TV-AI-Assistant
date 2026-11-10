const SYSTEM_INSTRUCTION = `You are Mohamed TV AI Assistant, a careful assistant for Android TV and Google TV.
Understand Egyptian Arabic, Modern Standard Arabic, and English, including mixed Arabic-English device commands. Reply naturally in the user's language, preferably concise Egyptian Arabic when the user speaks Egyptian Arabic.
Use only the declared tools for device actions. A tool call is a proposal, not proof that the TV performed the action.
Never claim an action succeeded unless the device later confirms it.
If a request is unsupported or needs user approval, explain that clearly. Ask a short clarification when required.
Never invent installed apps, device capabilities, settings, or action results. Usage-history tools require the user to grant Usage Access in Android settings. Explain limitations and never claim the foreground app is exact on every TV. The user message may include a JSON list named INSTALLED_APPS_FROM_DEVICE. Use only exact packageName values from that list when calling launch_app or open_app_details. If an app is absent, say it is not in the device-provided launchable app list; do not guess a package.
Never request passwords, API keys, or other secrets from the user.
`;

const TOOLS = [{
  type: "function",
  function: {
    name: "list_installed_apps",
    description: "List apps that this TV exposes as launchable.",
    parameters: { type: "object", properties: {}, required: [], additionalProperties: false }
  }
}, {
  type: "function",
  function: {
    name: "launch_app",
    description: "Launch an installed app by its exact package name from the app list.",
    parameters: {
      type: "object",
      properties: {
        packageName: { type: "string", description: "Exact installed Android package name" },
        appName: { type: "string", description: "Human-readable app name, if known" }
      },
      required: ["packageName"],
      additionalProperties: false
    }
  }
}, {
  type: "function",
  function: {
    name: "adjust_volume",
    description: "Adjust media volume by a relative number of steps.",
    parameters: {
      type: "object",
      properties: {
        direction: { type: "string", enum: ["up", "down"] },
        steps: { type: "integer", minimum: 1, maximum: 5 }
      },
      required: ["direction"],
      additionalProperties: false
    }
  }
}, {
  type: "function",
  function: {
    name: "open_settings",
    description: "Open a supported Android TV settings page. This opens a page but does not itself change protected system settings.",
    parameters: {
      type: "object",
      properties: {
        page: { type: "string", enum: ["general", "wifi", "vpn", "language", "display", "screensaver", "developer"] }
      },
      required: ["page"],
      additionalProperties: false
    }
  }
}, {
  type: "function",
  function: {
    name: "get_storage_info",
    description: "Read internal storage totals, used space and available space.",
    parameters: { type: "object", properties: {}, required: [], additionalProperties: false }
  }
}, {
  type: "function",
  function: {
    name: "audit_apps",
    description: "Run a preliminary permission review of user-installed apps. This is not a malware scan.",
    parameters: { type: "object", properties: {}, required: [], additionalProperties: false }
  }
}, {
  type: "function",
  function: {
    name: "open_usage_access",
    description: "Open Android Usage Access settings so the user can grant access manually.",
    parameters: { type: "object", properties: {}, required: [], additionalProperties: false }
  }
}, {
  type: "function",
  function: {
    name: "open_app_details",
    description: "Open Android app details for a verified installed package.",
    parameters: {
      type: "object",
      properties: { packageName: { type: "string" } },
      required: ["packageName"],
      additionalProperties: false
    }
  }
}, {
  type: "function",
  function: {
    name: "open_app_management",
    description: "Open app management settings so the user can review apps and clear app cache manually.",
    parameters: { type: "object", properties: {}, required: [], additionalProperties: false }
  }
}, {
  type: "function",
  function: {
    name: "clear_own_cache",
    description: "Clear only this assistant app's own cache directory. Never clears other apps' data or cache.",
    parameters: { type: "object", properties: {}, required: [], additionalProperties: false }
  }
}, {
  type: "function",
  function: {
    name: "get_device_info",
    description: "Read non-sensitive device details such as Android version, manufacturer, and model.",
    parameters: { type: "object", properties: {}, required: [], additionalProperties: false }
  }
}  { type: "function", function: { name: "get_memory_info", description: "Read total, used and available RAM on this TV.", parameters: { type: "object", properties: {}, required: [], additionalProperties: false } } },
  { type: "function", function: { name: "check_app_installed", description: "Check whether an app name matches an installed launchable app reported by this device.", parameters: { type: "object", properties: { appName: { type: "string" } }, required: ["appName"], additionalProperties: false } } },
  { type: "function", function: { name: "get_last_used_app", description: "Get the most recently used app from Android Usage Access data.", parameters: { type: "object", properties: {}, required: [], additionalProperties: false } } },
  { type: "function", function: { name: "launch_last_used_app", description: "Launch the most recently used app if it is still installed. Requires Usage Access.", parameters: { type: "object", properties: {}, required: [], additionalProperties: false } } },
  { type: "function", function: { name: "get_foreground_app", description: "Estimate the most recent foreground app using Android usage events. Requires Usage Access.", parameters: { type: "object", properties: {}, required: [], additionalProperties: false } } }
];

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff"
    }
  });
}

function validRequest(body) {
  return body && typeof body.message === "string" &&
    body.message.trim().length > 0 && body.message.length <= 4000;
}

function safeToolCalls(message) {
  if (!Array.isArray(message?.tool_calls)) return [];
  const allowed = new Set(["list_installed_apps", "launch_app", "adjust_volume", "open_settings", "get_device_info", "get_storage_info", "audit_apps", "open_usage_access", "open_app_details", "open_app_management", "clear_own_cache", "get_memory_info", "check_app_installed", "get_last_used_app", "launch_last_used_app", "get_foreground_app"]);
  return message.tool_calls.slice(0, 5).flatMap((call) => {
    const name = call?.function?.name;
    if (!allowed.has(name) || typeof call?.function?.arguments !== "string") return [];
    try {
      const args = JSON.parse(call.function.arguments);
      if (!args || typeof args !== "object" || Array.isArray(args)) return [];
      return [{ name, args }];
    } catch {
      return [];
    }
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/v1/health") {
      return json({ ok: true, service: "mohamed-tv-ai", apiVersion: 1, provider: "groq" });
    }
    if (request.method !== "POST" || url.pathname !== "/v1/assistant") {
      return json({ error: "not_found" }, 404);
    }

    // This temporary gate is not suitable for a token embedded in a public APK.
    // Keep the endpoint private until per-device enrollment is implemented.
    const missingSecrets = [];
    if (!env.APP_ACCESS_TOKEN) missingSecrets.push("APP_ACCESS_TOKEN");
    if (!env.GROQ_API_KEY) missingSecrets.push("GROQ_API_KEY");
    if (missingSecrets.length) {
      // Names only: never return or log secret values.
      return json({ error: "server_not_configured", missing: missingSecrets }, 503);
    }
    const auth = request.headers.get("authorization") || "";
    if (auth !== `Bearer ${env.APP_ACCESS_TOKEN}`) {
      return json({ error: "unauthorized" }, 401);
    }

    let body;
    try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
    if (!validRequest(body)) return json({ error: "invalid_request", maxMessageLength: 4000 }, 400);

    if (env.RATE_LIMITER) {
      const key = request.headers.get("cf-connecting-ip") || "unknown";
      const decision = await env.RATE_LIMITER.limit({ key });
      if (!decision.success) return json({ error: "rate_limited" }, 429);
    }

    const model = env.GROQ_MODEL || "openai/gpt-oss-120b";
    let upstream;
    try {
      upstream = await fetch("https://api.groq.com/openai/v1/chat/completions", {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "authorization": `Bearer ${env.GROQ_API_KEY}`
        },
        body: JSON.stringify({
          model,
          messages: [
            { role: "system", content: SYSTEM_INSTRUCTION },
            { role: "user", content: body.message.trim() + (Array.isArray(body.installedApps) ? "\\n\\nINSTALLED_APPS_FROM_DEVICE (actual launchable apps on this TV; use exact packageName): " + JSON.stringify(body.installedApps.slice(0, 150)) : "") }
          ],
          tools: TOOLS,
          tool_choice: "auto",
          temperature: 0.6,
          reasoning_effort: "low",
          include_reasoning: false,
          parallel_tool_calls: false,
          max_completion_tokens: 900,
          stream: false
        })
      });
    } catch {
      return json({ error: "provider_unavailable" }, 502);
    }

    const raw = await upstream.json().catch(() => ({}));
    if (upstream.status === 429) {
      return json({ error: "provider_rate_limited", retryAfter: upstream.headers.get("retry-after") || null }, 429);
    }
    if (!upstream.ok) {
      return json({ error: "provider_error", providerStatus: upstream.status }, 502);
    }

    const message = raw.choices?.[0]?.message || {};
    const text = typeof message.content === "string" ? message.content.trim() : "";
    const toolCalls = safeToolCalls(message);
    return json({
      ok: true,
      provider: "groq",
      model,
      text,
      toolCalls,
      finishReason: raw.choices?.[0]?.finish_reason || null
    });
  }
};

// Deployment refresh: re-publish the Worker so saved Production secrets are attached to the active version.
