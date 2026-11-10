# Mohamed TV AI Assistant — Release 0.7.0 Consolidated Roadmap

## Release policy
- Target release: **0.7.0**.
- This is a consolidated development and validation release, not a sequence of small APK releases.
- **Do not build, publish, or distribute an APK until the planned feature set is implemented, reviewed, and tested as a complete group.**
- GitHub Actions APK workflow remains manual. Do not dispatch it during implementation.
- Keep the current stable 0.6.2 release available as the fallback.

## Privilege strategy: support both Shizuku and direct ADB
The app should support two optional elevated-control paths while keeping ordinary features usable without either:

1. **Shizuku integration**
   - Detect whether Shizuku is installed, running, and authorized for this app.
   - Explain that the user must start Shizuku using the device-supported setup (typically Wireless debugging on supported Android versions) and explicitly authorize this app.
   - Request only the permissions actually needed for each operation.
   - Handle unavailable, stopped, revoked, and unsupported states without crashing.

2. **Direct ADB over Wi-Fi**
   - Provide a guided pairing and connection flow for Android versions/devices that support Wireless debugging pairing.
   - Keep pairing code, endpoint, and session credentials out of logs and never hard-code them in the APK or repository.
   - Require explicit user confirmation before connecting and before privileged/destructive actions.
   - Handle dynamic pairing/connect ports, reconnects, authorization revocation, timeouts, and device/vendor differences.
   - Do not assume legacy fixed-port ADB is enabled or available.

3. **Capability detection and fallback**
   - Present a clear status for: no elevated connection, Shizuku authorized, or direct ADB connected.
   - Prefer Shizuku when it is available and authorized; otherwise offer direct ADB. Do not silently elevate or switch modes.
   - Keep non-ADB features (voice assistant, app launching, volume, settings shortcuts, storage/RAM reports, Usage Access history, floating assistant, remote-key mapping) functional without either path.
   - Explain per feature which access method is required; never imply Accessibility or Usage Access is equivalent to ADB.

## Planned 0.7.0 feature groups

### A. Permission and capability center
- One in-app screen showing Microphone, Usage Access, Overlay, Accessibility/remote-key service, Shizuku, and ADB connection states.
- Re-check permissions on resume and after returning from Android settings.
- Give step-by-step TV-friendly setup instructions and explain why each permission is requested.
- Never repeatedly ask for a permission already granted.

### B. Usage Access and app activity accuracy
- Report permission state separately from whether usage data is available.
- Show recent app usage history with timestamps when available.
- Label this as historical usage, not a live inventory of background processes.
- Do not claim that Usage Access can enumerate or stop every running process.

### C. App/process management through elevated access
- Enumerate installed packages and, where the OS permits, process/service information.
- Add per-app **Force Stop** only where the authorized Shizuku/ADB route and Android build permit it.
- Require confirmation, validate package identity, and report actual command results.
- Do not promise universal process visibility or force-stop success across vendors/Android versions.

### D. Cache/storage maintenance
- Distinguish app cache, app data, temporary files, and free storage.
- Implement only operations that the selected privilege path demonstrably supports.
- Never clear app data as part of cache cleanup.
- Show target app, estimated scope, confirmation, and result before/after where measurable.
- Keep the existing own-cache cleanup clearly labeled as assistant-only.

### E. Direct device controls (brightness and VPN)
- Separate **perform the requested action** from **open the relevant settings page**. Opening Display/VPN settings must never be described as having changed brightness or connected a VPN.
- **Brightness:** first detect whether the TV exposes a supported brightness control. Where Android system brightness is applicable and the user grants the required write-settings access, offer direct brightness adjustment with bounded values and report the actual read-back value. Many TVs expose picture brightness only through manufacturer-specific picture settings; for those models, clearly report that direct control is unsupported and open the picture settings as a fallback.
- **VPN:** detect installed VPN apps and available supported connection methods. Prefer a documented app deep link/API or an explicit Android VPN consent flow when available. Do not assume that opening the VPN app or VPN settings connects it. If the selected VPN app exposes no supported external control, open it and tell the user to connect manually.
- Never bypass Android VPN consent, user confirmation, or third-party app security. Never claim VPN is connected unless connection state is independently confirmed by the device.
- Add clear voice intents for "lower/raise brightness to X" and "connect/disconnect VPN", with status checks and honest fallback behavior.

