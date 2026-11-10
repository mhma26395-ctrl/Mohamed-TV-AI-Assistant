# Mohamed TV AI Assistant

Android TV / Google TV voice assistant starter project. The project is designed to be maintained from a phone using GitHub and GitHub Actions.

## Version 0.1.0 scope
- TV-first native Android app (Java).
- Arabic/English speech-recognition entry point.
- Local commands: launch YouTube, Google Play, Settings; adjust media volume; help.
- Text-to-speech response when the device has a suitable voice installed.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact.

## Build from GitHub Actions
1. Open the **Actions** tab and enable workflows if GitHub asks.
2. Select **Build Mohamed TV AI Assistant APK**.
3. Choose **Run workflow** on the `main` branch.
4. Open the completed run and download artifact `Mohamed-TV-AI-Assistant-debug`.
5. Extract the ZIP and install `tv-app-debug.apk` on a compatible TV for testing.

## Important limitations
- This is an early test build, not a finished AI assistant.
- Speech recognition availability and offline behavior depend on the TV's installed recognition service and language packs. The initial recognizer requests Arabic (Egypt).
- This build does not yet call cloud AI providers. Gemini, DeepSeek, and OpenAI adapters are planned for a later milestone. Provider API keys must not be embedded in the TV APK.
- Phone companion, QR pairing, secure local transport, app inventory, screenshots, and broad in-app navigation are not implemented yet.
- Android does not guarantee that a normal app can automate arbitrary third-party app interfaces. Later control features will use public APIs and user-enabled capabilities only.
- The debug APK is for testing, not Play Store release. A production signing key must be created and stored as GitHub Actions secrets.

## Next milestones
1. Verify this TV APK on at least one Google TV / Android TV device.
2. Improve Arabic and English command matching and error handling.
3. Add secure phone pairing and the phone companion app.
4. Add a backend AI adapter interface for Gemini, DeepSeek, and OpenAI.
5. Add explicit confirmation for consequential commands and privacy controls.
