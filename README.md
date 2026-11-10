# Mohamed TV AI Assistant

Android TV / Google TV voice assistant. The project is maintained from a phone using GitHub and GitHub Actions.

## Version 0.2.0
- Keeps the TV-first native Java app and remote-friendly interface.
- Adds Arabic phonetic normalization for VPN (including "في بي ان") and Downloader.
- Adds shortcuts for VPN settings, system language settings, Wi-Fi/network settings, developer options, and Downloader.
- Gives an explicit explanation that Android does not allow an ordinary app to change the system language or silently connect/disconnect a VPN.
- Keeps YouTube, Google Play, Settings, volume, display settings, screen saver, and help commands.
- Uses a stable project-specific debug signing key so future debug builds can update this installation without a signature mismatch.

## Build from GitHub Actions
1. Open **Actions**.
2. Select **Build Mohamed TV AI Assistant APK**.
3. Choose **Run workflow** on `main` if a push did not start it automatically.
4. Open the successful run and download `Mohamed-TV-AI-Assistant-0.2.0-debug`.
5. Extract the ZIP and install `tv-app-debug.apk` on the TV.

## Important limitations
- This is a local-command milestone, not yet a cloud AI assistant. Gemini, DeepSeek, and OpenAI are not connected.
- The fixed signing key is for this private debug project only. It is not a Play Store/release signing key. Keep the repository private and do not reuse this key for another app.
- The system language page opens, but the user must select English manually. Android restricts ordinary apps from changing the system locale.
- VPN settings open, but connecting or disconnecting depends on the installed VPN app and Android's user-consent/security rules.
- Downloader is opened only if its expected package ID is installed.
- Speech recognition accuracy and available languages depend on the TV's speech service and installed language packs.
- Phone companion, QR pairing, secure local transport, and cloud AI backend are future milestones.

## Next
1. Test version 0.2.0 on the same TV.
2. Record which Arabic/English phrases work and which do not.
3. Improve intent matching based on actual TV tests.
4. Add secure AI-provider integration through a backend, never by embedding API keys in the APK.
5. Build the phone companion after the TV app is stable.
