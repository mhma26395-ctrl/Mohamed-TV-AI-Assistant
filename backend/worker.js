const SYSTEM_INSTRUCTION = `You are Mohamed TV AI Assistant, a careful assistant for Android TV and Google TV.
Understand Egyptian Arabic, Modern Standard Arabic, and English, including mixed Arabic-English device commands. Reply naturally in the user's language, preferably concise Egyptian Arabic when the user speaks Egyptian Arabic.
Use only the declared tools for device actions. A tool call is a proposal, not proof that the TV performed the action.
Never claim an action succeeded unless the device later confirms it. Opening a settings page or launching a VPN app is not the same as changing brightness or connecting a VPN. For brightness and VPN requests, distinguish direct execution from a manual fallback; only report success after device-side confirmation.
If a request is unsupported or needs user approval, explain that clearly. Ask a short clarification when required.
Never invent installed apps, device capabilities, settings, or action results. Usage-history tools require Usage Access. The Android app may provide an explicit current permission status and a recent-usage report; trust that device-provided status. NEVER tell the user to enable Usage Access if the device says it is already enabled. If permission is enabled but no usage rows are returned, say the permission is enabled but the TV returned no usage data. Usage Access shows historical usage/events, NOT a live list of all running background processes and does NOT permit stopping other apps. Never claim the foreground app is exact on every TV. The user message may include a JSON list named INSTALLED_APPS_FROM_DEVICE. Use only exact packageName values from that list when calling launch_app or open_app_details. If an app is absent, say it is not in the device-provided launchable app list; do not guess a package.
For cache cleanup: the only currently available cleanup tool is clear_own_cache, which clears only this assistant app's private cache. Never claim or imply that you can clear cache for YouTube, Netflix, Downloader, or other apps. If asked to clear other apps' caches, clearly say this build cannot do that directly; Android restricts it for ordinary apps. Do not tell the user that the assistant has cleared anything unless the clear_own_cache tool actually runs. Do not confuse RAM cleanup with app-cache cleanup. Never request passwords, API keys, or other secrets from the user.
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
}, { type: "function", function: { name: "get_memory_info", description: "Read total, used and available RAM on this TV.", parameters: { type: "object", properties: {}, required: [], additionalProperties: false } } },
  { type: "function", function: { name: "check_app_installed", description: "Check whether an app name matches an installed launchable app reported by this device.", parameters: { type: "object", properties: { appName: { type: "string" } }, required: ["appName"], additionalProperties: false } } },
  { type: "function", function: { name: "get_last_used_app", description: "Get the most recently used app from Android Usage Access data.", parameters: { type: "object", properties: {}, required: [], additionalProperties: false } } },
  { type: "function", function: { name: "launch_last_used_app", description: "Launch the most recently used app if it is still installed. Requires Usage Access.", parameters: { type: "object", properties: {}, required: [], additionalProperties: false } } },
  { type: "function", function: { name: "get_foreground_app", description: "Estimate the most recent foreground app using Android usage events. Requires Usage Access.", parameters: { type: "object", properties: {}, required: [], additionalProperties: false } } }
];


const CITY_TIMEZONES = {
  "cairo": "Africa/Cairo", "القاهرة": "Africa/Cairo", "القاهره": "Africa/Cairo",
  "london": "Europe/London", "لندن": "Europe/London",
  "los angeles": "America/Los_Angeles", "لوس انجلوس": "America/Los_Angeles",
  "new york": "America/New_York", "نيو york": "America/New_York",
  "paris": "Europe/Paris", "باريس": "Europe/Paris",
  "riyadh": "Asia/Riyadh", "الرياض": "Asia/Riyadh",
  "dubai": "Asia/Dubai", "دبي": "Asia/Dubai",
  "mecca": "Asia/Riyadh", "مكه": "Asia/Riyadh", "مكة": "Asia/Riyadh",
  "tokyo": "Asia/Tokyo", "طوكيو": "Asia/Tokyo",
  "istanbul": "Europe/Istanbul", "اسطنبول": "Europe/Istanbul"
};

function cleanLocation(value) {
  return String(value || "").trim().replace(/[؟?!.،,]+$/g, "").trim();
}

function resolveTimezone(location) {
  const key = cleanLocation(location).toLowerCase();
  if (CITY_TIMEZONES[key]) return CITY_TIMEZONES[key];
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: key });
    return key;
  } catch {
    return null;
  }
}

async function getTimeInfo(location, locale) {
  const timezone = resolveTimezone(location);
  if (!timezone) return { ok: false, error: "unknown_city_or_timezone", location };
  const now = new Date();
  const formatted = new Intl.DateTimeFormat(locale || "ar-EG", {
    timeZone: timezone, hour: "numeric", minute: "2-digit", second: "2-digit",
    weekday: "long", year: "numeric", month: "long", day: "numeric",
    hour12: true, timeZoneName: "long"
  }).format(now);
  return { ok: true, type: "time", location, timezone, localDateTime: formatted, timestamp: now.toISOString(), source: "IANA time zone database via Intl" };
}

function weatherDescription(code, locale) {
  const ar = {
    0:"سماء صافية",1:"غالبًا صافٍ",2:"غائم جزئيًا",3:"غائم",45:"ضباب",48:"ضباب متجمد",
    51:"رذاذ خفيف",53:"رذاذ متوسط",55:"رذاذ كثيف",61:"مطر خفيف",63:"مطر متوسط",65:"مطر غزير",
    71:"ثلوج خفيفة",73:"ثلوج متوسطة",75:"ثلوج كثيفة",80:"زخات مطر خفيفة",81:"زخات مطر متوسطة",
    82:"زخات مطر شديدة",95:"عواصف رعدية",96:"عواصف رعدية مع برد",99:"عواصف رعدية شديدة مع برد"
  };
  const en = {
    0:"Clear sky",1:"Mainly clear",2:"Partly cloudy",3:"Overcast",45:"Fog",48:"Depositing rime fog",
    51:"Light drizzle",53:"Moderate drizzle",55:"Dense drizzle",61:"Slight rain",63:"Moderate rain",
    65:"Heavy rain",71:"Slight snow",73:"Moderate snow",75:"Heavy snow",80:"Slight rain showers",
    81:"Moderate rain showers",82:"Violent rain showers",95:"Thunderstorm",96:"Thunderstorm with hail",
    99:"Severe thunderstorm with hail"
  };
  return (String(locale || "").startsWith("ar") ? ar : en)[code] || (String(locale || "").startsWith("ar") ? "حالة جوية غير محددة" : "Unknown conditions");
}

async function getWeatherInfo(location, locale) {
  const city = cleanLocation(location);
  if (!city) return { ok: false, error: "missing_location" };
  const geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + encodeURIComponent(city) + "&count=1&language=" + (String(locale || "").startsWith("ar") ? "ar" : "en") + "&format=json";
  const geoResponse = await fetch(geoUrl, { signal: AbortSignal.timeout(12000) });
  if (!geoResponse.ok) throw new Error("weather_geocoding_unavailable");
  const geo = await geoResponse.json();
  const place = geo.results?.[0];
  if (!place) return { ok: false, error: "location_not_found", location: city };
  const params = new URLSearchParams({
    latitude: String(place.latitude), longitude: String(place.longitude),
    current: "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m",
    daily: "weather_code,temperature_2m_max,temperature_2m_min",
    timezone: "auto", forecast_days: "3"
  });
  const wxResponse = await fetch("https://api.open-meteo.com/v1/forecast?" + params.toString(), { signal: AbortSignal.timeout(15000) });
  if (!wxResponse.ok) throw new Error("weather_provider_unavailable");
  const wx = await wxResponse.json();
  const ar = String(locale || "").startsWith("ar");
  const daily = (wx.daily?.time || []).map((date, i) => ({
    date, condition: weatherDescription(wx.daily.weather_code?.[i], locale),
    highC: wx.daily.temperature_2m_max?.[i], lowC: wx.daily.temperature_2m_min?.[i]
  }));
  return {
    ok: true, type: "weather",
    location: [place.name, place.admin1, place.country].filter(Boolean).join(", "),
    timezone: wx.timezone,
    observedAt: wx.current?.time || null,
    current: {
      temperatureC: wx.current?.temperature_2m,
      feelsLikeC: wx.current?.apparent_temperature,
      humidityPercent: wx.current?.relative_humidity_2m,
      windSpeedKmh: wx.current?.wind_speed_10m,
      precipitationMm: wx.current?.precipitation,
      condition: weatherDescription(wx.current?.weather_code, locale)
    },
    forecast: daily,
    source: "Open-Meteo",
    sourceUrl: "https://open-meteo.com/"
  };
}


async function getCurrencyInfo(from, to) {
  const base = String(from || "USD").toUpperCase();
  const quote = String(to || "EGP").toUpperCase();
  if (!/^[A-Z]{3}$/.test(base) || !/^[A-Z]{3}$/.test(quote)) return { ok: false, error: "invalid_currency" };
  const response = await fetch("https://api.frankfurter.app/latest?from=" + base + "&to=" + quote, { signal: AbortSignal.timeout(12000) });
  if (!response.ok) throw new Error("exchange_rate_provider_unavailable");
  const data = await response.json();
  const rate = data.rates?.[quote];
  if (typeof rate !== "number") return { ok: false, error: "currency_pair_unavailable", from: base, to: quote };
  return { ok: true, type: "currency", from: base, to: quote, rate, date: data.date, source: "Frankfurter (reference exchange rates)", note: "Reference rate; bank, cash-market and card rates may differ." };
}

async function getGoldInfo(currency) {
  const response = await fetch("https://api.gold-api.com/price/XAU", { signal: AbortSignal.timeout(12000) });
  if (!response.ok) throw new Error("gold_price_provider_unavailable");
  const data = await response.json();
  const usdPerOunce = Number(data.price);
  if (!Number.isFinite(usdPerOunce) || usdPerOunce <= 0) throw new Error("invalid_gold_price");
  const code = String(currency || "USD").toUpperCase();
  let conversion = 1;
  let fxDate = new Date().toISOString().slice(0,10);
  if (code !== "USD") {
    const fx = await getCurrencyInfo("USD", code);
    if (!fx.ok) return fx;
    conversion = fx.rate; fxDate = fx.date;
  }
  const perGram24 = usdPerOunce * conversion / 31.1034768;
  const stamp = data.updatedAt || data.timestamp || null;
  return {
    ok: true, type: "gold", currency: code, unit: "gram",
    pricePerGram: {
      "24K": Number(perGram24.toFixed(2)),
      "21K": Number((perGram24 * 21 / 24).toFixed(2)),
      "18K": Number((perGram24 * 18 / 24).toFixed(2))
    },
    spotPerOunce: Number((usdPerOunce * conversion).toFixed(2)),
    updatedAt: stamp, fxDate, source: "Gold-API spot XAU + Frankfurter FX",
    note: "Indicative spot-derived estimate, not a local jeweler quote; excludes workmanship, taxes and retail premiums."
  };
}

async function getNewsInfo(query, locale) {
  const q = cleanLocation(query || "Egypt");
  const params = new URLSearchParams({
    query: q + " sourcelang:" + (String(locale || "").startsWith("ar") ? "arabic" : "english"),
    mode: "ArtList", format: "json", sort: "DateDesc", maxrecords: "8",
    timespan: "24h", fields: "title,sourceCountry,domain,url,seendate,language"
  });
  const response = await fetch("https://api.gdeltproject.org/api/v2/doc/doc?" + params.toString(), { signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error("news_provider_unavailable");
  const data = await response.json();
  const articles = (data.articles || []).slice(0,8).map(item => ({
    title: item.title || "", source: item.domain || item.sourceCountry || "Unknown",
    publishedAt: item.seendate || null, url: item.url || ""
  })).filter(item => item.title && item.url);
  return { ok: true, type: "news", query: q, articles, source: "GDELT Project", timespan: "24h" };
}

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


const b64u = (bytes) => {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
};
const unb64u = (value) => {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - value.length % 4) % 4);
  return Uint8Array.from(atob(padded), c => c.charCodeAt(0));
};
const utf8 = (value) => new TextEncoder().encode(value);
const randomToken = (length = 32) => {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return b64u(bytes);
};
async function verifyDeviceSignature(publicKeySpki, signatureDer, message) {
  try {
    const key = await crypto.subtle.importKey("spki", unb64u(publicKeySpki), { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    const der = unb64u(signatureDer);
    // Android SHA256withECDSA emits ASN.1 DER; WebCrypto ECDSA expects fixed-width r||s.
    if (der.length < 8 || der[0] !== 0x30) return false;
    let offset = 2;
    if (der[1] & 0x80) offset = 2 + (der[1] & 0x7f);
    if (der[offset++] !== 0x02) return false;
    const rLen = der[offset++]; let r = der.slice(offset, offset + rLen); offset += rLen;
    if (der[offset++] !== 0x02) return false;
    const sLen = der[offset++]; let ss = der.slice(offset, offset + sLen);
    while (r.length > 32 && r[0] === 0) r = r.slice(1);
    while (ss.length > 32 && ss[0] === 0) ss = ss.slice(1);
    if (r.length > 32 || ss.length > 32) return false;
    const raw = new Uint8Array(64); raw.set(r, 32-r.length); raw.set(ss, 64-ss.length);
    return await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key, raw, utf8(message));
  } catch { return false; }
}
async function signSession(payload, secret) {
  const key = await crypto.subtle.importKey("raw", utf8(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const head = b64u(utf8(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const body = b64u(utf8(JSON.stringify(payload)));
  const input = head + "." + body;
  const sig = await crypto.subtle.sign("HMAC", key, utf8(input));
  return input + "." + b64u(new Uint8Array(sig));
}
async function verifySession(token, secret, registry) {
  try {
    const parts = String(token || "").split(".");
    if (parts.length !== 3) return null;
    const key = await crypto.subtle.importKey("raw", utf8(secret), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
    if (!await crypto.subtle.verify("HMAC", key, unb64u(parts[2]), utf8(parts[0] + "." + parts[1]))) return null;
    const claims = JSON.parse(new TextDecoder().decode(unb64u(parts[1])));
    const now = Math.floor(Date.now()/1000);
    if (claims.iss !== "mohamed-tv-ai" || claims.aud !== "tv-app" || !claims.sub || !Number.isInteger(claims.exp) || claims.exp <= now || claims.exp > now + 901) return null;
    const record = await registry.get("device:" + claims.sub, "json");
    if (!record || record.status !== "active") return null;
    return claims;
  } catch { return null; }
}
async function rateLimit(binding, key) {
  if (!binding) return false;
  const result = await binding.limit({ key });
  return !!result.success;
}
async function authRoutes(request, env, url) {
  const registry = env.DEVICE_REGISTRY;
  const secret = env.SESSION_SIGNING_SECRET;
  if (!registry || !secret || secret.length < 32 || !env.ENROLL_LIMITER || !env.RATE_LIMITER) {
    return json({ error: "auth_service_unavailable" }, 503);
  }
  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  if (url.pathname === "/v1/auth/challenge") {
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!await rateLimit(env.ENROLL_LIMITER, "challenge:" + ip)) return json({ error: "rate_limited" }, 429);
    let body; try { body = await request.json(); } catch { return json({ error: "invalid_request" }, 400); }
    const id = String(body.installId || "");
    const purpose = String(body.purpose || "");
    if (!/^[0-9a-f-]{36}$/i.test(id) || !["enroll", "session"].includes(purpose)) return json({ error: "invalid_request" }, 400);
    if (purpose === "session") {
      const record = await registry.get("device:" + id, "json");
      if (!record || record.status !== "active") return json({ error: "authentication_failed" }, 401);
    }
    const nonce = randomToken(32);
    await registry.put("challenge:" + nonce, JSON.stringify({ installId: id, purpose, createdAt: Date.now() }), { expirationTtl: 120 });
    return json({ ok: true, nonce, expiresIn: 120 });
  }
  if (url.pathname === "/v1/auth/enroll") {
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!await rateLimit(env.ENROLL_LIMITER, "enroll:" + ip)) return json({ error: "rate_limited" }, 429);
    if (!await rateLimit(env.ENROLL_LIMITER, "global-enrollment")) return json({ error: "rate_limited" }, 429);
    let body; try { body = await request.json(); } catch { return json({ error: "invalid_request" }, 400); }
    const id = String(body.installId || ""), nonce = String(body.nonce || ""), pub = String(body.publicKey || ""), sig = String(body.signature || "");
    if (!/^[0-9a-f-]{36}$/i.test(id) || nonce.length < 30 || pub.length > 1000 || sig.length > 300) return json({ error: "invalid_request" }, 400);
    const challenge = await registry.get("challenge:" + nonce, "json");
    if (!challenge || challenge.installId !== id || challenge.purpose !== "enroll") return json({ error: "authentication_failed" }, 401);
    await registry.delete("challenge:" + nonce);
    if (!await verifyDeviceSignature(pub, sig, "enroll|" + id + "|" + nonce)) return json({ error: "authentication_failed" }, 401);
    const existing = await registry.get("device:" + id, "json");
    if (existing) return json({ error: "enrollment_not_available" }, 409);
    const device = { publicKey: pub, status: "active", createdAt: Date.now(), keyVersion: 1 };
    await registry.put("device:" + id, JSON.stringify(device));
    return json({ ok: true, enrolled: true });
  }
  if (url.pathname === "/v1/auth/session") {
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!await rateLimit(env.ENROLL_LIMITER, "session:" + ip)) return json({ error: "rate_limited" }, 429);
    let body; try { body = await request.json(); } catch { return json({ error: "invalid_request" }, 400); }
    const id = String(body.installId || ""), nonce = String(body.nonce || ""), sig = String(body.signature || "");
    const challenge = await registry.get("challenge:" + nonce, "json");
    const device = await registry.get("device:" + id, "json");
    if (!challenge || challenge.installId !== id || challenge.purpose !== "session" || !device || device.status !== "active") return json({ error: "authentication_failed" }, 401);
    await registry.delete("challenge:" + nonce);
    if (!await verifyDeviceSignature(device.publicKey, sig, "session|" + id + "|" + nonce)) return json({ error: "authentication_failed" }, 401);
    const now = Math.floor(Date.now()/1000);
    const token = await signSession({ iss: "mohamed-tv-ai", aud: "tv-app", sub: id, iat: now, exp: now + 600 }, secret);
    return json({ ok: true, token, expiresIn: 600 });
  }
  return json({ error: "not_found" }, 404);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/v1/health") {
      return json({ ok: true, service: "mohamed-tv-ai", apiVersion: 1, provider: "groq" });
    }
    if (url.pathname.startsWith("/v1/auth/")) return authRoutes(request, env, url);
    if (request.method !== "POST" || !["/v1/assistant", "/v1/info"].includes(url.pathname)) {
      return json({ error: "not_found" }, 404);
    }
    if (!env.DEVICE_REGISTRY || !env.SESSION_SIGNING_SECRET || env.SESSION_SIGNING_SECRET.length < 32 || !env.RATE_LIMITER || !env.DEVICE_RATE_LIMITER) {
      return json({ error: "server_not_configured" }, 503);
    }
    if (url.pathname === "/v1/assistant" && !env.GROQ_API_KEY) {
      return json({ error: "server_not_configured" }, 503);
    }
    const auth = request.headers.get("authorization") || "";
    const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
    const claims = await verifySession(token, env.SESSION_SIGNING_SECRET, env.DEVICE_REGISTRY);
    if (!claims) return json({ error: "authentication_failed" }, 401);
    if (!await rateLimit(env.DEVICE_RATE_LIMITER, "device:" + claims.sub)) return json({ error: "rate_limited" }, 429);
    if (!await rateLimit(env.RATE_LIMITER, "ip:" + (request.headers.get("cf-connecting-ip") || "unknown"))) return json({ error: "rate_limited" }, 429);

    let body;
    try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
    if (url.pathname === "/v1/assistant" && !validRequest(body)) return json({ error: "invalid_request", maxMessageLength: 4000 }, 400);

    if (url.pathname === "/v1/info") {
      if (env.RATE_LIMITER) {
        const key = request.headers.get("cf-connecting-ip") || "unknown";
        const decision = await env.RATE_LIMITER.limit({ key });
        if (!decision.success) return json({ error: "rate_limited" }, 429);
      }
      const infoBody = body;
      if (!infoBody || !["time", "weather", "currency", "gold", "news"].includes(infoBody.type) || (["time", "weather", "news"].includes(infoBody.type) && (!infoBody.location || typeof infoBody.location !== "string" || !infoBody.location.trim() || infoBody.location.length > 100))) {
        return json({ error: "invalid_info_request" }, 400);
      }
      try {
        const data = infoBody.type === "time"
          ? await getTimeInfo(infoBody.location, infoBody.locale)
          : infoBody.type === "weather"
            ? await getWeatherInfo(infoBody.location, infoBody.locale)
            : infoBody.type === "currency"
              ? await getCurrencyInfo(infoBody.from, infoBody.to)
              : infoBody.type === "gold"
                ? await getGoldInfo(infoBody.currency || "EGP")
                : await getNewsInfo(infoBody.location, infoBody.locale);
        return json(data, data.ok ? 200 : 404);
      } catch (error) {
        return json({ ok: false, error: "live_info_unavailable", detail: error?.message || "provider_error" }, 502);
      }
    }

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
        signal: AbortSignal.timeout(25000),
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
    } catch (error) {
      const timedOut = error?.name === "TimeoutError" || error?.name === "AbortError";
      return json({ error: timedOut ? "provider_timeout" : "provider_unavailable" }, timedOut ? 504 : 502);
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
