# Mohamed TV AI Assistant — Central AI Backend

This folder contains the first centralized AI backend scaffold for the Android TV app.

## Architecture

Android TV app → HTTPS Cloudflare Worker → Groq API

The Groq API key exists only as an encrypted Cloudflare Worker secret. The Android APK must never contain the provider key.

## Provider and model

- Provider: GroqCloud
- Starting model: `openai/gpt-oss-120b`
- The model ID is configurable through the `GROQ_MODEL` Worker variable.
- The API uses Groq's OpenAI-compatible Chat Completions endpoint and local function/tool calling.
- The response preserves the app-facing contract: `ok`, `provider`, `model`, `text`, `toolCalls`, and `finishReason`.

The selected model is a starting point, not a promise of unlimited free service. Groq publishes model-specific limits, and free-tier availability/quotas can change. Stay on the free plan, monitor the account's limits, and do not enable paid billing or upgrade. When a quota is reached, requests may be rejected with HTTP 429 until the quota resets. There is no automatic paid-provider fallback.

## Current scope

- Egyptian Arabic, Modern Standard Arabic, and English instructions
- Small allowlist of TV functions: list apps, launch app, adjust volume, open supported settings, read device info
- Structured tool-call parsing with an allowlist on the backend
- Input length validation, generic provider errors, no-store responses
- Optional Cloudflare rate-limit binding
- Temporary server-side bearer-token gate

## Important: not production-ready yet

The bearer token is only a temporary server-side gate. Do not embed `APP_ACCESS_TOKEN` in the APK or publish this Worker as a public service. Before the TV app connects, implement per-device enrollment and revocable device credentials (preferably short-lived access tokens with refresh/rotation), plus rate limiting and usage limits. A shared token compiled into an APK can be extracted and abused.

No API key, access token, or user secret should be committed to Git.

## Zero-cost operating rule

This project must remain on free tiers only. Do not add paid services, enable automatic upgrades, or configure a paid fallback. Hosting on a free Cloudflare plan does not make model inference unlimited or guarantee that the provider's free quota will remain available forever. If free quota is exhausted, the assistant should show a clear temporary-limit message rather than incur charges.

## Deploy preparation

1. Create a Cloudflare account and a GroqCloud account, and confirm that the selected model is available on the account's free plan.
2. In Cloudflare Worker settings, add encrypted secrets:
   - `GROQ_API_KEY`
   - `APP_ACCESS_TOKEN` (temporary only; do not distribute it in the APK)
3. Keep `GROQ_MODEL` set to `openai/gpt-oss-120b` unless a free-tier alternative has been tested and selected.
4. Configure a rate limiting binding named `RATE_LIMITER`.
5. Deploy and test `GET /v1/health`.
6. Do not connect the Android APK until per-device authentication is implemented.

## API contract

### POST /v1/assistant

Request:
```json
{ "message": "افتح يوتيوب وخفض الصوت شوية" }
```

Response:
```json
{
  "ok": true,
  "provider": "groq",
  "model": "openai/gpt-oss-120b",
  "text": "",
  "toolCalls": [
    { "name": "adjust_volume", "args": { "direction": "down", "steps": 1 } }
  ],
  "finishReason": "tool_calls"
}
```

Tool calls are proposals, not proof of execution. The Android app must validate every tool name and argument against its own allowlist, execute only supported actions, request confirmation for sensitive actions, and report the actual result back to the model. The model must never directly execute shell commands or arbitrary intents.

## Next implementation steps

1. Add device enrollment and revocable per-device credentials.
2. Add Android HTTPS client and connection settings.
3. Implement strict tool dispatcher and execution-result feedback in the TV app.
4. Add short conversation context and function-response round trips.
5. Test Arabic/Egyptian Arabic command understanding on the actual TV box.
6. Add privacy notice and usage controls; avoid storing raw voice transcripts by default.
