# Phone companion architecture

## Product decision

The Android phone app is a **companion controller for the TV assistant**, not a standalone phone assistant. The TV app remains the execution authority for TV actions. The phone provides a touch-friendly remote interface, text/voice input, connection management, and status/response display.

## Components

- `:tv-app`: existing Android TV / Google TV assistant. It owns TV permissions, Android system APIs, Shizuku/ADB integrations, and executes approved device actions.
- `:phone-app` (planned module): Android phone companion. It must not request Shizuku, Accessibility, overlay, usage access, or TV-side permissions.
- Local transport: authenticated, encrypted communication over the same local network. Cloud AI remains accessed by the TV assistant/backend; the phone must not contain Groq or Worker secrets.

## Pairing and trust model

1. The TV app explicitly enables a temporary pairing screen after the user chooses **Pair a phone**.
2. The TV displays a QR code containing only a short-lived pairing endpoint, a random one-time pairing nonce, protocol version, and a TV ephemeral public key. It must not contain an API token, permanent password, or cloud session.
3. The phone scans the QR code and proves possession of a newly generated Android Keystore key. The TV asks the user to confirm the phone name and a matching short verification code on both screens.
4. Pairing is single-use, expires quickly, is rate-limited, and is invalidated after success or cancellation. The TV stores only the phone public key and a revocable device identifier.
5. Subsequent sessions use fresh challenges and signed requests; replayed, expired, malformed, or revoked sessions are rejected. Use TLS with certificate/public-key pinning or an equivalently reviewed authenticated channel. Do not silently fall back to unauthenticated HTTP.
6. The TV offers a paired-device list with last-seen information and an immediate revoke action. Pairing is local-network only by default; no port forwarding or internet exposure.

## Command boundary

- Phone requests are declarative intents, not shell commands or arbitrary URLs.
- TV validates every command against a strict allowlist, current device capability, permission state, and user confirmation requirements.
- Destructive or disruptive actions (reboot, force-stop, cache cleanup, permission changes) require confirmation on the TV.
- The TV reports observed results. The phone must not display success based only on a request being sent.
- The phone cannot invoke Shizuku/ADB directly and cannot bypass Android permission prompts.

## Initial phone scope

1. QR pairing and reconnect.
2. Connection state and paired-TV management.
3. Remote-friendly touch controls: navigation, OK, Back, Home, volume, mute, and app-launch shortcuts where the TV confirms support.
4. Text command entry and optional phone microphone input.
5. Display the TV assistant's response and actual operation status.
6. Arabic and English interface, responsive layouts, and accessible controls.

Voice recognition on the phone is an input method only; execution still occurs on the TV. The TV may continue using its own microphone independently.

## Implementation sequence

- [x] Confirm the phone product is a companion controller, not a standalone assistant.
- [x] Record the trust boundary, pairing model, and command safety rules.
- [ ] Implement the TV-side local pairing server and explicit pairing screen.
- [ ] Implement QR generation/scanning and the phone Keystore identity.
- [ ] Implement encrypted authenticated transport, replay protection, expiry, rate limits, and revocation.
- [ ] Implement a typed command protocol and TV-side capability/permission checks.
- [ ] Add the phone Android module and build workflow only after the protocol contract is stable.
- [ ] Test on physical phone + Android TV/Google TV on the same LAN, including reconnect, router isolation, wrong network, replay, revocation, and TV restart.

## Explicit non-goals

- No shared token embedded in either APK.
- No user-entered API token.
- No direct phone-to-Shizuku or phone-to-ADB execution.
- No unauthenticated LAN endpoint, open WebSocket, or internet-exposed TV control port.
- No claim that the companion is working until both ends are implemented and tested on physical devices.