### E. Device maintenance and reboot
- Add only commands supported by the active privilege route and the device.
- Require explicit confirmation for reboot or other disruptive actions.
- If unsupported, open the relevant system settings or explain the limitation; never report success without device confirmation.

### F. Floating assistant and remote control
- **Approved tap behavior:** one short press/tap on the floating AI bubble opens the compact assistant and automatically starts voice listening; no extra confirmation/menu is required for the normal one-tap path.
- Keep the approved one-tap behavior consistent for touch and the remote OK/DPAD_CENTER/ENTER activation.
- Validate DPAD arrow movement and OK activation on a physical Android TV/Mecool remote.
- Keep touch dragging as an additional method.
- Ensure Back, focus, overlay visibility, and navigation do not trap the user or unexpectedly consume unrelated remote keys.
- Preserve the existing optional Accessibility-based dedicated remote-key mapping, with a reliable disable/restore-original control.

### H. Live weather and forecasts
- Support natural Arabic/Egyptian Arabic and English weather questions for an explicitly named city (for example, Cairo) and for the user's location only when location permission/context is available and the user asks for local weather.
- Fetch current conditions and forecasts from a real weather data provider through the backend; do not rely on the language model's built-in knowledge or invent live temperatures.
- Prefer a provider that does not require embedding an API key in the APK. Keep provider access server-side and handle provider outages, rate limits, unknown city names, and missing data gracefully.
- Return location, observation/update time when supplied, temperature, feels-like temperature, condition, humidity, wind, and relevant forecast details only when the provider returns them.
- Distinguish current conditions from forecast; support at least today's forecast and a multi-day forecast where available.
- If live weather cannot be fetched, explain the specific temporary limitation instead of saying the assistant is inherently unable to provide weather.
- Test Arabic city names and common variants (القاهرة/القاهره, الإسكندرية/اسكندرية), English city names, ambiguous place names, unsupported locations, provider timeout, and offline behavior.
- Do not request precise location permission merely to answer a question that already names a city.

### G. Assistant language and tool reliability
- Review Arabic/Egyptian Arabic and English intents and synonyms.
- Ensure AI tool responses reflect actual device-side results.
- Do not let the model claim an operation succeeded merely because it proposed a tool call.
- Provide clear unsupported/permission-required/device-rejected responses.

## General assistant capabilities — not limited to TV controls
The assistant must behave as a useful general-purpose conversational assistant as well as a TV/device assistant. Do not require the user to enumerate individual question examples. Implement intent routing and reusable information services so new phrasings within a supported category work without adding a one-off phrase handler.

### I. Live information and lookup services
- **Time and time zones:** answer current local time for named cities/countries (e.g. Cairo, London, Los Angeles), with correct daylight-saving/time-zone rules. Use a reliable time-zone database or provider; do not infer offsets manually.
- **Weather:** current conditions and forecasts for named cities, with update/observation time and clear distinction between current weather and forecast.
- **News:** retrieve recent headlines and concise summaries for a named city, Egypt, the Arab world, or the world. Include publication time/source and links when available; distinguish reporting from opinion and avoid presenting old stories as breaking news.
- **Currencies and exchange rates:** retrieve current rates for named currency pairs (e.g. USD/EGP), include provider timestamp and clarify that bank, cash-market, and official rates may differ. Never invent a current rate.
- **Gold and commodity prices:** retrieve current gold prices with currency, unit (gram/ounce), karat (24K/21K/18K as applicable), source and timestamp; clearly distinguish spot price from local retail price and note making charges/taxes where relevant. Never invent a current quote.
- **General web lookup:** answer current factual questions by searching trusted sources when the answer may have changed, summarize evidence, and cite/link sources in the response. If sources conflict or cannot be reached, say so.
- **Time-sensitive data policy:** never use model training knowledge as a substitute for live data. Every live answer must carry source/time context where available; handle offline, timeout, rate-limit, ambiguous-location, and no-result cases gracefully.

