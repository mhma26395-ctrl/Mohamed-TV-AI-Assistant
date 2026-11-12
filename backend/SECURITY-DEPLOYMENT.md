# Device authentication — Cloudflare setup and security notes

## Production and staging AI provider

Both environments must use the subscribed DeepSeek provider. Production and staging are explicitly configured with:

- `AI_PROVIDER=deepseek`
- `DEEPSEEK_MODEL=deepseek-flash`

The Worker must have an encrypted `DEEPSEEK_API_KEY` secret in **each environment separately**. Wrangler environments do not inherit secrets. Do not configure or fall back to Groq. The Worker defaults to Groq only when `AI_PROVIDER` is absent; this repository now explicitly sets DeepSeek in both environments to prevent that accidental fallback.

Before deploying production, verify that the production Worker has these encrypted secrets:
- `SESSION_SIGNING_SECRET`
- `DEEPSEEK_API_KEY`
- `DEVICE_ADMIN_SECRET`

Never commit or paste secret values into source control, chat, APK resources, or logs. If the DeepSeek secret currently exists only in staging, add the same valid DeepSeek API key to the production Worker through Cloudflare's encrypted secret interface. Do not expose the key in GitHub Actions logs.

Deploy the default Worker (production) only after confirming the required production bindings and secrets. Deploy staging separately with `--env staging`.

## Required Worker bindings

| Binding | Type | Purpose |
|---|---|---|
| `AUTH_DEVICES` | Durable Object namespace bound to `AuthDeviceStore` | Device records and revocation |
| `AUTH_CHALLENGES` | Durable Object namespace bound to `AuthChallengeStore` | Single-use authentication challenges |
| `ENROLL_LIMITER` | Rate limiting binding | Enrollment throttling |
| `RATE_LIMITER` | Rate limiting binding | Per-IP API throttling |
| `DEVICE_RATE_LIMITER` | Rate limiting binding | Per-installation API throttling |
| `ADMIN_LIMITER` | Rate limiting binding | Administrative endpoint throttling |
| `SECURITY_EVENTS` | Durable Object namespace bound to `SecurityEventStore` | Aggregate security telemetry |

Rate limits are approximate per Cloudflare location, not strict global quotas. Namespace IDs must be unique within the Cloudflare account.

## Deployment commands

From the `backend` directory:

```sh
# Production dry run; does not publish.
npx wrangler deploy --dry-run --config wrangler.toml

# Production deployment after checking bindings and secrets.
npx wrangler deploy --config wrangler.toml

# Add/rotate the production DeepSeek key interactively if it is not already configured.
npx wrangler secret put DEEPSEEK_API_KEY --config wrangler.toml

# Staging remains isolated and also uses DeepSeek.
npx wrangler deploy --dry-run --env staging --config wrangler.toml
npx wrangler deploy --env staging --config wrangler.toml
npx wrangler secret put DEEPSEEK_API_KEY --env staging --config wrangler.toml
```

The secret command prompts securely; never place the key in the command text. The production and staging Workers each require their own secret entry.

## Authentication protocol and security notes

The Android app uses a P-256 signing key in Android Keystore. It requests short-lived, single-use challenges and receives a short-lived signed session. Every protected API request validates the session and device status, then applies per-device and per-IP rate limits.

Keep `SESSION_SIGNING_SECRET` and `DEVICE_ADMIN_SECRET` encrypted in Cloudflare. Never put provider keys or administrator secrets in the Android app. This design has not been independently penetration-tested; do not claim independent security certification.

## Deployment gate

Do not deploy until all required bindings and all three secrets are configured in the target environment. Protected routes intentionally fail closed with HTTP 503 when configuration is missing. Confirm production's `/v1/health` response reports `provider: "deepseek"` after deployment. Then run integration tests for general questions and live information before publishing a new APK.

No Cloudflare deployment is performed by editing this repository file alone.
