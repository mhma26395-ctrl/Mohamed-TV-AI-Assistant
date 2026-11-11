const SYSTEM_INSTRUCTION = `You are Mohamed TV AI Assistant, a careful assistant for Android TV and Google TV.
Understand Egyptian Arabic, Modern Standard Arabic, and English, including mixed Arabic-English device commands. Reply naturally in the user's language, preferably concise Egyptian Arabic when the user speaks Egyptian Arabic.
Use only the declared tools for device actions. A tool call is a proposal, not proof that the TV performed the action.
Never claim an action succeeded unless the device later confirms it. Opening a settings page or launching a VPN app is not the same as changing brightness or connecting a VPN. For brightness and VPN requests, distinguish direct execution from a manual fallback; only report success after device-side confirmation.
If a request is unsupported or needs user approval, explain that clearly. Ask a short clarification when required.
Never invent installed apps, device capabilities, settings, or action results. The app-permission audit checks user-installed apps that Android exposes as launchable through package visibility; it may not include apps without a launcher entry or packages hidden by Android. Usage-history tools require Usage Access. The Android app may provide an explicit current permission status and a recent-usage report; trust that device-provided status. NEVER tell the user to enable Usage Access if the device says it is already enabled. If permission is enabled but no usage rows are returned, say the permission is enabled but the TV returned no usage data. Usage Access shows historical usage/events, NOT a live list of all running background processes and does NOT permit stopping other apps. Never claim the foreground app is exact on every TV. The user message may include a JSON list named INSTALLED_APPS_FROM_DEVICE. Use only exact packageName values from that list when calling launch_app or open_app_details. If an app is absent, say it is not in the device-provided launchable app list; do not guess a package.
For cache cleanup: the only currently available cleanup tool is clear_own_cache, which clears only this assistant app's private cache. Never claim or imply that you can clear cache for YouTube, Netflix, Downloader, or other apps. If asked to clear other apps' caches, clearly say this build cannot do that directly; Android restricts it for ordinary apps. Do not tell the user that the assistant has cleared anything unless the clear_own_cache tool actually runs. Do not confuse RAM cleanup with app-cache cleanup. Never request passwords, API keys, or other secrets from the user.
For entertainment questions, you can recommend films, TV series and programs, and anime across Egyptian, Arabic, international, and Japanese animation categories. Ask about genre, language, country, release year, age suitability, or mood only when needed; otherwise give a short useful shortlist with title, year (when known), genre, spoiler-free premise, and why it may fit. Distinguish films from series and anime, and do not assume the user wants a streaming app opened.
Be honest about freshness: unless a live entertainment catalog result is explicitly supplied to you, do not claim a title is currently trending, newly released, available in Egypt, included in a subscription, or watchable on a specific platform. Do not invent ratings, release dates, availability, or content advisories. If asked what is currently available or where to watch, explain that this build does not yet have a connected live catalog and ask for the platform/service or offer general recommendations without claiming availability. Avoid spoilers unless the user asks for them. For family suitability, mention that age ratings and content vary by country and version; do not label a title universally child-safe without reliable evidence.
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
    description: "Run a preliminary permission review of user-installed apps that Android exposes as launchable. This is not a malware scan and may not include non-launchable or hidden packages.",
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

  // Prefer the current Frankfurter API hostname. Keep the legacy hostname as
  // a fallback for transient routing or DNS failures.
  const endpoints = [
    "https://api.frankfurter.dev/v1/latest?base=" + base + "&symbols=" + quote,
    "https://api.frankfurter.app/latest?from=" + base + "&to=" + quote
  ];
  let lastError = null;
  for (const endpoint of endpoints) {
    try {
      const response = await fetch(endpoint, { signal: AbortSignal.timeout(12000) });
      if (!response.ok) throw new Error("exchange_rate_http_" + response.status);
      const data = await response.json();
      const rate = data.rates?.[quote];
      if (typeof rate !== "number" || !Number.isFinite(rate)) {
        throw new Error("exchange_rate_missing_pair");
      }
      return {
        ok: true, type: "currency", from: base, to: quote, rate,
        date: data.date, source: "Frankfurter (reference exchange rates)",
        note: "Reference rate; bank, cash-market and card rates may differ."
      };
    } catch (error) {
      lastError = error;
    }
  }
  throw new Error("exchange_rate_providers_unavailable: " + (lastError?.message || "provider_error"));
}

export async function getGoldInfo(currency, fetcher = fetch) {
  const code = String(currency || "USD").toUpperCase();
  if (!/^[A-Z]{3}$/.test(code)) return { ok: false, error: "invalid_currency" };

  // Primary provider: Gold-API spot price, converted with Frankfurter FX.
  try {
    const response = await fetcher("https://api.gold-api.com/price/XAU", { signal: AbortSignal.timeout(12000) });
    if (!response.ok) throw new Error("gold_price_provider_unavailable");
    const data = await response.json();
    const usdPerOunce = Number(data.price);
    if (!Number.isFinite(usdPerOunce) || usdPerOunce <= 0) throw new Error("invalid_gold_price");
    let conversion = 1;
    let fxDate = new Date().toISOString().slice(0,10);
    if (code !== "USD") {
      const fx = await getCurrencyInfo("USD", code);
      if (!fx.ok) throw new Error("gold_fx_provider_unavailable");
      conversion = fx.rate;
      fxDate = fx.date;
    }
    const perGram24 = usdPerOunce * conversion / 31.1034768;
    return {
      ok: true, type: "gold", currency: code, unit: "gram",
      pricePerGram: {
        "24K": Number(perGram24.toFixed(2)),
        "21K": Number((perGram24 * 21 / 24).toFixed(2)),
        "18K": Number((perGram24 * 18 / 24).toFixed(2))
      },
      spotPerOunce: Number((usdPerOunce * conversion).toFixed(2)),
      updatedAt: data.updatedAt || data.timestamp || null,
      fxDate, source: "Gold-API spot XAU + Frankfurter FX",
      stale: false,
      note: "Indicative spot-derived estimate, not a local jeweler quote; excludes workmanship, taxes and retail premiums."
    };
  } catch (primaryError) {
    // Independent fallback provides gold already converted to the requested
    // currency and gram unit, so it does not depend on the FX provider.
    try {
      const fallbackUrl = "https://xaus.com/api/v1/spot?currency=" + encodeURIComponent(code) + "&unit=gram&compact=1";
      const response = await fetcher(fallbackUrl, { signal: AbortSignal.timeout(12000) });
      if (!response.ok) throw new Error("gold_fallback_provider_unavailable");
      const data = await response.json();
      const perGram24 = Number(data?.xau?.price);
      if (!Number.isFinite(perGram24) || perGram24 <= 0) throw new Error("invalid_fallback_gold_price");
      const state = data?.data_state?.status || (data.stale ? "stale" : "fresh");
      if (state === "unavailable") throw new Error("gold_price_unavailable");
      const ageSeconds = Number(data?.data_state?.age_seconds);
      if (state === "stale" && Number.isFinite(ageSeconds) && ageSeconds > 21600) {
        throw new Error("gold_price_too_old");
      }
      return {
        ok: true, type: "gold", currency: code, unit: "gram",
        pricePerGram: {
          "24K": Number(perGram24.toFixed(2)),
          "21K": Number((perGram24 * 21 / 24).toFixed(2)),
          "18K": Number((perGram24 * 18 / 24).toFixed(2))
        },
        spotPerOunce: Number((perGram24 * 31.1034768).toFixed(2)),
        updatedAt: data.price_as_of || data.updated_at || null,
        fxDate: null, source: "XAUS spot gold + currency conversion",
        stale: state === "stale" || data.fx_stale === true,
        note: "Indicative spot-derived estimate, not a local jeweler quote; excludes workmanship, taxes and retail premiums."
      };
    } catch (fallbackError) {
      throw new Error("gold_providers_unavailable");
    }
  }
}

const SPORTS_LEAGUES = {
  "egyptian-premier-league": { code: "egy.1", name: "الدوري المصري" },
  "saudi-pro-league": { code: "sau.1", name: "الدوري السعودي" },
  "english-premier-league": { code: "eng.1", name: "الدوري الإنجليزي الممتاز" },
  "spanish-la-liga": { code: "esp.1", name: "الدوري الإسباني" }
};

async function getSportsInfo(leagueKey, dateValue, locale) {
  const league = SPORTS_LEAGUES[leagueKey];
  if (!league) return { ok: false, error: "unsupported_league" };
  const date = /^\\d{8}$/.test(String(dateValue || "")) ? String(dateValue) :
    new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Cairo", year: "numeric", month: "2-digit", day: "2-digit" })
      .format(new Date()).replace(/-/g, "");
  const url = "https://site.api.espn.com/apis/site/v2/sports/soccer/" +
    league.code + "/scoreboard?dates=" + date;
  const response = await fetch(url, { signal: AbortSignal.timeout(12000), headers: { "accept": "application/json" } });
  if (!response.ok) throw new Error("sports_provider_unavailable");
  const data = await response.json();
  const events = Array.isArray(data.events) ? data.events : [];
  const matches = events.map(event => {
    const competition = event.competitions?.[0] || {};
    const competitors = Array.isArray(competition.competitors) ? competition.competitors : [];
    const home = competitors.find(x => x.homeAway === "home") || competitors[0] || {};
    const away = competitors.find(x => x.homeAway === "away") || competitors[1] || {};
    const status = competition.status || event.status || {};
    const venue = competition.venue || {};
    return {
      id: String(event.id || ""),
      dateTime: event.date || null,
      homeTeam: home.team?.displayName || home.team?.name || "غير معروف",
      awayTeam: away.team?.displayName || away.team?.name || "غير معروف",
      homeScore: home.score ?? null,
      awayScore: away.score ?? null,
      status: status.type?.detail || status.type?.description || "لم تبدأ",
      state: status.type?.state || "pre",
      venue: venue.fullName || null
    };
  }).filter(x => x.homeTeam !== "غير معروف" && x.awayTeam !== "غير معروف");
  return {
    ok: true, type: "sports", league: league.name, leagueCode: league.code,
    date, matches, count: matches.length,
    source: "ESPN public scoreboard endpoint",
    note: "Unofficial public feed; coverage may vary by competition. Empty results do not prove that no match exists."
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
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]+$/.test(value) || value.length % 4 === 1) {
    throw new Error("invalid_base64url");
  }
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - value.length % 4) % 4);
  return Uint8Array.from(atob(padded), c => c.charCodeAt(0));
};
const isObject = value => value !== null && typeof value === "object" && !Array.isArray(value);
const validInstallId = value => typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
const validNonce = value => typeof value === "string" && /^[A-Za-z0-9_-]{43}$/.test(value);
const utf8 = (value) => new TextEncoder().encode(value);
const randomToken = (length = 32) => {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return b64u(bytes);
};
export async function verifyDeviceSignature(publicKeySpki, signatureDer, message) {
  try {
    if (typeof publicKeySpki !== "string" || publicKeySpki.length < 80 || publicKeySpki.length > 1000 ||
        typeof signatureDer !== "string" || signatureDer.length < 80 || signatureDer.length > 300) return false;
    const key = await crypto.subtle.importKey(
      "spki", unb64u(publicKeySpki),
      { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]
    );
    const der = unb64u(signatureDer);
    // P-256 ECDSA signatures are short-form DER sequences containing two INTEGERs.
    if (der.length < 8 || der[0] !== 0x30 || der[1] !== der.length - 2) return false;
    let offset = 2;
    if (der[offset++] !== 0x02) return false;
    const rLen = der[offset++];
    if (!rLen || rLen > 33 || offset + rLen + 2 > der.length) return false;
    let r = der.slice(offset, offset + rLen); offset += rLen;
    if (der[offset++] !== 0x02) return false;
    const sLen = der[offset++];
    if (!sLen || sLen > 33 || offset + sLen !== der.length) return false;
    let ss = der.slice(offset, offset + sLen);
    // Require canonical positive DER INTEGER encodings (no negative or redundant leading zero).
    if ((r[0] & 0x80) || (ss[0] & 0x80)) return false;
    if (r.length > 1 && r[0] === 0 && !(r[1] & 0x80)) return false;
    if (ss.length > 1 && ss[0] === 0 && !(ss[1] & 0x80)) return false;
    if (r.length === 33 && r[0] !== 0) return false;
    if (ss.length === 33 && ss[0] !== 0) return false;
    while (r.length > 32 && r[0] === 0) r = r.slice(1);
    while (ss.length > 32 && ss[0] === 0) ss = ss.slice(1);
    if (!r.length || !ss.length || r.length > 32 || ss.length > 32 ||
        r.every(byte => byte === 0) || ss.every(byte => byte === 0)) return false;
    const raw = new Uint8Array(64);
    raw.set(r, 32 - r.length);
    raw.set(ss, 64 - ss.length);
    return await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key, raw, utf8(message));
  } catch { return false; }
}
export async function signSession(payload, secret) {
  const key = await crypto.subtle.importKey("raw", utf8(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const head = b64u(utf8(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const body = b64u(utf8(JSON.stringify(payload)));
  const input = head + "." + body;
  const sig = await crypto.subtle.sign("HMAC", key, utf8(input));
  return input + "." + b64u(new Uint8Array(sig));
}
export async function verifySession(token, secret, env) {
  try {
    const parts = String(token || "").split(".");
    if (parts.length !== 3) return null;
    const key = await crypto.subtle.importKey("raw", utf8(secret), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
    if (!await crypto.subtle.verify("HMAC", key, unb64u(parts[2]), utf8(parts[0] + "." + parts[1]))) return null;
    const header = JSON.parse(new TextDecoder().decode(unb64u(parts[0])));
    if (!isObject(header) || header.alg !== "HS256" || header.typ !== "JWT" || Object.keys(header).some(k => !["alg", "typ"].includes(k))) return null;
    const claims = JSON.parse(new TextDecoder().decode(unb64u(parts[1])));
    const now = Math.floor(Date.now()/1000);
    if (!isObject(claims) || claims.iss !== "mohamed-tv-ai" || claims.aud !== "tv-app" ||
        !validInstallId(claims.sub) || !Number.isInteger(claims.iat) || claims.iat > now + 30 ||
        !Number.isInteger(claims.exp) || claims.exp <= now || claims.exp > now + 601 ||
        claims.exp <= claims.iat || Object.keys(claims).some(k => !["iss", "aud", "sub", "iat", "exp"].includes(k))) return null;
    const record = await useDeviceStore(env, claims.sub, "get");
    if (!record || !record.ok || !record.device || record.device.status !== "active") return null;
    return claims;
  } catch { return null; }
}
async function useDeviceStore(env, installId, operation, device) {
  if (!env.AUTH_DEVICES) throw new Error("auth_device_store_unavailable");
  const stub = env.AUTH_DEVICES.get(env.AUTH_DEVICES.idFromName(installId));
  const response = await stub.fetch("https://auth-device/" + operation, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ device })
  });
  return await response.json();
}
async function rateLimit(binding, key) {
  if (!binding) return false;
  const result = await binding.limit({ key });
  return !!result.success;
}
async function useChallengeStore(env, nonce, operation, payload) {
  if (!env.AUTH_CHALLENGES) throw new Error("auth_challenge_store_unavailable");
  const id = env.AUTH_CHALLENGES.idFromName(nonce);
  const stub = env.AUTH_CHALLENGES.get(id);
  const response = await stub.fetch("https://auth-challenge/" + operation, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload || {})
  });
  return await response.json();
}
function constantTimeSecretEqual(candidate, expected) {
  if (typeof candidate !== "string" || typeof expected !== "string" || !expected || candidate.length !== expected.length) return false;
  let difference = 0;
  for (let i = 0; i < expected.length; i++) difference |= candidate.charCodeAt(i) ^ expected.charCodeAt(i);
  return difference === 0;
}

async function authRoutes(request, env, url) {
  const secret = env.SESSION_SIGNING_SECRET;
  if (url.pathname === "/v1/admin/devices/revoke" || url.pathname === "/v1/admin/devices/approve") {
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!env.DEVICE_ADMIN_SECRET || env.DEVICE_ADMIN_SECRET.length < 32 || !env.AUTH_DEVICES || !env.ADMIN_LIMITER) return json({ error: "admin_service_unavailable" }, 503);
    const ip = request.headers.get("cf-connecting-ip") || "unknown";
    if (!await rateLimit(env.ADMIN_LIMITER, "device-admin:" + ip)) { await recordSecurityEvent(env, "rate_limited"); return json({ error: "rate_limited" }, 429); }
    const supplied = request.headers.get("authorization") || "";
    const candidate = supplied.startsWith("Bearer ") ? supplied.slice(7) : "";
    if (!constantTimeSecretEqual(candidate, env.DEVICE_ADMIN_SECRET)) { await recordSecurityEvent(env, "admin_rejected"); return json({ error: "not_found" }, 404); }
    let body; try { body = await request.json(); } catch { return json({ error: "invalid_request" }, 400); }
    if (!isObject(body) || !validInstallId(body.installId)) return json({ error: "invalid_request" }, 400);
    const operation = url.pathname.endsWith("/approve") ? "approve" : "revoke";
    const result = await useDeviceStore(env, body.installId, operation);
    if (!result || !result.ok) return json({ error: "device_not_found_or_invalid_state" }, 404);
    return json({ ok: true, [operation === "approve" ? "approved" : "revoked"]: true });
  }
  if (!env.AUTH_DEVICES || !env.AUTH_CHALLENGES || !secret || secret.length < 32 || !env.ENROLL_LIMITER || !env.RATE_LIMITER) {
    return json({ error: "auth_service_unavailable" }, 503);
  }
  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  if (url.pathname === "/v1/auth/challenge") {
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!await rateLimit(env.ENROLL_LIMITER, "challenge:" + ip)) { await recordSecurityEvent(env, "rate_limited"); return json({ error: "rate_limited" }, 429); }
    let body; try { body = await request.json(); } catch { return json({ error: "invalid_request" }, 400); }
    if (!isObject(body)) return json({ error: "invalid_request" }, 400);
    const id = body.installId;
    const purpose = body.purpose;
    if (!validInstallId(id) || !["enroll", "session"].includes(purpose)) return json({ error: "invalid_request" }, 400);
    if (purpose === "enroll" && !await rateLimit(env.ENROLL_LIMITER, "enrollment-challenge-per-location")) return json({ error: "rate_limited" }, 429);
    if (purpose === "session") {
      const record = await useDeviceStore(env, id, "get");
      if (!record || !record.ok || !record.device || record.device.status !== "active") return json({ error: "authentication_failed" }, 401);
    }
    const nonce = randomToken(32);
    const stored = await useChallengeStore(env, nonce, "create", { installId: id, purpose });
    if (!stored.ok) return json({ error: "auth_service_unavailable" }, 503);
    return json({ ok: true, nonce, expiresIn: 120 });
  }
  if (url.pathname === "/v1/auth/enroll") {
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!await rateLimit(env.ENROLL_LIMITER, "enroll:" + ip)) { await recordSecurityEvent(env, "rate_limited"); return json({ error: "rate_limited" }, 429); }
    if (!await rateLimit(env.ENROLL_LIMITER, "enrollment-submit-per-location")) return json({ error: "rate_limited" }, 429);
    let body; try { body = await request.json(); } catch { return json({ error: "invalid_request" }, 400); }
    if (!isObject(body)) return json({ error: "invalid_request" }, 400);
    const { installId: id, nonce, publicKey: pub, signature: sig } = body;
    if (!validInstallId(id) || !validNonce(nonce) || typeof pub !== "string" || pub.length < 80 || pub.length > 1000 ||
        typeof sig !== "string" || sig.length < 80 || sig.length > 300) return json({ error: "invalid_request" }, 400);
    const challenge = await useChallengeStore(env, nonce, "consume", { installId: id, purpose: "enroll" });
    if (!challenge.ok) return json({ error: "authentication_failed" }, 401);
    if (!await verifyDeviceSignature(pub, sig, "enroll|" + id + "|" + nonce)) return json({ error: "authentication_failed" }, 401);
    // First-time installs activate after proving possession of their private key.
    // A matching pending install is promoted on retry; revoked installs stay revoked.
    const existing = await useDeviceStore(env, id, "register", { publicKey: pub, status: "active", createdAt: Date.now(), keyVersion: 1 });
    if (!existing.ok) return json({ error: "enrollment_not_available" }, 409);
    return json({ ok: true, enrolled: existing.status === "active", pending: false, status: existing.status });
  }
  if (url.pathname === "/v1/auth/session") {
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!await rateLimit(env.ENROLL_LIMITER, "session:" + ip)) { await recordSecurityEvent(env, "rate_limited"); return json({ error: "rate_limited" }, 429); }
    let body; try { body = await request.json(); } catch { return json({ error: "invalid_request" }, 400); }
    if (!isObject(body)) return json({ error: "invalid_request" }, 400);
    const { installId: id, nonce, signature: sig } = body;
    if (!validInstallId(id) || !validNonce(nonce) || typeof sig !== "string" || sig.length < 80 || sig.length > 300) {
      return json({ error: "invalid_request" }, 400);
    }
    const challenge = await useChallengeStore(env, nonce, "consume", { installId: id, purpose: "session" });
    const storedDevice = await useDeviceStore(env, id, "get");
    const device = storedDevice && storedDevice.device;
    if (!challenge.ok || !storedDevice.ok || !device || device.status !== "active") return json({ error: "authentication_failed" }, 401);
    if (!await verifyDeviceSignature(device.publicKey, sig, "session|" + id + "|" + nonce)) return json({ error: "authentication_failed" }, 401);
    const now = Math.floor(Date.now()/1000);
    const token = await signSession({ iss: "mohamed-tv-ai", aud: "tv-app", sub: id, iat: now, exp: now + 600 }, secret);
    return json({ ok: true, token, expiresIn: 600 });
  }
  return json({ error: "not_found" }, 404);
}



 // Privacy-preserving daily counters only; no IP, install ID, token, key, or message data.
async function recordSecurityEvent(env, event) {
  const allowed = new Set(["auth_rejected", "rate_limited", "enrollment_rejected", "admin_rejected"]);
  if (!allowed.has(event) || !env.SECURITY_EVENTS) return;
  try {
    const stub = env.SECURITY_EVENTS.get(env.SECURITY_EVENTS.idFromName("daily-security-aggregate"));
    await stub.fetch("https://security-events/increment", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ event, day: new Date().toISOString().slice(0, 10) })
    });
  } catch { /* Telemetry is best-effort and must not affect app availability. */ }
}

export class AuthDeviceStore {
  constructor(state) { this.state = state; }
  async fetch(request) {
    if (request.method !== "POST") return json({ ok: false }, 405);
    const operation = new URL(request.url).pathname.slice(1);
    let body; try { body = await request.json(); } catch { return json({ ok: false }, 400); }
    if (operation === "get") {
      const device = await this.state.storage.get("device");
      return json({ ok: !!device, device: device || null });
    }
    if (operation === "register") {
      const result = await this.state.storage.transaction(async txn => {
        const existing = await txn.get("device");
        if (existing) {
          if (existing.publicKey !== body.device?.publicKey) return { ok: false };
          if (existing.status === "pending") {
            existing.status = "active";
            existing.activatedAt = Date.now();
            await txn.put("device", existing);
          }
          // Revoked devices never self-reactivate.
          return { ok: true, status: existing.status, alreadyRegistered: true };
        }
        const device = body.device;
        if (!device || typeof device.publicKey !== "string" || device.publicKey.length < 80 || device.publicKey.length > 1000 || device.status !== "active") return { ok: false };
        await txn.put("device", device);
        return { ok: true, status: device.status, alreadyRegistered: false };
      });
      return json(result, result.ok ? 200 : 409);
    }
    if (operation === "approve") {
      const result = await this.state.storage.transaction(async txn => {
        const device = await txn.get("device");
        if (!device || device.status !== "pending") return { ok: false };
        device.status = "active";
        device.approvedAt = Date.now();
        await txn.put("device", device);
        return { ok: true };
      });
      return json(result, result.ok ? 200 : 404);
    }
    if (operation === "revoke") {
      const result = await this.state.storage.transaction(async txn => {
        const device = await txn.get("device");
        if (!device) return { ok: false };
        device.status = "revoked";
        device.revokedAt = Date.now();
        await txn.put("device", device);
        return { ok: true };
      });
      return json(result, result.ok ? 200 : 404);
    }
    return json({ ok: false }, 404);
  }
}

export class SecurityEventStore {
  constructor(state) { this.state = state; }
  async fetch(request) {
    if (request.method !== "POST" || new URL(request.url).pathname !== "/increment") return json({ ok: false }, 404);
    let body;
    try { body = await request.json(); } catch { return json({ ok: false }, 400); }
    const allowed = new Set(["auth_rejected", "rate_limited", "enrollment_rejected", "admin_rejected"]);
    if (!allowed.has(body?.event) || typeof body.day !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(body.day)) return json({ ok: false }, 400);
    const key = "daily:" + body.day + ":" + body.event;
    const count = await this.state.storage.transaction(async txn => {
      const next = Math.min(Number(await txn.get(key) || 0) + 1, 1000000000);
      await txn.put(key, next);
      return next;
    });
    await this.state.storage.setAlarm(Date.now() + 86400000);
    return json({ ok: true, count });
  }
  async alarm() {
    const cutoff = new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
    const entries = await this.state.storage.list({ prefix: "daily:", limit: 500 });
    const removals = [];
    for (const key of entries.keys()) {
      const day = String(key).split(":")[1] || "";
      if (day < cutoff) removals.push(key);
    }
    if (removals.length) await this.state.storage.delete(removals);
    await this.state.storage.setAlarm(Date.now() + 86400000);
  }
}

export class AuthChallengeStore {
  constructor(state) { this.state = state; }
  async fetch(request) {
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    const url = new URL(request.url);
    let body; try { body = await request.json(); } catch { return json({ ok: false }, 400); }
    if (url.pathname === "/create") {
      await this.state.storage.put("challenge", {
        installId: String(body.installId || ""),
        purpose: String(body.purpose || ""),
        createdAt: Date.now()
      });
      await this.state.storage.setAlarm(Date.now() + 120000);
      return json({ ok: true });
    }
    if (url.pathname === "/consume") {
      const result = await this.state.storage.transaction(async txn => {
        const challenge = await txn.get("challenge");
        const now = Date.now();
        if (!challenge || challenge.installId !== body.installId ||
            challenge.purpose !== body.purpose || !Number.isFinite(challenge.createdAt) ||
            challenge.createdAt > now || now - challenge.createdAt >= 120000) {
          return { ok: false };
        }
        await txn.delete("challenge");
        return { ok: true };
      });
      return json(result, result.ok ? 200 : 401);
    }
    return json({ ok: false }, 404);
  }
  async alarm() {
    await this.state.storage.delete("challenge");
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/v1/health") {
      return json({ ok: true, service: "mohamed-tv-ai", apiVersion: 1, provider: "groq" });
    }
    if (url.pathname.startsWith("/v1/auth/") || url.pathname.startsWith("/v1/admin/devices/")) return authRoutes(request, env, url);
    if (request.method !== "POST" || !["/v1/assistant", "/v1/info"].includes(url.pathname)) {
      return json({ error: "not_found" }, 404);
    }
    if (!env.AUTH_DEVICES || !env.AUTH_CHALLENGES || !env.SESSION_SIGNING_SECRET || env.SESSION_SIGNING_SECRET.length < 32 || !env.RATE_LIMITER || !env.DEVICE_RATE_LIMITER) {
      return json({ error: "server_not_configured" }, 503);
    }
    if (url.pathname === "/v1/assistant" && !env.GROQ_API_KEY) {
      return json({ error: "server_not_configured" }, 503);
    }
    const auth = request.headers.get("authorization") || "";
    const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
    const claims = await verifySession(token, env.SESSION_SIGNING_SECRET, env);
    if (!claims) return json({ error: "authentication_failed" }, 401);
    if (!await rateLimit(env.DEVICE_RATE_LIMITER, "device:" + claims.sub)) { await recordSecurityEvent(env, "rate_limited"); return json({ error: "rate_limited" }, 429); }
    if (!await rateLimit(env.RATE_LIMITER, "ip:" + (request.headers.get("cf-connecting-ip") || "unknown"))) { await recordSecurityEvent(env, "rate_limited"); return json({ error: "rate_limited" }, 429); }

    let body;
    try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
    if (url.pathname === "/v1/assistant" && !validRequest(body)) return json({ error: "invalid_request", maxMessageLength: 4000 }, 400);

    if (url.pathname === "/v1/info") {
      const infoBody = body;
      if (!infoBody || !["time", "weather", "currency", "gold", "news", "sports"].includes(infoBody.type) ||
          (["time", "weather", "news"].includes(infoBody.type) && (!infoBody.location || typeof infoBody.location !== "string" || !infoBody.location.trim() || infoBody.location.length > 100)) ||
          (infoBody.type === "sports" && (typeof infoBody.league !== "string" || !Object.prototype.hasOwnProperty.call(SPORTS_LEAGUES, infoBody.league) ||
            (infoBody.date != null && (typeof infoBody.date !== "string" || !/^\\d{8}$/.test(infoBody.date)))))) {
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
                : infoBody.type === "sports"
                  ? await getSportsInfo(infoBody.league, infoBody.date, infoBody.locale)
                  : await getNewsInfo(infoBody.location, infoBody.locale);
        return json(data, data.ok ? 200 : 404);
      } catch (error) {
        return json({ ok: false, error: "live_info_unavailable", detail: error?.message || "provider_error" }, 502);
      }
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