### J. Everyday knowledge, planning, and calculations
- **Food and meal ideas:** suggest practical meals and recipes based on ingredients, budget, cuisine, preparation time, and dietary preferences when supplied; include ingredients and clear steps. For health-related dietary restrictions, ask only the necessary clarifying question and avoid medical certainty.
- **Nutrition and healthy eating:** provide general balanced-eating guidance and approximate nutrition only when reasonably supportable; clearly label estimates and avoid prescribing therapeutic diets.
- **Weight and body measurements:** calculate BMI and explain its limitations. For an ideal/healthy weight estimate, ask for the missing essentials (at minimum height; age and sex where relevant to the chosen method), explain that there is no single exact ideal weight, and avoid making a diagnosis. For children/teens, use age- and sex-specific growth references rather than adult BMI categories.
- **General calculations and conversions:** support common arithmetic, percentages, dates, units, and practical calculations accurately; use deterministic code/calculation rather than asking the language model to guess.
- **General conversation and explanation:** handle ordinary educational, practical, and everyday questions without requiring a dedicated device-control tool.

### K. Intent router and service architecture
- Add a central intent router that classifies requests into: TV/device action, live information lookup, general knowledge/explanation, calculation, and practical planning.
- Dispatch each class to a suitable capability/service; combine services when needed (e.g. weather plus time, news plus source summaries).
- Keep external API credentials server-side in Cloudflare Worker secrets; never embed provider keys in the APK or commit them to GitHub.
- Prefer reliable providers with clear terms, timestamps, and reasonable free tiers; cache only where appropriate and always disclose stale/cached data.
- Return structured results to the Android app so the UI can present concise answers and follow-up details consistently.
- Provide Arabic/Egyptian Arabic and English intent coverage, including natural variations rather than brittle exact-phrase matching.
- Do not expose unsupported features as available. If a service is not configured or its provider is down, explain the actual reason and offer a useful fallback.
- Create a capability matrix and category-level test suite. Test representative prompts and paraphrases for every category, including named cities, Arabic/English variants, missing details, stale data, provider errors, and offline operation. User should not have to discover each category gap manually.

## Validation and release gates — all required before APK build
1. Static review of Android code, manifest permissions, service lifecycle, and backend tool contracts.
2. Build and lint/test checks without distributing an APK.
3. Test ordinary features with no Shizuku/ADB authorization.
4. Test Shizuku detection, authorization, revocation, service stopped/restarted, and failure recovery.
5. Test direct ADB pairing, authorization, connection, reconnect, revocation, and failure recovery.
6. Test supported operations individually on physical Android TV/Google TV hardware; record Android version, vendor/model, privilege path, command, and observed result.
7. Test negative cases: denied permission, unsupported command, missing app, stale connection, and command failure.
8. Test DPAD/OK bubble control and Accessibility remote-key mapping on the target remote.
9. Verify no secrets, pairing codes, or sensitive session material are logged or committed.
10. Update user-facing help and capability matrix to match only verified behavior.
11. Only after all applicable gates pass, manually run GitHub Actions and publish the single consolidated **0.7.0** APK.

## Known current baseline (0.6.2 source)
- Usage Access history, recent/foreground usage estimates, app launching, volume, settings shortcuts, storage/RAM reporting, preliminary permission audit, own-cache cleanup, floating overlay, and Accessibility remote-key capture/mapping exist in source.
- Usage Access does not provide a complete live list of background processes.
- Accessibility is used for remote-key filtering/mapping; it does not grant general app/process management.
- Shizuku status detection and explicit authorization UI are present in source; no privileged operation is wired or verified yet. Direct ADB pairing/connection is not implemented.
- Force-stopping other apps, clearing other apps' cache, and rebooting are not currently verified capabilities.
- Floating bubble DPAD/OK handling exists in source but requires physical-device validation.

