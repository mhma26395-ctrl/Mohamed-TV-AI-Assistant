# Companion protocol v1

This file is the normative wire contract for the phone companion. The TV is the execution authority.

## Transport and identity

- HTTPS only on the local network. No plaintext fallback.
- The QR code pins the TV installation's certificate SHA-256 fingerprint and carries only a one-time 120-second pairing nonce, address, port, and protocol version.
- The phone creates a P-256 signing key in Android Keystore. The TV stores its public key and a random revocable phone ID.
- Pairing requires explicit confirmation on the TV and is consumed exactly once.
- Every later request is signed, fresh, time-bounded, and replay-protected. A request ID is idempotent; counters must increase per paired phone.
- The TV can revoke a phone at any time. Revocation invalidates all sessions.

## Command envelope

Every request contains exactly the fields listed in `companion-protocol-v1.json`. Reject unknown fields, unknown command names, oversized bodies, invalid signatures, stale timestamps, reused counters, duplicate request IDs with different content, and revoked identities.

Commands are typed and allowlisted. Never accept shell text, arbitrary intent URIs, arbitrary package names, file paths, or backend credentials from the phone.

## Initial command groups

- Navigation: up, down, left, right, ok, back, home.
- Media: volume up/down and mute.
- Assistant: submit text or a phone-recognized voice transcript to the existing TV assistant flow.
- Apps: launch only a package the TV itself has enumerated and confirmed.

## Execution and confirmation

The TV validates each command against the active device, OS version, current permissions, and supported capability. Risky operations remain TV-confirmed and are intentionally excluded from v1. The phone shows the TV's observed result; sending a request is never treated as proof of success.

## Limits

- Maximum JSON request body: 8 KiB.
- Pairing nonce lifetime: 120 seconds, one use.
- Session challenge lifetime: 300 seconds, one use.
- Rate limits: 10 pairing attempts per minute per TV and 30 commands per minute per paired phone (initial conservative defaults; tune only after tests).
- No internet port forwarding or remote access.
