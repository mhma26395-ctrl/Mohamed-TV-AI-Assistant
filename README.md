# Mohamed TV AI Assistant

Arabic/English voice assistant for Android TV and Google TV, with a planned Android phone companion controller.

## Project status

The repository contains the TV application source and a Cloudflare Worker backend. The phone companion has been selected as the next product direction and its security/transport architecture is documented in [Phone Companion Architecture](docs/PHONE-COMPANION-ARCHITECTURE.md). The phone app is **not implemented or available as an APK yet**.

The tested Android TV / Google TV APK is **0.6.9** (versionCode 21). The signed APK was built and signature-verified in GitHub Actions run [43003494402](https://github.com/mhma26395-ctrl/Mohamed-TV-AI-Assistant/actions/runs/43003494402). The release workflow can publish that exact tested artifact as a GitHub Release.

## Support the project

If you find Mohamed TV AI Assistant useful and would like to support its development, you can send a voluntary contribution through PayPal:

**[Support Mohamed TV AI Assistant via PayPal](https://www.paypal.com/qrcodes/p2pqrc/VV4EEWFUZ8FCU)**

The PayPal QR code supplied for this project displays the recipient name **Ali Mostafa**. Contributions are voluntary and are not required to use the app.

## Main components

- `tv-app/`: Android TV / Google TV app. TV-side permissions and device operations remain on the television.
- `backend/`: Cloudflare Worker source for assistant and live-information APIs, including per-install device authentication.
- `docs/`: implementation plans, security requirements, and validation gates.

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE).

## Published APK

The signed APK is published under GitHub **Releases** when the maintainer runs **Publish tested Mohamed TV AI Assistant release** from the Actions tab. The workflow republishes the already-tested and signed artifact from run 43003494402; it does not rebuild or change the APK.

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

## Contributing and release status

The source is under active development. Features marked planned or in-progress are not guaranteed to work. Consult the roadmap and test results before relying on any feature. No independent security certification is claimed.