## Implementation progress (source changes only; not yet released)
- [x] Add backend live-information endpoint for current time, weather, reference USD/EGP exchange rate, indicative gold estimates, and recent news headlines.
- [x] Add Android intent routing and response rendering for those live-information categories.
- [x] Add general-assistant guidance for meal ideas, everyday knowledge, calculations, and safe healthy-weight explanations.
- [ ] Deploy and verify the Cloudflare Worker endpoints against their live providers.
- [ ] Build/lint the Android source and resolve any compile or static-analysis findings.
- [ ] Add general web search beyond the current news feed, and broaden currency-pair parsing.
- [ ] Add automated endpoint tests for provider timeouts, malformed results, unsupported cities/currencies, and rate limiting.
- [ ] Test Arabic/English speech variants on physical Android TV hardware.
- [x] Add Shizuku API/provider dependency, binder lifecycle listeners, status detection, privilege identity display, and explicit app authorization request UI (source only; compile/device validation pending).
- [ ] Implement direct ADB-over-Wi-Fi pairing/connection using a reviewed Android-compatible client library.
- [ ] Implement the shared capability center, Shizuku command execution bridge, operation-specific permission checks, and app/process actions.
- [ ] Continue brightness/VPN, floating assistant, and remaining release items.
- **Status caution:** checked source files are committed, but no APK has been built and no live service has been deployed or physically verified. Treat the new functionality as implementation-in-progress until those checks pass.

## Current decision
User approved **both Shizuku and direct ADB over Wi-Fi** for the 0.7.0 consolidated release. No APK should be issued until the combined feature set and validation gates are complete.


## Security-first automatic device authentication (mandatory before 0.7.0)

The manual `APP_ACCESS_TOKEN` entry is a development-only mechanism and must not ship in the public app. Do not replace it with a shared token, hard-coded API key, predictable installation ID, or a bearer credential embedded in the APK.

### Implementation progress (source only)
- [x] Add Worker challenge, first-enrollment, public-key verification, short-lived HMAC session issuance, and per-request device-status/session verification routes.
- [x] Require strongly consistent device/challenge Durable Objects, session signing secret, IP/device rate-limit bindings, and enrollment rate-limit binding; fail closed when required configuration is absent.
- [x] Add Android P-256 signing-key generation through Android Keystore, automatic enrollment/session acquisition, and a fresh installation identity if the local signing key is lost.
- [x] Move device records and single-use challenges to transactional Durable Objects; tighten JWT claim validation and DER ECDSA signature parsing.
- [x] Add backend source-check workflow for JavaScript syntax and Wrangler dry-run validation.
- [x] Remove manual Access Token entry UI and legacy token reads; clear old saved token preferences at app startup.
- [x] Document Cloudflare binding/secret setup, rate-limit guidance, administrator approval/revocation workflow, and known limitations in `backend/SECURITY-DEPLOYMENT.md`.
- [x] Add pending-by-default enrollment, protected administrator approval/revocation routes, and automated tests for pending/revoked device restrictions, administrator rate limiting, and verified first enrollment (backend CI and Wrangler dry-run passed on 2026-11-10).
- [ ] Configure Cloudflare staging bindings and secrets; run Worker endpoint tests.
- [ ] Compile Android app and test Keystore key generation/signing on Android TV devices.
- [ ] Test enrollment, challenge replay, invalid signatures, session expiry, revocation, key rotation, quota enforcement, and concurrent requests.
- [ ] Perform security review/penetration testing and confirm no secrets or private keys leak into APK/logs.
- **Status:** Authentication source code is committed, but not built, deployed, or validated. The Worker will return 503 until required Cloudflare bindings/secrets are configured. The backend CI workflow completed successfully for the pending/revoked-device tests on 2026-11-10; this validates source-level Worker checks only, not Cloudflare staging or Android hardware behavior. This is not a security certification and must not be released yet.

