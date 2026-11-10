# Device authentication — Cloudflare setup and security notes

This is the server-side half of the 0.7.0 per-install authentication protocol. It is committed as source only and is intentionally fail-closed until the Cloudflare bindings and secrets below exist.

## Required Worker bindings

Configure these in the Cloudflare Worker environments. The Wrangler file now defines a separate `staging` environment and separate Durable Object namespaces for it:

| Binding | Type | Purpose |
|---|---|---|
| `AUTH_DEVICES` | Durable Object namespace bound to `AuthDeviceStore` | Strongly consistent device records and atomic enrollment/revocation |\n| `AUTH_CHALLENGES` | Durable Object namespace bound to `AuthChallengeStore` | Atomic, single-use 120-second challenges |
| `ENROLL_LIMITER` | Rate limiting binding | Per-IP and global throttling for enrollment/challenge/session issuance |
| `RATE_LIMITER` | Rate limiting binding | Per-IP API throttling |
| `DEVICE_RATE_LIMITER` | Rate limiting binding | Per-installation API throttling |
| `ADMIN_LIMITER` | Rate limiting binding | Per-IP throttling for administrative approval and revocation |

Rate limits are declared in `backend/wrangler.toml` for both the default Worker and the isolated `staging` environment. Cloudflare's Worker Rate Limiting binding supports only 10-second or 60-second periods and is approximate per Cloudflare location; it is not a strict worldwide/global ceiling. The starter production limits are 10 enrollment/session requests, 60 requests per IP, 30 requests per device, and 10 admin requests per minute. Staging is intentionally tighter. Namespace IDs must be unique within the Cloudflare account; replace the sample numeric IDs if already used by another Worker. These limits are abuse-friction controls, not a substitute for strict quota enforcement.

The default environment's Wrangler Durable Object declarations and migration are already present in `backend/wrangler.toml`; staging has its own repeated bindings and migration tag so its state is isolated. The default environment configuration is:

```toml
[[durable_objects.bindings]]
name = "AUTH_CHALLENGES"
class_name = "AuthChallengeStore"

[[durable_objects.bindings]]
name = "AUTH_DEVICES"
class_name = "AuthDeviceStore"

[[migrations]]
tag = "auth-v1"
new_sqlite_classes = ["AuthChallengeStore", "AuthDeviceStore"]
```

Both classes are exported from `backend/worker.js`. Configure all four rate-limit bindings listed above in the Cloudflare dashboard; never commit secret values.

## Staging setup (deploy only after review)\n\nThe staging Worker name is `mohamed-tv-ai-backend-staging`. Wrangler environments do not inherit secrets, vars, or bindings. Add three separate staging secrets directly from a trusted terminal (never paste values into GitHub or chat):\n\n```sh\nwrangler secret put SESSION_SIGNING_SECRET --env staging\nwrangler secret put GROQ_API_KEY --env staging\nwrangler secret put DEVICE_ADMIN_SECRET --env staging\n```\n\nFor the default environment, use the same commands without `--env staging`. Keep staging and production values different. The staging Durable Object migration is tagged `auth-staging-v1`; the default migration remains `auth-v1`. Validate the staging configuration with `npx wrangler deploy --dry-run --env staging --config wrangler.toml` before any real deployment. A dry run does not publish a Worker.\n\n## Required secrets

- `SESSION_SIGNING_SECRET`: at least 32 cryptographically random bytes, encoded as a long random string. Rotate only with a planned session invalidation.
- `GROQ_API_KEY`: required for assistant inference only; keep it in Worker secrets.
- `DEVICE_ADMIN_SECRET`: at least 32 random bytes, stored only as a Worker secret. It protects the administrative device-approval and revocation endpoints. Never put it in the Android app or a public client.
- The legacy `APP_ACCESS_TOKEN` is no longer used by this Worker authentication path. Remove it from Worker secrets after confirming no older private test client depends on it.

Never put any of these values in GitHub, app resources, Gradle files, logs, or the APK.

## Protocol

1. The Android app generates a P-256 signing key pair in Android Keystore and keeps the private key non-exportable.
2. The app requests a one-time 120-second challenge for a random installation UUID.
3. For first enrollment, the app signs `enroll|<installId>|<nonce>`; the Worker verifies the signature and stores only the public key and status.
4. For each new API session, the app requests a fresh challenge and signs `session|<installId>|<nonce>`.
5. The Worker issues a 10-minute HMAC-SHA256 JWT. Every assistant/info request verifies its signature, expiry, audience, and current device status through the strongly consistent device Durable Object, then applies per-device and per-IP rate limits.
6. Challenges are consumed atomically by a Durable Object transaction, deleted after use, and expire automatically after 120 seconds. This avoids the read/delete race that a KV-only challenge store would have.

## Revocation

To revoke an installation, invoke the `revoke` operation on the Durable Object identified by the installation UUID. Session validation checks the strongly consistent device record on every API request. For emergency global invalidation, rotate `SESSION_SIGNING_SECRET` and expect all clients to obtain new sessions (the device keys remain enrolled).

## Important limitations — do not overstate the protection

- Administrative approval prevents a new public enrollment from immediately receiving API access, but it does not prove that the caller is an authentic copy of this app. A modified client can create a key and submit a pending enrollment; an administrator must review the request before approval.
- IP/global rate limits and per-device quotas reduce abuse but do not establish app authenticity. Review Cloudflare Turnstile/attestation options compatible with Android TV before claiming stronger anti-abuse guarantees.
- A random installation UUID is an identifier, not a credential. Device key cloning is harder when the private key is hardware-backed, but hardware-backed storage is not guaranteed on every TV.
- Android Keystore implementations vary. The app must report key-generation/authentication failure rather than silently fall back to a plain-text or APK-embedded secret.
- The device registry and challenge store use Durable Objects with transactional storage. Operational availability and correct production migrations still require staging validation.
- This protocol is not yet independently penetration-tested. Do not publish or describe it as fully secure until replay, key rotation, revocation, concurrency, rate-limit and physical-device tests pass.

## Deployment gate

Do not deploy until all six bindings and all three secrets are configured, then test all enrollment/session and API flows against a staging Worker. The current Worker deliberately returns `503` when required bindings are missing. `AUTH_DEVICES` and `AUTH_CHALLENGES` are declared for both environments in `backend/wrangler.toml`; ensure the matching migration is applied. No staging or production deployment was performed as part of the source changes.


## Administrator device actions

New installations are registered as `pending` and cannot request a session until an administrator approves them. The app displays the installation UUID while waiting. From a trusted administrator environment, call the Worker endpoints below. Never call these endpoints from the Android app or expose the admin secret to users.

Approve a pending installation:

```sh
curl -X POST "https://YOUR-WORKER-DOMAIN/v1/admin/devices/approve" \
  -H "Authorization: Bearer $DEVICE_ADMIN_SECRET" \
  -H "Content-Type: application/json" \
  --data '{"installId":"DEVICE-UUID-SHOWN-IN-APP"}'
```

Revoke an installation:

```sh
curl -X POST "https://YOUR-WORKER-DOMAIN/v1/admin/devices/revoke" \
  -H "Authorization: Bearer $DEVICE_ADMIN_SECRET" \
  -H "Content-Type: application/json" \
  --data '{"installId":"DEVICE-UUID-SHOWN-IN-APP"}'
```

Replace the example domain and UUID before use. Supply the admin secret only through a secure local environment variable. Approval is permitted only from `pending`; a revoked device cannot be re-approved through this endpoint.
