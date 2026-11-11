# Mohamed TV AI Assistant

Arabic/English voice assistant for Android TV and Google TV, with a planned Android phone companion controller.

## Project status

The repository contains the TV application source and a Cloudflare Worker backend. The phone companion has been selected as the next product direction and its security/transport architecture is documented in [Phone Companion Architecture](docs/PHONE-COMPANION-ARCHITECTURE.md). The phone app is **not implemented or available as an APK yet**.

The current TV source is version **0.6.9** (versionCode 21). The existing GitHub Actions workflow produces a temporary **debug APK artifact**, not a permanent signed release. Do not treat it as a production release.

## Main components

- `tv-app/`: Android TV / Google TV app. TV-side permissions and device operations remain on the television.
- `backend/`: Cloudflare Worker source for assistant and live-information APIs, including per-install device authentication.
- `docs/`: implementation plans, security requirements, and validation gates.

## Build the current TV debug APK

1. Open the repository's **Actions** tab.
2. Run **Build Mohamed TV AI Assistant APK** or open the latest successful run.
3. Download the artifact named for the TV debug build and extract the APK.
4. Install it on a compatible Android TV / Google TV device.

The artifact is temporary and expires according to the workflow retention setting. A stable, signed release build still requires a securely retained signing identity and completed release checks.

## Security status

- Do not put Groq keys, Worker signing secrets, administrator secrets, shared API tokens, or private signing keys in source code or either APK.
- The TV app's device identity is designed around Android Keystore. Backend authentication source code is not equivalent to a completed security audit.
- Cloudflare bindings/secrets, deployment, Android compilation, physical-device integration tests, and security tests must pass before describing cloud authentication as operational.
- The phone companion must pair locally with explicit user confirmation and use authenticated encrypted transport. It must not receive TV-side Shizuku/ADB privileges.

## Phone companion

The selected phone app is a **remote companion for the TV assistant**. It will provide QR pairing, remote controls, command input, and TV response/status display. The TV remains responsible for executing TV operations. See [the architecture and implementation checklist](docs/PHONE-COMPANION-ARCHITECTURE.md).

## License

This project is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text. Third-party components remain subject to their respective licenses and notices.

## Contributing and release status

The source is under active development. Features marked planned or in-progress are not guaranteed to work. Consult the roadmap and test results before relying on any feature. No independent security certification is claimed.
