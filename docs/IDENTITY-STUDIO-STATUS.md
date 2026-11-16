# Mora Identity Studio — Phase 1–10 implementation status

This matrix distinguishes code that is present from platform coverage that still needs validation. A profile label is not a physical device emulator.

| Phase | Current state | Implemented now | Remaining verification or gap |
|---|---|---|---|
| 1 — Identity Engine | Implemented for app-scoped browser compatibility | Read-only Android device snapshot; separate local browser profiles; select/reset/favorites/custom profiles | No global device identity rewrite; external browsers are not silently modified |
| 2 — Profile Database | Expanded curated catalogue | Android OEMs and legacy models; Pixel 11 / Pixel 11 Pro / Pixel 11 Pro XL, Galaxy S26 series; iPhone 18 Pro/Pro Max; iPhone/iPad generations; Windows PC/laptop; macOS hardware generations; Linux distro compatibility templates; search/filter in Android UI | Still not exhaustive; newest presets are browser compatibility fixtures, not verified physical-device emulation; some older UA strings are historical samples |
| 3 — Randomizer Engine | Implemented with Android scheduling caveat | Reusable seed; deterministic seed/scope/run selection; one-shot and 6–168 hour WorkManager schedule; optional randomization of enabled per-app non-security labels | Android may defer periodic work; target app processes may need restart; real-device battery/reboot testing required |
| 4 — Browser Identity Engine | Partial | User-Agent selection; supported Chromium User-Agent Metadata/Client Hints; custom profiles; favorites; Android Storage Access Framework file import/export plus schema-1 profile exchange | Client Hints are engine/version dependent; Safari/Firefox hints are not fabricated; desktop helper requires explicit command-line use |
| 5 — Fingerprint Protection | Partial, scoped across Android preview and Linux isolated profiles | Android Canvas/WebGL/WebRTC preview guards; Firefox-family RFP/WebGL/WebRTC preferences; Chromium-family page-context Canvas guard extension, WebGL disable switch and non-proxied UDP restriction | No global Android browser injection; Chromium worker Canvas, fonts/audio/GPU timing and other surfaces remain outside coverage; browser-specific settings can break sites |
| 6 — Android Integration & Recovery | Partial | WebView integration; browser discovery; read-only KSU/Magisk/LSPosed footprint checks; per-app compatibility labels; versioned backup/restore/rollback | External browsers are discovery-only; hook activation and process behavior must be verified with the actual ROM/LSPosed setup |
| 7 — Linux Browser Integration | Functional isolated desktop companion; physical browser validation pending | Firefox, LibreWolf, Zen, Brave, Chrome/Chromium; CLI plus lightweight Tk dashboard; native and common Flatpak detection/launch; separate profiles; Firefox built-in privacy controls; Chromium Canvas MV3 guard and WebGL/WebRTC options; doctor, import/export, diagnostics, backup/restore/rollback | Browser-specific toggles are best-effort; Chromium Canvas guard does not cover workers; test all six browsers on actual installed builds |
| 8 — Cross-Platform UI | Partial, direct manual LAN sync added | Android Compose Identity Studio; Linux Tk dashboard; private-LAN two-way custom-profile sync over pinned HTTPS with short-lived token; profile-only JSON fallback; searchable catalogue and recovery controls | No always-on/continuous sync; built-in presets, app settings, schedules and browser data are not shared; validate on a real Wi-Fi LAN |
| 9 — Fingerprint Diagnostics | Expanded local surface report | Android/Linux UA, Client Hints when available, Canvas, WebGL, WebRTC API, locale, timezone and screen; Android before/after baseline plus identity-surface report; Linux JSON audit and comparison | Reports inspect browser APIs/metadata, not public IP, DNS or routes; API visibility is not proof of an actual network leak |
| 10 — Build, Testing & Release | CI pipeline present; HyperOS and browser acceptance still pending | Android Gradle unit tests include LAN sync input validation; Linux tests cover pinned-session handler auth, idempotency and conflict protection; debug APK CI; protected signed release workflow; ADB HyperOS acceptance script | Latest CI must pass; install and test on a physical HyperOS device plus each installed Linux browser; no physical device farm is attached to this PR |

## Performance and safety boundaries

- The Android Identity Studio stays in the existing Mora Shield application; no second Android app is introduced.
- Randomizer and diagnostic state remain app-private. Empty profile catalogues are handled without crashing a scheduled randomizer; manual randomization refreshes the visible selected profile. Desktop profiles are stored under Mora's own configuration directory and use separate browser data directories.
- The Android preview blocks network loads and external navigation. WebView is destroyed when the preview leaves composition.
- Linux helper uses Python standard library only; Tkinter is loaded only when the GUI command is used. Browser launching remains opt-in and opens only Mora-created profiles.
- Original-device backup stores only read-only public Build labels and is reference metadata; restoring a backup never rewrites Android's live device identity.
- No IMEI, serial, Android ID, MAC address, build fingerprint, security patch, hardware attestation or Play Integrity value is fabricated, modified or bypassed.
- iOS, Windows, macOS and Linux labels are browser compatibility test profiles, not real device/OS emulation.

## Final acceptance checklist

1. Latest Android CI and signed release workflow both complete successfully for the current PR head.
2. Install the APK on a physical Android device and test profile switching, Client Hints support, Canvas/WebGL/WebRTC controls, scheduling, backup/restore and rollback.
3. Test per-app compatibility only on a non-critical app with the intended LSPosed scope and verify process restart behavior.
4. On Linux, run doctor and exercise every browser that is actually installed; confirm normal browser profiles remain unchanged.
5. Test Android ↔ Linux schema-1 profile import/export and compare before/after reports. For direct sync, verify the temporary certificate pin and repeat a sync to check idempotency.
6. On Linux, run `python mora_identity.py gui`; verify each installed browser and protection option in its own isolated profile.
7. On HyperOS, run the ADB preflight and complete the manual device checklist. CI cannot perform this physical-device acceptance.
