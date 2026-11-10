# Device authentication — Cloudflare setup and security notes

This is the server-side half of the 0.7.0 per-install authentication protocol. It is committed as source only and is intentionally fail-closed until the Cloudflare bindings and secrets below exist.

## Required Worker bindings

Configure these in the Cloudflare Worker environment (Production and Preview separately):

| Binding | Type | Purpose |
|---|---|---|
| `DEVICE_REGISTRY` | KV namespace | Device public-key records and one-time challenges |
| `ENROLL_LIMITER` | Rate limiting binding | Per-IP and global throttling for enrollment/challenge/session issuance |
| `RATE_LIMITER` | Rate limiting binding | Per-IP API throttling |
| `DEVICE_RATE_LIMITER` | Rate limiting binding | Per-installation API throttling |

Set rate-limit thresholds conservatively. Suggested starting policy: enrollment 3 requests per IP per 10 minutes and a global enrollment ceiling; session issuance 20 per IP per 10 minutes; API requests 30 per device per minute and 60 per IP per minute. Adapt after observing real usage and Cloudflare plan limits. The exact limit/window configuration is managed in Cloudflare, not in this source file.

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
6. Challenges are deleted after use and expire automatically after 120 seconds.

## Revocation

To revoke an installation, update its KV value at key `device:<installId>` so `status` is `revoked`. Session validation checks the registry on every API request; KV propagation is not instantaneous, so allow for Cloudflare KV eventual consistency. For emergency global invalidation, rotate `SESSION_SIGNING_SECRET` and expect all clients to obtain new sessions (the device keys remain enrolled).

## Important limitations — do not overstate the protection

- Public first-run enrollment proves possession of a newly generated private key, not that the caller is an authentic copy of this app. A modified client can create its own key pair and attempt enrollment.
- IP/global rate limits and per-device quotas reduce abuse but do not establish app authenticity. Review Cloudflare Turnstile/attestation options compatible with Android TV before claiming stronger anti-abuse guarantees.
- A random installation UUID is an identifier, not a credential. Device key cloning is harder when the private key is hardware-backed, but hardware-backed storage is not guaranteed on every TV.
- Android Keystore implementations vary. The app must report key-generation/authentication failure rather than silently fall back to a plain-text or APK-embedded secret.
- KV is eventually consistent. If immediate revocation is a hard requirement, migrate the device registry/session revocation check to a strongly consistent Durable Object.
- This protocol is not yet independently penetration-tested. Do not publish or describe it as fully secure until replay, key rotation, revocation, concurrency, rate-limit and physical-device tests pass.

## Deployment gate

Do not deploy until the four bindings and both secrets are configured, then test all enrollment/session and API flows against a staging Worker. The current Worker deliberately returns `503` when required bindings are missing. No production deployment was performed as part of the source changes.