### Required design
- Use an Android Keystore-backed, per-install asymmetric key pair (P-256 / ECDSA SHA-256 for Android API compatibility). The private key must be non-exportable where the platform supports it; never serialize it into preferences, logs, crash reports, or network payloads.
- Enroll the public key with the Worker. Treat first enrollment as an abuse-sensitive operation: enforce IP and global registration throttles, cap active installations, and support revocation. A random installation ID is only an identifier, never proof of identity.
- Authenticate every session issuance/renewal with a fresh server challenge signed by the installation private key. Challenges must be unpredictable, short-lived, single-use, and bound to the installation ID and requested action to prevent replay.
- Issue short-lived, scoped session credentials only after signature verification. Protect session issuance with server-side signing keys; rotate credentials and reject expired, revoked, malformed, or replayed sessions.
- Bind authenticated API requests to the enrolled installation and apply per-install, per-IP, and global quotas. Use separate limits for enrollment, assistant inference, and live-information endpoints. Fail closed if required KV/DO/rate-limit bindings or signing secrets are absent.
- Keep Groq and all server signing/encryption secrets exclusively in Cloudflare secrets. Never commit secrets or return secret values in errors.
- Store only public key metadata, status, quota counters, and minimal audit data server-side. Do not store voice transcripts or personal content for authentication. Define retention and deletion behavior.
- Add abuse controls: revoke/block one installation, rotate server keys, cap registrations, detect burst traffic, and return generic authentication errors without leaking whether an installation exists.
- Remove the manual token UI, token preferences, and all legacy token reads only after both Android and Worker authentication paths are implemented and tested together. During migration, do not silently fall back to the old shared token for public releases.

### Mandatory security tests
- First enrollment, repeated enrollment, duplicate IDs, malformed/oversized keys, and enrollment floods.
- Valid signature, invalid signature, wrong key, altered challenge, expired challenge, challenge replay, and cross-install replay.
- Session expiry, renewal, revoked installation, server key rotation, and missing/failed storage binding.
- Concurrent requests, per-device/IP/global quota enforcement, and generic error responses.
- Confirm private keys are non-exportable where supported and never appear in app storage exports, logs, network captures, Git history, or APK strings.
- Verify the public APK contains no `APP_ACCESS_TOKEN`, `GROQ_API_KEY`, signing secret, or reusable shared credential.
- Test on representative Android TV/Google TV devices across the app's supported API range; document devices where hardware-backed Keystore is unavailable and the fallback security posture.

### Release gate
- This authentication work is **not complete** merely because the token field is hidden or removed.
- Keep the existing development path out of any public release until the replacement protocol is operational end-to-end.
- Do not build or distribute 0.7.0 until implementation, automated security tests, backend deployment verification, and physical-device validation pass.


## Phone companion decision (2026-11-11)

The selected Android phone app is a **companion controller for the TV assistant**, not a standalone phone assistant. The TV remains the authority for executing TV actions; the phone handles pairing, touch/voice input, remote controls, and displaying observed results. The trust boundary and proposed protocol are documented in `docs/PHONE-COMPANION-ARCHITECTURE.md`.

- [x] Record the product decision and trust boundary.
- [ ] Implement TV-side temporary QR pairing and explicit pairing confirmation.
- [ ] Implement phone-side QR scanning and Android Keystore device identity.
- [ ] Implement authenticated encrypted local transport with expiry, replay prevention, rate limits, and revocation.
- [ ] Implement a typed allowlisted command protocol; never accept arbitrary shell commands from the phone.
- [ ] Implement the phone UI/module after the protocol contract is stable.
- [ ] Add physical phone + TV integration tests before publishing a phone APK.
