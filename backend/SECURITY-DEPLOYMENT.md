# Device authentication — Cloudflare setup and security notes

This is the server-side half of the 0.7.0 per-install authentication protocol. It is committed as source only and is intentionally fail-closed until the Cloudflare bindings and secrets below exist.

## Required Worker bindings

Configure these in the Cloudflare Worker environment (Production and Preview separately):

| Binding | Type | Purpose |
|---|---|---|
| `DEVICE_REGISTRY` | KV namespace | Device public-key records and status |\n| `AUTH_CHALLENGES` | Durable Object namespace bound to exported class `AuthChallengeStore` | Atomic, single-use 120-second challenges |
| `ENROLL_LIMITER` | Rate limiting binding | Per-IP and global throttling for enrollment/challenge/session issuance |
| `RATE_LIMITER` | Rate limiting binding | Per-IP API throttling |
| `DEVICE_RATE_LIMITER` | Rate limiting binding | Per-installation API throttling |

Set rate-limit thresholds conservatively. Suggested starting policy: enrollment 3 requests per IP per 10 minutes and a global enrollment ceiling; session issuance 20 per IP per 10 minutes; API requests 30 per device per minute and 60 per IP per minute. Adapt after observing real usage and Cloudflare plan limits. The exact limit/window configuration is managed in Cloudflare, not in this source file.

Example Wrangler Durable Object declaration (merge into the existing Worker configuration; do not overwrite existing bindings or migrations):

```toml
[[durable_objects.bindings]]
name = "AUTH_CHALLENGES"
class_name = "AuthChallengeStore"

[[migrations]]
tag = "auth-challenges-v1"
new_sqlite_classes = ["AuthChallengeStore"]
```

The class is exported from `backend/worker.js`. The Durable Object bindings are declared in `backend/wrangler.toml`. Configure the three rate-limit bindings listed above in the Cloudflare dashboard as well. Configure the namespace IDs and rate-limit rules in Cloudflare; never commit secret values.

## Required secrets

- `SESSION_SIGNING_SECRET`: at least 32 cryptographically random bytes, encoded as a long random string. Rotate only with a planned session invalidation.
- `GROQ_API_KEY`: required for assistant inference only; keep it in Worker secrets.
- The legacy `APP_ACCESS_TOKEN` is no longer used by this Worker authentication path. Remove it from Worker secrets after confirming no older private test client depends on it.

Never put any of these values in GitHub, app resources, Gradle files, logs, or the APK.

## Protocol

1. The Android app generates a P-256 signing key pair in Android Keystore and keeps the private key non-exportable.
2. The app requests a one-time 120-second challenge for a random installation UUID.
3. For first enrollment, the app signs `enroll|<installId>|<nonce>`; the Worker verifies the signature and stores only the public key and status.
4. For each new API session, the app requests a fresh challenge and signs `session|<installId>|<nonce>`.
5. The Worker issues a 10-minute HMAC-SHA256 JWT. Every assistant/info request verifies its signature, expiry, audience, and current device status in KV, then applies per-device and per-IP rate limits.
6. Challenges are consumed atomically by a Durable Object transaction, deleted after use, and expire automatically after 120 seconds. This avoids the read/delete race that a KV-only challenge store would have.

## Revocation

To revoke an installation, invoke the `revoke` operation on the Durable Object identified by the installation UUID. Session validation checks the strongly consistent device record on every API request. For emergency global invalidation, rotate `SESSION_SIGNING_SECRET` and expect all clients to obtain new sessions (the device keys remain enrolled).

## Important limitations — do not overstate the protection

- Public first-run enrollment proves possession of a newly generated private key, not that the caller is an authentic copy of this app. A modified client can create its own key pair and attempt enrollment.
- IP/global rate limits and per-device quotas reduce abuse but do not establish app authenticity. Review Cloudflare Turnstile/attestation options compatible with Android TV before claiming stronger anti-abuse guarantees.
- A random installation UUID is an identifier, not a credential. Device key cloning is harder when the private key is hardware-backed, but hardware-backed storage is not guaranteed on every TV.
- Android Keystore implementations vary. The app must report key-generation/authentication failure rather than silently fall back to a plain-text or APK-embedded secret.
- The device registry and challenge store use Durable Objects with transactional storage. Operational availability and correct production migrations still require staging validation.
- This protocol is not yet independently penetration-tested. Do not publish or describe it as fully secure until replay, key rotation, revocation, concurrency, rate-limit and physical-device tests pass.

## Deployment gate

Do not deploy until the four bindings and both secrets are configured, then test all enrollment/session and API flows against a staging Worker. The current Worker deliberately returns `503` when required bindings are missing. `AUTH_DEVICES` and `AUTH_CHALLENGES` are declared in `backend/wrangler.toml`; ensure the migration `auth-v1` is applied. No production deployment was performed as part of the source changes.
