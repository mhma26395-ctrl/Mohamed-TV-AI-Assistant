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

### G. Assistant language and tool reliability
- Review Arabic/Egyptian Arabic and English intents and synonyms.
- Ensure AI tool responses reflect actual device-side results.
- Do not let the model claim an operation succeeded merely because it proposed a tool call.
- Provide clear unsupported/permission-required/device-rejected responses.

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
- Shizuku and direct ADB integration are not yet implemented.
- Force-stopping other apps, clearing other apps' cache, and rebooting are not currently verified capabilities.
- Floating bubble DPAD/OK handling exists in source but requires physical-device validation.

## Current decision
User approved **both Shizuku and direct ADB over Wi-Fi** for the 0.7.0 consolidated release. No APK should be issued until the combined feature set and validation gates are complete.
