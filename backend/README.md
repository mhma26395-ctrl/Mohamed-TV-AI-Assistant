# Mohamed TV AI Assistant — Central AI Backend

This folder contains the first backend scaffold for the centralized service.

## Architecture

Android TV app → HTTPS backend → Gemini API

The Gemini key exists only as a Cloudflare Worker secret. The Android APK must never contain the provider key.

## Current scope

- Gemini server-side request
- Arabic/Egyptian Arabic/English system instruction
- Function declarations for a small, explicit set of TV capabilities
- Structured function-call response for the Android app
- Input size validation, generic provider errors, no-store responses
- Optional Cloudflare rate-limit binding
- Server-side bearer-token gate

## Important: not production-ready yet

The bearer token is only a temporary server-side gate. Do not embed APP_ACCESS_TOKEN in the APK or publish this Worker as a public service. Before the TV app connects, implement per-device enrollment and revocable device credentials (preferably short-lived access tokens with refresh/rotation), plus rate limiting and usage limits. A shared token compiled into an APK can be extracted and abused.

No API key or user secret should be committed to Git.

## Deploy preparation

1. Create a Cloudflare account and a Worker named `mohamed-tv-ai-backend`.
2. Install Wrangler on a trusted computer or use the Cloudflare dashboard editor.
3. Set encrypted secrets:
   - `GEMINI_API_KEY`
   - `APP_ACCESS_TOKEN` (temporary only; do not distribute it in the APK)
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
  "provider": "gemini",
  "model": "gemini-2.5-flash",
  "text": "",
  "toolCalls": [
    { "name": "adjust_volume", "args": { "direction": "down", "steps": 1 } }
  ],
  "finishReason": "STOP"
}
```

Tool calls are proposals, not proof of execution. The Android app must validate every tool name and argument against its own allowlist, execute only supported actions, request confirmation for sensitive actions, and report the actual result back to the model. The model must never directly execute shell commands or arbitrary intents.

## Next implementation steps

1. Add device enrollment and revocable per-device credentials.
2. Add Android HTTPS client and connection settings.
3. Implement strict tool dispatcher and execution-result feedback in the TV app.
4. Add conversation context and function-response round trips.
5. Add provider abstraction for OpenAI and DeepSeek.
6. Add quotas, observability without storing raw voice transcripts by default, and a privacy notice.
